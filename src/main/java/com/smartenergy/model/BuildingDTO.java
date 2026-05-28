package com.smartenergy.model;

public class BuildingDTO {

    private String id;
    private String nom;
    private BuildingType type;
    private double totalConsommation;
    private double coutTotal;
    private int nombreReleves;

    public BuildingDTO() {}

    public BuildingDTO(String id, String nom, BuildingType type,
                       double totalConsommation, double coutTotal, int nombreReleves) {
        this.id = id; this.nom = nom; this.type = type;
        this.totalConsommation = totalConsommation;
        this.coutTotal = coutTotal; this.nombreReleves = nombreReleves;
    }

    public String getId() { return id; }
    public String getNom() { return nom; }
    public BuildingType getType() { return type; }
    public double getTotalConsommation() { return totalConsommation; }
    public double getCoutTotal() { return coutTotal; }
    public int getNombreReleves() { return nombreReleves; }

    public void setId(String id) { this.id = id; }
    public void setNom(String nom) { this.nom = nom; }
    public void setType(BuildingType type) { this.type = type; }
    public void setTotalConsommation(double totalConsommation) { this.totalConsommation = totalConsommation; }
    public void setCoutTotal(double coutTotal) { this.coutTotal = coutTotal; }
    public void setNombreReleves(int nombreReleves) { this.nombreReleves = nombreReleves; }

    @Override
    public String toString() {
        return "BuildingDTO{id=" + id + ", nom=" + nom + "}";
    }
}
