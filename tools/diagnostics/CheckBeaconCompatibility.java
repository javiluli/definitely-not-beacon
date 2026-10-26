import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.common.semver.SemverRange;
import com.hypixel.hytale.codec.ExtraInfo;
import org.bson.BsonDocument;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.jar.JarFile;

/** Comprueba los manifiestos con el parser nativo; se ejecuta desde la raíz, fuera del JAR del mod. */
class CheckBeaconCompatibility {
    public static void main(String[] args) throws Exception {
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("gradle.properties"))) {
            properties.load(reader);
        }
        BsonDocument source = BsonDocument.parse(Files.readString(Path.of("src/main/resources/manifest.json")));
        String declared = source.getString("ServerVersion").getValue();
        require("=0.7.4".equals(declared), "Unexpected support decision: " + declared);
        require(declared.equals(properties.getProperty("manifestServerVersion")), "Gradle/manifest mismatch");
        require(source.getString("Group").getValue().equals(properties.getProperty("manifest_group")), "Manifest group mismatch");
        require(source.getString("Name").getValue().equals(properties.getProperty("mod_name")), "Manifest display name mismatch");
        require(source.getString("Version").getValue().equals(properties.getProperty("version")), "Manifest version mismatch");
        require(source.getString("Description").getValue().equals(properties.getProperty("mod_description")), "Manifest description mismatch");
        require(source.getString("Main").getValue().equals(properties.getProperty("main_class")), "Manifest entry point mismatch");
        require(source.getString("Description").getValue().contains(properties.getProperty("release_label")), "Public release label missing");
        var manifest = PluginManifest.CODEC.decode(source, new ExtraInfo());
        require(manifest.getVersion().compareTo(Semver.fromString(properties.getProperty("version"))) == 0, "Native plugin version differs");
        String identity = properties.getProperty("manifest_group") + ":" + properties.getProperty("mod_name");
        require(new PluginIdentifier(manifest).equals(PluginIdentifier.fromString(identity)), "Native plugin identity differs");
        Path pluginJar = args.length == 0 ? Path.of("build/libs/" + properties.getProperty("mod_id")
                + "-ht" + properties.getProperty("hytale_version") + "-" + properties.getProperty("version") + ".jar") : Path.of(args[0]);
        try (var jar = new JarFile(pluginJar.toFile());
             var stream = jar.getInputStream(jar.getJarEntry("manifest.json"))) {
            var bundled = BsonDocument.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            require(source.equals(bundled), "Packaged manifest differs from source");
            var iconEntry = jar.getJarEntry("icon-256.png");
            require(iconEntry != null, "Native mod-list icon missing from JAR root");
            try (var iconStream = jar.getInputStream(iconEntry)) {
                var icon = javax.imageio.ImageIO.read(iconStream);
                require(icon != null && icon.getWidth() == 256 && icon.getHeight() == 256, "Mod-list icon must be a 256x256 PNG");
            }
            require(jar.getEntry(properties.getProperty("main_class").replace('.', '/') + ".class") != null, "Plugin entry point missing");
            require(jar.getEntry("dev/hytalemodding/BeaconPlugin.class") == null, "Old plugin entry point still packaged");
            require(jar.stream().noneMatch(entry -> entry.getName().matches(".*Beacon(SmokePlugin|ProfilePlugin|PerformanceProbe|HasteAssetsChecks|PaymentChecks|TextChecks).*\\.class")),
                    "Integration classes must not be packaged");
        }
        var range = SemverRange.fromString(declared);
        require(range.satisfies(Semver.fromString("0.7.4")), "Tested version rejected");
        for (String version : new String[]{"0.7.3", "0.7.5", "0.8.0", "0.7.4-dev"}) {
            require(!range.satisfies(Semver.fromString(version)), "Untested version accepted: " + version);
            require(PluginManifest.checkServerVersionCompatibility(range, version) == PluginManifest.ServerVersionCheck.INCOMPATIBLE,
                    "Native manifest compatibility check accepted " + version);
        }
        System.out.println("PASS: " + manifest.getName() + " " + properties.getProperty("release_label")
                + " (" + manifest.getVersion() + "); Gradle/source/JAR metadata and native identity agree; =0.7.4 compatibility and production packaging verified.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
