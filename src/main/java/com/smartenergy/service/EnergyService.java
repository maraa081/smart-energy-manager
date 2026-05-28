package com.smartenergy.service;

import com.smartenergy.data.CsvImporter;
import com.smartenergy.data.JsonRepository;
import com.smartenergy.model.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

public class EnergyService {

    private static EnergyService instance;
    private JsonRepository repository;
    private Map<String, Building> buildings;
    private boolean loaded = false;

    private EnergyService() {
        this.repository = JsonRepository.getInstance();
    }

    public static synchronized EnergyService getInstance() {
        if (instance == null) {
            instance = new EnergyService();
        }
        return instance;
    }

    private void ensureLoaded() {
        if (!loaded) {
            try {
                buildings = repository.loadAll();
                loaded = true;
                int fixes = migrerUnites();
                if (fixes > 0) {
                    persist();
                    System.out.println("[OK]  " + fixes + " relevés migrés (unités corrigées)");
                }
                System.out.println("[OK]  " + buildings.size() + " bâtiments chargés depuis : " + repository.getDataFilePath());
            } catch (Exception e) {
                System.err.println(" Erreur chargement données : " + e.getMessage());
                System.err.println("→ Réinitialisation avec données d'exemple...");
                try {
                    java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(repository.getDataFilePath()));
                } catch (java.io.IOException ignored) {}
                this.repository = JsonRepository.configure(repository.getDataFilePath());
                try {
                    buildings = repository.loadAll();
                    System.out.println("[OK]  " + buildings.size() + " bâtiments regénérés");
                } catch (IOException e2) {
                    System.err.println(" Échec reseed : " + e2.getMessage());
                    buildings = new HashMap<>();
                }
                loaded = true;
            }
        }
    }

    private int migrerUnites() {
        int fixes = 0;
        for (Building b : buildings.values()) {
            for (ConsumptionRecord r : b.getConsommationRecords()) {
                String bonneUnite = r.getType().getUnite();
                if (!bonneUnite.equals(r.getUnite())) {
                    r.setUnite(bonneUnite);
                    fixes++;
                }
            }
        }
        return fixes;
    }

    private void persist() {
        try {
            repository.saveAll(buildings);
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de la sauvegarde", e);
        }
    }

    public record DashboardSummary(
            double consoJour, double consoMois, double consoAnnee, double coutTotal,
            String topBuildingNom, double topBuildingConso,
            double eauJour, double eauMois, double eauAnnee,
            List<Anomaly> recentAlerts) {}

    public DashboardSummary getDashboardSummary() {
        ensureLoaded();
        LocalDate now = LocalDate.now();
        double consoJour = 0, consoMois = 0, consoAnnee = 0;
        double eauJour = 0, eauMois = 0, eauAnnee = 0, coutTotal = 0;
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
                boolean isEau = r.getType() == EnergyType.EAU;
                if (d.equals(now)) { if (isEau) eauJour += r.getQuantite(); else consoJour += r.getQuantite(); }
                if (d.getMonth() == now.getMonth() && d.getYear() == now.getYear()) {
                    if (isEau) eauMois += r.getQuantite(); else consoMois += r.getQuantite();
                }
                if (d.getYear() == now.getYear()) {
                    if (isEau) eauAnnee += r.getQuantite(); else consoAnnee += r.getQuantite();
                }
            }
        }
        return new DashboardSummary(consoJour, consoMois, consoAnnee, coutTotal,
                topBuildingNom, topBuildingConso, eauJour, eauMois, eauAnnee, alerts);
    }

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
        b.setNom(nom); b.setAdresse(adresse); b.setSurface(surface); b.setType(type);
        b.setLatitude(48.8566); b.setLongitude(2.3522);
        return b;
    }

    public void deleteBuilding(String id) {
        ensureLoaded();
        buildings.remove(id);
        persist();
    }

    public List<ConsumptionRecord> getConsumptionRecords(String buildingId) {
        ensureLoaded();
        Building b = buildings.get(buildingId);
        return b == null ? List.of() : new ArrayList<>(b.getConsommationRecords());
    }

    public List<ConsumptionRecord> getConsumptionRecords(String buildingId, LocalDate start, LocalDate end) {
        ensureLoaded();
        Building b = buildings.get(buildingId);
        if (b == null) return List.of();
        return b.getConsommationRecords().stream()
                .filter(r -> !r.getDateHeure().toLocalDate().isBefore(start) && !r.getDateHeure().toLocalDate().isAfter(end))
                .collect(Collectors.toList());
    }

    public void addConsumptionRecord(String buildingId, ConsumptionRecord record) {
        ensureLoaded();
        Building b = buildings.get(buildingId);
        if (b == null) return;
        b.addConsumptionRecord(record);
        persist();
    }

    public void importCsv(String buildingId, String filePath) {
        CsvImporter importer = new CsvImporter();
        try {
            List<ConsumptionRecord> records = importer.importFromFile(filePath);
            ensureLoaded();
            Building b = buildings.get(buildingId);
            if (b == null) return;
            for (ConsumptionRecord r : records) b.addConsumptionRecord(r);
            persist();
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de l'import CSV : " + e.getMessage(), e);
        }
    }

    public void generateTestData(String buildingId) {
        ensureLoaded();
        Building b = buildings.get(buildingId);
        if (b == null) return;
        Random rand = new Random();
        LocalDate now = LocalDate.now();
        EnergyType[] types = EnergyType.values();
        for (int i = 0; i < 500; i++) {
            LocalDateTime dt = now.minusDays(rand.nextInt(730)).atTime(rand.nextInt(24), rand.nextInt(60));
            EnergyType type = types[rand.nextInt(types.length)];
            double qte = 1.0 + rand.nextDouble() * 50;
            double cout = qte * (0.10 + rand.nextDouble() * 0.30);
            b.addConsumptionRecord(new ConsumptionRecord(dt, type, qte, cout, type.getUnite()));
        }
        persist();
    }

    public record TopBuilding(String nom, double consommation, double cout) {}
    public record DominantEnergy(EnergyType type, double total, double percentage) {}
    public record ConsumptionPeak(LocalDate date, double quantite) {}
    public enum Trend { HAUSSE, BAISSE, STABLE }

    public TopBuilding getTopBuilding() {
        ensureLoaded();
        return buildings.values().stream()
                .map(b -> new TopBuilding(b.getNom(),
                        b.getConsommationRecords().stream().filter(r -> r.getType() != EnergyType.EAU)
                                .mapToDouble(ConsumptionRecord::getQuantite).sum(), b.getCoutTotal()))
                .max(Comparator.comparingDouble(TopBuilding::consommation))
                .orElse(new TopBuilding("—", 0, 0));
    }

    public DominantEnergy getDominantEnergy() {
        ensureLoaded();
        Map<EnergyType, Double> totals = new HashMap<>();
        for (Building b : buildings.values())
            for (ConsumptionRecord r : b.getConsommationRecords())
                totals.merge(r.getType(), r.getQuantite(), Double::sum);
        double grandTotal = totals.values().stream().mapToDouble(Double::doubleValue).sum();
        EnergyType top = totals.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(EnergyType.ELECTRICITE);
        double topTotal = totals.getOrDefault(top, 0.0);
        return new DominantEnergy(top, topTotal, grandTotal > 0 ? (topTotal / grandTotal) * 100 : 0);
    }

    public Trend getTrend() {
        ensureLoaded();
        LocalDate now = LocalDate.now();
        double ceMois = 0, moisPrec = 0;
        YearMonth ym = YearMonth.from(now), ymPrev = ym.minusMonths(1);
        for (Building b : buildings.values()) {
            for (ConsumptionRecord r : b.getConsommationRecords()) {
                if (r.getType() == EnergyType.EAU) continue;
                YearMonth rm = YearMonth.from(r.getDateHeure());
                if (rm.equals(ym)) ceMois += r.getQuantite();
                if (rm.equals(ymPrev)) moisPrec += r.getQuantite();
            }
        }
        if (moisPrec == 0) return ceMois > 0 ? Trend.HAUSSE : Trend.STABLE;
        double diff = (ceMois - moisPrec) / moisPrec;
        return diff > 0.05 ? Trend.HAUSSE : diff < -0.05 ? Trend.BAISSE : Trend.STABLE;
    }

    public double getMonthlyEstimate() {
        ensureLoaded();
        LocalDate now = LocalDate.now();
        double total = 0; long months = 0;
        for (int i = 1; i <= 3; i++) {
            YearMonth ym = YearMonth.from(now.minusMonths(i));
            double m = 0;
            for (Building b : buildings.values())
                for (ConsumptionRecord r : b.getConsommationRecords()) {
                    if (r.getType() == EnergyType.EAU) continue;
                    if (YearMonth.from(r.getDateHeure()).equals(ym)) m += r.getQuantite();
                }
            total += m;
            if (m > 0) months++;
        }
        return months > 0 ? total / months : 0;
    }

    public double getMonthlyCostEstimate() {
        ensureLoaded();
        LocalDate now = LocalDate.now();
        double total = 0; long months = 0;
        for (int i = 1; i <= 3; i++) {
            YearMonth ym = YearMonth.from(now.minusMonths(i));
            double m = 0;
            for (Building b : buildings.values())
                for (ConsumptionRecord r : b.getConsommationRecords())
                    if (YearMonth.from(r.getDateHeure()).equals(ym)) m += r.getCoutEstime();
            total += m;
            if (m > 0) months++;
        }
        return months > 0 ? total / months : 0;
    }

    public List<ConsumptionPeak> getConsumptionPeaks() {
        ensureLoaded();
        Map<LocalDate, Double> dailyTotals = new HashMap<>();
        for (Building b : buildings.values())
            for (ConsumptionRecord r : b.getConsommationRecords()) {
                if (r.getType() == EnergyType.EAU) continue;
                dailyTotals.merge(r.getDateHeure().toLocalDate(), r.getQuantite(), Double::sum);
            }
        return dailyTotals.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, Double>comparingByValue().reversed()).limit(5)
                .map(e -> new ConsumptionPeak(e.getKey(), e.getValue())).collect(Collectors.toList());
    }

    public List<Anomaly> getAnomalies() {
        ensureLoaded();
        AnomalyDetector detector = new AnomalyDetector(this);
        List<Anomaly> all = new ArrayList<>();
        for (Building b : buildings.values()) all.addAll(detector.detectAnomalies(b.getId()));
        return all;
    }

    public double getNextMonthPrediction() {
        ensureLoaded();
        LocalDate now = LocalDate.now();
        List<Double> monthlyTotals = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = YearMonth.from(now.minusMonths(i));
            double total = 0;
            for (Building b : buildings.values())
                for (ConsumptionRecord r : b.getConsommationRecords()) {
                    if (r.getType() == EnergyType.EAU) continue;
                    if (YearMonth.from(r.getDateHeure()).equals(ym)) total += r.getQuantite();
                }
            monthlyTotals.add(total);
        }
        int n = monthlyTotals.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) { sumX += i; sumY += monthlyTotals.get(i); sumXY += i * monthlyTotals.get(i); sumX2 += i * i; }
        double b = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        double a = (sumY - b * sumX) / n;
        return Math.max(0, a + b * n);
    }

    public record DailyConsumption(LocalDate date, double quantite) {}
    public record TypeSummary(EnergyType type, double total) {}
    public record MonthlySummary(String month, double total) {}
    public record BuildingComparison(String nom, double consommation) {}

    public List<DailyConsumption> getDailyConsumption(String buildingId, LocalDate start, LocalDate end) {
        ensureLoaded();
        Map<LocalDate, Double> daily = new HashMap<>();
        for (ConsumptionRecord r : getConsumptionRecords(buildingId, start, end)) {
            if (r.getType() == EnergyType.EAU) continue;
            daily.merge(r.getDateHeure().toLocalDate(), r.getQuantite(), Double::sum);
        }
        List<DailyConsumption> result = new ArrayList<>();
        LocalDate d = start;
        while (!d.isAfter(end)) { result.add(new DailyConsumption(d, daily.getOrDefault(d, 0.0))); d = d.plusDays(1); }
        return result;
    }

    public List<TypeSummary> getConsumptionByType(String buildingId) {
        ensureLoaded();
        Map<EnergyType, Double> types = new HashMap<>();
        for (ConsumptionRecord r : getConsumptionRecords(buildingId))
            types.merge(r.getType(), r.getQuantite(), Double::sum);
        return types.entrySet().stream().map(e -> new TypeSummary(e.getKey(), e.getValue())).collect(Collectors.toList());
    }

    public List<MonthlySummary> getMonthlyConsumption(String buildingId, int year) {
        ensureLoaded();
        Map<YearMonth, Double> monthly = new HashMap<>();
        for (ConsumptionRecord r : getConsumptionRecords(buildingId)) {
            if (r.getType() == EnergyType.EAU) continue;
            YearMonth ym = YearMonth.from(r.getDateHeure());
            if (ym.getYear() == year) monthly.merge(ym, r.getQuantite(), Double::sum);
        }
        return monthly.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> new MonthlySummary(e.getKey().getMonth().toString().substring(0, 3) + " " + e.getKey().getYear(), e.getValue()))
                .collect(Collectors.toList());
    }

    public List<BuildingComparison> getBuildingComparison(LocalDate start, LocalDate end) {
        ensureLoaded();
        List<BuildingComparison> result = new ArrayList<>();
        for (Building b : buildings.values()) {
            double total = b.getConsommationRecords().stream()
                    .filter(r -> !r.getDateHeure().toLocalDate().isBefore(start) && !r.getDateHeure().toLocalDate().isAfter(end) && r.getType() != EnergyType.EAU)
                    .mapToDouble(ConsumptionRecord::getQuantite).sum();
            result.add(new BuildingComparison(b.getNom(), total));
        }
        return result;
    }
}
