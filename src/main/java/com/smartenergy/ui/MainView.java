package com.smartenergy.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.HashMap;
import java.util.Map;

public class MainView extends BorderPane {

    private static final String[] NAV_ITEMS = {
            "Dashboard", "Bâtiments", "Consommations", "Graphiques", "Analyse"
    };

    private static final String[] NAV_ICONS = {
            "\uD83D\uDCCA",  // 📊
            "\uD83C\uDFE2",  // 🏢
            "\u26A1",        // ⚡
            "\uD83D\uDCC8",  // 📈
            "\uD83D\uDD0D"   // 🔍
    };

    private final Map<String, Node> viewCache = new HashMap<>();
    private final VBox sidebar;
    private final StackPane contentArea;
    private final Map<String, Button> navButtons = new HashMap<>();
    private Node currentView;

    public MainView() {
        setStyle("-fx-background-color: #1a1a2e;");

        // Sidebar
        sidebar = createSidebar();
        setLeft(sidebar);

        // Content area
        contentArea = new StackPane();
        contentArea.setStyle("-fx-background-color: #16213e;");
        setCenter(contentArea);

        // Default view
        showView("Dashboard");
    }

    // ──────────────────────────────────────────────────────────────
    // Sidebar
    // ──────────────────────────────────────────────────────────────

    private VBox createSidebar() {
        VBox box = new VBox();
        box.getStyleClass().add("sidebar");
        box.setPrefWidth(220);
        box.setMinWidth(220);
        box.setMaxWidth(220);
        box.setPadding(new Insets(20, 10, 20, 10));
        box.setSpacing(8);
        box.setStyle(
                "-fx-background-color: #0f3460;" +
                "-fx-border-color: #1a1a2e;" +
                "-fx-border-width: 0 2 0 0;"
        );

        // Brand
        Label brand = new Label("⚡ Smart Energy");
        brand.getStyleClass().add("title");
        brand.setFont(Font.font("System", FontWeight.BOLD, 18));
        brand.setTextFill(javafx.scene.paint.Color.web("#e94560"));
        brand.setPadding(new Insets(0, 0, 20, 10));
        box.getChildren().add(brand);

        // Separator
        Region sep = new Region();
        sep.setStyle("-fx-border-color: #1a1a2e; -fx-border-width: 0 0 1 0;");
        sep.setPrefHeight(1);
        sep.setPadding(new Insets(0, 0, 10, 0));
        box.getChildren().add(sep);

        // Nav buttons
        for (int i = 0; i < NAV_ITEMS.length; i++) {
            Button btn = createNavButton(NAV_ICONS[i] + "  " + NAV_ITEMS[i], NAV_ITEMS[i]);
            navButtons.put(NAV_ITEMS[i], btn);
            box.getChildren().add(btn);
        }

        // Spacer
        VBox.setVgrow(box, Priority.ALWAYS);

        // Footer / version
        Label version = new Label("v1.0.0");
        version.setStyle("-fx-text-fill: #555; -fx-font-size: 11px;");
        version.setPadding(new Insets(10, 0, 0, 10));
        box.getChildren().add(version);

        return box;
    }

    private Button createNavButton(String text, String viewName) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPrefHeight(44);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setPadding(new Insets(0, 0, 0, 15));
        btn.setStyle(
                "-fx-background-color: transparent;" +
                "-fx-text-fill: #a0a0b0;" +
                "-fx-font-size: 14px;" +
                "-fx-border-radius: 8;" +
                "-fx-background-radius: 8;" +
                "-fx-cursor: hand;"
        );
        btn.setOnAction(e -> showView(viewName));
        return btn;
    }

    // ──────────────────────────────────────────────────────────────
    // View switching
    // ──────────────────────────────────────────────────────────────

    private void showView(String viewName) {
        // Highlight nav button
        for (Map.Entry<String, Button> entry : navButtons.entrySet()) {
            boolean active = entry.getKey().equals(viewName);
            entry.getValue().setStyle(active
                    ? "-fx-background-color: #e94560; -fx-text-fill: white; -fx-font-size: 14px; -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;"
                    : "-fx-background-color: transparent; -fx-text-fill: #a0a0b0; -fx-font-size: 14px; -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;"
            );
        }

        // Refresh building lists when switching back
        boolean refreshConsommations = "Consommations".equals(viewName)
                && viewCache.containsKey(viewName);
        boolean refreshGraphiques = "Graphiques".equals(viewName)
                && viewCache.containsKey(viewName);

        // Cached or create
        Node view = viewCache.computeIfAbsent(viewName, this::createView);
        if (refreshConsommations) {
            ((ConsumptionView) view).rafraichir();
        } else if (refreshGraphiques) {
            ((ChartView) view).rafraichir();
        }
        if (currentView != view) {
            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
            currentView = view;
        }
    }

    private Node createView(String viewName) {
        return switch (viewName) {
            case "Dashboard" -> new DashboardView();
            case "Bâtiments" -> new BuildingView();
            case "Consommations" -> new ConsumptionView();
            case "Graphiques" -> new ChartView();
            case "Analyse" -> new AnalysisView();
            default -> {
                Label err = new Label("Vue inconnue : " + viewName);
                err.setStyle("-fx-text-fill: red; -fx-font-size: 18px;");
                yield err;
            }
        };
    }
}
