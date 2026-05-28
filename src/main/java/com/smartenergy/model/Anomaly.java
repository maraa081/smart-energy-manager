package com.smartenergy.model;

import java.time.LocalDateTime;

public record Anomaly(String description, LocalDateTime date, AnomalyType type, double ecart) {
    public enum AnomalyType {
        PIC_CONSOMMATION, ABSENCE_DONNEES, VALEUR_ABERRANTE, AUTRE
    }
}
