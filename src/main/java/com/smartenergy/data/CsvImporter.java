package com.smartenergy.data;

import com.smartenergy.model.ConsumptionRecord;
import com.smartenergy.model.EnergyType;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CsvImporter {

    private static final Logger LOG = Logger.getLogger(CsvImporter.class.getName());

    private static final String[] EXPECTED_HEADERS = {"date", "heure", "type_energie", "quantite", "cout"};

    private static final int COL_DATE = 0;
    private static final int COL_HEURE = 1;
    private static final int COL_TYPE = 2;
    private static final int COL_QUANTITE = 3;
    private static final int COL_COUT = 4;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Importe les relevés de consommation depuis un fichier CSV.
     * <p>
     * Format attendu (avec en-tête) :<br>
     * {@code date,heure,type_energie,quantite,cout}<br>
     * {@code 2025-01-15,14:30,ELECTRICITE,12.5,2.35}
     *
     * @param filePath chemin du fichier CSV à parser
     * @return liste des relevés de consommation parsés
     * @throws IOException            si le fichier est introuvable ou illisible
     * @throws IllegalArgumentException si le format est invalide
     */
    public List<ConsumptionRecord> importFromFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            throw new IOException("Fichier introuvable : " + filePath);
        }

        List<ConsumptionRecord> records = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();

        try (CSVParser parser = CSVParser.parse(
                path,
                StandardCharsets.UTF_8,
                CSVFormat.DEFAULT
                        .withHeader()
                        .withIgnoreHeaderCase()
                        .withTrim())) {

            // Validate expected headers
            validateHeaders(parser);

            int lineNumber = 1; // header is line 1
            for (CSVRecord record : parser) {
                lineNumber++;
                try {
                    ConsumptionRecord consumption = parseRecord(record, lineNumber);
                    records.add(consumption);
                } catch (IllegalArgumentException e) {
                    errorMessages.add("Ligne " + lineNumber + " ignorée : " + e.getMessage());
                }
            }
        }

        // Log errors after full parse
        if (!errorMessages.isEmpty()) {
            for (String error : errorMessages) {
                LOG.warning(error);
            }
        }

        return records;
    }

    private void validateHeaders(CSVParser parser) {
        var headers = parser.getHeaderMap();
        if (headers == null || headers.isEmpty()) {
            throw new IllegalArgumentException("Le fichier CSV ne contient pas d'en-tête valide. "
                    + "En-têtes attendus : " + String.join(", ", EXPECTED_HEADERS));
        }

        for (String expected : EXPECTED_HEADERS) {
            if (!headers.containsKey(expected)) {
                throw new IllegalArgumentException("En-tête manquant : '" + expected + "'. "
                        + "Format attendu : " + String.join(", ", EXPECTED_HEADERS));
            }
        }
    }

    private ConsumptionRecord parseRecord(CSVRecord record, int lineNumber) {
        // Date
        String dateStr = record.get(EXPECTED_HEADERS[COL_DATE]).trim();
        if (dateStr.isEmpty()) {
            throw new IllegalArgumentException("Champ 'date' vide à la ligne " + lineNumber);
        }

        LocalDate date;
        try {
            date = LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Format de date invalide à la ligne " + lineNumber
                    + " : '" + dateStr + "' (format attendu : yyyy-MM-dd)");
        }

        // Heure
        String heureStr = record.get(EXPECTED_HEADERS[COL_HEURE]).trim();
        LocalTime time;
        try {
            time = heureStr.isEmpty() ? LocalTime.MIDNIGHT : LocalTime.parse(heureStr, TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Format d'heure invalide à la ligne " + lineNumber
                    + " : '" + heureStr + "' (format attendu : HH:mm)");
        }

        LocalDateTime dateHeure = LocalDateTime.of(date, time);

        // Type d'énergie
        String typeStr = record.get(EXPECTED_HEADERS[COL_TYPE]).trim().toUpperCase();
        EnergyType type;
        try {
            type = EnergyType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Type d'énergie invalide à la ligne " + lineNumber
                    + " : '" + typeStr + "'. Valeurs acceptées : "
                    + java.util.Arrays.toString(EnergyType.values()));
        }

        // Quantité
        String quantiteStr = record.get(EXPECTED_HEADERS[COL_QUANTITE]).trim();
        if (quantiteStr.isEmpty()) {
            throw new IllegalArgumentException("Champ 'quantite' vide à la ligne " + lineNumber);
        }
        double quantite;
        try {
            quantite = Double.parseDouble(quantiteStr.replace(",", "."));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valeur de quantité invalide à la ligne " + lineNumber
                    + " : '" + quantiteStr + "'");
        }

        // Coût
        String coutStr = record.get(EXPECTED_HEADERS[COL_COUT]).trim();
        double cout;
        try {
            cout = coutStr.isEmpty() ? 0.0 : Double.parseDouble(coutStr.replace(",", "."));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valeur de coût invalide à la ligne " + lineNumber
                    + " : '" + coutStr + "'");
        }

        // Unité : kWh par défaut (l'unité est implicite dans le format)
        String unite = "kWh";

        return new ConsumptionRecord(dateHeure, type, quantite, cout, unite);
    }
}
