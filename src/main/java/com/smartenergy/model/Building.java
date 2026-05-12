package com.smartenergy.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class Building {

    private String id;
    private String nom;
    private String adresse;
    private double surface;
    private BuildingType type;
    private final List<ConsumptionRecord> consommationRecords;

    public Building() {
        this.id = UUID.randomUUID().toString();
        this.consommationRecords = new ArrayList<>();
    }

    @JsonCreator
    public Building(
            @JsonProperty("id") String id,
            @JsonProperty("nom") String nom,
            @JsonProperty("adresse") String adresse,
            @JsonProperty("surface") double surface,
            @JsonProperty("type") BuildingType type,
            @JsonProperty("consommationRecords") List<ConsumptionRecord> consommationRecords) {
        this.id = id;
        this.nom = nom;
        this.adresse = adresse;
        this.surface = surface;
        this.type = type;
        this.consommationRecords = consommationRecords != null
                ? new ArrayList<>(consommationRecords) : new ArrayList<>();
    }

    // --- Getters ---

    public String getId()                           { return id; }
    public String getNom()                          { return nom; }
    public String getAdresse()                      { return adresse; }
    public double getSurface()                      { return surface; }
    public BuildingType getType()                   { return type; }
    public List<ConsumptionRecord> getConsommationRecords() { return consommationRecords; }

    // --- Setters ---

    public void setId(String id)                    { this.id = id; }
    public void setNom(String nom)                  { this.nom = nom; }
    public void setAdresse(String adresse)          { this.adresse = adresse; }
    public void setSurface(double surface)          { this.surface = surface; }
    public void setType(BuildingType type)          { this.type = type; }

    // --- Helpers ---

    public double getTotalConsommation() {
        return consommationRecords.stream()
                .mapToDouble(ConsumptionRecord::getQuantite)
                .sum();
    }

    public double getCoutTotal() {
        return consommationRecords.stream()
                .mapToDouble(ConsumptionRecord::getCoutEstime)
                .sum();
    }

    public int getNombreReleves() {
        return consommationRecords.size();
    }

    public void addConsumptionRecord(ConsumptionRecord record) {
        this.consommationRecords.add(record);
    }

    // --- Record-style accessors (for compat with other services) ---

    public String id()  { return id; }
    public String nom() { return nom; }
    public List<ConsumptionRecord> consommations() { return consommationRecords; }

    @Override
    public String toString() {
        return nom != null ? nom : "(sans nom)";
    }
}
