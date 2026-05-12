package com.smartenergy.ui;

import com.smartenergy.model.Building;
import com.smartenergy.model.BuildingType;
import com.smartenergy.service.EnergyService;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Optional;

public class BuildingView extends VBox {

    private final EnergyService service = EnergyService.getInstance();
    private final TableView<Building> table;
    private final ObservableList<Building> buildingList;

    public BuildingView() {
        setPadding(new Insets(30));
        setSpacing(20);
        setStyle("-fx-background-color: #16213e;");

        // Header
        Label header = new Label("Gestion des bâtiments");
        header.setFont(Font.font("System", FontWeight.BOLD, 24));
        header.setTextFill(Color.web("#e94560"));
        getChildren().add(header);

        // Toolbar
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Button btnAjouter = createButton("+ Ajouter", "#00d2ff");
        Button btnModifier = createButton("\u270E Modifier", "#ffd700");
        Button btnSupprimer = createButton("\u2716 Supprimer", "#e94560");
        Button btnCloner = createButton("\uD83D\uDD04 Cloner", "#00e676");
        Button btnRafraichir = createButton("\u21BB Actualiser", "#8f8f9f");

        btnAjouter.setOnAction(e -> ajouterBatiment());
        btnModifier.setOnAction(e -> modifierBatiment());
        btnSupprimer.setOnAction(e -> supprimerBatiment());
        btnCloner.setOnAction(e -> clonerBatiment());
        btnRafraichir.setOnAction(e -> rafraichir());

        toolbar.getChildren().addAll(btnAjouter, btnModifier, btnSupprimer, btnCloner, btnRafraichir);
        getChildren().add(toolbar);

        // Table
        table = new TableView<>();
        table.setStyle(
                "-fx-background-color: #0f3460;" +
                "-fx-table-cell-border-color: #1a1a2e;" +
                "-fx-text-fill: white;"
        );
        table.setPrefHeight(400);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("Aucun bâtiment — cliquez sur « Ajouter »"));
        table.setRowFactory(tv -> {
            TableRow<Building> row = new TableRow<>();
            row.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white;");
            return row;
        });

        // Columns
        TableColumn<Building, String> colNom = new TableColumn<>("Nom");
        colNom.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNom()));
        colNom.setStyle("-fx-text-fill: white;");

        TableColumn<Building, String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getType() != null ? d.getValue().getType().toString() : "—"));
        colType.setStyle("-fx-text-fill: white;");

        TableColumn<Building, Double> colSurface = new TableColumn<>("Surface (m²)");
        colSurface.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getSurface()).asObject());
        colSurface.setStyle("-fx-text-fill: white;");

        TableColumn<Building, Double> colConso = new TableColumn<>("Consommation (kWh)");
        colConso.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getTotalConsommation()).asObject());
        colConso.setStyle("-fx-text-fill: white;");

        TableColumn<Building, Double> colCout = new TableColumn<>("Coût (€)");
        colCout.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getCoutTotal()).asObject());
        colCout.setStyle("-fx-text-fill: white;");

        TableColumn<Building, Integer> colReleves = new TableColumn<>("Relevés");
        colReleves.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getNombreReleves()).asObject());
        colReleves.setStyle("-fx-text-fill: white;");

        table.getColumns().addAll(colNom, colType, colSurface, colConso, colCout, colReleves);

        buildingList = FXCollections.observableArrayList();
        rafraichir();
        table.setItems(buildingList);

        getChildren().add(table);

        // Detail section (shows when a building is selected)
        Label detailLabel = new Label("Sélectionnez un bâtiment pour voir les détails");
        detailLabel.setFont(Font.font("System", FontWeight.NORMAL, 13));
        detailLabel.setTextFill(Color.web("#8f8f9f"));
        detailLabel.setPadding(new Insets(10, 0, 0, 0));
        getChildren().add(detailLabel);

        table.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                detailLabel.setText("Bâtiment sélectionné : " + sel.getNom()
                        + " | " + sel.getAdresse()
                        + " | " + sel.getTotalConsommation() + " kWh"
                        + " | " + sel.getCoutTotal() + " €");
            } else {
                detailLabel.setText("Sélectionnez un bâtiment pour voir les détails");
            }
        });

        VBox.setVgrow(table, Priority.ALWAYS);
    }

    // ── CRUD ──

    private void rafraichir() {
        buildingList.setAll(service.getAllBuildings());
    }

    private void ajouterBatiment() {
        showBuildingDialog(null, "Ajouter un bâtiment");
    }

    private void modifierBatiment() {
        Building selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Veuillez sélectionner un bâtiment à modifier.");
            return;
        }
        showBuildingDialog(selected, "Modifier un bâtiment");
    }

    private void supprimerBatiment() {
        Building selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Veuillez sélectionner un bâtiment à supprimer.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText("Supprimer le bâtiment ?");
        confirm.setContentText("Voulez-vous vraiment supprimer « " + selected.getNom() + " » ?");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            service.deleteBuilding(selected.getId());
            rafraichir();
        }
    }

    private void clonerBatiment() {
        Building selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Veuillez sélectionner un bâtiment à cloner.");
            return;
        }
        Building clone = new Building();
        clone.setNom(selected.getNom() + " (copie)");
        clone.setAdresse(selected.getAdresse());
        clone.setSurface(selected.getSurface());
        clone.setType(selected.getType());
        // Copy consumption records (new objects)
        for (var r : selected.getConsommationRecords()) {
            clone.addConsumptionRecord(new com.smartenergy.model.ConsumptionRecord(
                    r.getDateHeure(), r.getType(), r.getQuantite(),
                    r.getCoutEstime(), r.getUnite()));
        }
        service.saveBuilding(clone);
        rafraichir();
    }

    // ── Dialog ──

    private void showBuildingDialog(Building existing, String title) {
        Dialog<Building> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(existing == null ? "Nouveau bâtiment" : "Modifier : " + existing.getNom());
        dialog.getDialogPane().setStyle("-fx-background-color: #16213e;");

        ButtonType saveBtnType = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        // Form
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));
        grid.setStyle("-fx-background-color: #16213e;");

        TextField tfNom = new TextField(existing != null ? existing.getNom() : "");
        tfNom.setPromptText("Nom du bâtiment");
        tfNom.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white; -fx-prompt-text-fill: #666;");

        TextField tfAdresse = new TextField(existing != null ? existing.getAdresse() : "");
        tfAdresse.setPromptText("Adresse");
        tfAdresse.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white; -fx-prompt-text-fill: #666;");

        TextField tfSurface = new TextField(existing != null ? String.valueOf(existing.getSurface()) : "");
        tfSurface.setPromptText("Surface (m²)");
        tfSurface.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white; -fx-prompt-text-fill: #666;");

        ComboBox<BuildingType> cbType = new ComboBox<>(
                FXCollections.observableArrayList(BuildingType.values()));
        cbType.setValue(existing != null ? existing.getType() : BuildingType.MAISON);
        cbType.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white;");

        grid.add(new Label("Nom :") {{
            setTextFill(Color.web("#a0a0b0"));
            setStyle("-fx-font-size: 13px;");
        }}, 0, 0);
        grid.add(tfNom, 1, 0);

        grid.add(new Label("Adresse :") {{
            setTextFill(Color.web("#a0a0b0"));
            setStyle("-fx-font-size: 13px;");
        }}, 0, 1);
        grid.add(tfAdresse, 1, 1);

        grid.add(new Label("Surface :") {{
            setTextFill(Color.web("#a0a0b0"));
            setStyle("-fx-font-size: 13px;");
        }}, 0, 2);
        grid.add(tfSurface, 1, 2);

        grid.add(new Label("Type :") {{
            setTextFill(Color.web("#a0a0b0"));
            setStyle("-fx-font-size: 13px;");
        }}, 0, 3);
        grid.add(cbType, 1, 3);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == saveBtnType) {
                String nom = tfNom.getText().trim();
                String adresse = tfAdresse.getText().trim();
                double surface;
                try {
                    surface = Double.parseDouble(tfSurface.getText().trim());
                } catch (NumberFormatException e) {
                    showWarning("La surface doit être un nombre valide.");
                    return null;
                }
                BuildingType type = cbType.getValue();

                Building b;
                if (existing != null) {
                    b = existing;
                    b.setNom(nom);
                    b.setAdresse(adresse);
                    b.setSurface(surface);
                    b.setType(type);
                } else {
                    b = service.createBuilding(nom, adresse, surface, type);
                }
                return b;
            }
            return null;
        });

        Optional<Building> result = dialog.showAndWait();
        result.ifPresent(b -> {
            service.saveBuilding(b);
            rafraichir();
        });
    }

    // ── Helpers ──

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
}
