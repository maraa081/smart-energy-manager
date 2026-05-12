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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service météo utilisant l'API Open-Meteo (gratuite, sans clé).
 * <p>
 * Utilise {@link java.net.http.HttpClient} natif (Java 11+).
 * </p>
 */
public class WeatherService {

    private static final String OPEN_METEO_BASE = "https://api.open-meteo.com/v1/forecast";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public WeatherService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.mapper = new ObjectMapper();
    }

    // ---------------------------------------------------------------
    // Température moyenne d'un jour donné
    // ---------------------------------------------------------------

    /**
     * Récupère la température moyenne (°C) pour une localisation et une date.
     *
     * @return la température moyenne, ou {@link Optional#empty()} si indisponible.
     */
    public Optional<Double> getTemperature(double lat, double lon, LocalDate date) {
        try {
            String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            String url = OPEN_METEO_BASE
                    + "?latitude=" + lat
                    + "&longitude=" + lon
                    + "&daily=temperature_2m_mean"
                    + "&timezone=auto"
                    + "&start_date=" + dateStr
                    + "&end_date=" + dateStr;

            String json = fetch(url);
            JsonNode root = mapper.readTree(json);

            JsonNode daily = root.path("daily");
            JsonNode times = daily.path("time");
            JsonNode temps = daily.path("temperature_2m_mean");

            for (int i = 0; i < times.size(); i++) {
                if (dateStr.equals(times.get(i).asText())) {
                    JsonNode val = temps.get(i);
                    if (val != null && !val.isNull()) {
                        return Optional.of(val.asDouble());
                    }
                }
            }
        } catch (Exception e) {
            // Silently fail — météo non critique
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------
    // Alertes météo
    // ---------------------------------------------------------------

    /**
     * Vérifie les conditions météo extrêmes pour une localisation (jour courant + lendemain).
     *
     * @return un message d'alerte, ou une chaîne vide si aucun risque.
     */
    public String getWeatherAlert(double lat, double lon) {
        try {
            String url = OPEN_METEO_BASE
                    + "?latitude=" + lat
                    + "&longitude=" + lon
                    + "&current=temperature_2m,precipitation,weather_code"
                    + "&daily=temperature_2m_max,precipitation_sum,weather_code"
                    + "&timezone=auto"
                    + "&forecast_days=2";

            String json = fetch(url);
            JsonNode root = mapper.readTree(json);

            // Conditions actuelles
            JsonNode current = root.path("current");
            double temp = current.path("temperature_2m").asDouble(-999);
            double precip = current.path("precipitation").asDouble(0);
            int weatherCode = current.path("weather_code").asInt(0);

            // Prévisions jour + 1
            JsonNode daily = root.path("daily");
            double maxTempTomorrow = -999;
            double precipTomorrow = 0;
            if (daily.has("temperature_2m_max") && daily.path("temperature_2m_max").size() > 1) {
                maxTempTomorrow = daily.path("temperature_2m_max").get(1).asDouble(-999);
                precipTomorrow = daily.path("precipitation_sum").get(1).asDouble(0);
            }

            List<String> alerts = new java.util.ArrayList<>();

            // Vague de chaleur
            if (temp > 35 || maxTempTomorrow > 35) {
                alerts.add("⚠️ Alerte canicule : température > 35°C");
            }
            // Gel
            if (temp < -5 || maxTempTomorrow < -5) {
                alerts.add("❄️ Alerte gel : température < -5°C");
            }
            // Fortes précipitations
            if (precip > 20 || precipTomorrow > 20) {
                alerts.add("🌧️ Fortes précipitations attendues (> 20 mm)");
            }
            // Code météo extrême (orage, neige, etc.)
            if (isSevereWeatherCode(weatherCode)) {
                alerts.add("⛈️ Conditions météo sévères détectées (code %d)".formatted(weatherCode));
            }

            return String.join(" | ", alerts);

        } catch (Exception e) {
            return "";
        }
    }

    // ---------------------------------------------------------------
    // Privé
    // ---------------------------------------------------------------

    private String fetch(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Open-Meteo returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    /**
     * Codes WMO pour conditions sévères :
     * 95-99 = orage, 71-77 = neige, 56-57 = grésil, 24-27 = phénomènes de neige/grésil
     */
    private static boolean isSevereWeatherCode(int code) {
        return (code >= 71 && code <= 77)    // Neige
                || (code >= 95 && code <= 99) // Orage
                || code == 56 || code == 57;   // Grésil verglaçant
    }
}
