package com.smartenergy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class WeatherService {

    private static final String OPEN_METEO_BASE = "https://api.open-meteo.com/v1/forecast";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public WeatherService() {
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        this.mapper = new ObjectMapper();
    }

    public Optional<Double> getTemperature(double lat, double lon, LocalDate date) {
        try {
            String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            String url = OPEN_METEO_BASE + "?latitude=" + lat + "&longitude=" + lon
                    + "&daily=temperature_2m_mean&timezone=auto&start_date=" + dateStr + "&end_date=" + dateStr;
            JsonNode root = mapper.readTree(fetch(url));
            JsonNode temps = root.path("daily").path("temperature_2m_mean");
            for (int i = 0; i < root.path("daily").path("time").size(); i++) {
                if (dateStr.equals(root.path("daily").path("time").get(i).asText())) {
                    JsonNode val = temps.get(i);
                    if (val != null && !val.isNull()) return Optional.of(val.asDouble());
                }
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    public String getWeatherAlert(double lat, double lon) {
        try {
            String url = OPEN_METEO_BASE + "?latitude=" + lat + "&longitude=" + lon
                    + "&current=temperature_2m,precipitation,weather_code"
                    + "&daily=temperature_2m_max,precipitation_sum,weather_code"
                    + "&timezone=auto&forecast_days=2";
            JsonNode root = mapper.readTree(fetch(url));

            double temp = root.path("current").path("temperature_2m").asDouble(-999);
            double precip = root.path("current").path("precipitation").asDouble(0);
            int weatherCode = root.path("current").path("weather_code").asInt(0);
            double maxTempTomorrow = root.path("daily").path("temperature_2m_max").size() > 1
                    ? root.path("daily").path("temperature_2m_max").get(1).asDouble(-999) : -999;
            double precipTomorrow = root.path("daily").path("precipitation_sum").size() > 1
                    ? root.path("daily").path("precipitation_sum").get(1).asDouble(0) : 0;

            java.util.List<String> alerts = new java.util.ArrayList<>();
            if (temp > 35 || maxTempTomorrow > 35) alerts.add("⚠️ Alerte canicule : température > 35°C");
            if (temp < -5 || maxTempTomorrow < -5) alerts.add("❄️ Alerte gel : température < -5°C");
            if (precip > 20 || precipTomorrow > 20) alerts.add("🌧️ Fortes précipitations (> 20 mm)");
            if (isSevereWeatherCode(weatherCode)) alerts.add("⛈️ Conditions météo sévères");

            return String.join(" | ", alerts);
        } catch (Exception e) { return ""; }
    }

    private String fetch(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(TIMEOUT).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new RuntimeException("Open-Meteo HTTP " + response.statusCode());
        return response.body();
    }

    private static boolean isSevereWeatherCode(int code) {
        return (code >= 71 && code <= 77) || (code >= 95 && code <= 99) || code == 56 || code == 57;
    }
}
