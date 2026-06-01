import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class TSPTWSolver {

    static int[] xs;
    static int[] ys;
    static int[] openTimes;
    static int[] closeTimes;

    public static void main(String[] args) {

        if (args.length != 2) {
            System.out.println("Input and output file path arguments should be provided!");
            System.exit(1);
        }

        int numberOfCities = DataParser.getNodeCount(args[0]);
        DataParser.init_arrays(args[0]);
        xs = DataParser.xs;
        ys = DataParser.ys;
        openTimes = DataParser.open_times;
        closeTimes = DataParser.close_times;

        Solution bestSolution = new Solution(0, new int[0], new int[0], new boolean[0], new long[0], 0, 0, 0);

        int maxStartCityTrial = numberOfCities;
        for (int i = 0; i < maxStartCityTrial; i++) {
            boolean[] isVisited = new boolean[numberOfCities];
            int[] nextCities = new int[numberOfCities];
            int[] previousCities = new int[numberOfCities];
            long[] arrivalTimes = new long[numberOfCities];

            int startCity = i;
            long totalDistance = 0;
            long elapsedTime = openTimes[startCity];
            int numberOfVisitedCities = 0;

            int currentCity = startCity;
            isVisited[currentCity] = true;
            previousCities[currentCity] = -1;
            arrivalTimes[currentCity] = 0;
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
                arrivalTimes[nextCity] = elapsedTime + shortestLength;
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
            Solution newSolution = new Solution(startCity, nextCities, previousCities, isVisited, arrivalTimes,
                    numberOfVisitedCities, totalDistance,
                    elapsedTime);
            insertNoCost(newSolution, numberOfCities);
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

    public static void insertNoCost(Solution solution, int numberOfCities) {

        int currentCity = solution.firstCity;
        while ((currentCity = solution.nextCities[currentCity]) != -1) {
            long waitingTime = openTimes[currentCity] - solution.arrivalTimes[currentCity];
            if (waitingTime <= 0)
                continue;
            
            int previousCity = solution.previousCities[currentCity];
            if(previousCity == -1)
                continue;

            long departureFromPrevious = Math.max(solution.arrivalTimes[previousCity], openTimes[previousCity]);
            int bestToInsert = -1;
            long minimumNewCurrentArrival = Long.MAX_VALUE;

            for (int i = 0; i < numberOfCities; i++) {
                if (solution.isVisited[i])
                    continue;

                long arriveToNew = departureFromPrevious
                        + getDistance(xs[previousCity], ys[previousCity], xs[i], ys[i]);
                if (arriveToNew > closeTimes[i])
                    continue;

                long departureFromNew = Math.max(arriveToNew, openTimes[i]);
                long newArriveToCurrent = departureFromNew
                        + getDistance(xs[i], ys[i], xs[currentCity], ys[currentCity]);

                if (newArriveToCurrent <= openTimes[currentCity] && newArriveToCurrent < minimumNewCurrentArrival) {
                    bestToInsert = i;
                    minimumNewCurrentArrival = newArriveToCurrent;
                }
            }

            if (bestToInsert != -1) {

                solution.nextCities[previousCity] = bestToInsert;
                solution.previousCities[bestToInsert] = previousCity;

                solution.nextCities[bestToInsert] = currentCity;
                solution.previousCities[currentCity] = bestToInsert;

                long arriveToNew = departureFromPrevious
                        + getDistance(xs[previousCity], ys[previousCity], xs[bestToInsert], ys[bestToInsert]);
                solution.arrivalTimes[bestToInsert] = arriveToNew;

                long departureFromNew = Math.max(arriveToNew, openTimes[bestToInsert]);
                solution.arrivalTimes[currentCity] = departureFromNew
                        + getDistance(xs[bestToInsert], ys[bestToInsert], xs[currentCity], ys[currentCity]);

                solution.isVisited[bestToInsert] = true;
                solution.numberOfCities++;

                solution.totalDistance += getDistance(xs[previousCity], ys[previousCity], xs[bestToInsert],
                        ys[bestToInsert])
                        + getDistance(xs[bestToInsert], ys[bestToInsert], xs[currentCity], ys[currentCity])
                        - getDistance(xs[previousCity], ys[previousCity], xs[currentCity], ys[currentCity]);

                currentCity = previousCity;

            }
        }
    }
}
