package com.example.bot.service;

import com.example.bot.model.City;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CityServiceTest {

    private CityService cityService;
    private List<City> testCities;

    @BeforeEach
    void setUp() {
        testCities = Arrays.asList(
                new City("Москва", "Москва", 12615882L, 55.7558, 37.6176),
                new City("Санкт-Петербург", "Санкт-Петербург", 5384342L, 59.9343, 30.3351),
                new City("Новосибирск", "Новосибирская область", 1620162L, 55.0084, 82.9357),
                new City("Екатеринбург", "Свердловская область", 1544376L, 56.8389, 60.6057),
                new City("Казань", "Татарстан", 1254886L, 55.8304, 49.0661),
                new City("Нижний Новгород", "Нижегородская область", 1244254L, 56.2965, 43.9369),
                new City("Челябинск", "Челябинская область", 1189525L, 55.1644, 61.4368),
                new City("Самара", "Самарская область", 1144759L, 53.2004, 50.1543),
                new City("Ростов-на-Дону", "Ростовская область", 1142117L, 47.2313, 39.7233),
                new City("Омск", "Омская область", 1125738L, 54.9882, 73.3679),
                new City("Уфа", "Башкортостан", 1125661L, 54.7348, 55.9578)
        );
        cityService = new CityService(testCities);
    }

    @Test
    void findCity_exactMatch_returnsCity() {
        City result = cityService.findCity("Москва");
        assertNotNull(result);
        assertEquals("Москва", result.getName());
    }

    @Test
    void findCity_fuzzyMatch_closeName_returnsCity() {
        City result = cityService.findCity("мск");
        assertNotNull(result);
        assertEquals("Москва", result.getName());
    }

    @Test
    void findCity_fuzzyMatch_lowScore_returnsNull() {
        City result = cityService.findCity("Абракадабра");
        assertNull(result);
    }

    @Test
    void findCity_nullInput_returnsNull() {
        City result = cityService.findCity(null);
        assertNull(result);
    }

    @Test
    void findCity_emptyInput_returnsNull() {
        City result = cityService.findCity("");
        assertNull(result);
    }

    @Test
    void findCity_whitespaceOnly_returnsNull() {
        City result = cityService.findCity("   ");
        assertNull(result);
    }

    @Test
    void getTop10Cities_returnsTop10ByPopulation() {
        List<String> top10 = cityService.getTop10Cities();

        assertEquals(10, top10.size());
        assertEquals("Москва", top10.get(0));
        assertEquals("Санкт-Петербург", top10.get(1));
        assertTrue(top10.contains("Нижний Новгород"));
        
        // Проверяем порядок по убыванию населения
        long prevPop = Long.MAX_VALUE;
        for (String cityName : top10) {
            City city = testCities.stream()
                    .filter(c -> c.getName().equals(cityName))
                    .findFirst()
                    .orElseThrow();
            assertTrue(city.getPopulation() <= prevPop, "Города должны быть отсортированы по убыванию населения");
            prevPop = city.getPopulation();
        }
    }

    @Test
    void getTop10Cities_withLessThan10Cities_returnsAll() {
        List<City> fewCities = List.of(
                new City("Москва", "Москва", 12000000L, 55.7558, 37.6176),
                new City("СПб", "СПб", 5000000L, 59.9343, 30.3351)
        );
        CityService service = new CityService(fewCities);
        List<String> top = service.getTop10Cities();
        assertEquals(2, top.size());
        assertEquals("Москва", top.get(0));
        assertEquals("СПб", top.get(1));
    }
}