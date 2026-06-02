import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class trial {

    static int n;
    static int[] xs, ys, open, close;
    static int[][] neighbors;
    static final int NUM_NEIGHBORS = 400; // Sadece en yakın 800 şehri kontrol et
    static final int BEAM_WIDTH = 3; // Her şehir için tutulacak maksimum alternatif yol

    // AI'ın Paralel Evren mantığının RAM dostu hali
    static class State {
        State parent;
        int city;
        long time;
        long totalDist;
        int depth;
        long[] visited;

        State(State parent, int city, long time, long totalDist, int depth) {
            this.parent = parent;
            this.city = city;
            this.time = time;
            this.totalDist = totalDist;
            this.depth = depth;

            // Ziyaret edilen şehirleri bit seviyesinde şifrele (İnanılmaz RAM tasarrufu ve
            // hız)
            int bitsetLen = (n + 63) >> 6;
            this.visited = new long[bitsetLen];
            if (parent != null) {
                System.arraycopy(parent.visited, 0, this.visited, 0, bitsetLen);
            }
            this.visited[city >> 6] |= (1L << (city & 63));
        }
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Input and output files required!");
            return;
        }

        // 1. Veri Okuma
        n = DataParser.getNodeCount(args[0]);
        DataParser.init_arrays(args[0]);
        xs = DataParser.xs;
        ys = DataParser.ys;
        open = DataParser.open_times;
        close = DataParser.close_times;

        // 2. RAM Dostu Komşuluk Matrisi (10 GB'lık dist[n][n] yerine sadece 800 komşu)
        System.out.println("Komsuluk Matrisi Hazirlaniyor... (Sadece 1 kez yapilir)");
        neighbors = new int[n][Math.min(n, NUM_NEIGHBORS)];
        for (int i = 0; i < n; i++) {
            long[] paired = new long[n];
            for (int j = 0; j < n; j++) {
                if (i == j)
                    paired[j] = Long.MAX_VALUE;
                else
                    paired[j] = (getDistance(i, j) << 32) | j;
            }
            Arrays.sort(paired);
            int kLimit = Math.min(n - 1, NUM_NEIGHBORS);
            for (int k = 0; k < kLimit; k++) {
                neighbors[i][k] = (int) (paired[k] & 0xFFFFFFFFL);
            }
        }

        System.out.println("Beam Search (Isin Aramasi) Basliyor...");
        long startCompute = System.currentTimeMillis();

        List<State> currentBeam = new ArrayList<>();

        // İlk başlangıç durumlarını yarat (Tüm şehirlerden paralel olarak başla)
        for (int i = 0; i < n; i++) {
            currentBeam.add(new State(null, i, open[i], 0, 1));
        }

        State globalBest = currentBeam.get(0);
        int bestDepth = 1;

        // FAZ: DINAMIK PROGRAMLAMA (Derinlik döngüsü)
        for (int depth = 2; depth <= n; depth++) {
            State[][] nextBest = new State[n][BEAM_WIDTH];
            boolean extended = false;

            for (State s : currentBeam) {
                // Sadece en yakın komşulara bak (Büyük optimizasyon)
                int limit = Math.min(n - 1, NUM_NEIGHBORS);
                for (int k = 0; k < limit; k++) {
                    int v = neighbors[s.city][k];

                    // Eğer bu şehir zaten bu yolda ziyaret edildiyse geç
                    if ((s.visited[v >> 6] & (1L << (v & 63))) != 0)
                        continue;

                    long d = getDistance(s.city, v);
                    long arrival = s.time + d;

                    if (arrival <= close[v]) {
                        long departure = Math.max(arrival, open[v]);

                        // Bu şehre daha önce bu kadar erken gelen oldu mu? Kontrol et!
                        State[] topM = nextBest[v];
                        if (topM[BEAM_WIDTH - 1] == null || departure < topM[BEAM_WIDTH - 1].time) {
                            State newState = new State(s, v, departure, s.totalDist + d, depth);
                            extended = true;

                            // Sıralı yerleştirme (En erken gelen en üstte durur)
                            int pos = BEAM_WIDTH - 1;
                            while (pos > 0 && (topM[pos - 1] == null || departure < topM[pos - 1].time)) {
                                topM[pos] = topM[pos - 1];
                                pos--;
                            }
                            topM[pos] = newState;
                        }
                    }
                }
            }

            if (!extended) {
                System.out.println("Gidilebilecek tum yollar tukendi. Maksimum Derinlik: " + (depth - 1));
                break;
            }

            // Yeni nesil (beam) havuzunu güncelle
            currentBeam.clear();
            for (int i = 0; i < n; i++) {
                for (int m = 0; m < BEAM_WIDTH; m++) {
                    if (nextBest[i][m] != null) {
                        currentBeam.add(nextBest[i][m]);
                        globalBest = nextBest[i][m]; // İzlemede kal
                    } else {
                        break;
                    }
                }
            }

            bestDepth = depth;
            if (depth % 10 == 0) {
                System.out
                        .println("Ulasilan Sehir Sayisi: " + depth + " | Paralel Evren Sayisi: " + currentBeam.size());
            }
        }

        System.out.println("Arama bitti. Sure: " + (System.currentTimeMillis() - startCompute) / 1000 + " sn");

        // FAZ: EN İYİ ROTAYI SEÇME (Lexicographical Kural)
        State realBest = null;
        long bestDist = Long.MAX_VALUE;
        long bestTime = Long.MAX_VALUE;

        for (State s : currentBeam) {
            int startCity = findStart(s);
            long returnDist = getDistance(s.city, startCity);
            long totalD = s.totalDist + returnDist;
            long finalTime = s.time + returnDist; // Geri dönerken kapanış saati aranmaz

            if (totalD < bestDist || (totalD == bestDist && finalTime < bestTime)) {
                bestDist = totalD;
                bestTime = finalTime;
                realBest = s;
            }
        }

        System.out.println("Nihai Skor: " + bestDepth + " Sehir | Mesafe: " + bestDist + " | Sure: " + bestTime);

        // Rotayı geriye doğru sarmalayarak çıkar
        int[] path = new int[bestDepth];
        State curr = realBest;
        for (int i = bestDepth - 1; i >= 0; i--) {
            path[i] = curr.city;
            curr = curr.parent;
        }

        outputWriter(args[1], path, bestDepth, bestDist, bestTime);
    }

    public static long getDistance(int i, int j) {
        long dx = xs[i] - xs[j];
        long dy = ys[i] - ys[j];
        return (long) Math.floor(Math.sqrt(dx * dx + dy * dy) + 0.5);
    }

    static int findStart(State s) {
        while (s.parent != null) {
            s = s.parent;
        }
        return s.city;
    }

    public static void outputWriter(String filePath, int[] path, int len, long d, long t) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            pw.print(len + " ");
            pw.print(d + " ");
            pw.println(t);
            for (int i = 0; i < len; i++) {
                pw.println(path[i]);
            }
        } catch (IOException e) {
            System.out.println("Output file could not be written!");
        }
    }
}