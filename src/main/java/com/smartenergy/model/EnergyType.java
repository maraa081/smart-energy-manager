package com.smartenergy.model;

public enum EnergyType {
    ELECTRICITE("kWh"),
    GAZ("kWh"),
    SOLAIRE("kWh"),
    CHAUFFAGE("kWh"),
    CLIMATISATION("kWh"),
    EAU("m³");

    private final String unite;

    EnergyType(String unite) {
        this.unite = unite;
    }

    public String getUnite() {
        return unite;
    }
}
