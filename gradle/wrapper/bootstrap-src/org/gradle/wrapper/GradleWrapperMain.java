package org.gradle.wrapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Small JDK-17-compatible bootstrap used only when the official Gradle wrapper
 * JAR cannot be bundled by the artifact-producing environment. GitHub Actions
 * replaces this JAR with Gradle's official, checksum-verified wrapper before
 * invoking ./gradlew.
 */
public final class GradleWrapperMain {
    private GradleWrapperMain() {}

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path propsPath = root.resolve("gradle/wrapper/gradle-wrapper.properties");
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(propsPath)) {
            props.load(in);
        }

        String distributionUrl = require(props, "distributionUrl").replace("\\:", ":");
        String expectedSha = props.getProperty("distributionSha256Sum", "").trim();
        String fileName = distributionUrl.substring(distributionUrl.lastIndexOf('/') + 1);
        String version = fileName.replace("gradle-", "")
                .replace("-bin.zip", "")
                .replace("-all.zip", "");

        String home = System.getenv().getOrDefault(
                "GRADLE_USER_HOME",
                Paths.get(System.getProperty("user.home"), ".gradle").toString()
        );
        Path distRoot = Paths.get(home, "wrapper", "dists", "evolune-" + version);
        Path gradleHome = distRoot.resolve("gradle-" + version);
        Path executable = gradleHome.resolve("bin").resolve(isWindows() ? "gradle.bat" : "gradle");

        if (!Files.exists(executable)) {
            Files.createDirectories(distRoot);
            Path archive = distRoot.resolve(fileName);
            Path temp = distRoot.resolve(fileName + ".part");
            Files.deleteIfExists(temp);

            System.out.println("Downloading " + distributionUrl);
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(distributionUrl)).GET().build();
            HttpResponse<Path> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofFile(temp)
            );
            if (response.statusCode() / 100 != 2) {
                Files.deleteIfExists(temp);
                throw new IOException("Gradle download failed with HTTP " + response.statusCode());
            }
            Files.move(temp, archive, StandardCopyOption.REPLACE_EXISTING);

            if (!expectedSha.isEmpty()) {
                String actual = sha256(archive);
                if (!actual.equalsIgnoreCase(expectedSha)) {
                    Files.deleteIfExists(archive);
                    throw new SecurityException(
                            "Gradle distribution checksum mismatch: expected " + expectedSha + ", got " + actual
                    );
                }
            }

            unzip(archive, distRoot);
            if (!isWindows()) {
                executable.toFile().setExecutable(true, false);
            }
        }

        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(Arrays.asList(args));
        int exit = new ProcessBuilder(command)
                .directory(root.toFile())
                .inheritIO()
                .start()
                .waitFor();
        System.exit(exit);
    }

    private static String require(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing " + key + " in gradle-wrapper.properties");
        }
        return value;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder result = new StringBuilder(64);
        for (byte b : digest.digest()) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private static void unzip(Path archive, Path destination) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path output = destination.resolve(entry.getName()).normalize();
                if (!output.startsWith(destination.normalize())) {
                    throw new IOException("Unsafe ZIP entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Path parent = output.getParent();
                    if (parent != null) Files.createDirectories(parent);
                    Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }
}
