// src/main/java/com/example/bot/model/JsonCity.java
package com.example.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonCity {
    @JsonProperty("name")
    public String name;

    @JsonProperty("subject")
    public String subject;

    @JsonProperty("population")
    public long population;

    @JsonProperty("coords")
    public Coords coords;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Coords {
        @JsonProperty("lat")
        public String lat;

        @JsonProperty("lon")
        public String lon;
    }

    public City toCity() {
        double lat = Double.parseDouble(this.coords.lat);
        double lng = Double.parseDouble(this.coords.lon);
        return new City(name, subject, population, lat, lng);
    }
}