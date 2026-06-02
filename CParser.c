#include <stdio.h>
#include <stdlib.h>
#include <ctype.h>

int *xs = NULL;
int *ys = NULL;
int *open_times = NULL;
int *close_times = NULL;

int getNodeCount(const char* filePath) {
    FILE *file = fopen(filePath, "r");
    if (!file) return 0;

    fseek(file, 0, SEEK_END);
    long size = ftell(file);
    if (size == 0) {
        fclose(file);
        return 0;
    }

    long pos = size - 1;
    int ch;
    int found_content = 0;
    
    while (pos >= 0) {
        fseek(file, pos, SEEK_SET);
        ch = fgetc(file);
        
        if (ch == '\n') {
            if (found_content) {
                break;
            }
        } else if (!isspace(ch)) {
            found_content = 1;
        }
        pos--;
    }
    
    fseek(file, pos + 1, SEEK_SET);
    int id = 0;
    
    if (fscanf(file, "%d", &id) == 1) {
        fclose(file);
        return id + 1;
    }
    
    fclose(file);
    return 0;
}

void init_arrays(const char* filePath) {
    int number_of_nodes = getNodeCount(filePath);
    if (number_of_nodes <= 0) return;

    xs = (int*)malloc(number_of_nodes * sizeof(int));
    ys = (int*)malloc(number_of_nodes * sizeof(int));
    open_times = (int*)malloc(number_of_nodes * sizeof(int));
    close_times = (int*)malloc(number_of_nodes * sizeof(int));

    FILE *file = fopen(filePath, "r");
    if (!file) return;

    int id, x, y, open_t, close_t;
    while (fscanf(file, "%d %d %d %d %d", &id, &x, &y, &open_t, &close_t) == 5) {
        if (id >= 0 && id < number_of_nodes) {
            xs[id] = x;
            ys[id] = y;
            open_times[id] = open_t;
            close_times[id] = close_t;
        }
    }

    fclose(file);
}

void free_arrays() {
    if (xs) { free(xs); xs = NULL; }
    if (ys) { free(ys); ys = NULL; }
    if (open_times) { free(open_times); open_times = NULL; }
    if (close_times) { free(close_times); close_times = NULL; }
}
