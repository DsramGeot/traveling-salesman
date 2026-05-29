import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.concurrent.ThreadLocalRandom;

public class Solver {

    static int N;
    static int[] distCache;
    static int[][] neighbors;
    static final int NUM_NEIGHBORS = 800;

    public static int dist(int i, int j) {
        return distCache[i * N + j];
    }

    record BestSolution(int cityCount, long tourLength, long completionTime, int[] path) {}
    static final AtomicReference<BestSolution> globalBest = new AtomicReference<>(new BestSolution(-1, -1, -1, new int[0]));

    static class Route {
        int[] next;
        int[] prev;
        long[] A;
        long[] D;
        long[] W;
        long[] M;
        boolean[] inRoute;
        int head, tail, size;

        public Route(int maxNodes) {
            next = new int[maxNodes];
            prev = new int[maxNodes];
            A = new long[maxNodes];
            D = new long[maxNodes];
            W = new long[maxNodes];
            M = new long[maxNodes];
            inRoute = new boolean[maxNodes];
            java.util.Arrays.fill(next, -1);
            java.util.Arrays.fill(prev, -1);
            head = -1;
            tail = -1;
            size = 0;
        }

        public void init(int startNode) {
            head = startNode;
            tail = startNode;
            next[startNode] = -1;
            prev[startNode] = -1;
            inRoute[startNode] = true;
            size = 1;
            A[startNode] = Math.max(0, DataParser.open_times[startNode]);
            D[startNode] = A[startNode];
            W[startNode] = 0;
        }

        public void copyFrom(Route other) {
            this.size = other.size;
            this.head = other.head;
            this.tail = other.tail;
            System.arraycopy(other.next, 0, this.next, 0, N);
            System.arraycopy(other.prev, 0, this.prev, 0, N);
            System.arraycopy(other.A, 0, this.A, 0, N);
            System.arraycopy(other.D, 0, this.D, 0, N);
            System.arraycopy(other.W, 0, this.W, 0, N);
            System.arraycopy(other.M, 0, this.M, 0, N);
            System.arraycopy(other.inRoute, 0, this.inRoute, 0, N);
        }

        public void insertAfter(int existingNode, int newNode) {
            int oldNext = next[existingNode];
            next[existingNode] = newNode;
            prev[newNode] = existingNode;
            next[newNode] = oldNext;
            if (oldNext != -1) prev[oldNext] = newNode;
            else tail = newNode;
            inRoute[newNode] = true;
            size++;
        }

        public void remove(int node) {
            int p = prev[node];
            int n = next[node];
            if (p != -1) next[p] = n;
            else head = n;
            if (n != -1) prev[n] = p;
            else tail = p;
            inRoute[node] = false;
            next[node] = -1;
            prev[node] = -1;
            size--;
        }

        public void computeMetrics() {
            if (size == 0) return;
            A[head] = Math.max(0, DataParser.open_times[head]);
            D[head] = A[head];
            W[head] = 0;
            
            int curr = next[head];
            int prevNode = head;
            while (curr != -1) {
                long d = dist(prevNode, curr);
                A[curr] = D[prevNode] + d;
                D[curr] = Math.max(A[curr], DataParser.open_times[curr]);
                W[curr] = D[curr] - A[curr];
                prevNode = curr;
                curr = next[curr];
            }

            M[tail] = DataParser.close_times[tail] - A[tail];
            curr = prev[tail];
            int nextNode = tail;
            while (curr != -1) {
                M[curr] = Math.min(DataParser.close_times[curr] - A[curr], W[curr] + M[nextNode]);
                nextNode = curr;
                curr = prev[curr];
            }
        }
        
        public long getReturnDistance() {
            if (size <= 1) return 0;
            return dist(tail, head);
        }

        public long getTourLength() {
            long len = 0;
            int curr = next[head];
            int p = head;
            while (curr != -1) {
                len += dist(p, curr);
                p = curr;
                curr = next[curr];
            }
            len += getReturnDistance();
            return len;
        }

        public long getCompletionTime() {
            if (size == 0) return 0;
            return D[tail] + getReturnDistance();
        }

        public int[] toArray() {
            int[] arr = new int[size];
            int curr = head;
            int idx = 0;
            while (curr != -1) {
                arr[idx++] = curr;
                curr = next[curr];
            }
            return arr;
        }
    }

    public static void buildGrasp(Route r) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        while (true) {
            int numTop = 0;
            int[] topNodes = new int[3];
            long[] topCosts = new long[3];
            long[] topDists = new long[3];

            int currNode = r.tail;
            long currentTime = r.D[currNode];

            for (int j : neighbors[currNode]) {
                if (!r.inRoute[j]) {
                    long d = dist(currNode, j);
                    long arrivalTime = currentTime + d;
                    
                    if (arrivalTime <= DataParser.close_times[j]) {
                        long departureTime = Math.max(arrivalTime, DataParser.open_times[j]);
                        
                        if (numTop < 3) {
                            topNodes[numTop] = j; topCosts[numTop] = departureTime; topDists[numTop] = d; numTop++;
                            for (int k = numTop - 1; k > 0; k--) {
                                if (topCosts[k] < topCosts[k-1] || (topCosts[k] == topCosts[k-1] && topDists[k] < topDists[k-1])) {
                                    int tN = topNodes[k]; topNodes[k] = topNodes[k-1]; topNodes[k-1] = tN;
                                    long tC = topCosts[k]; topCosts[k] = topCosts[k-1]; topCosts[k-1] = tC;
                                    long tD = topDists[k]; topDists[k] = topDists[k-1]; topDists[k-1] = tD;
                                } else break;
                            }
                        } else {
                            if (departureTime < topCosts[2] || (departureTime == topCosts[2] && d < topDists[2])) {
                                topNodes[2] = j; topCosts[2] = departureTime; topDists[2] = d;
                                for (int k = 2; k > 0; k--) {
                                    if (topCosts[k] < topCosts[k-1] || (topCosts[k] == topCosts[k-1] && topDists[k] < topDists[k-1])) {
                                        int tN = topNodes[k]; topNodes[k] = topNodes[k-1]; topNodes[k-1] = tN;
                                        long tC = topCosts[k]; topCosts[k] = topCosts[k-1]; topCosts[k-1] = tC;
                                        long tD = topDists[k]; topDists[k] = topDists[k-1]; topDists[k-1] = tD;
                                    } else break;
                                }
                            }
                        }
                    }
                }
            }

            if (numTop == 0) break;

            int chosenIdx = 0;
            if (numTop > 1) {
                double rnd = rand.nextDouble();
                if (rnd < 0.6) chosenIdx = 0;
                else if (rnd < 0.9 || numTop == 2) chosenIdx = 1;
                else chosenIdx = 2;
            }

            int nextNode = topNodes[chosenIdx];
            r.insertAfter(currNode, nextNode);
            r.D[nextNode] = Math.max(currentTime + dist(currNode, nextNode), DataParser.open_times[nextNode]);
        }
    }

    public static void runInsertionPhase(Route r, boolean useRegret) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        boolean inserted = true;
        
        long[] bestDelta1 = new long[N];
        long[] bestDelta2 = new long[N];
        int[] bestPrev1 = new int[N];
        
        while (inserted) {
            inserted = false;
            r.computeMetrics();

            if (useRegret) {
                java.util.Arrays.fill(bestDelta1, Long.MAX_VALUE);
                java.util.Arrays.fill(bestDelta2, Long.MAX_VALUE);
                java.util.Arrays.fill(bestPrev1, -1);

                for (int curr = r.head; curr != -1; curr = r.next[curr]) {
                    int p = curr;
                    int n = r.next[curr];
                    for (int u : neighbors[p]) {
                        if (r.inRoute[u]) continue;
                        long close_u = DataParser.close_times[u];
                        long open_u = DataParser.open_times[u];

                        long A_u = r.D[p] + dist(p, u);
                        if (A_u > close_u) continue;

                        long D_u = Math.max(A_u, open_u);
                        long delta = 0;
                        if (n != -1) {
                            long A_n_prime = D_u + dist(u, n);
                            delta = A_n_prime - r.A[n];
                            if (delta > r.M[n]) continue;
                        } else {
                            delta = D_u - r.D[r.tail];
                        }
                        
                        if (delta < bestDelta1[u]) {
                            bestDelta2[u] = bestDelta1[u];
                            bestDelta1[u] = delta;
                            bestPrev1[u] = p;
                        } else if (delta < bestDelta2[u]) {
                            bestDelta2[u] = delta;
                        }
                    }
                }

                int bestU = -1;
                long maxRegret = -1;
                long globalBestDelta = Long.MAX_VALUE;

                for (int u = 0; u < N; u++) {
                    if (bestPrev1[u] != -1) {
                        long regret = (bestDelta2[u] == Long.MAX_VALUE ? 1000000 : bestDelta2[u]) - bestDelta1[u];
                        if (regret > maxRegret || (regret == maxRegret && bestDelta1[u] < globalBestDelta)) {
                            maxRegret = regret;
                            bestU = u;
                            globalBestDelta = bestDelta1[u];
                        }
                    }
                }

                if (bestU != -1) {
                    r.insertAfter(bestPrev1[bestU], bestU);
                    inserted = true;
                }
            } else {
                int numTop = 0;
                int[] topU = new int[3];
                int[] topPrev = new int[3];
                long[] topDelta = new long[3];
                long[] topDist = new long[3];

                int curr = r.head;
                while (curr != -1) {
                    int p = curr;
                    int n = r.next[curr];
                    for (int u : neighbors[p]) {
                        if (r.inRoute[u]) continue;

                        long close_u = DataParser.close_times[u];
                        long open_u = DataParser.open_times[u];

                        long A_u = r.D[p] + dist(p, u);
                        if (A_u > close_u) continue; 

                        long D_u = Math.max(A_u, open_u);
                        long delta = 0;
                        
                        if (n != -1) {
                            long A_n_prime = D_u + dist(u, n);
                            delta = A_n_prime - r.A[n];
                            if (delta > r.M[n]) continue; 
                        } else {
                            delta = D_u - r.D[r.tail]; 
                        }

                        long d = dist(p, u);
                        
                        if (numTop < 3) {
                            topU[numTop] = u; topPrev[numTop] = p; topDelta[numTop] = delta; topDist[numTop] = d;
                            numTop++;
                            for (int k = numTop - 1; k > 0; k--) {
                                if (topDelta[k] < topDelta[k-1] || (topDelta[k] == topDelta[k-1] && topDist[k] < topDist[k-1])) {
                                    int tU = topU[k]; topU[k] = topU[k-1]; topU[k-1] = tU;
                                    int tP = topPrev[k]; topPrev[k] = topPrev[k-1]; topPrev[k-1] = tP;
                                    long tDl = topDelta[k]; topDelta[k] = topDelta[k-1]; topDelta[k-1] = tDl;
                                    long tDt = topDist[k]; topDist[k] = topDist[k-1]; topDist[k-1] = tDt;
                                } else break;
                            }
                        } else {
                            if (delta < topDelta[2] || (delta == topDelta[2] && d < topDist[2])) {
                                topU[2] = u; topPrev[2] = p; topDelta[2] = delta; topDist[2] = d;
                                for (int k = 2; k > 0; k--) {
                                    if (topDelta[k] < topDelta[k-1] || (topDelta[k] == topDelta[k-1] && topDist[k] < topDist[k-1])) {
                                        int tU = topU[k]; topU[k] = topU[k-1]; topU[k-1] = tU;
                                        int tP = topPrev[k]; topPrev[k] = topPrev[k-1]; topPrev[k-1] = tP;
                                        long tDl = topDelta[k]; topDelta[k] = topDelta[k-1]; topDelta[k-1] = tDl;
                                        long tDt = topDist[k]; topDist[k] = topDist[k-1]; topDist[k-1] = tDt;
                                    } else break;
                                }
                            }
                        }
                    }
                    curr = n;
                }

                if (numTop > 0) {
                    int chosenIdx = 0;
                    if (numTop > 1) {
                        double rnd = rand.nextDouble();
                        if (rnd < 0.6) chosenIdx = 0;
                        else if (rnd < 0.9 || numTop == 2) chosenIdx = 1;
                        else chosenIdx = 2;
                    }

                    r.insertAfter(topPrev[chosenIdx], topU[chosenIdx]);
                    inserted = true;
                }
            }
        }
    }

    public static void run2Opt(Route r) {
        boolean improved = true;
        while (improved) {
            improved = false;
            r.computeMetrics();
            
            int i = r.next[r.head];
            while (i != -1 && r.next[i] != -1) {
                int j = r.next[i];
                while (j != -1) {
                    int p_i = r.prev[i];
                    int n_j = r.next[j];
                    
                    long currentTime = r.D[p_i];
                    boolean feasible = true;
                    int currRev = j;
                    while (true) {
                        int prevInNew = (currRev == j) ? p_i : r.next[currRev];
                        long arr = currentTime + dist(prevInNew, currRev);
                        
                        if (arr > DataParser.close_times[currRev]) {
                            feasible = false;
                            break;
                        }
                        currentTime = Math.max(arr, DataParser.open_times[currRev]);
                        
                        if (currRev == i) break;
                        currRev = r.prev[currRev];
                    }
                    
                    if (feasible) {
                        long delta = 0;
                        if (n_j != -1) {
                            long arr_n_j = currentTime + dist(i, n_j);
                            delta = arr_n_j - r.A[n_j];
                            if (delta > r.M[n_j]) feasible = false;
                        } else {
                            delta = currentTime - r.D[r.tail]; 
                        }
                        
                        if (feasible && delta < 0) {
                            int curr = i;
                            while (curr != n_j) {
                                int temp = r.next[curr];
                                r.next[curr] = r.prev[curr];
                                r.prev[curr] = temp;
                                curr = temp; 
                            }
                            r.next[p_i] = j;
                            r.prev[j] = p_i;
                            r.next[i] = n_j;
                            if (n_j != -1) r.prev[n_j] = i;
                            else r.tail = i;
                            
                            improved = true;
                            break;
                        }
                    }
                    j = r.next[j];
                }
                if (improved) break;
                i = r.next[i];
            }
        }
    }

    public static void runAlnsRuin(Route r, ThreadLocalRandom rand) {
        int numToRemove = 1 + rand.nextInt(5);
        if (r.size <= 2) return;
        
        int strategy = rand.nextInt(3);
        if (strategy == 0) {
            double[] scores = new double[N];
            double sumScores = 0;
            int curr = r.next[r.head];
            while (curr != -1) {
                int p = r.prev[curr];
                int n = r.next[curr];
                long detour;
                if (n == -1) detour = dist(p, curr);
                else detour = dist(p, curr) + dist(curr, n) - dist(p, n);
                scores[curr] = r.W[curr] + detour;
                sumScores += scores[curr];
                curr = n;
            }
            
            for (int k = 0; k < numToRemove; k++) {
                if (r.size <= 2 || sumScores <= 0) break;
                double rVal = rand.nextDouble() * sumScores;
                double runningSum = 0;
                int rmNode = r.tail;
                curr = r.next[r.head];
                while (curr != -1) {
                    runningSum += scores[curr];
                    if (runningSum >= rVal) {
                        rmNode = curr;
                        break;
                    }
                    curr = r.next[curr];
                }
                if (rmNode != r.head && rmNode != -1) {
                    sumScores -= scores[rmNode];
                    scores[rmNode] = 0;
                    r.remove(rmNode);
                }
            }
        } else if (strategy == 1) {
            for (int k = 0; k < numToRemove; k++) {
                if (r.size <= 2) break;
                int targetIdx = 1 + rand.nextInt(r.size - 1);
                int curr = r.head;
                for (int i = 0; i < targetIdx; i++) curr = r.next[curr];
                r.remove(curr);
            }
        } else {
            int maxStart = r.size - numToRemove;
            if (maxStart < 1) maxStart = 1;
            int startIdx = 1 + rand.nextInt(maxStart);
            int curr = r.head;
            for (int i = 0; i < startIdx; i++) curr = r.next[curr];
            for (int k = 0; k < numToRemove; k++) {
                if (curr == -1) break;
                int n = r.next[curr];
                r.remove(curr);
                curr = n;
            }
        }
    }

    public static void checkBest(Route r) {
        r.computeMetrics();
        int cityCount = r.size;
        long tourLength = r.getTourLength();
        long completionTime = r.getCompletionTime();
        int[] path = r.toArray();
        
        BestSolution proposed = new BestSolution(cityCount, tourLength, completionTime, path);
        globalBest.accumulateAndGet(proposed, (current, update) -> {
            if (update.cityCount() > current.cityCount()) {
                System.out.println("New best! Cities: " + update.cityCount() + " Length: " + update.tourLength() + " Time: " + update.completionTime());
                return update;
            } else if (update.cityCount() == current.cityCount() && update.tourLength() < current.tourLength()) {
                System.out.println("New best! Cities: " + update.cityCount() + " Length: " + update.tourLength() + " Time: " + update.completionTime());
                return update;
            }
            return current;
        });
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java -Xmx4G Solver <input_file> [time_limit_in_seconds]");
            return;
        }

        String inputPath = args[0];

        DataParser.init_arrays(inputPath);
        N = DataParser.xs.length;
        if (N == 0) {
            System.out.println("No data found.");
            return;
        }

        System.out.println("Precomputing distances...");
        distCache = new int[N * N];
        IntStream.range(0, N).parallel().forEach(i -> {
            long x1 = DataParser.xs[i];
            long y1 = DataParser.ys[i];
            for (int j = 0; j < N; j++) {
                long dx = x1 - DataParser.xs[j];
                long dy = y1 - DataParser.ys[j];
                distCache[i * N + j] = (int) Math.round(Math.sqrt(dx * dx + dy * dy));
            }
        });

        System.out.println("Precomputing neighbors...");
        neighbors = new int[N][NUM_NEIGHBORS];
        IntStream.range(0, N).parallel().forEach(i -> {
            long[] paired = new long[N];
            for (int j = 0; j < N; j++) {
                paired[j] = (((long) distCache[i * N + j]) << 32) | j;
            }
            java.util.Arrays.sort(paired);
            int maxK = Math.min(NUM_NEIGHBORS, N);
            for (int k = 0; k < maxK; k++) {
                neighbors[i][k] = (int) (paired[k] & 0xFFFFFFFFL);
            }
        });

        long startTimeMs = System.currentTimeMillis();
        long parsedTimeLimitMs = 60 * 1000;
        if (args.length > 1) {
            try {
                parsedTimeLimitMs = Long.parseLong(args[1]) * 1000;
            } catch (NumberFormatException e) {
                System.out.println("Invalid time limit. Using default of 60 seconds.");
            }
        }
        final long timeLimitMs = parsedTimeLimitMs;
        
        AtomicInteger iterCounter = new AtomicInteger(0);
        int numThreads = Runtime.getRuntime().availableProcessors();
        System.out.println("Starting advanced ALNS search with " + numThreads + " threads...");

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                ThreadLocalRandom rand = ThreadLocalRandom.current();
                while (System.currentTimeMillis() - startTimeMs < timeLimitMs) {
                    int iteration = iterCounter.getAndIncrement();
                    int startNode = (iteration < N) ? iteration : rand.nextInt(N);
                    
                    Route r = new Route(N);
                    r.init(startNode);

                    buildGrasp(r);
                    runInsertionPhase(r, false);
                    run2Opt(r);
                    checkBest(r);

                    int threshold = globalBest.get().cityCount() - 15;
                    if (threshold < 10) threshold = 10;
                    if (r.size >= threshold) {
                        Route bestIterRoute = new Route(N);
                        bestIterRoute.copyFrom(r);

                        for (int step = 0; step < 25; step++) {
                            if (System.currentTimeMillis() - startTimeMs > timeLimitMs) break;

                            Route temp = new Route(N);
                            temp.copyFrom(bestIterRoute);

                            runAlnsRuin(temp, rand);
                            runInsertionPhase(temp, true); // Use regret-2
                            run2Opt(temp);

                            if (temp.size > bestIterRoute.size || (temp.size == bestIterRoute.size && temp.getTourLength() < bestIterRoute.getTourLength())) {
                                bestIterRoute.copyFrom(temp);
                                checkBest(bestIterRoute);
                            }
                        }
                    }
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(timeLimitMs + 5000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        BestSolution finalBest = globalBest.get();
        System.out.println("Time limit reached. Completed " + iterCounter.get() + " iterations.");
        System.out.println("Best City Count: " + finalBest.cityCount());
        System.out.println("Tour Length: " + finalBest.tourLength());
        System.out.println("Completion Time: " + finalBest.completionTime());

        String outputPath = inputPath.replace("input", "output");
        if (outputPath.equals(inputPath)) {
            outputPath = "output-" + inputPath;
        }

        try (PrintWriter out = new PrintWriter(new FileWriter(outputPath))) {
            out.println(finalBest.cityCount() + " " + finalBest.tourLength() + " " + finalBest.completionTime());
            for (int node : finalBest.path()) {
                out.println(node);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
