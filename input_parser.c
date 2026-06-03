#include <stdio.h>
#include <stdlib.h>
#include <ctype.h>
#include "tsptw_helper.h"

int *xs = NULL;
int *ys = NULL;
int *openTimes = NULL;
int *closeTimes = NULL;

int getCityCount(const char *filePath)
{
    FILE *file = fopen(filePath, "r");
    if (!file)
    {
        printf("File is not found!");
        return 0;
    }

    fseek(file, 0, SEEK_END);
    long size = ftell(file);
    if (size == 0)
    {
        printf("File cannot be empty!");
        fclose(file);
        return 0;
    }

    long currentPosition = size - 1;
    int ch;
    int isContentFound = 0;

    while (currentPosition >= 0)
    {
        fseek(file, currentPosition, SEEK_SET);
        ch = fgetc(file);

        if (ch == '\n' && isContentFound)
            break;
        else if (!isspace(ch))
            isContentFound = 1;

        currentPosition--;
    }

    currentPosition += 1;
    fseek(file, currentPosition, SEEK_SET);
    int lastId = 0;

    if (fscanf(file, "%d", &lastId) == 1)
    {
        fclose(file);
        return lastId + 1;
    }

    fclose(file);
    return 0;
}

void initializeArrays(const char *filePath)
{
    int numberOfCities = getCityCount(filePath);
    if (numberOfCities == 0)
        return;

    xs = (int *)malloc(numberOfCities * sizeof(int));
    ys = (int *)malloc(numberOfCities * sizeof(int));
    openTimes = (int *)malloc(numberOfCities * sizeof(int));
    closeTimes = (int *)malloc(numberOfCities * sizeof(int));

    FILE *file = fopen(filePath, "r");
    if (!file)
    {
        printf("File is not found!");
        return;
    }

    int cityId, x, y, openTime, closeTime;
    while (fscanf(file, "%d %d %d %d %d", &cityId, &x, &y, &openTime, &closeTime) == 5)
    {
        xs[cityId] = x;
        ys[cityId] = y;
        openTimes[cityId] = openTime;
        closeTimes[cityId] = closeTime;
    }

    fclose(file);
}

void freeAll()
{
    free(xs);
    free(ys);
    free(openTimes);
    free(closeTimes);
}
