package com.smartenergy.service;

import com.smartenergy.model.Anomaly;
import com.smartenergy.model.Anomaly.AnomalyType;
import com.smartenergy.model.Building;
import com.smartenergy.model.ConsumptionRecord;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class AnomalyDetector {

    private static final double ZSCORE_THRESHOLD = 2.5;
    private static final long MISSING_DATA_DAYS = 3;
    private static final double ABERRANT_MULTIPLIER = 5.0;

    private final EnergyService energyService;

    public AnomalyDetector(EnergyService energyService) {
        this.energyService = energyService;
    }

    public List<Anomaly> detectAnomalies(String buildingId) {
        Building b = energyService.getBuilding(buildingId);
        if (b == null || b.getConsommationRecords().isEmpty()) return List.of();

        List<ConsumptionRecord> records = b.getConsommationRecords();
        List<Anomaly> anomalies = new ArrayList<>();
        anomalies.addAll(detectSpikes(records));
        anomalies.addAll(detectMissingData(records));
        anomalies.addAll(detectAberrantValues(records));
        return Collections.unmodifiableList(anomalies);
    }

    public List<Anomaly> detectSpikes(Collection<ConsumptionRecord> records) {
        if (records.isEmpty()) return List.of();
        double mean = records.stream().mapToDouble(ConsumptionRecord::getQuantite).average().orElse(0);
        double variance = records.stream().mapToDouble(r -> Math.pow(r.getQuantite() - mean, 2)).average().orElse(0);
        double stdDev = Math.sqrt(variance);
        if (stdDev == 0) return List.of();

        List<Anomaly> spikes = new ArrayList<>();
        for (ConsumptionRecord r : records) {
            double zScore = (r.getQuantite() - mean) / stdDev;
            if (Math.abs(zScore) > ZSCORE_THRESHOLD) {
                spikes.add(new Anomaly(
                        "Pic de consommation : %.2f kWh (z-score=%.2f)".formatted(r.getQuantite(), zScore),
                        r.getDateHeure(), AnomalyType.PIC_CONSOMMATION, zScore));
            }
        }
        return spikes;
    }

    public List<Anomaly> detectMissingData(Collection<ConsumptionRecord> records) {
        if (records.size() < 2) return List.of();
        List<ConsumptionRecord> sorted = new ArrayList<>(records);
        sorted.sort(Comparator.comparing(ConsumptionRecord::getDateHeure));

        List<Anomaly> gaps = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            long daysBetween = ChronoUnit.DAYS.between(sorted.get(i - 1).getDateHeure(), sorted.get(i).getDateHeure());
            if (daysBetween > MISSING_DATA_DAYS) {
                gaps.add(new Anomaly(
                        "Absence de données du %s au %s (%d jours)"
                                .formatted(sorted.get(i - 1).getDateHeure().toLocalDate(), sorted.get(i).getDateHeure().toLocalDate(), daysBetween),
                        sorted.get(i).getDateHeure(), AnomalyType.ABSENCE_DONNEES, daysBetween));
            }
        }
        return gaps;
    }

    public List<Anomaly> detectAberrantValues(Collection<ConsumptionRecord> records) {
        if (records.isEmpty()) return List.of();
        double mean = records.stream().mapToDouble(ConsumptionRecord::getQuantite).average().orElse(0);
        double threshold = mean * ABERRANT_MULTIPLIER;

        List<Anomaly> aberrant = new ArrayList<>();
        for (ConsumptionRecord r : records) {
            if (r.getQuantite() < 0) {
                aberrant.add(new Anomaly("Valeur négative : %.2f %s".formatted(r.getQuantite(), r.getUnite()),
                        r.getDateHeure(), AnomalyType.VALEUR_ABERRANTE, r.getQuantite()));
            } else if (mean > 0 && r.getQuantite() > threshold) {
                aberrant.add(new Anomaly("Valeur anormalement élevée : %.2f %s".formatted(r.getQuantite(), r.getUnite()),
                        r.getDateHeure(), AnomalyType.VALEUR_ABERRANTE, r.getQuantite() / mean));
            }
        }
        return aberrant;
    }
}
