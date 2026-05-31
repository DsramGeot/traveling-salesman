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

        boolean[] isVisited = new boolean[numberOfCities];
        int[] path = new int[numberOfCities];
        int pathIndex = 0;

        long totalDistance = 0;
        long elapsedTime = openTimes[0];

        int currentCity = 0;
        isVisited[currentCity] = true;
        path[pathIndex++] = 0;

        while (true) {
            int nextCity = -1;
            long shortestLength = Long.MAX_VALUE;

            for (int i = 1; i < numberOfCities; i++) {
                if (isVisited[i])
                    continue;

                long distance = getDistance(xs[currentCity], ys[currentCity], xs[i], ys[i]);

                if (elapsedTime + distance > closeTimes[i])
                    continue;

                if (distance < shortestLength) {
                    nextCity = i;
                    shortestLength = distance;
                }
            }
            if (nextCity == -1)
                break;

            path[pathIndex++] = nextCity;
            totalDistance += shortestLength;
            elapsedTime = Math.max(elapsedTime + shortestLength, openTimes[nextCity]);
            isVisited[nextCity] = true;
            currentCity = nextCity;
        }

        if (currentCity != 0) {
            long temp = getDistance(xs[0], ys[0], xs[currentCity], ys[currentCity]);
            totalDistance += temp;
            elapsedTime += temp;
        }
        outputWriter(args[1], path, pathIndex, totalDistance, elapsedTime);
    }

    public static long getDistance(int x1, int y1, int x2, int y2) {
        double sqrt = Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
        return (long) Math.floor(sqrt + 0.5);
    }

    public static void outputWriter(String filePath, int[] path, int numberOfVisitedCities,
            long totalDistance,
            long totalTime) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.print(numberOfVisitedCities + " ");
            writer.print(totalDistance + " ");
            writer.println(totalTime);

            for (int i = 0; i < numberOfVisitedCities; i++)
                writer.println(path[i]);

        } catch (IOException e) {
            System.out.println("Output file could not be written!");
            System.exit(1);
        }
    }
}
