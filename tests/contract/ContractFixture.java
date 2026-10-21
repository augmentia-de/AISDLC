package com.echobox.contract;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

/**
 * Lesehilfe fuer die Vertragstests.
 *
 * <p>Die Vertragstests lesen den eingefrorenen Vertrag
 * ({@code docs/api/openapi.yaml}) und die maschinenlesbaren Vertragsspezifikationen
 * aus {@code tests/contract/*.yaml}. Beide werden relativ zur Projektwurzel
 * aufgeloest, damit der Test unabhaengig vom Arbeitsverzeichnis laeuft.
 */
final class ContractFixture {

    private ContractFixture() {
        // Hilfsklasse ohne Zustand.
    }

    /**
     * Liefert die Projektwurzel — den naechsten Vorfahren des Arbeitsverzeichnisses,
     * in dem {@code docs/api/openapi.yaml} liegt.
     *
     * @return die Projektwurzel
     */
    static Path projectRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("docs").resolve("api").resolve("openapi.yaml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(
                "docs/api/openapi.yaml wurde weder im Arbeitsverzeichnis noch in einem Vorfahren gefunden.");
    }

    /**
     * Liefert den eingefrorenen Vertrag.
     *
     * @return der Vertrag als Baum
     */
    static Map<String, Object> contract() {
        return loadYaml(projectRoot().resolve("docs").resolve("api").resolve("openapi.yaml"));
    }

    /**
     * Liefert eine Vertragsspezifikation aus {@code tests/contract/}.
     *
     * @param fileName der Dateiname der Spezifikation
     * @return die Spezifikation als Baum
     */
    static Map<String, Object> spec(String fileName) {
        return loadYaml(projectRoot().resolve("tests").resolve("contract").resolve(fileName));
    }

    /**
     * Liest eine YAML-Datei als Baum.
     *
     * @param path der Pfad der Datei
     * @return der Inhalt als Baum
     */
    static Map<String, Object> loadYaml(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            Object loaded = new Yaml().load(in);
            if (!(loaded instanceof Map)) {
                throw new IllegalStateException(path + " enthaelt keinen YAML-Baum.");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> tree = (Map<String, Object>) loaded;
            return tree;
        } catch (IOException e) {
            throw new IllegalStateException("Die Datei " + path + " konnte nicht gelesen werden.", e);
        }
    }

    /**
     * Navigiert durch einen Baum und liefert den Wert an der angegebenen Stelle.
     *
     * @param tree  der Baum
     * @param path  der Pfad im Baum
     * @return der Wert oder {@code null}, wenn der Pfad nicht existiert
     */
    static Object at(Map<String, Object> tree, String... path) {
        Object current = tree;
        for (String step : path) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(step);
        }
        return current;
    }

    /**
     * Navigiert durch einen Baum und liefert eine Liste.
     *
     * @param tree der Baum
     * @param path der Pfad im Baum
     * @return die Liste oder eine leere Liste, wenn der Pfad nicht existiert
     */
    static List<Object> listAt(Map<String, Object> tree, String... path) {
        Object value = at(tree, path);
        return value instanceof List<?> list ? List.copyOf(list) : List.of();
    }

    /**
     * Navigiert durch einen Baum und liefert eine Map.
     *
     * @param tree der Baum
     * @param path der Pfad im Baum
     * @return die Map oder eine leere Map, wenn der Pfad nicht existiert
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> mapAt(Map<String, Object> tree, String... path) {
        Object value = at(tree, path);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
