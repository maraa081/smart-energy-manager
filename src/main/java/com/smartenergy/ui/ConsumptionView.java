package com.smartenergy.ui;

import com.smartenergy.model.Building;
import com.smartenergy.model.ConsumptionRecord;
import com.smartenergy.model.EnergyType;
import com.smartenergy.service.EnergyService;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class ConsumptionView extends VBox {

    private final EnergyService service = EnergyService.getInstance();
    private final TableView<ConsumptionRecord> table;
    private final ObservableList<ConsumptionRecord> recordList;
    private final ComboBox<Building> buildingSelector;
    private final DatePicker filterStartDate;
    private final DatePicker filterEndDate;
    private final ComboBox<EnergyType> filterType;

    public ConsumptionView() {
        setPadding(new Insets(30));
        setSpacing(16);
        setStyle("-fx-background-color: #16213e;");

        // Header
        Label header = new Label("Consommations");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));
        header.setTextFill(Color.web("#e94560"));
        getChildren().add(header);

        // ── Building selector ──
        HBox selectorRow = new HBox(10);
        selectorRow.setAlignment(Pos.CENTER_LEFT);

        Label selLabel = new Label("Bâtiment :");
        selLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));
        selLabel.setTextFill(Color.web("#a0a0b0"));

        buildingSelector = new ComboBox<>();
        buildingSelector.setPrefWidth(250);
        buildingSelector.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white;");
        buildingSelector.setOnAction(e -> loadRecords());

        selectorRow.getChildren().addAll(selLabel, buildingSelector);
        getChildren().add(selectorRow);

        // ── Filters ──
        HBox filterRow = new HBox(10);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        Label dateLabel = new Label("Filtre dates :");
        dateLabel.setFont(Font.font("System", 12));
        dateLabel.setTextFill(Color.web("#a0a0b0"));

        filterStartDate = new DatePicker();
        filterStartDate.setPromptText("Du");
        filterStartDate.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white;");

        filterEndDate = new DatePicker();
        filterEndDate.setPromptText("Au");
        filterEndDate.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white;");

        Label typeLabel = new Label("Type :");
        typeLabel.setFont(Font.font("System", 12));
        typeLabel.setTextFill(Color.web("#a0a0b0"));

        filterType = new ComboBox<>(FXCollections.observableArrayList(EnergyType.values()));
        filterType.setPromptText("Tous");
        filterType.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white;");

        Button btnApplyFilter = createButton("Filtrer", "#00d2ff");
        btnApplyFilter.setOnAction(e -> applyFilter());

        Button btnClearFilter = createButton("Effacer filtres", "#8f8f9f");
        btnClearFilter.setOnAction(e -> clearFilters());

        filterRow.getChildren().addAll(
                dateLabel, filterStartDate, filterEndDate,
                typeLabel, filterType,
                btnApplyFilter, btnClearFilter
        );
        getChildren().add(filterRow);

        // ── Action buttons ──
        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button btnAjouter = createButton("+ Ajouter relevé", "#00d2ff");
        Button btnImporter = createButton(" Importer CSV", "#ffd700");
        Button btnGenerer = createButton(" Générer données test", "#00e676");

        btnAjouter.setOnAction(e -> ajouterReleve());
        btnImporter.setOnAction(e -> importerCsv());
        btnGenerer.setOnAction(e -> genererTest());

        actions.getChildren().addAll(btnAjouter, btnImporter, btnGenerer);
        getChildren().add(actions);

        // ── Table ──
        table = new TableView<>();
        table.setStyle(
                "-fx-background-color: #0f3460;" +
                "-fx-table-cell-border-color: #1a1a2e;"
        );
        table.setPrefHeight(400);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("Sélectionnez un bâtiment pour voir ses relevés"));

        TableColumn<ConsumptionRecord, String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getDateHeure()
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))));
        colDate.setStyle("-fx-text-fill: white;");

        TableColumn<ConsumptionRecord, String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getType() != null
                        ? d.getValue().getType().toString() : "—"));
        colType.setStyle("-fx-text-fill: white;");

        TableColumn<ConsumptionRecord, Double> colQuantite = new TableColumn<>("Quantité");
        colQuantite.setCellValueFactory(d ->
                new SimpleDoubleProperty(d.getValue().getQuantite()).asObject());
        colQuantite.setStyle("-fx-text-fill: white;");

        TableColumn<ConsumptionRecord, String> colUnite = new TableColumn<>("Unité");
        colUnite.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getUnite()));
        colUnite.setStyle("-fx-text-fill: white;");

        TableColumn<ConsumptionRecord, Double> colCout = new TableColumn<>("Coût (€)");
        colCout.setCellValueFactory(d ->
                new SimpleDoubleProperty(d.getValue().getCoutEstime()).asObject());
        colCout.setStyle("-fx-text-fill: white;");

        table.getColumns().addAll(colDate, colType, colQuantite, colUnite, colCout);

        recordList = FXCollections.observableArrayList();
        table.setItems(recordList);
        getChildren().add(table);

        VBox.setVgrow(table, Priority.ALWAYS);

        // Initial load
        rafraichirBatiments();
    }

    /** Call this when switching back to this view to refresh building list */
    public void rafraichir() {
        rafraichirBatiments();
    }

    private void rafraichirBatiments() {
        List<Building> buildings = service.getAllBuildings();
        buildingSelector.setItems(FXCollections.observableArrayList(buildings));
        if (!buildings.isEmpty()) {
            buildingSelector.getSelectionModel().selectFirst();
        }
        loadRecords();
    }

    private void loadRecords() {
        Building selected = buildingSelector.getValue();
        if (selected == null) {
            recordList.clear();
            return;
        }
        recordList.setAll(service.getConsumptionRecords(selected.getId()));
    }

    private void applyFilter() {
        Building selected = buildingSelector.getValue();
        if (selected == null) return;

        List<ConsumptionRecord> all = service.getConsumptionRecords(selected.getId());
        List<ConsumptionRecord> filtered = all.stream()
                .filter(r -> {
                    if (filterStartDate.getValue() != null
                            && r.getDateHeure().toLocalDate().isBefore(filterStartDate.getValue()))
                        return false;
                    if (filterEndDate.getValue() != null
                            && r.getDateHeure().toLocalDate().isAfter(filterEndDate.getValue()))
                        return false;
                    if (filterType.getValue() != null && r.getType() != filterType.getValue())
                        return false;
                    return true;
                })
                .collect(Collectors.toList());
        recordList.setAll(filtered);
    }

    private void clearFilters() {
        filterStartDate.setValue(null);
        filterEndDate.setValue(null);
        filterType.setValue(null);
        loadRecords();
    }

    private void ajouterReleve() {
        Building building = buildingSelector.getValue();
        if (building == null) {
            showWarning("Veuillez d'abord sélectionner un bâtiment.");
            return;
        }

        Dialog<ConsumptionRecord> dialog = new Dialog<>();
        dialog.setTitle("Ajouter un relevé");
        dialog.setHeaderText("Nouveau relevé pour : " + building.getNom());
        dialog.getDialogPane().setStyle("-fx-background-color: #16213e;");

        ButtonType saveBtn = new ButtonType("Ajouter", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));
        grid.setStyle("-fx-background-color: #16213e;");

        DatePicker datePicker = new DatePicker(LocalDate.now());
        datePicker.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white;");

        TextField heureField = new TextField(LocalTime.now().format(
                DateTimeFormatter.ofPattern("HH:mm")));
        heureField.setPromptText("HH:mm");
        heureField.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white; -fx-prompt-text-fill: #666;");

        ComboBox<EnergyType> cbType = new ComboBox<>(
                FXCollections.observableArrayList(EnergyType.values()));
        cbType.setValue(EnergyType.ELECTRICITE);
        cbType.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white;");

        TextField qteField = new TextField();
        qteField.setPromptText("Quantité");
        qteField.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white; -fx-prompt-text-fill: #666;");

        TextField coutField = new TextField();
        coutField.setPromptText("Coût (€)");
        coutField.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white; -fx-prompt-text-fill: #666;");

        grid.add(new Label("Date :") {{ setTextFill(Color.web("#a0a0b0")); }}, 0, 0);
        grid.add(datePicker, 1, 0);
        grid.add(new Label("Heure :") {{ setTextFill(Color.web("#a0a0b0")); }}, 0, 1);
        grid.add(heureField, 1, 1);
        grid.add(new Label("Type énergie :") {{ setTextFill(Color.web("#a0a0b0")); }}, 0, 2);
        grid.add(cbType, 1, 2);
        grid.add(new Label("Quantité :") {{ setTextFill(Color.web("#a0a0b0")); }}, 0, 3);
        grid.add(qteField, 1, 3);
        grid.add(new Label("Coût :") {{ setTextFill(Color.web("#a0a0b0")); }}, 0, 4);
        grid.add(coutField, 1, 4);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                LocalDate date = datePicker.getValue();
                if (date == null) return null;
                LocalTime time;
                try {
                    time = LocalTime.parse(heureField.getText().trim(),
                            DateTimeFormatter.ofPattern("HH:mm"));
                } catch (Exception e) {
                    time = LocalTime.MIDNIGHT;
                }
                double qte;
                try {
                    qte = Double.parseDouble(qteField.getText().trim());
                } catch (NumberFormatException e) {
                    showWarning("Quantité invalide.");
                    return null;
                }
                double cout;
                try {
                    cout = coutField.getText().trim().isEmpty()
                            ? 0 : Double.parseDouble(coutField.getText().trim());
                } catch (NumberFormatException e) {
                    cout = 0;
                }
                return new ConsumptionRecord(
                        LocalDateTime.of(date, time),
                        cbType.getValue(), qte, cout, cbType.getValue().getUnite());
            }
            return null;
        });

        dialog.showAndWait().ifPresent(record -> {
            service.addConsumptionRecord(building.getId(), record);
            loadRecords();
        });
    }

    private void importerCsv() {
        Building building = buildingSelector.getValue();
        if (building == null) {
            showWarning("Veuillez d'abord sélectionner un bâtiment.");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Importer un fichier CSV");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Fichiers CSV", "*.csv"));
        File file = fc.showOpenDialog(getScene().getWindow());
        if (file != null) {
            try {
                service.importCsv(building.getId(), file.getAbsolutePath());
                loadRecords();
                showInfo("Import terminé", "Les données ont été importées avec succès.");
            } catch (Exception e) {
                showWarning("Erreur lors de l'import : " + e.getMessage());
            }
        }
    }

    private void genererTest() {
        Building building = buildingSelector.getValue();
        if (building == null) {
            showWarning("Veuillez d'abord sélectionner un bâtiment.");
            return;
        }
        service.generateTestData(building.getId());
        loadRecords();
        showInfo("Données générées", "Données de test générées avec succès.");
    }

    private Button createButton(String text, String color) {
        Button btn = new Button(text);
        btn.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));
        btn.setStyle(
                "-fx-background-color: " + color + "; " +
                "-fx-text-fill: " + (color.equals("#8f8f9f") ? "white" : "#1a1a2e") + ";" +
                "-fx-background-radius: 6;" +
                "-fx-border-radius: 6;" +
                "-fx-padding: 8 16;" +
                "-fx-cursor: hand;"
        );
        return btn;
    }

    private void showWarning(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Attention");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showInfo(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
