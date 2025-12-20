package com.example.bot.service;

import com.example.bot.model.City;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import me.xdrop.fuzzywuzzy.model.ExtractedResult;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class CityService {
    private final List<City> cities;
    private final List<String> cityNames;

    public CityService(List<City> cities) {
        this.cities = cities;
        this.cityNames = cities.stream()
                .map(City::getName)
                .collect(Collectors.toList());
    }
    private static final Map<String, String> ABBREVIATIONS = Map.of(
            "мск", "Москва",
            "спб", "Санкт-Петербург",
            "екб", "Екатеринбург",
            "нск", "Новосибирск"
    );

    public City findCity(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        String clean = input.trim().toLowerCase(Locale.ROOT);
        String full = ABBREVIATIONS.get(clean);
        if (full != null) {
            return cities.stream()
                    .filter(c -> c.getName().equals(full))
                    .findFirst()
                    .orElse(null);
        }

        ExtractedResult match = FuzzySearch.extractOne(input, cityNames);
        if (match != null && match.getScore() >= 80) {
            String matchedName = match.getString();
            return cities.stream()
                    .filter(city -> city.getName().equals(matchedName))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }
    // В класс CityService
    public List<String> getTop10Cities() {
        return cities.stream()
                .sorted((c1, c2) -> {
                    int cmp = Long.compare(c2.getPopulation(), c1.getPopulation());
                    if (cmp == 0) {
                        return c1.getName().compareTo(c2.getName()); // стабилизация
                    }
                    return cmp;
                })                .limit(10)
                .map(City::getName)
                .toList();
    }
}