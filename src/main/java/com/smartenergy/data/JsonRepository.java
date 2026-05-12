package com.smartenergy.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.smartenergy.model.Building;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class JsonRepository {

    private static final String DEFAULT_FILE_PATH = "data/buildings.json";

    private static JsonRepository instance;

    private final ObjectMapper objectMapper;
    private final File dataFile;

    private JsonRepository() {
        this(DEFAULT_FILE_PATH);
    }

    private JsonRepository(String filePath) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.dataFile = new File(filePath);

        // Ensure parent directory exists
        File parentDir = dataFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
    }

    /**
     * Retourne l'instance unique du singleton.
     */
    public static synchronized JsonRepository getInstance() {
        if (instance == null) {
            instance = new JsonRepository();
        }
        return instance;
    }

    /**
     * Reconfigure le chemin du fichier de données (utile pour les tests).
     */
    public static synchronized void configure(String filePath) {
        instance = new JsonRepository(filePath);
    }

    /**
     * Réinitialise le singleton (utile pour les tests).
     */
    public static synchronized void reset() {
        instance = null;
    }

    // ---- Sauvegarde globale ----

    /**
     * Sauvegarde toute la map de bâtiments dans le fichier JSON.
     */
    public void saveAll(Map<String, Building> buildings) throws IOException {
        objectMapper.writeValue(dataFile, buildings);
    }

    /**
     * Charge tous les bâtiments depuis le fichier JSON.
     * Retourne une map vide si le fichier n'existe pas.
     */
    public Map<String, Building> loadAll() throws IOException {
        if (!dataFile.exists()) {
            return new HashMap<>();
        }
        return objectMapper.readValue(dataFile,
                objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, Building.class));
    }

    // ---- Opérations individuelles ----

    /**
     * Sauvegarde (ajoute ou remplace) un bâtiment dans le fichier.
     */
    public void saveBuilding(Building building) throws IOException {
        Map<String, Building> buildings = loadAll();
        buildings.put(building.getId(), building);
        saveAll(buildings);
    }

    /**
     * Charge un bâtiment par son identifiant.
     * Retourne null s'il n'existe pas.
     */
    public Building loadBuilding(String id) throws IOException {
        Map<String, Building> buildings = loadAll();
        return buildings.get(id);
    }

    /**
     * Supprime un bâtiment du fichier par son identifiant.
     */
    public void deleteBuilding(String id) throws IOException {
        Map<String, Building> buildings = loadAll();
        buildings.remove(id);
        saveAll(buildings);
    }

    /**
     * Retourne tous les bâtiments sous forme de map.
     */
    public Map<String, Building> getAllBuildings() throws IOException {
        return loadAll();
    }
}
