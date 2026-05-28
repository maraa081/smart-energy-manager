package com.smartenergy.service;

import com.smartenergy.model.EnergyType;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.Locale;
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

    public record ForestResult(double prediction, double intervalMin, double intervalMax) {}

    /**
     * Appelle le modèle RandomForest et retourne la prédiction.
     */
    public static Optional<ForestResult> predict(int mois, int heure, EnergyType type, double temperature) {
        try {
            // 1. Trouver le script predict.py
            File script = findScript();
            if (script == null) {
                System.err.println("⚠ [PythonPredictor] predict.py introuvable");
                return Optional.empty();
            }
            System.out.println("ℹ [PythonPredictor] Script trouvé : " + script.getAbsolutePath());

            // 2. Trouver la commande Python
            String pythonCmd = detectPythonCommand(script.getParentFile());
            System.out.println("ℹ [PythonPredictor] Commande Python : " + pythonCmd);

            // 3. Lancer le processus
            ProcessBuilder pb = new ProcessBuilder(
                    pythonCmd, script.getAbsolutePath(),
                    "--predict",
                    "--mois", String.valueOf(mois),
                    "--heure", String.valueOf(heure),
                    "--type", type.name(),
                    "--temperature", String.format(Locale.US, "%.1f", temperature)
            );

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
            String fullOutput = output.toString();

            if (exitCode != 0) {
                System.err.println("⚠ [PythonPredictor] Exit code " + exitCode);
                System.err.println("   Full output:");
                for (String l : fullOutput.split("\n")) {
                    System.err.println("   | " + l);
                }
                return Optional.empty();
            }

            System.out.println("ℹ [PythonPredictor] Sortie : " + fullOutput.replace("\n", " | "));

            // 4. Parser la sortie
            return parseOutput(fullOutput);

        } catch (Exception e) {
            System.err.println("⚠ [PythonPredictor] Erreur : " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Parse la sortie du script predict.py.
     * Format : "⚡ X.XX kWh estimés" + "Intervalle ... [X.XX, X.XX]"
     */
    private static Optional<ForestResult> parseOutput(String output) {
        try {
            double prediction = 0;
            double intervalMin = 0;
            double intervalMax = 0;

            for (String line : output.split("\n")) {
                line = line.trim();

                // Ligne : "⚡ 6.56 kWh estimés"
                if (line.contains("kWh estimés") || line.contains("kWh estime")) {
                    // Extrait le premier nombre
                    String cleaned = line.replaceAll("[^0-9.,]", " ").trim();
                    String[] parts = cleaned.split("\\s+");
                    for (String p : parts) {
                        if (!p.isEmpty()) {
                            prediction = Double.parseDouble(p.replace(",", "."));
                            break;
                        }
                    }
                }

                // Ligne : "Intervalle de confiance (95%) : [4.31, 8.82]"
                if (line.contains("Intervalle") || line.contains("intervalle")) {
                    int start = line.indexOf("[");
                    int end = line.indexOf("]");
                    if (start >= 0 && end > start) {
                        String interval = line.substring(start + 1, end);
                        String[] vals = interval.split(",");
                        if (vals.length >= 2) {
                            intervalMin = Double.parseDouble(vals[0].trim().replace(",", "."));
                            intervalMax = Double.parseDouble(vals[1].trim().replace(",", "."));
                        }
                    }
                }
            }

            if (prediction > 0) {
                if (intervalMin == 0 && intervalMax == 0) {
                    intervalMin = prediction * 0.7;
                    intervalMax = prediction * 1.3;
                }
                return Optional.of(new ForestResult(prediction, intervalMin, intervalMax));
            }

            System.err.println("⚠ [PythonPredictor] Aucune prédiction trouvée dans la sortie");
            return Optional.empty();

        } catch (Exception e) {
            System.err.println("⚠ [PythonPredictor] Erreur parsing : " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Détecte la commande Python utilisable.
     */
    private static String detectPythonCommand(File scriptDir) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        // 1. Venv Linux
        File linuxVenv = new File(scriptDir, "venv/bin/python3");
        if (linuxVenv.exists()) return linuxVenv.getAbsolutePath();

        // 2. Venv Windows
        File winVenv = new File(scriptDir, "venv/Scripts/python.exe");
        if (winVenv.exists()) return winVenv.getAbsolutePath();

        // 3. Windows : essayer "python" puis "py"
        if (isWindows) {
            // Essayer de trouver le chemin complet via where
            String fullPath = findWindowsPython("python");
            if (fullPath != null) return fullPath;

            fullPath = findWindowsPython("py");
            if (fullPath != null) return fullPath;

            // Fallback : chemins classiques d'installation
            String[] commonPaths = {
                System.getenv("LOCALAPPDATA") + "/Programs/Python/Python314/python.exe",
                System.getenv("LOCALAPPDATA") + "/Programs/Python/Python313/python.exe",
                System.getenv("LOCALAPPDATA") + "/Programs/Python/Python312/python.exe",
                "C:/Python314/python.exe",
                "C:/Python313/python.exe",
                "C:/Python312/python.exe",
            };
            for (String p : commonPaths) {
                File f = new File(p);
                if (f.exists()) return f.getAbsolutePath();
            }

            return "python"; // dernier recours
        }

        return "python3";
    }

    /**
     * Windows : exécute "where <cmd>" pour trouver le chemin complet.
     */
    private static String findWindowsPython(String cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "where", cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                if (line != null && !line.isEmpty()) {
                    File f = new File(line.trim());
                    if (f.exists()) return f.getAbsolutePath();
                }
            }
            p.waitFor();
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Cherche le script predict.py dans différents endroits.
     */
    private static File findScript() {
        String userDir = System.getProperty("user.dir");

        String[] candidates = {
            ML_DIR + "/" + SCRIPT,
            ML_DIR + "\\" + SCRIPT,
            "../" + ML_DIR + "/" + SCRIPT,
            userDir + "/" + ML_DIR + "/" + SCRIPT,
            userDir + "\\" + ML_DIR + "\\" + SCRIPT,
            System.getProperty("user.home") + "/smart-energy-manager/" + ML_DIR + "/" + SCRIPT,
        };

        for (String path : candidates) {
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                return f;
            }
        }

        System.err.println("⚠ [PythonPredictor] predict.py introuvable (user.dir=" + userDir + ")");
        return null;
    }
}
