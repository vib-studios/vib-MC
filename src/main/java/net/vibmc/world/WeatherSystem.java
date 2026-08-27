package net.vibmc.world;

public class WeatherSystem {
    private volatile String weather = "clear";

    public void setWeather(String weather) {
        if (!"clear".equals(weather) && !"rain".equals(weather) && !"thunder".equals(weather)) {
            throw new IllegalArgumentException("weather must be clear, rain, or thunder");
        }
        this.weather = weather;
    }

    public void tick() {
        // Lightweight weather hooks for future expansion.
    }

    public String weather() {
        return weather;
    }
}
