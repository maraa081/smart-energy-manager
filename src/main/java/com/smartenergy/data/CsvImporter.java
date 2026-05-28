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
import java.util.logging.Logger;

public class CsvImporter {

    private static final Logger LOG = Logger.getLogger(CsvImporter.class.getName());
    private static final String[] EXPECTED_HEADERS = {"date", "heure", "type_energie", "quantite", "cout"};
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public List<ConsumptionRecord> importFromFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IOException("Fichier introuvable : " + filePath);
        }

        List<ConsumptionRecord> records = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();

        try (CSVParser parser = CSVParser.parse(
                path, StandardCharsets.UTF_8,
                CSVFormat.DEFAULT.withHeader().withIgnoreHeaderCase().withTrim())) {

            validateHeaders(parser);

            int lineNumber = 1;
            for (CSVRecord record : parser) {
                lineNumber++;
                try {
                    records.add(parseRecord(record, lineNumber));
                } catch (IllegalArgumentException e) {
                    errorMessages.add("Ligne " + lineNumber + " ignorée : " + e.getMessage());
                }
            }
        }

        for (String error : errorMessages) {
            LOG.warning(error);
        }

        return records;
    }

    private void validateHeaders(CSVParser parser) {
        var headers = parser.getHeaderMap();
        if (headers == null || headers.isEmpty()) {
            throw new IllegalArgumentException("En-tête manquants. Format attendu : " + String.join(", ", EXPECTED_HEADERS));
        }
        for (String expected : EXPECTED_HEADERS) {
            if (!headers.containsKey(expected)) {
                throw new IllegalArgumentException("En-tête manquant : '" + expected + "'");
            }
        }
    }

    private ConsumptionRecord parseRecord(CSVRecord record, int lineNumber) {
        String dateStr = record.get(EXPECTED_HEADERS[0]).trim();
        if (dateStr.isEmpty()) throw new IllegalArgumentException("Champ 'date' vide");

        LocalDate date;
        try {
            date = LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Format date invalide : '" + dateStr + "' (yyyy-MM-dd)");
        }

        String heureStr = record.get(EXPECTED_HEADERS[1]).trim();
        LocalTime time;
        try {
            time = heureStr.isEmpty() ? LocalTime.MIDNIGHT : LocalTime.parse(heureStr, TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Format heure invalide : '" + heureStr + "' (HH:mm)");
        }

        String typeStr = record.get(EXPECTED_HEADERS[2]).trim().toUpperCase();
        EnergyType type;
        try {
            type = EnergyType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Type d'énergie invalide : '" + typeStr + "'");
        }

        String quantiteStr = record.get(EXPECTED_HEADERS[3]).trim();
        if (quantiteStr.isEmpty()) throw new IllegalArgumentException("Champ 'quantite' vide");
        double quantite = Double.parseDouble(quantiteStr.replace(",", "."));

        String coutStr = record.get(EXPECTED_HEADERS[4]).trim();
        double cout = coutStr.isEmpty() ? 0.0 : Double.parseDouble(coutStr.replace(",", "."));

        return new ConsumptionRecord(LocalDateTime.of(date, time), type, quantite, cout, type.getUnite());
    }
}
