package com.smartenergy.data;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.smartenergy.model.Building;
import com.smartenergy.model.BuildingType;
import com.smartenergy.model.ConsumptionRecord;
import com.smartenergy.model.EnergyType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class JsonRepository {

    private static final String DATA_DIR = ".smart-energy-manager/data";
    private static final String DATA_FILE = "buildings.json";

    private static JsonRepository instance;

    private final ObjectMapper objectMapper;
    private final File dataFile;

    private JsonRepository() {
        this(getDefaultDataPath());
    }

    private JsonRepository(String filePath) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.dataFile = new File(filePath);

        // Ensure parent directory exists
        File parentDir = dataFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // Seed sample data on first run
        if (!dataFile.exists()) {
            seedSampleData();
        }
    }

    /**
     * Retourne le chemin absolu vers le fichier de données utilisateur.
     * Exemple : /home/maraa/.smart-energy-manager/data/buildings.json
     */
    private static String getDefaultDataPath() {
        String home = System.getProperty("user.home", ".");
        return Paths.get(home, DATA_DIR, DATA_FILE).toString();
    }

    /**
     * Crée des données d'exemple au premier lancement.
     */
    private void seedSampleData() {
        try {
            Map<String, Building> samples = new HashMap<>();
            Random rand = new Random(42); // seed fixe = reproductible
            LocalDate now = LocalDate.now();

            // ── Bâtiment 1 : Appartement ──
            Building apt = new Building();
            apt.setNom("Appartement Paris 11e");
            apt.setAdresse("15 Rue de la Roquette, 75011 Paris");
            apt.setSurface(65);
            apt.setType(BuildingType.APPARTEMENT);
            apt.setLatitude(48.8566);
            apt.setLongitude(2.3823);
            generateRecords(apt, now, rand, 180, 1.0, 15.0, EnergyType.ELECTRICITE, 90);
            generateRecords(apt, now, rand, 60, 2.0, 30.0, EnergyType.CHAUFFAGE, 60);
            generateRecords(apt, now, rand, 30, 0.1, 5.0, EnergyType.EAU, 30);
            samples.put(apt.getId(), apt);

            // ── Bâtiment 2 : Maison ──
            Building maison = new Building();
            maison.setNom("Maison Lyon 3e");
            maison.setAdresse("8 Rue de la Bourse, 69003 Lyon");
            maison.setSurface(120);
            maison.setType(BuildingType.MAISON);
            maison.setLatitude(45.7578);
            maison.setLongitude(4.8594);
            generateRecords(maison, now, rand, 200, 3.0, 35.0, EnergyType.ELECTRICITE, 80);
            generateRecords(maison, now, rand, 150, 5.0, 50.0, EnergyType.GAZ, 85);
            generateRecords(maison, now, rand, 40, 0.5, 8.0, EnergyType.EAU, 30);
            samples.put(maison.getId(), maison);

            // ── Bâtiment 3 : Bureau ──
            Building bureau = new Building();
            bureau.setNom("Bureau Tech Center");
            bureau.setAdresse("45 Cours du Médoc, 33000 Bordeaux");
            bureau.setSurface(350);
            bureau.setType(BuildingType.BUREAU);
            bureau.setLatitude(44.8378);
            bureau.setLongitude(-0.5792);
            generateRecords(bureau, now, rand, 300, 15.0, 80.0, EnergyType.ELECTRICITE, 95);
            generateRecords(bureau, now, rand, 100, 2.0, 20.0, EnergyType.CLIMATISATION, 50);
            generateRecords(bureau, now, rand, 50, 1.0, 10.0, EnergyType.EAU, 30);
            samples.put(bureau.getId(), bureau);

            // ── Bâtiment 4 : Commerce ──
            Building commerce = new Building();
            commerce.setNom("Boulangerie St-Michel");
            commerce.setAdresse("12 Place St-Michel, 31000 Toulouse");
            commerce.setSurface(80);
            commerce.setType(BuildingType.LOCAL_COMMERCIAL);
            commerce.setLatitude(43.6045);
            commerce.setLongitude(1.4440);
            generateRecords(commerce, now, rand, 250, 8.0, 45.0, EnergyType.ELECTRICITE, 90);
            generateRecords(commerce, now, rand, 80, 10.0, 60.0, EnergyType.GAZ, 80);
            generateRecords(commerce, now, rand, 20, 0.3, 3.0, EnergyType.EAU, 25);
            samples.put(commerce.getId(), commerce);

            saveAll(samples);
            System.out.println("✔ Données d'exemple créées dans : " + dataFile.getAbsolutePath());

        } catch (IOException e) {
            System.err.println("⚠ Impossible de créer les données d'exemple : " + e.getMessage());
        }
    }

    private void generateRecords(Building building, LocalDate now, Random rand,
                                  int count, double minQte, double maxQte,
                                  EnergyType type, int probabilityPct) {
        for (int i = 0; i < count; i++) {
            if (rand.nextInt(100) >= probabilityPct) continue;
            LocalDateTime dt = now.minusDays(rand.nextInt(365))
                    .atTime(LocalTime.of(rand.nextInt(8, 20), rand.nextInt(0, 60)));
            double qte = minQte + rand.nextDouble() * (maxQte - minQte);
            // Arrondi à 1 décimale
            qte = Math.round(qte * 10.0) / 10.0;
            double cout = Math.round(qte * (0.12 + rand.nextDouble() * 0.18) * 100.0) / 100.0;
            building.addConsumptionRecord(
                    new ConsumptionRecord(dt, type, qte, cout, type.getUnite()));
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
     * Retourne la nouvelle instance.
     */
    public static synchronized JsonRepository configure(String filePath) {
        instance = new JsonRepository(filePath);
        return instance;
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

    /**
     * Retourne le chemin du fichier de données (utile pour l'affichage).
     */
    public String getDataFilePath() {
        return dataFile.getAbsolutePath();
    }
}
