package com.smartenergy.model;

import java.time.LocalDateTime;

public class ConsumptionRecord {

    private LocalDateTime dateHeure;
    private EnergyType type;
    private double quantite;       // en kWh
    private double coutEstime;     // en euros
    private String unite;

    public ConsumptionRecord() {
    }

    public ConsumptionRecord(LocalDateTime dateHeure, EnergyType type,
                             double quantite, double coutEstime, String unite) {
        this.dateHeure = dateHeure;
        this.type = type;
        this.quantite = quantite;
        this.coutEstime = coutEstime;
        this.unite = unite;
    }

    // --- Getters ---

    public LocalDateTime getDateHeure() {
        return dateHeure;
    }

    public EnergyType getType() {
        return type;
    }

    public double getQuantite() {
        return quantite;
    }

    public double getCoutEstime() {
        return coutEstime;
    }

    public String getUnite() {
        return unite;
    }

    // --- Setters ---

    public void setDateHeure(LocalDateTime dateHeure) {
        this.dateHeure = dateHeure;
    }

    public void setType(EnergyType type) {
        this.type = type;
    }

    public void setQuantite(double quantite) {
        this.quantite = quantite;
    }

    public void setCoutEstime(double coutEstime) {
        this.coutEstime = coutEstime;
    }

    public void setUnite(String unite) {
        this.unite = unite;
    }

    @Override
    public String toString() {
        return "ConsumptionRecord{" +
                "dateHeure=" + dateHeure +
                ", type=" + type +
                ", quantite=" + quantite +
                ", coutEstime=" + coutEstime +
                ", unite='" + unite + '\'' +
                '}';
    }
}
