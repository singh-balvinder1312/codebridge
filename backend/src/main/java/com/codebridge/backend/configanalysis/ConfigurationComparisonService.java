
package com.codebridge.backend.configanalysis;

import com.codebridge.backend.dto.ConfigurationComparisonResponse;
import com.codebridge.backend.dto.ConfigurationIssue;
import com.codebridge.backend.github.GitHubConfigurationClient;
import com.codebridge.backend.github.GitHubRepository;
import com.codebridge.backend.github.GitHubUrlParser;

import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

@Service
public class ConfigurationComparisonService {

    private static final List<String> PATHS = List.of(
            GitHubConfigurationClient.RESOURCES_PATH,
            GitHubConfigurationClient.ROOT_PATH
    );

    // Deliberately compare only recognized, non-secret settings.
    // Configuration values are never returned in the API response.
    private static final Set<String> REVIEW_KEYS = Set.of(
            "server.port",
            "spring.main.web-application-type",
            "spring.jpa.hibernate.ddl-auto",
            "spring.flyway.enabled",
            "management.server.port"
    );

    private static final Set<String> INFORMATIONAL_KEYS = Set.of(
            "spring.application.name",
            "logging.level.root"
    );

    private final GitHubConfigurationClient githubClient;
    private final GitHubUrlParser urlParser = new GitHubUrlParser();

    private record PropertyLocation(
            String value,
            String filePath
    ) {
    }

    private record RepositoryConfiguration(
            List<String> filesFound,
            Map<String, PropertyLocation> properties
    ) {
    }

    public ConfigurationComparisonService(
            GitHubConfigurationClient githubClient) {
        this.githubClient = githubClient;
    }

    public ConfigurationComparisonResponse compare(
            String existingRepositoryUrl,
            String incomingRepositoryUrl) {

        // Validate both URLs before making any network requests.
        GitHubRepository existingRepository =
                urlParser.parse(existingRepositoryUrl);

        GitHubRepository incomingRepository =
                urlParser.parse(incomingRepositoryUrl);

        RepositoryConfiguration existing =
                loadConfiguration(existingRepositoryUrl);

        RepositoryConfiguration incoming =
                loadConfiguration(incomingRepositoryUrl);

        List<ConfigurationIssue> issues = new ArrayList<>();

        int comparedKeyCount = 0;
        int reviewCount = 0;
        int informationalCount = 0;

        for (Map.Entry<String, PropertyLocation> entry
                : existing.properties().entrySet()) {

            String key = entry.getKey();

            PropertyLocation incomingProperty =
                    incoming.properties().get(key);

            if (incomingProperty == null) {
                continue;
            }

            PropertyLocation existingProperty = entry.getValue();

            // Do not claim to know values that still contain
            // unresolved property placeholders.
            if (containsPlaceholder(existingProperty.value())
                    || containsPlaceholder(incomingProperty.value())) {
                continue;
            }

            comparedKeyCount++;

            if (existingProperty.value()
                    .equals(incomingProperty.value())) {
                continue;
            }

            String classification;
            String explanation;

            if (REVIEW_KEYS.contains(key)) {
                classification = "REVIEW";
                explanation = reviewExplanation(key);
                reviewCount++;
            } else {
                classification = "INFORMATIONAL";
                explanation =
                        "The repositories use different settings. "
                                + "This may be intentional and does not "
                                + "establish an integration conflict.";
                informationalCount++;
            }

            issues.add(new ConfigurationIssue(
                    key,
                    existingProperty.filePath(),
                    incomingProperty.filePath(),
                    classification,
                    explanation
            ));
        }

        return new ConfigurationComparisonResponse(
                repositoryName(existingRepository),
                repositoryName(incomingRepository),
                existing.filesFound(),
                incoming.filesFound(),
                comparedKeyCount,
                reviewCount,
                informationalCount,
                issues
        );
    }

    private RepositoryConfiguration loadConfiguration(
            String repositoryUrl) {

        List<String> filesFound = new ArrayList<>();

        Map<String, PropertyLocation> properties =
                new LinkedHashMap<>();

        // Resources configuration is considered before root
        // configuration as a conservative analysis convention.
        // This is not full Spring Boot property precedence.
        for (String path : PATHS) {

            Optional<String> content =
                    githubClient.getPropertiesFile(
                            repositoryUrl,
                            path
                    );

            if (content.isEmpty()) {
                continue;
            }

            filesFound.add(path);

            Properties parsed = new Properties();

            try {
                parsed.load(new StringReader(content.get()));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Invalid Java properties configuration file"
                );
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(
                        "Unable to parse configuration file",
                        exception
                );
            }

            for (String key : parsed.stringPropertyNames()) {

                if (!REVIEW_KEYS.contains(key)
                        && !INFORMATIONAL_KEYS.contains(key)) {
                    continue;
                }

                String value = parsed.getProperty(key);

                if (value == null) {
                    continue;
                }

                // Retain the first encountered location.
                properties.putIfAbsent(
                        key,
                        new PropertyLocation(value, path)
                );
            }
        }

        return new RepositoryConfiguration(
                List.copyOf(filesFound),
                Map.copyOf(properties)
        );
    }

    private boolean containsPlaceholder(String value) {
        return value.contains("${");
    }

    private String repositoryName(GitHubRepository repository) {
        return repository.owner()
                + "/"
                + repository.repository();
    }

    private String reviewExplanation(String key) {

        return switch (key) {
            case "server.port", "management.server.port" ->
                    "Different server ports may need reconciliation "
                            + "if both applications are combined into one process. "
                            + "Different ports can also be intentional.";

            case "spring.main.web-application-type" ->
                    "Different web application types may require "
                            + "architectural review before integration.";

            case "spring.jpa.hibernate.ddl-auto" ->
                    "Different Hibernate schema-management settings "
                            + "may affect database initialization or migrations.";

            case "spring.flyway.enabled" ->
                    "Different Flyway settings may affect how database "
                            + "migrations run after integration.";

            default ->
                    "Different configuration values require review "
                            + "before combining these applications.";
        };
    }
}
