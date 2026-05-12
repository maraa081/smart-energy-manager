package com.smartenergy.service;

import com.smartenergy.model.Building;
import com.smartenergy.model.ConsumptionRecord;
import com.smartenergy.model.EnergyType;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service d'analyse statique fournissant tendances, prévisions
 * et comparaisons sur les données de consommation.
 */
public final class AnalysisService {

    private AnalysisService() {
        throw new UnsupportedOperationException("Classe utilitaire");
    }

    /** Tendance sur les 3 derniers mois glissants. */
    public enum Trend { HAUSSE, BAISSE, STABLE }

    // ---------------------------------------------------------------
    // Type d'énergie dominant
    // ---------------------------------------------------------------

    /**
     * Retourne le type d'énergie le plus consommé sur une période donnée.
     */
    public static EnergyType getDominantEnergyType(String buildingId,
                                                   LocalDate start,
                                                   LocalDate end,
                                                   EnergyService service) {
        Map<EnergyType, Double> byType = getConsumptionByType(buildingId, start, end, service);
        return byType.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(EnergyType.ELECTRICITE);
    }

    // ---------------------------------------------------------------
    // Tendance de consommation
    // ---------------------------------------------------------------

    /**
     * Compare le mois en cours au mois précédent pour déterminer la tendance.
     */
    public static Trend getConsumptionTrend(String buildingId, EnergyService service) {
        LocalDate now = LocalDate.now();
        LocalDate thisMonthStart = now.withDayOfMonth(1);
        LocalDate lastMonthStart = thisMonthStart.minusMonths(1);
        LocalDate lastMonthEnd = thisMonthStart.minusDays(1);

        double thisMonth = sumConsumption(service.getConsumptionRecords(buildingId, thisMonthStart, now));
        double lastMonth = sumConsumption(service.getConsumptionRecords(buildingId, lastMonthStart, lastMonthEnd));

        if (lastMonth == 0) return Trend.STABLE;

        double ratio = (thisMonth - lastMonth) / lastMonth;
        if (ratio > 0.05) return Trend.HAUSSE;
        if (ratio < -0.05) return Trend.BAISSE;
        return Trend.STABLE;
    }

    private static double sumConsumption(List<ConsumptionRecord> records) {
        return records.stream().mapToDouble(ConsumptionRecord::getQuantite).sum();
    }

    // ---------------------------------------------------------------
    // Estimation mensuelle
    // ---------------------------------------------------------------

    /**
     * Estimation de la consommation mensuelle basée sur la moyenne
     * des 3 derniers mois.
     */
    public static double getMonthlyEstimate(String buildingId, EnergyService service) {
        LocalDate now = LocalDate.now();
        double total = 0;
        int months = 3;
        for (int i = 1; i <= months; i++) {
            LocalDate start = now.minusMonths(i).withDayOfMonth(1);
            LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
            total += sumConsumption(service.getConsumptionRecords(buildingId, start, end));
        }
        return total / months;
    }

    // ---------------------------------------------------------------
    // Pics de consommation
    // ---------------------------------------------------------------

    /**
     * Retourne les {@code topN} jours avec la plus forte consommation.
     */
    public static List<LocalDate> getPeakPeriods(String buildingId,
                                                  int topN,
                                                  EnergyService service) {
        Building b = service.getBuilding(buildingId);
        if (b == null) return List.of();

        Map<LocalDate, Double> daySums = new HashMap<>();
        for (ConsumptionRecord r : b.getConsommationRecords()) {
            LocalDate day = r.getDateHeure().toLocalDate();
            daySums.merge(day, r.getQuantite(), Double::sum);
        }

        return daySums.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, Double>comparingByValue().reversed())
                .limit(topN)
                .map(Map.Entry::getKey)
                .toList();
    }

    // ---------------------------------------------------------------
    // Comparaison entre bâtiments
    // ---------------------------------------------------------------

    /**
     * Compare la consommation totale de plusieurs bâtiments sur l'année en cours.
     * @return map nom du bâtiment → consommation totale
     */
    public static Map<String, Double> compareBuildings(List<String> ids,
                                                        EnergyService service) {
        LocalDate now = LocalDate.now();
        LocalDate yearStart = now.withDayOfYear(1);
        Map<String, Double> result = new LinkedHashMap<>();
        for (String id : ids) {
            Building b = service.getBuilding(id);
            if (b != null) {
                double total = sumConsumption(service.getConsumptionRecords(b.getId(), yearStart, now));
                result.put(b.getNom(), total);
            }
        }
        return result;
    }

    // ---------------------------------------------------------------
    // Consommation par type d'énergie
    // ---------------------------------------------------------------

    /**
     * Répartition de la consommation par type d'énergie (toute la période disponible).
     */
    public static Map<EnergyType, Double> getConsumptionByType(String buildingId,
                                                                EnergyService service) {
        return getConsumptionByType(buildingId, null, null, service);
    }

    private static Map<EnergyType, Double> getConsumptionByType(String buildingId,
                                                                 LocalDate start,
                                                                 LocalDate end,
                                                                 EnergyService service) {
        List<ConsumptionRecord> records;
        if (start != null && end != null) {
            records = service.getConsumptionRecords(buildingId, start, end);
        } else {
            Building b = service.getBuilding(buildingId);
            records = b != null ? b.getConsommationRecords() : List.of();
        }

        return records.stream()
                .collect(Collectors.groupingBy(
                        ConsumptionRecord::getType,
                        Collectors.summingDouble(ConsumptionRecord::getQuantite)
                ));
    }

    // ---------------------------------------------------------------
    // Prédiction mois suivant (moyenne mobile simple)
    // ---------------------------------------------------------------

    /**
     * Prédiction pour le mois prochain basée sur une moyenne mobile
     * simple des 3 derniers mois.
     */
    public static double predictNextMonth(String buildingId, EnergyService service) {
        return getMonthlyEstimate(buildingId, service);
    }
}
