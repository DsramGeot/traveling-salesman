public class Solution {
    int[] path;
    int numberOfCities;
    int totalDistance;
    int timeToComplete;
    
    public Solution(int[] path, int totalDistance, int timeToComplete) {
        this.path = path;
        this.numberOfCities = path.length;
        this.totalDistance = totalDistance;
        this.timeToComplete = timeToComplete;
    }
    public boolean isBetterSolutionThan(Solution alternative) {
        if(this.numberOfCities != alternative.numberOfCities) 
            return (this.numberOfCities > alternative.numberOfCities);
        else {
            if(this.totalDistance != alternative.totalDistance)
                return (this.totalDistance < alternative.totalDistance);
            else
                return this.timeToComplete < alternative.timeToComplete;
        }
    }
}
