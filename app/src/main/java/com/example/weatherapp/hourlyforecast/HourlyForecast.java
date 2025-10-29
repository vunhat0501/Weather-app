package com.example.weatherapp.hourlyforecast;

public class HourlyForecast {
    private String time;
    private String temperature;
    private String windSpeed;
    private int weatherIconResId; // Using an int for the drawable resource ID

    // Constructor
    public HourlyForecast(String time, String temperature, String windSpeed, int weatherIconResId) {
        this.time = time;
        this.temperature = temperature;
        this.windSpeed = windSpeed;
        this.weatherIconResId = weatherIconResId;
    }

    // Getters
    public String getTime() {
        return time;
    }

    public String getTemperature() {
        return temperature;
    }

    public String getWindSpeed() {
        return windSpeed;
    }

    public int getWeatherIconResId() {
        return weatherIconResId;
    }
}