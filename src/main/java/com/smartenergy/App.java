package com.smartenergy;

import com.smartenergy.service.EnergyService;
import com.smartenergy.ui.MainView;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

public class App extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // Initialize the service on startup
        EnergyService.getInstance();

        // Build the main view
        MainView mainView = new MainView();
        Scene scene = new Scene(mainView, 1280, 800);

        // Load CSS
        URL cssUrl = getClass().getResource("/styles.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            System.err.println(" styles.css not found in resources");
        }

        primaryStage.setTitle("Smart Energy Manager");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.show();
    }
}
