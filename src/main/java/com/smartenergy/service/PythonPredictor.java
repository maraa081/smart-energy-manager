package com.smartenergy.service;

import com.smartenergy.model.EnergyType;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.Optional;

public class PythonPredictor {

    private static final String ML_DIR = "ml-prediction";
    private static final String SCRIPT = "predict.py";

    public record ForestResult(double prediction, double intervalMin, double intervalMax) {}
    public record TrainResult(double r2, double mae, double rmse, int nTrain, int nTest) {}

    public static Optional<ForestResult> predict(int mois, int heure, EnergyType type, double temperature) {
        try {
            File script = findScript();
            if (script == null) return Optional.empty();
            String pythonCmd = detectPythonCommand(script.getParentFile());

            ProcessBuilder pb = new ProcessBuilder(pythonCmd, script.getAbsolutePath(),
                    "--predict", "--mois", String.valueOf(mois), "--heure", String.valueOf(heure),
                    "--type", type.name(), "--temperature", String.format(Locale.US, "%.1f", temperature));
            pb.directory(script.getParentFile());
            pb.redirectErrorStream(true);
            pb.environment().put("PYTHONIOENCODING", "utf-8");

            String output = runProcess(pb);
            if (output == null) return Optional.empty();

            double prediction = 0, intervalMin = 0, intervalMax = 0;
            for (String line : output.split("\n")) {
                line = line.trim();
                if (line.startsWith("[PREDICT]")) {
                    String val = line.replaceAll("[^0-9.,]", " ").trim();
                    for (String p : val.split("\\s+")) {
                        if (!p.isEmpty()) { prediction = Double.parseDouble(p.replace(",", ".")); break; }
                    }
                }
                if (line.startsWith("[INTERVAL]")) {
                    int s = line.indexOf("["), e = line.indexOf("]");
                    if (s >= 0 && e > s) {
                        String[] vals = line.substring(s + 1, e).split(",");
                        if (vals.length >= 2) {
                            intervalMin = Double.parseDouble(vals[0].trim().replace(",", "."));
                            intervalMax = Double.parseDouble(vals[1].trim().replace(",", "."));
                        }
                    }
                }
            }
            if (prediction > 0) {
                if (intervalMin == 0 && intervalMax == 0) { intervalMin = prediction * 0.7; intervalMax = prediction * 1.3; }
                return Optional.of(new ForestResult(prediction, intervalMin, intervalMax));
            }
            return Optional.empty();
        } catch (Exception e) {
            System.err.println("⚠ [PythonPredictor] Erreur predict: " + e.getMessage());
            return Optional.empty();
        }
    }

    public static Optional<TrainResult> train(int nEstimators, int maxDepth, int minSamplesSplit, int minSamplesLeaf) {
        try {
            File script = findScript();
            if (script == null) return Optional.empty();
            String pythonCmd = detectPythonCommand(script.getParentFile());

            ProcessBuilder pb = new ProcessBuilder(pythonCmd, script.getAbsolutePath(),
                    "--train", "--n-estimators", String.valueOf(nEstimators),
                    "--max-depth", String.valueOf(maxDepth),
                    "--min-samples-split", String.valueOf(minSamplesSplit),
                    "--min-samples-leaf", String.valueOf(minSamplesLeaf));
            pb.directory(script.getParentFile());
            pb.redirectErrorStream(true);
            pb.environment().put("PYTHONIOENCODING", "utf-8");

            String output = runProcess(pb);
            if (output == null) return Optional.empty();

            double r2 = 0, mae = 0, rmse = 0;
            int nTrain = 0, nTest = 0;
            for (String line : output.split("\n")) {
                line = line.trim();
                if (line.startsWith("[METRICS]")) {
                    for (String p : line.split("\\s+")) {
                        if (p.startsWith("R2=")) r2 = Double.parseDouble(p.substring(3));
                        if (p.startsWith("MAE=")) mae = Double.parseDouble(p.substring(4));
                        if (p.startsWith("RMSE=")) rmse = Double.parseDouble(p.substring(5));
                        if (p.startsWith("Train=")) nTrain = Integer.parseInt(p.substring(6));
                        if (p.startsWith("Test=")) nTest = Integer.parseInt(p.substring(5));
                    }
                }
            }
            return r2 > 0 ? Optional.of(new TrainResult(r2, mae, rmse, nTrain, nTest)) : Optional.empty();
        } catch (Exception e) {
            System.err.println("⚠ [PythonPredictor] Erreur train: " + e.getMessage());
            return Optional.empty();
        }
    }

    public static boolean visualize() {
        try {
            File script = findScript();
            if (script == null) return false;
            String pythonCmd = detectPythonCommand(script.getParentFile());
            ProcessBuilder pb = new ProcessBuilder(pythonCmd, script.getAbsolutePath(), "--visualize");
            pb.directory(script.getParentFile());
            pb.redirectErrorStream(true);
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            return runProcess(pb) != null;
        } catch (Exception e) {
            System.err.println("⚠ [PythonPredictor] Erreur visualize: " + e.getMessage());
            return false;
        }
    }

    private static String runProcess(ProcessBuilder pb) throws Exception {
        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) output.append(line).append("\n");
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.err.println("⚠ [PythonPredictor] Exit code " + exitCode);
            return null;
        }
        return output.toString();
    }

    private static String detectPythonCommand(File scriptDir) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        File linuxVenv = new File(scriptDir, "venv/bin/python3");
        if (linuxVenv.exists()) return linuxVenv.getAbsolutePath();
        File winVenv = new File(scriptDir, "venv/Scripts/python.exe");
        if (winVenv.exists()) return winVenv.getAbsolutePath();
        if (isWindows) {
            String fullPath = findWindowsPython("python");
            if (fullPath != null) return fullPath;
            fullPath = findWindowsPython("py");
            if (fullPath != null) return fullPath;
            String[] commonPaths = {
                System.getenv("LOCALAPPDATA") + "/Programs/Python/Python314/python.exe",
                System.getenv("LOCALAPPDATA") + "/Programs/Python/Python313/python.exe",
                System.getenv("LOCALAPPDATA") + "/Programs/Python/Python312/python.exe",
                "C:/Python314/python.exe", "C:/Python313/python.exe", "C:/Python312/python.exe",
            };
            for (String p : commonPaths) { File f = new File(p); if (f.exists()) return f.getAbsolutePath(); }
            return "python";
        }
        return "python3";
    }

    private static String findWindowsPython(String cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "where", cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                if (line != null && !line.isEmpty()) { File f = new File(line.trim()); if (f.exists()) return f.getAbsolutePath(); }
            }
            p.waitFor();
        } catch (Exception ignored) {}
        return null;
    }

    private static File findScript() {
        String userDir = System.getProperty("user.dir");
        String[] candidates = {
            ML_DIR + "/" + SCRIPT, ML_DIR + "\\" + SCRIPT,
            userDir + "/" + ML_DIR + "/" + SCRIPT, userDir + "\\" + ML_DIR + "\\" + SCRIPT,
        };
        for (String path : candidates) { File f = new File(path); if (f.exists() && f.isFile()) return f; }
        System.err.println("⚠ [PythonPredictor] predict.py introuvable (user.dir=" + userDir + ")");
        return null;
    }
}
