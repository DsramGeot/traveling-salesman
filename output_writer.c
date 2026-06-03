#include <stdio.h>
#include <stdlib.h>
#include "tsptw_helper.h"

void outputWriter(const char *filePath, int *path, int pathLength, long pathDistance, long pathTime)
{
    FILE *file = fopen(filePath, "w");
    if (!file)
    {
        printf("Error: Output could not be written!");
        return;
    }
    fprintf(file, "%d %ld %ld\n", pathLength, pathDistance, pathTime);
    for (int i = 0; i < pathLength; i++)
        fprintf(file, "%d\n", path[i]);

    fclose(file);
}