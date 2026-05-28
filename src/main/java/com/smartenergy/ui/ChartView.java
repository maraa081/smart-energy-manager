package com.smartenergy.ui;

import com.smartenergy.model.Building;
import com.smartenergy.model.EnergyType;
import com.smartenergy.service.EnergyService;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ChartView extends VBox {

    private final EnergyService service = EnergyService.getInstance();
    private final ComboBox<Building> buildingSelector;
    private final DatePicker startDate;
    private final DatePicker endDate;
    private final TabPane chartTabs;

    public ChartView() {
        setPadding(new Insets(30));
        setSpacing(16);
        setStyle("-fx-background-color: #16213e;");

        // Header
        Label header = new Label("Graphiques");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));
        header.setTextFill(Color.web("#e94560"));
        getChildren().add(header);

        // Controls row
        HBox controls = new HBox(12);
        controls.setAlignment(Pos.CENTER_LEFT);

        Label selLabel = new Label("Bâtiment(s) :");
        selLabel.setTextFill(Color.web("#a0a0b0"));

        buildingSelector = new ComboBox<>();
        buildingSelector.setPrefWidth(220);
        buildingSelector.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white;");
        buildingSelector.setOnAction(e -> refreshAllCharts());

        Label startLabel = new Label("Du :");
        startLabel.setTextFill(Color.web("#a0a0b0"));
        startDate = new DatePicker(LocalDate.now().minusMonths(6));
        startDate.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white;");

        Label endLabel = new Label("Au :");
        endLabel.setTextFill(Color.web("#a0a0b0"));
        endDate = new DatePicker(LocalDate.now());
        endDate.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white;");

        Button btnRefresh = createButton("Actualiser", "#00d2ff");
        btnRefresh.setOnAction(e -> refreshAllCharts());

        controls.getChildren().addAll(
                selLabel, buildingSelector,
                startLabel, startDate,
                endLabel, endDate,
                btnRefresh);

        getChildren().add(controls);

        // Tabs
        chartTabs = new TabPane();
        chartTabs.setStyle("-fx-background-color: #0f3460; -fx-text-base-color: white;");
        chartTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab tabLine = new Tab("Courbe temporelle");
        tabLine.setContent(createLineChart());

        Tab tabBar = new Tab("Histogramme");
        tabBar.setContent(createBarChart());

        Tab tabPie = new Tab("Répartition");
        tabPie.setContent(createPieChart());

        Tab tabCompare = new Tab("Comparaison");
        tabCompare.setContent(createComparisonChart());

        chartTabs.getTabs().addAll(tabLine, tabBar, tabPie, tabCompare);
        getChildren().add(chartTabs);

        VBox.setVgrow(chartTabs, Priority.ALWAYS);

        // Load buildings
        rafraichirBatiments();
    }

    /** Refresh building list when switching back to this view */
    public void rafraichir() {
        rafraichirBatiments();
    }

    private void rafraichirBatiments() {
        List<Building> buildings = service.getAllBuildings();
        buildingSelector.setItems(FXCollections.observableArrayList(buildings));
        if (!buildings.isEmpty()) {
            buildingSelector.getSelectionModel().selectFirst();
        }
        refreshAllCharts();
    }

    private void refreshAllCharts() {
        chartTabs.getTabs().clear();
        Tab tabLine = new Tab("Courbe temporelle");
        tabLine.setContent(createLineChart());
        Tab tabBar = new Tab("Histogramme");
        tabBar.setContent(createBarChart());
        Tab tabPie = new Tab("Répartition");
        tabPie.setContent(createPieChart());
        Tab tabCompare = new Tab("Comparaison");
        tabCompare.setContent(createComparisonChart());
        chartTabs.getTabs().addAll(tabLine, tabBar, tabPie, tabCompare);
    }

    // ── Line Chart: consommation over time ──

    private VBox createLineChart() {
        VBox container = new VBox(10);
        container.setPadding(new Insets(20));
        container.setStyle("-fx-background-color: #0f3460;");

        Label lbl = new Label("Consommation dans le temps");
        lbl.setFont(Font.font("System", FontWeight.BOLD, 16));
        lbl.setTextFill(Color.web("#00d2ff"));

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Date");
        xAxis.setStyle("-fx-tick-label-fill: #a0a0b0;");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Consommation (kWh)");
        yAxis.setStyle("-fx-tick-label-fill: #a0a0b0;");

        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Consommation quotidienne (kWh)");
        chart.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white;");
        chart.setAnimated(false);
        chart.setLegendVisible(true);

        Building selected = buildingSelector.getValue();
        if (selected != null) {
            LocalDate start = startDate.getValue();
            LocalDate end = endDate.getValue();
            if (start == null) start = LocalDate.now().minusMonths(3);
            if (end == null) end = LocalDate.now();

            List<EnergyService.DailyConsumption> data = service.getDailyConsumption(
                    selected.getId(), start, end);

            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(selected.getNom());
            for (EnergyService.DailyConsumption dc : data) {
                series.getData().add(new XYChart.Data<>(
                        dc.date().format(DateTimeFormatter.ofPattern("dd/MM")),
                        dc.quantite()));
            }
            chart.getData().add(series);
        }

        container.getChildren().addAll(lbl, chart);
        VBox.setVgrow(chart, Priority.ALWAYS);
        return container;
    }

    // ── Bar Chart: consommation par mois ──

    private VBox createBarChart() {
        VBox container = new VBox(10);
        container.setPadding(new Insets(20));
        container.setStyle("-fx-background-color: #0f3460;");

        Label lbl = new Label("Consommation par mois");
        lbl.setFont(Font.font("System", FontWeight.BOLD, 16));
        lbl.setTextFill(Color.web("#ffd700"));

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Mois");
        xAxis.setStyle("-fx-tick-label-fill: #a0a0b0;");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Consommation (kWh)");
        yAxis.setStyle("-fx-tick-label-fill: #a0a0b0;");

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Consommation mensuelle (kWh)");
        chart.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white;");
        chart.setAnimated(false);

        Building selected = buildingSelector.getValue();
        if (selected != null) {
            int year = LocalDate.now().getYear();
            List<EnergyService.MonthlySummary> data = service.getMonthlyConsumption(
                    selected.getId(), year);

            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(selected.getNom());
            for (EnergyService.MonthlySummary ms : data) {
                series.getData().add(new XYChart.Data<>(ms.month(), ms.total()));
            }
            chart.getData().add(series);
        }

        container.getChildren().addAll(lbl, chart);
        VBox.setVgrow(chart, Priority.ALWAYS);
        return container;
    }

    // ── Pie Chart: répartition par type d'énergie ──

    private VBox createPieChart() {
        VBox container = new VBox(10);
        container.setPadding(new Insets(20));
        container.setStyle("-fx-background-color: #0f3460;");

        Label lbl = new Label("Répartition par type d'énergie");
        lbl.setFont(Font.font("System", FontWeight.BOLD, 16));
        lbl.setTextFill(Color.web("#00e676"));

        PieChart chart = new PieChart();
        chart.setTitle("Répartition énergétique");
        chart.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white;");
        chart.setAnimated(false);
        chart.setLabelsVisible(true);

        Building selected = buildingSelector.getValue();
        if (selected != null) {
            List<EnergyService.TypeSummary> data = service.getConsumptionByType(
                    selected.getId());
            for (EnergyService.TypeSummary ts : data) {
                chart.getData().add(new PieChart.Data(
                        ts.type().toString() + " (" + String.format("%.1f", ts.total()) + " kWh)",
                        ts.total()));
            }
        }

        container.getChildren().addAll(lbl, chart);
        VBox.setVgrow(chart, Priority.ALWAYS);
        return container;
    }

    // ── Comparison BarChart: multi-bâtiments ──

    private VBox createComparisonChart() {
        VBox container = new VBox(10);
        container.setPadding(new Insets(20));
        container.setStyle("-fx-background-color: #0f3460;");

        Label lbl = new Label("Comparaison entre bâtiments");
        lbl.setFont(Font.font("System", FontWeight.BOLD, 16));
        lbl.setTextFill(Color.web("#e94560"));

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Bâtiment");
        xAxis.setStyle("-fx-tick-label-fill: #a0a0b0;");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Consommation (kWh)");
        yAxis.setStyle("-fx-tick-label-fill: #a0a0b0;");

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Comparaison entre bâtiments (kWh)");
        chart.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white;");
        chart.setAnimated(false);

        LocalDate start = startDate.getValue();
        LocalDate end = endDate.getValue();
        if (start == null) start = LocalDate.now().minusMonths(6);
        if (end == null) end = LocalDate.now();

        List<EnergyService.BuildingComparison> data = service.getBuildingComparison(start, end);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Consommation totale");
        for (EnergyService.BuildingComparison bc : data) {
            series.getData().add(new XYChart.Data<>(bc.nom(), bc.consommation()));
        }
        chart.getData().add(series);

        container.getChildren().addAll(lbl, chart);
        VBox.setVgrow(chart, Priority.ALWAYS);
        return container;
    }

    // ── Helpers ──

    private Button createButton(String text, String color) {
        Button btn = new Button(text);
        btn.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));
        btn.setStyle(
                "-fx-background-color: " + color + "; " +
                "-fx-text-fill: #1a1a2e;" +
                "-fx-background-radius: 6;" +
                "-fx-border-radius: 6;" +
                "-fx-padding: 8 16;" +
                "-fx-cursor: hand;"
        );
        return btn;
    }
}
