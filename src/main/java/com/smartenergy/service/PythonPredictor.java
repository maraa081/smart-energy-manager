package com.smartenergy.service;

import com.smartenergy.model.EnergyType;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Interface entre l'app Java et le modèle RandomForest Python.
 * <p>
 * Appelle predict.py dans ml-prediction/ via ProcessBuilder,
 * parse la sortie et retourne la prédiction + intervalle.
 * </p>
 */
public class PythonPredictor {

    private static final String ML_DIR = "ml-prediction";
    private static final String SCRIPT = "predict.py";

    /**
     * Résultat d'une prédiction RandomForest.
     */
    public record ForestResult(double prediction, double intervalMin, double intervalMax) {}

    /**
     * Appelle le modèle RandomForest et retourne la prédiction.
     *
     * @param mois        mois (1-12)
     * @param heure       heure (0-23)
     * @param type        type d'énergie
     * @param temperature température extérieure estimée (°C)
     * @return le résultat de la prédiction, ou {@link Optional#empty()} si le script est indisponible
     */
    public static Optional<ForestResult> predict(int mois, int heure, EnergyType type, double temperature) {
        try {
            // Chercher le script predict.py dans le projet ou aux alentours
            File script = findScript();
            if (script == null) {
                return Optional.empty();
            }

            // Essayer d'utiliser le venv local s'il existe, sinon python3 système
            File venvPython = new File(script.getParentFile(), "venv/bin/python3");
            String pythonCmd = venvPython.exists() ? venvPython.getAbsolutePath() : "python3";

            ProcessBuilder pb = new ProcessBuilder(
                    pythonCmd, script.getAbsolutePath(),
                    "--predict",
                    "--mois", String.valueOf(mois),
                    "--heure", String.valueOf(heure),
                    "--type", type.name(),
                    "--temperature", String.format("%.1f", temperature)
            );

            // Répertoire de travail = dossier contenant predict.py et model.pkl
            pb.directory(script.getParentFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("⚠ Python predict.py exited with code " + exitCode);
                return Optional.empty();
            }

            // Parser la sortie pour extraire prediction et intervalle
            return parseOutput(output.toString());

        } catch (Exception e) {
            System.err.println("⚠ Erreur appel Python : " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Parse la sortie du script predict.py.
     * Format attendu :
     *   ⚡ X.XX kWh estimés
     *   📊 Intervalle de confiance (95%) : [X.XX, X.XX] kWh
     */
    private static Optional<ForestResult> parseOutput(String output) {
        try {
            double prediction = 0;
            double intervalMin = 0;
            double intervalMax = 0;

            for (String line : output.split("\n")) {
                // Ligne contenant la prédiction
                if (line.contains("kWh estimés")) {
                    // Extrait : "   ⚡ 12.45 kWh estimés"
                    String cleaned = line.replaceAll("[^0-9.,]", " ").trim();
                    String[] parts = cleaned.split("\\s+");
                    prediction = Double.parseDouble(parts[0].replace(",", "."));
                }
                // Ligne contenant l'intervalle
                if (line.contains("Intervalle") && line.contains("[")) {
                    // Extrait : "   📊 Intervalle de confiance (95%) : [10.2, 14.7] kWh"
                    int start = line.indexOf("[");
                    int end = line.indexOf("]");
                    if (start >= 0 && end > start) {
                        String interval = line.substring(start + 1, end);
                        String[] vals = interval.split(",");
                        intervalMin = Double.parseDouble(vals[0].trim().replace(",", "."));
                        intervalMax = Double.parseDouble(vals[1].trim().replace(",", "."));
                    }
                }
            }

            if (prediction > 0) {
                return Optional.of(new ForestResult(prediction, intervalMin, intervalMax));
            }

            // Fallback : essayer de trouver un nombre après ⚡
            for (String line : output.split("\n")) {
                if (line.contains("⚡")) {
                    String cleaned = line.replaceAll("[^0-9.,]", " ").trim();
                    String[] parts = cleaned.split("\\s+");
                    for (String p : parts) {
                        if (!p.isEmpty()) {
                            prediction = Double.parseDouble(p.replace(",", "."));
                            break;
                        }
                    }
                }
                if (prediction > 0) break;
            }

            if (prediction > 0) {
                return Optional.of(new ForestResult(prediction, prediction * 0.8, prediction * 1.2));
            }

            return Optional.empty();

        } catch (Exception e) {
            System.err.println("⚠ Erreur parsing sortie Python : " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Cherche le script predict.py dans le répertoire courant, le répertoire parent,
     * et le user.dir.
     */
    private static File findScript() {
        // Chemins à essayer
        String[] candidates = {
            ML_DIR + "/" + SCRIPT,                                          // ml-prediction/predict.py
            "../" + ML_DIR + "/" + SCRIPT,                                  // ../ml-prediction/predict.py
            Paths.get(System.getProperty("user.dir"), ML_DIR, SCRIPT).toString(),  // CWD/ml-prediction/predict.py
            System.getProperty("user.home") + "/smart-energy-manager/" + ML_DIR + "/" + SCRIPT,
        };

        for (String path : candidates) {
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                return f;
            }
        }
        return null;
    }
}
