import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class TSPTWSolver {
    public static void main(String[] args) {

        if (args.length != 2) {
            System.out.println("Input and output file path arguments should be provided!");
            System.exit(1);
        }

        int numberOfCities = DataParser.getNodeCount(args[0]);
        DataParser.init_arrays(args[0]);

        int[] xs = DataParser.xs;
        int[] ys = DataParser.ys;
        int[] openTimes = DataParser.open_times;
        int[] closeTimes = DataParser.close_times;
        Solution bestSolution = new Solution(0, new int[0], new int[0], 0, 0, 0);

        int maxStartCityTrial = numberOfCities;
        for (int i = 0; i < maxStartCityTrial; i++) {
            boolean[] isVisited = new boolean[numberOfCities];
            int[] nextCities = new int[numberOfCities];
            int[] previousCities = new int[numberOfCities]; // To avoid path array's O(n) insert operation, it is O(1)
                                                            // this
                                                            // way
            long totalDistance = 0;
            long elapsedTime = openTimes[i];
            int numberOfVisitedCities = 0;

            int startCity = i;
            int currentCity = startCity;
            isVisited[currentCity] = true;
            previousCities[currentCity] = -1;
            numberOfVisitedCities++;

            while (true) {
                int nextCity = -1;
                long shortestLength = Long.MAX_VALUE;

                for (int j = 0; j < numberOfCities; j++) {
                    if (isVisited[j])
                        continue;

                    long distance = getDistance(xs[currentCity], ys[currentCity], xs[j], ys[j]);

                    if (elapsedTime + distance > closeTimes[j])
                        continue;

                    if (distance < shortestLength) {
                        nextCity = j;
                        shortestLength = distance;
                    }
                }
                if (nextCity == -1)
                    break;

                nextCities[currentCity] = nextCity;
                previousCities[nextCity] = currentCity;
                numberOfVisitedCities++;
                totalDistance += shortestLength;
                elapsedTime = Math.max(elapsedTime + shortestLength, openTimes[nextCity]);
                isVisited[nextCity] = true;
                currentCity = nextCity;
            }

            nextCities[currentCity] = -1;
            long temp = getDistance(xs[startCity], ys[startCity], xs[currentCity], ys[currentCity]);
            totalDistance += temp;
            elapsedTime += temp;
            Solution newSolution = new Solution(i, nextCities, previousCities, numberOfVisitedCities, totalDistance,
                    elapsedTime);
            bestSolution = newSolution.isBetterSolutionThan(bestSolution) ? newSolution : bestSolution;
        }

        outputWriter(args[1], bestSolution);
    }

    public static long getDistance(int x1, int y1, int x2, int y2) {
        double sqrt = Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
        return (long) Math.floor(sqrt + 0.5);
    }

    public static void outputWriter(String filePath, Solution bestSolution) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.print(bestSolution.numberOfCities + " ");
            writer.print(bestSolution.totalDistance + " ");
            writer.println(bestSolution.timeToComplete);

            int currentCity = bestSolution.firstCity;
            writer.println(currentCity);
            while ((currentCity = bestSolution.nextCities[currentCity]) != -1)
                writer.println(currentCity);

        } catch (IOException e) {
            System.out.println("Output file could not be written!");
            System.exit(1);
        }
    }

    public static void insertNoCost(Solution solution) {
    }

}
