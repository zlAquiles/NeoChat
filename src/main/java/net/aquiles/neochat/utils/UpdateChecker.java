package net.aquiles.neochat.utils;

import net.aquiles.neochat.NeoChat;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;

public class UpdateChecker {

    private static final String MODRINTH_API_BASE = "https://api.modrinth.com/v2";
    private static final String MODRINTH_PROJECT_ID = "sYzaQwLq";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final Pattern VERSION_OBJECT_PATTERN = Pattern.compile("\\{(.*?)\\}", Pattern.DOTALL);
    private static final Pattern VERSION_NUMBER_PATTERN = jsonFieldPattern("version_number");
    private static final Pattern VERSION_TYPE_PATTERN = jsonFieldPattern("version_type");
    private static final Pattern STATUS_PATTERN = jsonFieldPattern("status");
    private static final Pattern DATE_PUBLISHED_PATTERN = jsonFieldPattern("date_published");

    private final NeoChat plugin;

    public UpdateChecker(NeoChat plugin) {
        this.plugin = plugin;
    }

    public void getVersion(final Consumer<String> consumer) {
        plugin.runAsync(() -> {
            if (MODRINTH_PROJECT_ID.isBlank()) {
                plugin.getLogger().warning("Unable to search for updates: Modrinth project ID is missing.");
                return;
            }

            try {
                String encodedProjectId = URLEncoder.encode(MODRINTH_PROJECT_ID, StandardCharsets.UTF_8);
                String url = MODRINTH_API_BASE + "/project/" + encodedProjectId + "/version?include_changelog=false";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Accept", "application/json")
                        .header("User-Agent", "NeoChat/" + plugin.getDescription().getVersion() + " (Modrinth update checker)")
                        .GET()
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    plugin.getLogger().warning("Unable to search for updates: Modrinth API responded with HTTP " + response.statusCode() + ".");
                    return;
                }

                String latestVersion = findLatestReleaseVersion(response.body());
                if (latestVersion == null || latestVersion.isBlank()) {
                    plugin.getLogger().warning("Unable to search for updates: Modrinth returned no versions.");
                    return;
                }

                consumer.accept(latestVersion);
            } catch (Exception exception) {
                plugin.getLogger().warning("Unable to search for updates: " + exception.getMessage());
            }
        });
    }

    private String findLatestReleaseVersion(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        Instant latestDate = Instant.EPOCH;
        String latestVersion = null;

        Matcher objectMatcher = VERSION_OBJECT_PATTERN.matcher(responseBody);
        while (objectMatcher.find()) {
            String object = objectMatcher.group(1);

            String versionNumber = extractValue(VERSION_NUMBER_PATTERN, object);
            String versionType = extractValue(VERSION_TYPE_PATTERN, object);
            String status = extractValue(STATUS_PATTERN, object);
            Instant publishedAt = parseInstant(extractValue(DATE_PUBLISHED_PATTERN, object));

            if (versionNumber == null || versionNumber.isBlank()) {
                continue;
            }

            if (!"release".equalsIgnoreCase(versionType) || !"listed".equalsIgnoreCase(status)) {
                continue;
            }

            if (publishedAt.isAfter(latestDate)) {
                latestDate = publishedAt;
                latestVersion = versionNumber;
            }
        }

        return latestVersion;
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }

        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return Instant.EPOCH;
        }
    }

    private static String extractValue(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? unescapeJson(matcher.group(1)) : null;
    }

    private static Pattern jsonFieldPattern(String fieldName) {
        return Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    }

    private static String unescapeJson(String value) {
        if (value == null || value.indexOf('\\') == -1) {
            return value;
        }

        return value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\/", "/")
                .replace("\\b", "\b")
                .replace("\\f", "\f")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }
}
