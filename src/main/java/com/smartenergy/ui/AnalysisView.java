package com.smartenergy.ui;

import com.smartenergy.model.Anomaly;
import com.smartenergy.model.EnergyType;
import com.smartenergy.service.EnergyService;
import com.smartenergy.service.PythonPredictor;
import com.smartenergy.service.PythonPredictor.ForestResult;
import com.smartenergy.service.WeatherService;

import javafx.application.Platform;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class AnalysisView extends ScrollPane {

    private final EnergyService service = EnergyService.getInstance();

    public AnalysisView() {
        setFitToWidth(true);
        setFitToHeight(true);
        setStyle("-fx-background: #16213e; -fx-background-color: #16213e;");
        setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        VBox root = new VBox();
        root.setPadding(new Insets(30));
        root.setSpacing(20);
        root.setStyle("-fx-background-color: #16213e;");

        // Header
        Label header = new Label("Analyses");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));
        header.setTextFill(Color.web("#e94560"));
        root.getChildren().add(header);

        // ── Top row: 2 cards ──
        HBox topRow = new HBox(16);

        // Top Building card
        VBox topBuildingCard = createSection("Bâtiment le plus consommateur");
        EnergyService.TopBuilding topBuilding = service.getTopBuilding();

        Label tbName = new Label(topBuilding.nom());
        tbName.setFont(Font.font("System", FontWeight.BOLD, 20));
        tbName.setTextFill(Color.web("#ffd700"));

        Label tbConso = new Label(formatConso(topBuilding.consommation()) + " (toutes énergies)");
        tbConso.setFont(Font.font("System", FontWeight.NORMAL, 16));
        tbConso.setTextFill(Color.web("#a0a0b0"));

        Label tbCout = new Label(String.format("%.2f €", topBuilding.cout()));
        tbCout.setFont(Font.font("System", FontWeight.NORMAL, 14));
        tbCout.setTextFill(Color.web("#8f8f9f"));

        topBuildingCard.getChildren().addAll(tbName, tbConso, tbCout);

        // Dominant Energy card
        VBox dominantCard = createSection("Type d'énergie dominant");
        EnergyService.DominantEnergy dominant = service.getDominantEnergy();

        Label deName = new Label(dominant.type().toString());
        deName.setFont(Font.font("System", FontWeight.BOLD, 20));
        deName.setTextFill(Color.web("#00d2ff"));

        Label deTotal = new Label(formatConso(dominant.total()) + " " + dominant.type().getUnite());
        deTotal.setFont(Font.font("System", FontWeight.NORMAL, 16));
        deTotal.setTextFill(Color.web("#a0a0b0"));

        Label dePercent = new Label(String.format("%.1f %% de la consommation totale",
                dominant.percentage()));
        dePercent.setFont(Font.font("System", FontWeight.NORMAL, 14));
        dePercent.setTextFill(Color.web("#8f8f9f"));

        dominantCard.getChildren().addAll(deName, deTotal, dePercent);

        topRow.getChildren().addAll(topBuildingCard, dominantCard);
        for (var c : topRow.getChildren()) {
            HBox.setHgrow(c, Priority.ALWAYS);
        }
        root.getChildren().add(topRow);

        // ── Second row: 2 cards (Trend + Monthly Estimate) ──
        HBox secondRow = new HBox(16);

        // Trend card
        VBox trendCard = createSection("Tendance");
        EnergyService.Trend trend = service.getTrend();

        String trendText;
        Color trendColor;
        switch (trend) {
            case HAUSSE ->  { trendText = "\u2191 En hausse";    trendColor = Color.web("#e94560"); }
            case BAISSE ->  { trendText = "\u2193 En baisse";    trendColor = Color.web("#00e676"); }
            default ->      { trendText = "\u2192 Stable";       trendColor = Color.web("#ffd700"); }
        }

        Label trendValue = new Label(trendText);
        trendValue.setFont(Font.font("System", FontWeight.BOLD, 22));
        trendValue.setTextFill(trendColor);

        trendCard.getChildren().add(trendValue);

        // Monthly estimate card
        VBox estimateCard = createSection("Estimation mensuelle");
        double estimate = service.getMonthlyEstimate();
        double costEstimate = service.getMonthlyCostEstimate();

        Label estValue = new Label(formatConso(estimate) + " kWh (énergie)");
        estValue.setFont(Font.font("System", FontWeight.BOLD, 22));
        estValue.setTextFill(Color.web("#00d2ff"));

        Label estCost = new Label("~ " + String.format("%.2f €/mois", costEstimate));
        estCost.setFont(Font.font("System", FontWeight.NORMAL, 14));
        estCost.setTextFill(Color.web("#8f8f9f"));

        estimateCard.getChildren().addAll(estValue, estCost);

        secondRow.getChildren().addAll(trendCard, estimateCard);
        for (var c : secondRow.getChildren()) {
            HBox.setHgrow(c, Priority.ALWAYS);
        }
        root.getChildren().add(secondRow);

        // ── Third row: PieChart for energy distribution ──
        VBox pieSection = createSection("Répartition par type d'énergie");
        PieChart pieChart = new PieChart();
        pieChart.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white;");
        pieChart.setAnimated(false);
        pieChart.setPrefHeight(300);

        // Aggregate data across all buildings
        var types = new java.util.HashMap<EnergyType, Double>();
        for (var b : service.getAllBuildings()) {
            for (var r : service.getConsumptionRecords(b.getId())) {
                types.merge(r.getType(), r.getQuantite(), Double::sum);
            }
        }
        for (var e : types.entrySet()) {
            pieChart.getData().add(new PieChart.Data(
                    e.getKey().toString() + " (" + String.format("%.1f", e.getValue()) + " kWh)",
                    e.getValue()));
        }

        pieSection.getChildren().add(pieChart);
        root.getChildren().add(pieSection);

        // ── Consumption Peaks ──
        VBox peaksSection = createSection("Pics de consommation");
        List<EnergyService.ConsumptionPeak> peaks = service.getConsumptionPeaks();
        if (peaks.isEmpty()) {
            Label noPeaks = new Label("Aucun pic détecté");
            noPeaks.setTextFill(Color.web("#8f8f9f"));
            peaksSection.getChildren().add(noPeaks);
        } else {
            for (EnergyService.ConsumptionPeak peak : peaks) {
                HBox peakItem = new HBox(12);
                peakItem.setPadding(new Insets(8, 0, 8, 12));
                peakItem.setAlignment(Pos.CENTER_LEFT);
                peakItem.setStyle(
                        "-fx-background-color: #1a1a2e;" +
                        "-fx-background-radius: 6;" +
                        "-fx-border-radius: 6;"
                );

                Label icon = new Label("\u26A0");
                icon.setFont(Font.font("System", 16));

                Label peakLabel = new Label(
                        peak.date().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        + "  →  " + formatConso(peak.quantite()) + " (toutes énergies)");
                peakLabel.setFont(Font.font("System", 14));
                peakLabel.setTextFill(Color.web("#e94560"));

                peakItem.getChildren().addAll(icon, peakLabel);
                peaksSection.getChildren().add(peakItem);
            }
        }
        root.getChildren().add(peaksSection);

        // ── Anomalies ──
        VBox anomaliesSection = createSection("Anomalies détectées");
        List<Anomaly> anomalies = service.getAnomalies();

        if (anomalies.isEmpty()) {
            Label noAnomalies = new Label("✅ Aucune anomalie détectée");
            noAnomalies.setFont(Font.font("System", 14));
            noAnomalies.setTextFill(Color.web("#00e676"));
            anomaliesSection.getChildren().add(noAnomalies);
        } else {
            TableView<Anomaly> anomaliesTable = new TableView<>();
            anomaliesTable.setStyle(
                    "-fx-background-color: #0f3460;" +
                    "-fx-table-cell-border-color: #1a1a2e;"
            );
            anomaliesTable.setPrefHeight(200);
            anomaliesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

            TableColumn<Anomaly, String> colDate = new TableColumn<>("Date");
            colDate.setCellValueFactory(d ->
                    new SimpleStringProperty(d.getValue().date()
                            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))));
            colDate.setStyle("-fx-text-fill: white;");

            TableColumn<Anomaly, String> colType = new TableColumn<>("Type");
            colType.setCellValueFactory(d ->
                    new SimpleStringProperty(d.getValue().type().toString()));
            colType.setStyle("-fx-text-fill: white;");

            TableColumn<Anomaly, String> colDesc = new TableColumn<>("Description");
            colDesc.setCellValueFactory(d ->
                    new SimpleStringProperty(d.getValue().description()));
            colDesc.setStyle("-fx-text-fill: white;");

            TableColumn<Anomaly, String> colEcart = new TableColumn<>("Écart");
            colEcart.setCellValueFactory(d ->
                    new SimpleStringProperty(String.format("%.1f", d.getValue().ecart())));
            colEcart.setStyle("-fx-text-fill: white;");

            anomaliesTable.getColumns().addAll(colDate, colType, colDesc, colEcart);
            anomaliesTable.setItems(FXCollections.observableArrayList(anomalies));

            anomaliesSection.getChildren().add(anomaliesTable);
        }
        root.getChildren().add(anomaliesSection);

        // ── Comparaison des prédictions ──
        VBox comparisonSection = createSection("Comparaison des modèles de prédiction");

        HBox modelsRow = new HBox(16);

        // --- Modèle 1 : Régression linéaire Java ---
        VBox javaModel = new VBox(10);
        javaModel.setPadding(new Insets(16));
        javaModel.setStyle("-fx-background-color: #1a1a2e; -fx-background-radius: 8;");
        javaModel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(javaModel, Priority.ALWAYS);

        Label javaTitle = new Label("☕ Régression linéaire (Java)");
        javaTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        javaTitle.setTextFill(Color.web("#00d2ff"));

        double javaPred = service.getNextMonthPrediction();
        Label javaValue = new Label(formatConso(javaPred) + " kWh");
        javaValue.setFont(Font.font("System", FontWeight.BOLD, 28));
        javaValue.setTextFill(Color.web("#ffd700"));

        Label javaMeth = new Label("Moyenne 6 mois + régression linéaire");
        javaMeth.setFont(Font.font("System", 11));
        javaMeth.setTextFill(Color.web("#666"));

        javaModel.getChildren().addAll(javaTitle, javaValue, javaMeth);

        // --- Modèle 2 : RandomForest Python ---
        VBox forestModel = new VBox(10);
        forestModel.setPadding(new Insets(16));
        forestModel.setStyle("-fx-background-color: #1a1a2e; -fx-background-radius: 8;");
        forestModel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(forestModel, Priority.ALWAYS);

        Label forestTitle = new Label("🌲 RandomForest (Python / scikit-learn)");
        forestTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        forestTitle.setTextFill(Color.web("#00e676"));

        Label forestValue = new Label("Chargement...");
        forestValue.setFont(Font.font("System", FontWeight.BOLD, 28));
        forestValue.setTextFill(Color.web("#a0a0b0"));

        Label forestInterval = new Label("");
        forestInterval.setFont(Font.font("System", 12));
        forestInterval.setTextFill(Color.web("#8f8f9f"));

        Label forestMeth = new Label("200 arbres, 7 features, R² ≈ 0.87");
        forestMeth.setFont(Font.font("System", 11));
        forestMeth.setTextFill(Color.web("#555"));

        forestModel.getChildren().addAll(forestTitle, forestValue, forestInterval, forestMeth);

        modelsRow.getChildren().addAll(javaModel, forestModel);
        comparisonSection.getChildren().add(modelsRow);

        // Appel asynchrone au modèle Python
        new Thread(() -> {
            try {
                int mois = java.time.LocalDate.now().getMonthValue();
                int heure = 14;
                EnergyType dominantType = service.getDominantEnergy().type();
                double temperature = 20.0;

                // Essayer de récupérer une température via la météo
                var buildings = service.getAllBuildings();
                if (!buildings.isEmpty()) {
                    var b = buildings.get(0);
                    if (b.getLatitude() != 0 || b.getLongitude() != 0) {
                        WeatherService ws = new WeatherService();
                        var tempOpt = ws.getTemperature(b.getLatitude(), b.getLongitude(),
                                java.time.LocalDate.now());
                        if (tempOpt.isPresent()) temperature = tempOpt.get();
                    }
                }

                Optional<ForestResult> result = PythonPredictor.predict(
                        mois, heure, dominantType, temperature);

                Platform.runLater(() -> {
                    if (result.isPresent()) {
                        ForestResult r = result.get();
                        forestValue.setText(formatConso(r.prediction()) + " kWh");
                        forestValue.setTextFill(Color.web("#00e676"));
                        forestInterval.setText("Intervalle 95% : ["
                                + String.format("%.1f", r.intervalMin()) + ", "
                                + String.format("%.1f", r.intervalMax()) + "] kWh");
                    } else {
                        forestValue.setText("❌ Non disponible");
                        forestValue.setTextFill(Color.web("#e94560"));
                        forestInterval.setText("Installez Python + scikit-learn \n(" +
                                "cd ml-prediction && pip install -r requirements.txt && " +
                                "python predict.py --train)");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    forestValue.setText("❌ Erreur");
                    forestValue.setTextFill(Color.web("#e94560"));
                    forestInterval.setText(e.getMessage());
                });
            }
        }).start();

        root.getChildren().add(comparisonSection);

        setContent(root);
    }

    // ── Helpers ──

    private VBox createSection(String title) {
        VBox section = new VBox(10);
        section.setPadding(new Insets(16));
        section.setStyle(
                "-fx-background-color: #0f3460;" +
                "-fx-background-radius: 10;" +
                "-fx-border-radius: 10;"
        );
        section.setMaxWidth(Double.MAX_VALUE);

        Label lbl = new Label(title);
        lbl.setFont(Font.font("System", FontWeight.SEMI_BOLD, 15));
        lbl.setTextFill(Color.web("#a0a0b0"));

        section.getChildren().add(lbl);
        return section;
    }

    private String formatConso(double val) {
        if (val >= 1000) return String.format("%.1f", val / 1000) + "k";
        if (val >= 1) return String.format("%.1f", val);
        return String.format("%.2f", val);
    }
}
