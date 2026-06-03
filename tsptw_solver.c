#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <omp.h>
#include <time.h>
#include <limits.h>
#include "tsptw_helper.h"

#define MAX_NEIGHBORS 800
#define MAX_PATHS_PER_CITY 5

long getDistance(int i, int j)
{
    long dx = xs[i] - xs[j];
    long dy = ys[i] - ys[j];
    return (long)floor(sqrt(dx * dx + dy * dy) + 0.5);
}

// Creates a new visited city
State *createState(State *parent, int city, long time, long totalDistance, int depth)
{
    State *s = (State *)malloc(sizeof(State));
    s->parent = parent;
    if (parent)
    {
#pragma omp atomic
        parent->refCount++;
    }
    s->city = city;
    s->time = time;
    s->totalDistance = totalDistance;
    s->depth = depth;
    s->refCount = 0;
    return s;
}

// When a path is cancelled
void releaseState(State *currentState)
{
    while (currentState)
    {
        int newCount;
#pragma omp atomic capture
            newCount = --currentState->refCount;
        
        if (newCount == 0)
        {
            State *parent = currentState->parent;
            free(currentState);
            currentState = parent;
        }
        else
            break;
    }
}

int findStart(State *s)
{
    while (s->parent != NULL)
        s = s->parent;
    return s->city;
}

int isBetterSolutionThan(Solution *current, Solution *alternative)
{
    if (current->numberOfCities != alternative->numberOfCities)
        return current->numberOfCities > alternative->numberOfCities;
    else
    {
        if (current->totalDistance != alternative->totalDistance)
            return current->totalDistance < alternative->totalDistance;
        else
            return current->timeToComplete < alternative->timeToComplete;
    }
}

int main(int argc, char **argv)
{
    if (argc != 3)
    {
        printf("Input and output file paths should be provided!\n");
        return 1;
    }

    const char *inputFile = argv[1];
    const char *outputFile = argv[2];

    double start = omp_get_wtime();

    int numberOfNodes = getCityCount(inputFile);
    if (numberOfNodes == 0)
    {
        printf("Input file should contain cities in the given format!\n");
        return 1;
    }
    printf("Total number of cities in the input file: %d\n", numberOfNodes);
    initializeArrays(inputFile);

    int **neighbors = (int **)malloc(numberOfNodes * sizeof(int *));
    int *neighborCounts = (int *)malloc(numberOfNodes * sizeof(int));

// Best neighbor arrays and counts are filled
#pragma omp parallel for
    for (int i = 0; i < numberOfNodes; i++)
    {
        neighbors[i] = (int *)malloc(MAX_NEIGHBORS * sizeof(int));
        Neighbor bestNeighbors[MAX_NEIGHBORS];
        int count = 0;

        for (int j = 0; j < numberOfNodes; j++)
        {
            if (i == j)
                continue;
            long distance = getDistance(i, j);

            if (count < MAX_NEIGHBORS)
            {
                int indexToInsert = count - 1;
                while (indexToInsert >= 0 && bestNeighbors[indexToInsert].distance > distance)
                {
                    bestNeighbors[indexToInsert + 1] = bestNeighbors[indexToInsert];
                    indexToInsert--;
                }
                indexToInsert++;
                bestNeighbors[indexToInsert].distance = distance;
                bestNeighbors[indexToInsert].cityIndex = j;
                count++;
            }
            else if (distance < bestNeighbors[MAX_NEIGHBORS - 1].distance)
            {
                int indexToInsert = MAX_NEIGHBORS - 2;
                while (indexToInsert >= 0 && bestNeighbors[indexToInsert].distance > distance)
                {
                    bestNeighbors[indexToInsert + 1] = bestNeighbors[indexToInsert];
                    indexToInsert--;
                }
                indexToInsert++;
                bestNeighbors[indexToInsert].distance = distance;
                bestNeighbors[indexToInsert].cityIndex = j;
            }
        }
        for (int k = 0; k < count; k++)
            neighbors[i][k] = bestNeighbors[k].cityIndex;

        neighborCounts[i] = count;
    }

    // Local beam search
    int currentBeamCapacity = numberOfNodes * MAX_PATHS_PER_CITY;
    State **currentBeam = (State **)malloc(currentBeamCapacity * sizeof(State *));
    int currentBeamSize = numberOfNodes;

    for (int i = 0; i < numberOfNodes; i++)
    {
        State *state = createState(NULL, i, openTimes[i], 0, 1);
        if (state)
        {
#pragma omp atomic
            state->refCount++;
        }
        currentBeam[i] = state;
    }

    int bestDepth = 1;

    State ***nextBest = (State ***)malloc(numberOfNodes * sizeof(State **));
    omp_lock_t *cityLocks = (omp_lock_t *)malloc(numberOfNodes * sizeof(omp_lock_t));
    for (int i = 0; i < numberOfNodes; i++)
    {
        nextBest[i] = (State **)malloc(MAX_PATHS_PER_CITY * sizeof(State *));
        omp_init_lock(&cityLocks[i]);
    }

    for (int depth = 2; depth <= numberOfNodes; depth++)
    {
        for (int i = 0; i < numberOfNodes; i++)
            for (int m = 0; m < MAX_PATHS_PER_CITY; m++)
                nextBest[i][m] = NULL;

        int isBetterPathFound = 0;

#pragma omp parallel
        {
            int *visitedLocal = (int *)calloc(numberOfNodes, sizeof(int));

#pragma omp for
            for (int i = 0; i < currentBeamSize; i++)
            {
                State *state = currentBeam[i];

                State *temp = state;
                while (temp != NULL)
                {
                    visitedLocal[temp->city] = 1;
                    temp = temp->parent;
                }

                int limit = neighborCounts[state->city];
                for (int k = 0; k < limit; k++)
                {
                    int newCity = neighbors[state->city][k];

                    if (visitedLocal[newCity])
                        continue;

                    long distance = getDistance(state->city, newCity);
                    long arrival = state->time + distance;

                    if (arrival <= closeTimes[newCity])
                    {
                        long departure = (arrival > openTimes[newCity]) ? arrival : openTimes[newCity];

                        State **topPaths = nextBest[newCity];
                        if (topPaths[MAX_PATHS_PER_CITY - 1] == NULL || departure < topPaths[MAX_PATHS_PER_CITY - 1]->time)
                        {

                            omp_set_lock(&cityLocks[newCity]);

                            if (topPaths[MAX_PATHS_PER_CITY - 1] == NULL || departure < topPaths[MAX_PATHS_PER_CITY - 1]->time)
                            {
                                State *newState = createState(state, newCity, departure, state->totalDistance + distance, depth);
                                isBetterPathFound = 1;

                                State *dropped = topPaths[MAX_PATHS_PER_CITY - 1];

                                int indexToInsert = MAX_PATHS_PER_CITY - 1;
                                while (indexToInsert > 0 && (topPaths[indexToInsert - 1] == NULL || departure < topPaths[indexToInsert - 1]->time))
                                {
                                    topPaths[indexToInsert] = topPaths[indexToInsert - 1];
                                    indexToInsert--;
                                }

                                topPaths[indexToInsert] = newState;
                                if (newState)
                                {
#pragma omp atomic
                                    newState->refCount++;
                                }
                                if (dropped != NULL)
                                    releaseState(dropped);
                            }
                            omp_unset_lock(&cityLocks[newCity]);
                        }
                    }
                }
                temp = state;
                while (temp != NULL)
                {
                    visitedLocal[temp->city] = 0;
                    temp = temp->parent;
                }
            }

            free(visitedLocal);
        }

        if (!isBetterPathFound)
        {
            printf("Beam Search Completed!\n");
            break;
        }

        for (int i = 0; i < currentBeamSize; i++)
            releaseState(currentBeam[i]);

        currentBeamSize = 0;
        for (int i = 0; i < numberOfNodes; i++)
        {
            for (int m = 0; m < MAX_PATHS_PER_CITY; m++)
            {
                if (nextBest[i][m] != NULL)
                {
                    currentBeam[currentBeamSize++] = nextBest[i][m];
                    if (nextBest[i][m])
                    {
#pragma omp atomic
                        nextBest[i][m]->refCount++;
                    }
                }
                else
                    break;
            }
        }

        for (int i = 0; i < numberOfNodes; i++)
            for (int m = 0; m < MAX_PATHS_PER_CITY; m++)
                if (nextBest[i][m] != NULL)
                    releaseState(nextBest[i][m]);

        bestDepth = depth;
        printf("New Maximum Path Length: %d - Number of Paths to Check: %d\n", depth, currentBeamSize);
    }

    Solution bestSolution;
    bestSolution.numberOfCities = -1;
    bestSolution.timeToComplete = LONG_MAX;
    bestSolution.totalDistance = LONG_MAX;

    State *bestState = NULL;

    for (int i = 0; i < currentBeamSize; i++)
    {
        State *state = currentBeam[i];
        int startCity = findStart(state);
        long returnDistance = getDistance(state->city, startCity);
        long totalDistance = state->totalDistance + returnDistance;
        long finalTime = state->time + returnDistance;

        Solution currentSolution;
        currentSolution.numberOfCities = bestDepth;
        currentSolution.totalDistance = totalDistance;
        currentSolution.timeToComplete = finalTime;

        if (isBetterSolutionThan(&currentSolution, &bestSolution))
        {
            bestSolution = currentSolution;
            bestState = state;
        }
    }

    if (bestState != NULL)
    {
        double end = omp_get_wtime();
        double elapsedTime = end - start;
        printf("\n");
        printf("Maximum Path Length: %d\n", bestSolution.numberOfCities);
        printf("Total Distance : %ld\n", bestSolution.totalDistance);
        printf("Completion Time: %ld\n", bestSolution.timeToComplete);
        printf("Elapsed time to find the path: %.2f seconds\n", elapsedTime);

        int *path = (int *)malloc(bestDepth * sizeof(int));
        State *currentCity = bestState;
        for (int i = bestDepth - 1; i >= 0; i--)
        {
            path[i] = currentCity->city;
            currentCity = currentCity->parent;
        }

        outputWriter(outputFile, path, bestSolution.numberOfCities, bestSolution.totalDistance, bestSolution.timeToComplete);
        free(path);
    }
    else
        printf("\nThe algorithm could not find any valid path.\n");

    for (int i = 0; i < currentBeamSize; i++)
        releaseState(currentBeam[i]);
    free(currentBeam);

    for (int i = 0; i < numberOfNodes; i++)
    {
        omp_destroy_lock(&cityLocks[i]);
        free(nextBest[i]);
        free(neighbors[i]);
    }
    free(cityLocks);
    free(nextBest);
    free(neighbors);
    free(neighborCounts);
    freeAll();

    return 0;
}
