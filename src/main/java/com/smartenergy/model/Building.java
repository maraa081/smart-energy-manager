package com.smartenergy.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Building {

    private String id;
    private String nom;
    private String adresse;
    private double surface;
    private BuildingType type;
    private double latitude;
    private double longitude;
    private final List<ConsumptionRecord> consommationRecords;

    public Building() {
        this.id = UUID.randomUUID().toString();
        this.consommationRecords = new ArrayList<>();
    }

    @JsonCreator
    public Building(
            @JsonProperty("id") String id, @JsonProperty("nom") String nom,
            @JsonProperty("adresse") String adresse, @JsonProperty("surface") double surface,
            @JsonProperty("type") BuildingType type,
            @JsonProperty("latitude") double latitude, @JsonProperty("longitude") double longitude,
            @JsonProperty("consommationRecords") List<ConsumptionRecord> consommationRecords) {
        this.id = id; this.nom = nom; this.adresse = adresse; this.surface = surface; this.type = type;
        this.latitude = latitude; this.longitude = longitude;
        this.consommationRecords = consommationRecords != null ? new ArrayList<>(consommationRecords) : new ArrayList<>();
    }

    public String getId() { return id; }
    public String getNom() { return nom; }
    public String getAdresse() { return adresse; }
    public double getSurface() { return surface; }
    public BuildingType getType() { return type; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public List<ConsumptionRecord> getConsommationRecords() { return consommationRecords; }

    public void setId(String id) { this.id = id; }
    public void setNom(String nom) { this.nom = nom; }
    public void setAdresse(String adresse) { this.adresse = adresse; }
    public void setSurface(double surface) { this.surface = surface; }
    public void setType(BuildingType type) { this.type = type; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    @JsonIgnore
    public double getTotalConsommation() {
        return consommationRecords.stream().mapToDouble(ConsumptionRecord::getQuantite).sum();
    }

    @JsonIgnore
    public double getCoutTotal() {
        return consommationRecords.stream().mapToDouble(ConsumptionRecord::getCoutEstime).sum();
    }

    @JsonIgnore
    public int getNombreReleves() {
        return consommationRecords.size();
    }

    public void addConsumptionRecord(ConsumptionRecord record) {
        this.consommationRecords.add(record);
    }

    @JsonIgnore
    public String id() { return id; }
    @JsonIgnore
    public String nom() { return nom; }
    @JsonIgnore
    public List<ConsumptionRecord> consommations() { return consommationRecords; }

    @Override
    public String toString() { return nom != null ? nom : "(sans nom)"; }
}
