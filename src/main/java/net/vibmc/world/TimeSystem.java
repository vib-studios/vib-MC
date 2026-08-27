package net.vibmc.world;

public class TimeSystem {
    private volatile long timeOfDay;

    public TimeSystem() {
        this.timeOfDay = 6000;
    }

    public void tick() {
        timeOfDay = (timeOfDay + 1) % 24000;
    }

    public void setTimeOfDay(long time) {
        this.timeOfDay = ((time % 24000) + 24000) % 24000;
    }

    public void addTime(long ticks) {
        setTimeOfDay(timeOfDay + ticks);
    }

    public String phase() {
        if (timeOfDay < 12000) {
            return "day";
        }
        if (timeOfDay < 13000) {
            return "sunset";
        }
        if (timeOfDay < 23000) {
            return "night";
        }
        return "sunrise";
    }

    public long timeOfDay() {
        return timeOfDay;
    }
}
