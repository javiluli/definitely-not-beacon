package dev.hytalemodding.beacon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Comprueba los catálogos sin arrancar Hytale; la fixture nativa valida su formato de mensajes. */
class BeaconTranslationsTest {
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void bothLanguagesProvideTheSameKeysAndParameters() throws IOException {
        Properties english = catalog("en-US"), spanish = catalog("es-ES");
        assertFalse(english.isEmpty());
        assertEquals(english.stringPropertyNames(), spanish.stringPropertyNames());
        for (String key : english.stringPropertyNames()) {
            assertFalse(english.getProperty(key).isBlank(), key);
            assertFalse(spanish.getProperty(key).isBlank(), key);
            assertEquals(matches(english.getProperty(key), "\\{(\\w+)\\}"),
                    matches(spanish.getProperty(key), "\\{(\\w+)\\}"), key);
        }
        for (BeaconPower power : BeaconPower.values()) {
            String key = "power." + power.name().replace("_", "");
            assertNotNull(english.getProperty(key + ".name"), key);
            assertTrue(english.getProperty(key + ".description").contains("{amount}"), key);
        }
    }

    @Test
    void bundledUiAndAssetsReferenceExistingTranslations() throws IOException {
        Set<String> references = new HashSet<>();
        try (var files = Files.walk(RESOURCES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".ui") || path.toString().endsWith(".json")).toList()) {
                references.addAll(matches(Files.readString(file), "[%\"](beacon\\.[A-Za-z0-9_.]+)"));
            }
        }
        assertFalse(references.isEmpty());
        for (String locale : new String[]{"en-US", "es-ES"}) {
            Properties translations = catalog(locale);
            for (String reference : references) {
                assertNotNull(translations.getProperty(reference.substring("beacon.".length())), locale + ": " + reference);
            }
        }
    }

    @Test
    void tooltipReductionsUseTheMinusSupportedByTheClientFont() throws IOException {
        Properties spanish = catalog("es-ES");
        assertEquals("+5% de resistencia física y de proyectiles; +9 de salud máxima",
                spanish.getProperty("power.RESISTANCE.description").replace("{amount}", "5").replace("{health}", "9"));
        String jump = spanish.getProperty("power.JUMPBOOST.description");
        assertTrue(jump.replace("{fall}", "20").contains("-20% de daño por caída"));
        assertTrue(jump.replace("{fall}", "40").contains("-40% de daño por caída"));
    }

    private Properties catalog(String locale) throws IOException {
        Properties catalog = new Properties();
        try (var reader = Files.newBufferedReader(RESOURCES.resolve("Server/Languages/" + locale + "/beacon.lang"), StandardCharsets.UTF_8)) {
            catalog.load(reader);
        }
        return catalog;
    }

    private Set<String> matches(String text, String expression) {
        Set<String> matches = new HashSet<>();
        var matcher = Pattern.compile(expression).matcher(text);
        while (matcher.find()) matches.add(matcher.group(1));
        return matches;
    }
}
