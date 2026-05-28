package com.smartenergy.service;

import com.smartenergy.data.CsvImporter;
import com.smartenergy.data.JsonRepository;
import com.smartenergy.model.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EnergyService {

    private static EnergyService instance;

    private JsonRepository repository;
    private Map<String, Building> buildings;
    private boolean loaded = false;

    // ---- Singleton ----

    private EnergyService() {
        this.repository = JsonRepository.getInstance();
    }

    public static synchronized EnergyService getInstance() {
        if (instance == null) {
            instance = new EnergyService();
        }
        return instance;
    }

    // ---- Loading ----

    private void ensureLoaded() {
        if (!loaded) {
            try {
                buildings = repository.loadAll();
                loaded = true;
                System.out.println("✔ " + buildings.size() + " bâtiments chargés depuis : "
                        + repository.getDataFilePath());
            } catch (Exception e) {
                System.err.println("⚠ Erreur chargement données : " + e.getMessage());
                System.err.println("→ Réinitialisation avec données d'exemple...");
                // Si le fichier est corrompu, on le supprime et on reseed
                try {
                    java.nio.file.Files.deleteIfExists(
                            java.nio.file.Paths.get(repository.getDataFilePath()));
                } catch (java.io.IOException ignored) {}
                // Force reseed en recréant le repository
                this.repository = JsonRepository.configure(repository.getDataFilePath());
                // Maintenant on recharge
                try {
                    buildings = repository.loadAll();
                    System.out.println("✔ " + buildings.size() + " bâtiments regénérés");
                } catch (IOException e2) {
                    System.err.println("⚠ Échec reseed : " + e2.getMessage());
                    System.err.println("→ Création d'un jeu vierge");
                    buildings = new HashMap<>();
                }
                loaded = true;
            }
        }
    }

    private void persist() {
        try {
            repository.saveAll(buildings);
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de la sauvegarde", e);
        }
    }

    // ========================================================================
    // DASHBOARD
    // ========================================================================

    public record DashboardSummary(
            double consoJour,
            double consoMois,
            double consoAnnee,
            double coutTotal,
            String topBuildingNom,
            double topBuildingConso,
            List<Anomaly> recentAlerts
    ) {}

    public DashboardSummary getDashboardSummary() {
        ensureLoaded();
        LocalDate now = LocalDate.now();
        double consoJour = 0;
        double consoMois = 0;
        double consoAnnee = 0;
        double coutTotal = 0;
        String topBuildingNom = "—";
        double topBuildingConso = 0;
        List<Anomaly> alerts = new ArrayList<>();

        for (Building b : buildings.values()) {
            double total = b.getTotalConsommation();
            coutTotal += b.getCoutTotal();
            if (total > topBuildingConso) {
                topBuildingConso = total;
                topBuildingNom = b.getNom();
            }
            for (ConsumptionRecord r : b.getConsommationRecords()) {
                LocalDate d = r.getDateHeure().toLocalDate();
                if (d.equals(now)) consoJour += r.getQuantite();
                if (d.getMonth() == now.getMonth() && d.getYear() == now.getYear())
                    consoMois += r.getQuantite();
                if (d.getYear() == now.getYear()) consoAnnee += r.getQuantite();
            }
        }

        return new DashboardSummary(consoJour, consoMois, consoAnnee, coutTotal,
                topBuildingNom, topBuildingConso, alerts);
    }

    // ========================================================================
    // BUILDINGS
    // ========================================================================

    public List<Building> getAllBuildings() {
        ensureLoaded();
        return new ArrayList<>(buildings.values());
    }

    public Building getBuilding(String id) {
        ensureLoaded();
        return buildings.get(id);
    }

    public void saveBuilding(Building building) {
        ensureLoaded();
        buildings.put(building.getId(), building);
        persist();
    }

    public Building createBuilding(String nom, String adresse, double surface, BuildingType type) {
        Building b = new Building();
        b.setNom(nom);
        b.setAdresse(adresse);
        b.setSurface(surface);
        b.setType(type);
        b.setLatitude(48.8566);  // Paris par défaut
        b.setLongitude(2.3522);
        return b;
    }

    public void deleteBuilding(String id) {
        ensureLoaded();
        buildings.remove(id);
        persist();
    }

    public Optional<Building> findBuildingById(String id) {
        ensureLoaded();
        return Optional.ofNullable(buildings.get(id));
    }

    // ========================================================================
    // CONSUMPTION RECORDS
    // ========================================================================

    public List<ConsumptionRecord> getConsumptionRecords(String buildingId) {
        ensureLoaded();
        Building b = buildings.get(buildingId);
        if (b == null) return List.of();
        return new ArrayList<>(b.getConsommationRecords());
    }

    public List<ConsumptionRecord> getConsumptionRecords(String buildingId, LocalDate start, LocalDate end) {
        ensureLoaded();
        Building b = buildings.get(buildingId);
        if (b == null) return List.of();
        return b.getConsommationRecords().stream()
                .filter(r -> {
                    LocalDate d = r.getDateHeure().toLocalDate();
                    return !d.isBefore(start) && !d.isAfter(end);
                })
                .collect(Collectors.toList());
    }

    public void addConsumptionRecord(String buildingId, ConsumptionRecord record) {
        ensureLoaded();
        Building b = buildings.get(buildingId);
        if (b == null) return;
        b.addConsumptionRecord(record);
        persist();
    }

    public double getTotalConsumption(String buildingId, LocalDate start, LocalDate end) {
        ensureLoaded();
        Building b = buildings.get(buildingId);
        if (b == null) return 0;
        return b.getConsommationRecords().stream()
                .filter(r -> {
                    LocalDate d = r.getDateHeure().toLocalDate();
                    return !d.isBefore(start) && !d.isAfter(end);
                })
                .mapToDouble(ConsumptionRecord::getQuantite)
                .sum();
    }

    public List<ConsumptionRecord> getConsumptionsBetween(String buildingId, LocalDate start, LocalDate end) {
        return getConsumptionRecords(buildingId, start, end);
    }

    public void importCsv(String buildingId, String filePath) {
        CsvImporter importer = new CsvImporter();
        try {
            List<ConsumptionRecord> records = importer.importFromFile(filePath);
            ensureLoaded();
            Building b = buildings.get(buildingId);
            if (b == null) return;
            for (ConsumptionRecord r : records) {
                b.addConsumptionRecord(r);
            }
            persist();
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de l'import CSV : " + e.getMessage(), e);
        }
    }

    /**
     * Génère des données de test aléatoires pour un bâtiment.
     */
    public void generateTestData(String buildingId) {
        ensureLoaded();
        Building b = buildings.get(buildingId);
        if (b == null) return;

        Random rand = new Random();
        LocalDate now = LocalDate.now();
        EnergyType[] types = EnergyType.values();

        // Génère ~500 relevés sur les 24 derniers mois
        for (int i = 0; i < 500; i++) {
            LocalDateTime dt = now.minusDays(rand.nextInt(730))
                    .atTime(rand.nextInt(24), rand.nextInt(60));
            EnergyType type = types[rand.nextInt(types.length)];
            double qte = 1.0 + rand.nextDouble() * 50;
            double cout = qte * (0.10 + rand.nextDouble() * 0.30);
            b.addConsumptionRecord(new ConsumptionRecord(dt, type, qte, cout, "kWh"));
        }
        persist();
    }

    // ========================================================================
    // ANALYSIS
    // ========================================================================

    /** Bâtiment le plus consommateur */
    public record TopBuilding(String nom, double consommation, double cout) {}

    public TopBuilding getTopBuilding() {
        ensureLoaded();
        return buildings.values().stream()
                .map(b -> new TopBuilding(b.getNom(), b.getTotalConsommation(), b.getCoutTotal()))
                .max(Comparator.comparingDouble(TopBuilding::consommation))
                .orElse(new TopBuilding("—", 0, 0));
    }

    /** Type d'énergie dominant */
    public record DominantEnergy(EnergyType type, double total, double percentage) {}

    public DominantEnergy getDominantEnergy() {
        ensureLoaded();
        Map<EnergyType, Double> totals = new HashMap<>();
        for (Building b : buildings.values()) {
            for (ConsumptionRecord r : b.getConsommationRecords()) {
                totals.merge(r.getType(), r.getQuantite(), Double::sum);
            }
        }
        double grandTotal = totals.values().stream().mapToDouble(Double::doubleValue).sum();
        EnergyType top = totals.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(EnergyType.ELECTRICITE);
        double topTotal = totals.getOrDefault(top, 0.0);
        return new DominantEnergy(top, topTotal,
                grandTotal > 0 ? (topTotal / grandTotal) * 100 : 0);
    }

    /** Tendance */
    public enum Trend { HAUSSE, BAISSE, STABLE }

    public Trend getTrend() {
        ensureLoaded();
        LocalDate now = LocalDate.now();
        double ceMois = 0, moisPrec = 0;
        YearMonth ym = YearMonth.from(now);
        YearMonth ymPrev = ym.minusMonths(1);
        for (Building b : buildings.values()) {
            for (ConsumptionRecord r : b.getConsommationRecords()) {
                YearMonth rm = YearMonth.from(r.getDateHeure());
                if (rm.equals(ym)) ceMois += r.getQuantite();
                if (rm.equals(ymPrev)) moisPrec += r.getQuantite();
            }
        }
        if (moisPrec == 0) return ceMois > 0 ? Trend.HAUSSE : Trend.STABLE;
        double diff = (ceMois - moisPrec) / moisPrec;
        if (diff > 0.05) return Trend.HAUSSE;
        if (diff < -0.05) return Trend.BAISSE;
        return Trend.STABLE;
    }

    /** Estimation mensuelle (moyenne des 3 derniers mois) */
    public double getMonthlyEstimate() {
        ensureLoaded();
        LocalDate now = LocalDate.now();
        double total = 0;
        long months = 0;
        for (int i = 1; i <= 3; i++) {
            YearMonth ym = YearMonth.from(now.minusMonths(i));
            double m = 0;
            for (Building b : buildings.values()) {
                for (ConsumptionRecord r : b.getConsommationRecords()) {
                    if (YearMonth.from(r.getDateHeure()).equals(ym)) {
                        m += r.getQuantite();
                    }
                }
            }
            total += m;
            if (m > 0) months++;
        }
        return months > 0 ? total / months : 0;
    }

    /** Estimation coût mensuel */
    public double getMonthlyCostEstimate() {
        ensureLoaded();
        LocalDate now = LocalDate.now();
        double total = 0;
        long months = 0;
        for (int i = 1; i <= 3; i++) {
            YearMonth ym = YearMonth.from(now.minusMonths(i));
            double m = 0;
            for (Building b : buildings.values()) {
                for (ConsumptionRecord r : b.getConsommationRecords()) {
                    if (YearMonth.from(r.getDateHeure()).equals(ym)) {
                        m += r.getCoutEstime();
                    }
                }
            }
            total += m;
            if (m > 0) months++;
        }
        return months > 0 ? total / months : 0;
    }

    /** Pics de consommation */
    public record ConsumptionPeak(LocalDate date, double quantite) {}

    public List<ConsumptionPeak> getConsumptionPeaks() {
        ensureLoaded();
        // Group by day, find top 5 days with highest consumption
        Map<LocalDate, Double> dailyTotals = new HashMap<>();
        for (Building b : buildings.values()) {
            for (ConsumptionRecord r : b.getConsommationRecords()) {
                LocalDate d = r.getDateHeure().toLocalDate();
                dailyTotals.merge(d, r.getQuantite(), Double::sum);
            }
        }
        return dailyTotals.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, Double>comparingByValue().reversed())
                .limit(5)
                .map(e -> new ConsumptionPeak(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    /** Anomalies détectées via AnomalyDetector */
    public List<Anomaly> getAnomalies() {
        ensureLoaded();
        AnomalyDetector detector = new AnomalyDetector(this);
        List<Anomaly> allAnomalies = new ArrayList<>();
        for (Building b : buildings.values()) {
            allAnomalies.addAll(detector.detectAnomalies(b.getId()));
        }
        return allAnomalies;
    }

    /** Prédiction mois prochain (régression linéaire simple sur 6 mois) */
    public double getNextMonthPrediction() {
        ensureLoaded();
        LocalDate now = LocalDate.now();
        // Build an array of monthly totals for the last 6 months
        List<Double> monthlyTotals = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = YearMonth.from(now.minusMonths(i));
            double total = 0;
            for (Building b : buildings.values()) {
                for (ConsumptionRecord r : b.getConsommationRecords()) {
                    if (YearMonth.from(r.getDateHeure()).equals(ym)) {
                        total += r.getQuantite();
                    }
                }
            }
            monthlyTotals.add(total);
        }

        // Simple linear regression: y = a + b*x
        int n = monthlyTotals.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += monthlyTotals.get(i);
            sumXY += i * monthlyTotals.get(i);
            sumX2 += i * i;
        }
        double b = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        double a = (sumY - b * sumX) / n;

        double prediction = a + b * n; // n = next month index
        return Math.max(0, prediction);
    }

    // ========================================================================
    // CHART DATA
    // ========================================================================

    /** Données pour LineChart (consommation par jour sur une période) */
    public record DailyConsumption(LocalDate date, double quantite) {}

    public List<DailyConsumption> getDailyConsumption(String buildingId, LocalDate start, LocalDate end) {
        ensureLoaded();
        Map<LocalDate, Double> daily = new HashMap<>();
        List<ConsumptionRecord> records = getConsumptionRecords(buildingId, start, end);
        for (ConsumptionRecord r : records) {
            LocalDate d = r.getDateHeure().toLocalDate();
            daily.merge(d, r.getQuantite(), Double::sum);
        }
        List<DailyConsumption> result = new ArrayList<>();
        LocalDate d = start;
        while (!d.isAfter(end)) {
            result.add(new DailyConsumption(d, daily.getOrDefault(d, 0.0)));
            d = d.plusDays(1);
        }
        return result;
    }

    /** Données pour PieChart (par type d'énergie) */
    public record TypeSummary(EnergyType type, double total) {}

    public List<TypeSummary> getConsumptionByType(String buildingId) {
        ensureLoaded();
        Map<EnergyType, Double> types = new HashMap<>();
        List<ConsumptionRecord> records = getConsumptionRecords(buildingId);
        for (ConsumptionRecord r : records) {
            types.merge(r.getType(), r.getQuantite(), Double::sum);
        }
        return types.entrySet().stream()
                .map(e -> new TypeSummary(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    /** Données pour BarChart (par mois) */
    public record MonthlySummary(String month, double total) {}

    public List<MonthlySummary> getMonthlyConsumption(String buildingId, int year) {
        ensureLoaded();
        Map<YearMonth, Double> monthly = new HashMap<>();
        List<ConsumptionRecord> records = getConsumptionRecords(buildingId);
        for (ConsumptionRecord r : records) {
            YearMonth ym = YearMonth.from(r.getDateHeure());
            if (ym.getYear() == year) {
                monthly.merge(ym, r.getQuantite(), Double::sum);
            }
        }
        return monthly.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new MonthlySummary(
                        e.getKey().getMonth().toString().substring(0, 3) + " " + e.getKey().getYear(),
                        e.getValue()))
                .collect(Collectors.toList());
    }

    /** Comparaison multi-bâtiments */
    public record BuildingComparison(String nom, double consommation) {}

    public List<BuildingComparison> getBuildingComparison(LocalDate start, LocalDate end) {
        ensureLoaded();
        List<BuildingComparison> result = new ArrayList<>();
        for (Building b : buildings.values()) {
            double total = b.getConsommationRecords().stream()
                    .filter(r -> {
                        LocalDate d = r.getDateHeure().toLocalDate();
                        return !d.isBefore(start) && !d.isAfter(end);
                    })
                    .mapToDouble(ConsumptionRecord::getQuantite)
                    .sum();
            result.add(new BuildingComparison(b.getNom(), total));
        }
        return result;
    }
}
