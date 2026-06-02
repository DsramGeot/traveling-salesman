#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <string.h>
#include <math.h>
#include <omp.h>
#include <sys/time.h>

#include "CParser.c"

#define NUM_NEIGHBORS 800
#define BEAM_WIDTH 5

typedef struct {
    int cityIndex;
    long distance;
} Neighbor;

// RAM Optimizasyonu: visited listesini kaldirarak her State'in boyutunu ~50 KB'den sadece 32 Byte'a dusurduk!
typedef struct State {
    struct State* parent;
    int city;
    long time;
    long totalDist;
    int depth;
    int refCount;
} State;

long getDistance(int i, int j) {
    long dx = xs[i] - xs[j];
    long dy = ys[i] - ys[j];
    return (long)floor(sqrt((double)(dx * dx + dy * dy)) + 0.5);
}

State* createState(State* parent, int city, long time, long totalDist, int depth) {
    State* s = (State*)malloc(sizeof(State));
    s->parent = parent;
    if (parent) {
        #pragma omp atomic
        parent->refCount++;
    }
    s->city = city;
    s->time = time;
    s->totalDist = totalDist;
    s->depth = depth;
    s->refCount = 0;
    return s;
}

void retainState(State* s) {
    if (s) {
        #pragma omp atomic
        s->refCount++;
    }
}

void releaseState(State* s) {
    if (!s) return;
    int new_count;
    #pragma omp atomic capture
    {
        s->refCount--;
        new_count = s->refCount;
    }
    if (new_count == 0) {
        State* p = s->parent;
        // visited array silindiği için free işlemine gerek kalmadı
        free(s);
        releaseState(p);
    }
}

int findStart(State* s) {
    while (s->parent != NULL) {
        s = s->parent;
    }
    return s->city;
}

typedef struct {
    int numberOfCities;
    long totalDistance;
    long timeToComplete;
} Solution;

bool isBetterSolutionThan(Solution* current, Solution* alternative) {
    if (current->numberOfCities != alternative->numberOfCities) {
        return current->numberOfCities > alternative->numberOfCities;
    } else {
        if (current->totalDistance != alternative->totalDistance) {
            return current->totalDistance < alternative->totalDistance;
        } else {
            return current->timeToComplete < alternative->timeToComplete;
        }
    }
}

long currentTimeMillis() {
    struct timeval time;
    gettimeofday(&time, NULL);
    return time.tv_sec * 1000 + time.tv_usec / 1000;
}

void outputWriter(const char* filePath, int* path, int len, long d, long t) {
    FILE* fw = fopen(filePath, "w");
    if (!fw) {
        printf("[HATA] Output dosyasi yazilamadi: %s\n", filePath);
        return;
    }
    fprintf(fw, "%d %ld %ld\n", len, d, t);
    for (int i = 0; i < len; i++) {
        fprintf(fw, "%d\n", path[i]);
    }
    fclose(fw);
}

int main(int argc, char** argv) {
    if (argc < 2 || argc > 3) {
        printf("Kullanim: ./edited <input_file> [output_file]\n");
        return 1;
    }

    const char* inputFile = argv[1];
    const char* outputFile = (argc == 3) ? argv[2] : "out.txt";

    printf("[BILGI] %s dosyasi okunuyor...\n", inputFile);
    long startProgram = currentTimeMillis();

    int numberOfNodes = getNodeCount(inputFile);
    if (numberOfNodes <= 0) {
        printf("[HATA] Gecerli bir input dosyasi bulunamadi veya sehir sayisi 0!\n");
        return 1;
    }
    printf("[BILGI] Toplam %d sehir bulundu.\n", numberOfNodes);

    init_arrays(inputFile);

    printf("[BILGI] Komsuluk Matrisi (Top-K) paralelde hazirlaniyor...\n");
    long startNeighbors = currentTimeMillis();

    int** neighbors = (int**)malloc(numberOfNodes * sizeof(int*));
    int* neighborCounts = (int*)malloc(numberOfNodes * sizeof(int));
    
    #pragma omp parallel for
    for (int i = 0; i < numberOfNodes; i++) {
        neighbors[i] = (int*)malloc(NUM_NEIGHBORS * sizeof(int));
        Neighbor topK[NUM_NEIGHBORS];
        int count = 0;
        
        for (int j = 0; j < numberOfNodes; j++) {
            if (i == j) continue;
            long d = getDistance(i, j);
            
            if (count < NUM_NEIGHBORS) {
                int pos = count - 1;
                while (pos >= 0 && topK[pos].distance > d) {
                    topK[pos + 1] = topK[pos];
                    pos--;
                }
                topK[pos + 1].distance = d;
                topK[pos + 1].cityIndex = j;
                count++;
            } else if (d < topK[NUM_NEIGHBORS - 1].distance) {
                int pos = NUM_NEIGHBORS - 2;
                while (pos >= 0 && topK[pos].distance > d) {
                    topK[pos + 1] = topK[pos];
                    pos--;
                }
                topK[pos + 1].distance = d;
                topK[pos + 1].cityIndex = j;
            }
        }
        for (int k = 0; k < count; k++) {
            neighbors[i][k] = topK[k].cityIndex;
        }
        neighborCounts[i] = count;
    }
    
    printf("[BILGI] Komsuluk matrisi %.2f saniyede tamamlandi!\n", (currentTimeMillis() - startNeighbors) / 1000.0);

    printf("[BILGI] RAM Dostu Paralel Beam Search algoritmasi baslatiliyor...\n");
    long startSearch = currentTimeMillis();

    int currentBeamCapacity = numberOfNodes * BEAM_WIDTH;
    State** currentBeam = (State**)malloc(currentBeamCapacity * sizeof(State*));
    int currentBeamSize = numberOfNodes;

    for (int i = 0; i < numberOfNodes; i++) {
        State* s = createState(NULL, i, open_times[i], 0, 1);
        retainState(s);
        currentBeam[i] = s;
    }

    int bestDepth = 1;

    State*** nextBest = (State***)malloc(numberOfNodes * sizeof(State**));
    omp_lock_t* cityLocks = (omp_lock_t*)malloc(numberOfNodes * sizeof(omp_lock_t));
    for (int i = 0; i < numberOfNodes; i++) {
        nextBest[i] = (State**)malloc(BEAM_WIDTH * sizeof(State*));
        omp_init_lock(&cityLocks[i]);
    }

    for (int depth = 2; depth <= numberOfNodes; depth++) {
        for (int i = 0; i < numberOfNodes; i++) {
            for (int m = 0; m < BEAM_WIDTH; m++) {
                nextBest[i][m] = NULL;
            }
        }

        bool extended = false;

        // OMP Parallel Region: Thread bazlı RAM tahsisi
        #pragma omp parallel
        {
            // Her thread sadece 1 adet 50 KB'lık visited listesi oluşturur (Toplam = Çekirdek Sayısı x 50 KB = <1 MB)
            bool* local_visited = (bool*)calloc(numberOfNodes, sizeof(bool));
            
            #pragma omp for
            for (int i = 0; i < currentBeamSize; i++) {
                State* s = currentBeam[i];
                
                // O anki State'in geçmişini geçici diziye işaretle (Pointer takibi ile sıfır RAM kopyalaması)
                State* temp = s;
                while (temp != NULL) {
                    local_visited[temp->city] = true;
                    temp = temp->parent;
                }

                int limit = neighborCounts[s->city];
                for (int k = 0; k < limit; k++) {
                    int v = neighbors[s->city][k];

                    // Önceden gidildiyse atla
                    if (local_visited[v]) continue;

                    long d = getDistance(s->city, v);
                    long arrival = s->time + d;

                    if (arrival <= close_times[v]) {
                        long departure = (arrival > open_times[v]) ? arrival : open_times[v];

                        State** topM = nextBest[v];
                        if (topM[BEAM_WIDTH - 1] == NULL || departure < topM[BEAM_WIDTH - 1]->time) {
                            
                            omp_set_lock(&cityLocks[v]);
                            
                            if (topM[BEAM_WIDTH - 1] == NULL || departure < topM[BEAM_WIDTH - 1]->time) {
                                State* newState = createState(s, v, departure, s->totalDist + d, depth);
                                extended = true;

                                State* dropped = topM[BEAM_WIDTH - 1];

                                int pos = BEAM_WIDTH - 1;
                                while (pos > 0 && (topM[pos - 1] == NULL || departure < topM[pos - 1]->time)) {
                                    topM[pos] = topM[pos - 1];
                                    pos--;
                                }
                                
                                topM[pos] = newState;
                                retainState(newState);

                                if (dropped != NULL) {
                                    releaseState(dropped);
                                }
                            }
                            
                            omp_unset_lock(&cityLocks[v]);
                        }
                    }
                }
                
                // Sonraki State'e geçmeden önce diziyi geri temizle
                temp = s;
                while (temp != NULL) {
                    local_visited[temp->city] = false;
                    temp = temp->parent;
                }
            }
            
            // Thread işini bitirince belleğini serbest bırak
            free(local_visited);
        }

        if (!extended) {
            printf("[BILGI] Gidilebilecek butun gecerli yollar tukendi.\n");
            break;
        }

        for (int i = 0; i < currentBeamSize; i++) {
            releaseState(currentBeam[i]);
        }

        currentBeamSize = 0;
        for (int i = 0; i < numberOfNodes; i++) {
            for (int m = 0; m < BEAM_WIDTH; m++) {
                if (nextBest[i][m] != NULL) {
                    currentBeam[currentBeamSize++] = nextBest[i][m];
                    retainState(nextBest[i][m]);
                } else {
                    break;
                }
            }
        }
        
        for (int i = 0; i < numberOfNodes; i++) {
            for (int m = 0; m < BEAM_WIDTH; m++) {
                if (nextBest[i][m] != NULL) {
                    releaseState(nextBest[i][m]);
                }
            }
        }

        bestDepth = depth;
        
        if (depth % 10 == 0 || depth == numberOfNodes) {
            printf("[DEVAM EDIYOR] Derinlik (Sehir Sayisi): %d | Guncel Rota Ihtimali: %d\n", depth, currentBeamSize);
        }
    }

    printf("[BILGI] Arama tamamlandi! Sure: %.2f saniye\n", (currentTimeMillis() - startSearch) / 1000.0);

    Solution bestSolution = {0, -1, -1};
    bool hasBest = false;
    State* realBest = NULL;

    for (int i = 0; i < currentBeamSize; i++) {
        State* s = currentBeam[i];
        int startCity = findStart(s);
        long returnDist = getDistance(s->city, startCity);
        long totalD = s->totalDist + returnDist;
        long finalTime = s->time + returnDist;

        Solution currentSolution = {bestDepth, totalD, finalTime};

        if (!hasBest || isBetterSolutionThan(&currentSolution, &bestSolution)) {
            bestSolution = currentSolution;
            realBest = s;
            hasBest = true;
        }
    }

    if (hasBest) {
        printf("\n======================================================\n");
        printf("[SONUC] Ulasilan Maksimum Sehir : %d\n", bestSolution.numberOfCities);
        printf("[SONUC] Toplam Mesafe (Uzunluk) : %ld\n", bestSolution.totalDistance);
        printf("[SONUC] Tamamlanma Suresi (Time): %ld\n", bestSolution.timeToComplete);
        printf("======================================================\n");

        int* path = (int*)malloc(bestDepth * sizeof(int));
        State* curr = realBest;
        for (int i = bestDepth - 1; i >= 0; i--) {
            path[i] = curr->city;
            curr = curr->parent;
        }

        outputWriter(outputFile, path, bestSolution.numberOfCities, bestSolution.totalDistance, bestSolution.timeToComplete);
        printf("[BASARILI] Rota basariyla '%s' dosyasina yazildi!\n", outputFile);
        
        free(path);
    }

    for (int i = 0; i < currentBeamSize; i++) {
        releaseState(currentBeam[i]);
    }
    free(currentBeam);

    for (int i = 0; i < numberOfNodes; i++) {
        omp_destroy_lock(&cityLocks[i]);
        free(nextBest[i]);
        free(neighbors[i]);
    }
    free(cityLocks);
    free(nextBest);
    free(neighbors);
    free(neighborCounts);

    free_arrays();

    return 0;
}
