package com.smartenergy.ui;

import com.smartenergy.model.Anomaly;
import com.smartenergy.service.EnergyService;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class DashboardView extends ScrollPane {

    private final EnergyService service = EnergyService.getInstance();
    private EnergyService.DashboardSummary lastSummary;

    public DashboardView() {
        setFitToWidth(true);
        setFitToHeight(true);
        setStyle("-fx-background: #16213e; -fx-background-color: #16213e;");
        setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        VBox root = new VBox();
        root.setPadding(new Insets(30));
        root.setSpacing(24);
        root.setStyle("-fx-background-color: #16213e;");

        // Header
        Label header = new Label("Tableau de bord");
        header.getStyleClass().add("title");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));
        header.setTextFill(Color.web("#e94560"));
        root.getChildren().add(header);

        refresh(root);
        setContent(root);
    }

    private void refresh(VBox root) {
        // Remove children after header
        if (root.getChildren().size() > 1) {
            root.getChildren().remove(1, root.getChildren().size());
        }

        lastSummary = service.getDashboardSummary();

        // ── Metric Cards ──
        HBox metricsRow = new HBox(16);
        metricsRow.setAlignment(Pos.CENTER);

        metricsRow.getChildren().addAll(
                createMetricCard("Aujourd'hui", formatConso(lastSummary.consoJour()), "kWh", "#0f3460", "#ffd700"),
                createMetricCard("Ce mois", formatConso(lastSummary.consoMois()), "kWh", "#0f3460", "#00d2ff"),
                createMetricCard("Cette année", formatConso(lastSummary.consoAnnee()), "kWh", "#0f3460", "#00e676"),
                createMetricCard("Coût total", String.format("%.2f", lastSummary.coutTotal()), "€", "#0f3460", "#e94560")
        );

        // Ensure cards take equal width
        for (javafx.scene.Node c : metricsRow.getChildren()) {
            HBox.setHgrow(c, Priority.ALWAYS);
        }

        root.getChildren().add(metricsRow);

        // ── Top Building ──
        HBox topBuildingRow = new HBox(16);

        VBox topBuildingCard = new VBox(8);
        topBuildingCard.setPadding(new Insets(20));
        topBuildingCard.setStyle(
                "-fx-background-color: #0f3460;" +
                "-fx-background-radius: 12;" +
                "-fx-border-radius: 12;"
        );
        topBuildingCard.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(topBuildingCard, Priority.ALWAYS);

        Label buildingLabel = new Label("Bâtiment le plus consommateur");
        buildingLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));
        buildingLabel.setTextFill(Color.web("#a0a0b0"));

        Label buildingName = new Label(lastSummary.topBuildingNom());
        buildingName.setFont(Font.font("System", FontWeight.BOLD, 22));
        buildingName.setTextFill(Color.web("#ffd700"));

        Label buildingConso = new Label(formatConso(lastSummary.topBuildingConso()) + " kWh");
        buildingConso.setFont(Font.font("System", FontWeight.NORMAL, 16));
        buildingConso.setTextFill(Color.web("#8f8f9f"));

        topBuildingCard.getChildren().addAll(buildingLabel, buildingName, buildingConso);

        // ── Trend mini-chart (placeholder) ──
        VBox trendCard = new VBox(8);
        trendCard.setPadding(new Insets(20));
        trendCard.setStyle(
                "-fx-background-color: #0f3460;" +
                "-fx-background-radius: 12;" +
                "-fx-border-radius: 12;"
        );
        trendCard.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(trendCard, Priority.ALWAYS);

        Label trendLabel = new Label("Tendance");
        trendLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));
        trendLabel.setTextFill(Color.web("#a0a0b0"));

        EnergyService.Trend trend = service.getTrend();
        String trendText;
        Color trendColor;
        switch (trend) {
            case HAUSSE ->  { trendText = "\u2191 En hausse"; trendColor = Color.web("#e94560"); }
            case BAISSE ->  { trendText = "\u2193 En baisse"; trendColor = Color.web("#00e676"); }
            default ->      { trendText = "\u2192 Stable";    trendColor = Color.web("#ffd700"); }
        }

        Label trendValue = new Label(trendText);
        trendValue.setFont(Font.font("System", FontWeight.BOLD, 22));
        trendValue.setTextFill(trendColor);

        Label estimateLabel = new Label("Estimation mensuelle : "
                + String.format("%.1f", service.getMonthlyEstimate()) + " kWh");
        estimateLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));
        estimateLabel.setTextFill(Color.web("#8f8f9f"));

        trendCard.getChildren().addAll(trendLabel, trendValue, estimateLabel);

        topBuildingRow.getChildren().addAll(topBuildingCard, trendCard);
        root.getChildren().add(topBuildingRow);

        // ── Recent Alerts ──
        VBox alertsSection = new VBox(10);
        alertsSection.setPadding(new Insets(0));

        Label alertsHeader = new Label("Alertes récentes");
        alertsHeader.setFont(Font.font("System", FontWeight.SEMI_BOLD, 16));
        alertsHeader.setTextFill(Color.web("#e94560"));

        alertsSection.getChildren().add(alertsHeader);

        List<Anomaly> alerts = lastSummary.recentAlerts();
        if (alerts.isEmpty()) {
            alerts = service.getAnomalies();
        }

        if (alerts.isEmpty()) {
            Label noAlerts = new Label("✅ Aucune anomalie détectée");
            noAlerts.setFont(Font.font("System", 14));
            noAlerts.setTextFill(Color.web("#00e676"));
            noAlerts.setPadding(new Insets(10, 0, 0, 0));
            alertsSection.getChildren().add(noAlerts);
        } else {
            // Show last 10
            alerts.stream().limit(10).forEach(a -> {
                HBox alertItem = new HBox(12);
                alertItem.setPadding(new Insets(12, 16, 12, 16));
                alertItem.setAlignment(Pos.CENTER_LEFT);
                alertItem.setStyle(
                        "-fx-background-color: #1a1a2e;" +
                        "-fx-background-radius: 8;" +
                        "-fx-border-radius: 8;"
                );

                Label iconLabel = new Label("⚠");
                iconLabel.setFont(Font.font("System", 18));

                VBox textBox = new VBox(2);
                Label descLabel = new Label(a.description());
                descLabel.setFont(Font.font("System", 14));
                descLabel.setTextFill(Color.web("#ffd700"));

                Label dateLabel = new Label(a.date().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
                dateLabel.setFont(Font.font("System", 11));
                dateLabel.setTextFill(Color.web("#666"));

                textBox.getChildren().addAll(descLabel, dateLabel);
                alertItem.getChildren().addAll(iconLabel, textBox);
                alertsSection.getChildren().add(alertItem);
            });
        }

        root.getChildren().add(alertsSection);
    }

    private VBox createMetricCard(String label, String value, String unit, String bgColor, String accentColor) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(20));
        card.setAlignment(Pos.CENTER);
        card.setStyle(
                "-fx-background-color: " + bgColor + ";" +
                "-fx-background-radius: 12;" +
                "-fx-border-radius: 12;" +
                "-fx-border-color: " + accentColor + ";" +
                "-fx-border-width: 0 0 3 0;"
        );

        Label lbl = new Label(label);
        lbl.getStyleClass().add("metric-label");
        lbl.setFont(Font.font("System", FontWeight.NORMAL, 13));
        lbl.setTextFill(Color.web("#a0a0b0"));
        lbl.setTextAlignment(TextAlignment.CENTER);

        Label val = new Label(value + " " + unit);
        val.getStyleClass().add("metric-value");
        val.setFont(Font.font("System", FontWeight.BOLD, 20));
        val.setTextFill(Color.web(accentColor));
        val.setTextAlignment(TextAlignment.CENTER);

        card.getChildren().addAll(lbl, val);
        return card;
    }

    private String formatConso(double val) {
        if (val >= 1000) return String.format("%.1f", val);
        if (val >= 1) return String.format("%.1f", val);
        return String.format("%.2f", val);
    }
}
