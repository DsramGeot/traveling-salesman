public class Solution {
    int firstCity;
    int[] nextCities;
    int[] previousCities;
    boolean[] isVisited;
    long[] arrivalTimes;
    int numberOfCities;
    long totalDistance;
    long timeToComplete;

    public Solution(int firstCity, int[] nextCities, int[] previousCities, boolean[] isVisited, long[] arrivalTimes,int numberOfCities, long totalDistance,
            long timeToComplete) {
        this.firstCity = firstCity;
        this.nextCities = nextCities;
        this.previousCities = previousCities;
        this.isVisited = isVisited;
        this.arrivalTimes = arrivalTimes;
        this.numberOfCities = numberOfCities;
        this.totalDistance = totalDistance;
        this.timeToComplete = timeToComplete;
    }

    public boolean isBetterSolutionThan(Solution alternative) {
        if (this.numberOfCities != alternative.numberOfCities)
            return (this.numberOfCities > alternative.numberOfCities);
        else {
            if (this.totalDistance != alternative.totalDistance)
                return (this.totalDistance < alternative.totalDistance);
            else
                return this.timeToComplete < alternative.timeToComplete;
        }
    }
}
