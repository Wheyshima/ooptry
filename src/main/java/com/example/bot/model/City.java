package com.example.bot.model;

public class City {
    private final String name;
    private final String region;      // = subject из JSON
    private final long population;
    private final double lat;
    private final double lng;

    public City(String name, String region, long population) {
        this(name, region, population, 0.0, 0.0);
    }

    public City(String name, String region, long population, double lat, double lng) {
        this.name = name;
        this.region = region;
        this.population = population;
        this.lat = lat;
        this.lng = lng;
    }

    // Геттеры
    public String getName() { return name; }
    public String getRegion() { return region; }
    public long getPopulation() { return population; }
    @SuppressWarnings("unused")
    public double getLat() { return lat; }
    @SuppressWarnings("unused")
    public double getLng() { return lng; }

    @Override
    public String toString() {
        return name + " (" + region + ")";
    }
}