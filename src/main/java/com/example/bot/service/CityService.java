package com.example.bot.service;

import com.example.bot.model.City;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import me.xdrop.fuzzywuzzy.model.ExtractedResult;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class CityService {
    private final List<City> cities;
    private final Map<String, City> cityByName;

    public CityService(List<City> cities) {
        this.cities = cities;
        this.cityByName = cities.stream()
                .collect(Collectors.toMap(
                        City::getName,
                        c -> c,
                        this::resolveDuplicate // метод для разрешения конфликтов
                ));
    }

    private City resolveDuplicate(City existing, City replacement) {
        // Оставляем город с бОльшим населением
        if (existing.getPopulation() >= replacement.getPopulation()) {
            return existing;
        }
        return replacement;
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
        return cityByName.get(Objects.requireNonNullElse(full, input));
    }
    /**
     * Найти до `limit` городов с помощью нечёткого поиска.
     * Возвращает города с рейтингом >= minScore (0–100).
     */
    public List<City> findCitiesFuzzy(String input, int limit, int minScore) {
        if (input == null || input.trim().isEmpty()) {
            return List.of();
        }

        List<ExtractedResult> results = FuzzySearch.extractTop(input, cityByName.keySet(), limit);
        return results.stream()
                .filter(r -> r.getScore() >= minScore)
                .map(ExtractedResult::getString)
                .map(cityByName::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<String> getTop10Cities() {
        return cities.stream()
                .sorted((c1, c2) -> {
                    int cmp = Long.compare(c2.getPopulation(), c1.getPopulation());
                    if (cmp == 0) return c1.getName().compareTo(c2.getName());
                    return cmp;
                })
                .limit(10)
                .map(City::getName)
                .toList();
    }
}