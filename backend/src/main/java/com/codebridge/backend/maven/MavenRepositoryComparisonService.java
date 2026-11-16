
package com.codebridge.backend.maven;

import com.codebridge.backend.dto.MavenComparisonResponse;
import com.codebridge.backend.exception.GitHubApiException;
import com.codebridge.backend.github.GitHubPomClient;
import com.codebridge.backend.github.GitHubRepository;
import com.codebridge.backend.github.GitHubUrlParser;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class MavenRepositoryComparisonService {

    private final GitHubPomClient githubPomClient;
    private final MavenPomParser mavenPomParser;
    private final MavenDependencyComparisonService comparisonService;

    private final GitHubUrlParser urlParser = new GitHubUrlParser();

    public MavenRepositoryComparisonService(
            GitHubPomClient githubPomClient,
            MavenPomParser mavenPomParser,
            MavenDependencyComparisonService comparisonService) {

        this.githubPomClient = githubPomClient;
        this.mavenPomParser = mavenPomParser;
        this.comparisonService = comparisonService;
    }

    // Strict analysis used by POST /api/maven/compare.
    // Missing root POMs still produce an error.
    public MavenComparisonResponse analyzeRepositories(
            String existingRepositoryUrl,
            String incomingRepositoryUrl) {

        validateUrls(existingRepositoryUrl, incomingRepositoryUrl);

        GitHubRepository existingRepository =
                urlParser.parse(existingRepositoryUrl);

        GitHubRepository incomingRepository =
                urlParser.parse(incomingRepositoryUrl);

        MavenPomFile existingPom =
                githubPomClient.getRootPom(existingRepositoryUrl);

        MavenPomFile incomingPom =
                githubPomClient.getRootPom(incomingRepositoryUrl);

        List<MavenDependency> existingDependencies =
                mavenPomParser.parseDependencies(
                        existingPom.content()
                );

        List<MavenDependency> incomingDependencies =
                mavenPomParser.parseDependencies(
                        incomingPom.content()
                );

        return buildResponse(
                existingRepository,
                incomingRepository,
                existingDependencies,
                incomingDependencies
        );
    }

    // Flexible analysis used by the combined compatibility API.
    // Only GitHub NOT_FOUND is treated as missing POM data.
    // Rate limits, upstream errors, and malformed POMs still fail.
    public MavenAnalysisResult analyzeForCompatibility(
            String existingRepositoryUrl,
            String incomingRepositoryUrl) {

        validateUrls(existingRepositoryUrl, incomingRepositoryUrl);

        GitHubRepository existingRepository =
                urlParser.parse(existingRepositoryUrl);

        GitHubRepository incomingRepository =
                urlParser.parse(incomingRepositoryUrl);

        Optional<MavenPomFile> existingPom =
                findOptionalRootPom(existingRepositoryUrl);

        Optional<MavenPomFile> incomingPom =
                findOptionalRootPom(incomingRepositoryUrl);

        List<MavenDependency> existingDependencies =
                existingPom.isPresent()
                        ? mavenPomParser.parseDependencies(
                        existingPom.get().content()
                )
                        : List.of();

        List<MavenDependency> incomingDependencies =
                incomingPom.isPresent()
                        ? mavenPomParser.parseDependencies(
                        incomingPom.get().content()
                )
                        : List.of();

        MavenComparisonResponse response = buildResponse(
                existingRepository,
                incomingRepository,
                existingDependencies,
                incomingDependencies
        );

        return new MavenAnalysisResult(
                response,
                existingPom.isPresent(),
                incomingPom.isPresent()
        );
    }

    // Preserve the method used by earlier code and tests.
    public List<MavenDependencyConflict> compareRepositories(
            String existingRepositoryUrl,
            String incomingRepositoryUrl) {

        return analyzeRepositories(
                existingRepositoryUrl,
                incomingRepositoryUrl
        ).conflicts();
    }

    private Optional<MavenPomFile> findOptionalRootPom(
            String repositoryUrl) {

        try {
            return Optional.of(
                    githubPomClient.getRootPom(repositoryUrl)
            );

        } catch (GitHubApiException exception) {

            if (exception.getReason()
                    == GitHubApiException.Reason.NOT_FOUND) {
                return Optional.empty();
            }

            // Do not disguise rate limits or upstream failures
            // as missing Maven files.
            throw exception;
        }
    }

    private MavenComparisonResponse buildResponse(
            GitHubRepository existingRepository,
            GitHubRepository incomingRepository,
            List<MavenDependency> existingDependencies,
            List<MavenDependency> incomingDependencies) {

        List<MavenDependencyConflict> conflicts =
                comparisonService.compare(
                        existingDependencies,
                        incomingDependencies
                );

        return new MavenComparisonResponse(
                repositoryName(existingRepository),
                repositoryName(incomingRepository),
                existingDependencies.size(),
                incomingDependencies.size(),
                conflicts.size(),
                conflicts
        );
    }

    private String repositoryName(GitHubRepository repository) {
        return repository.owner()
                + "/"
                + repository.repository();
    }

    private void validateUrls(
            String existingRepositoryUrl,
            String incomingRepositoryUrl) {

        Objects.requireNonNull(
                existingRepositoryUrl,
                "Existing repository URL is required"
        );

        Objects.requireNonNull(
                incomingRepositoryUrl,
                "Incoming repository URL is required"
        );

        // Validate both before any network request occurs.
        urlParser.parse(existingRepositoryUrl);
        urlParser.parse(incomingRepositoryUrl);
    }
}
