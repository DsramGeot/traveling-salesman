#ifndef TSPHELPER_H
#define TSPHELPER_H

extern int *xs;
extern int *ys;
extern int *openTimes;
extern int *closeTimes;

typedef struct
{
    int cityIndex;
    long long distance;
} Neighbor;

typedef struct State
{
    struct State *parent;
    int city;
    long long time;
    long long totalDistance;
    int depth;
    int refCount;
} State;

typedef struct
{
    int numberOfCities;
    long long totalDistance;
    long long timeToComplete;
} Solution;

// input_parser.c
int getCityCount(const char*);
void initializeArrays(const char*);
void freeAll();

// output_writer.c
void outputWriter(const char*, int*, int, long long, long long);

#endif 