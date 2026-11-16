
package com.codebridge.backend.codeanalysis;

import com.codebridge.backend.dto.JavaClassCollision;
import com.codebridge.backend.dto.JavaCodeComparisonResponse;
import com.codebridge.backend.dto.JavaSourceCollision;
import com.codebridge.backend.dto.SpringRouteCollision;
import com.codebridge.backend.github.GitHubJavaSourceClient;
import com.codebridge.backend.github.GitHubJavaTree;
import com.codebridge.backend.github.GitHubJavaTreeClient;
import com.codebridge.backend.github.GitHubUrlParser;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JavaCodeComparisonService {

    private static final int MAX_CONTROLLERS_PER_REPOSITORY = 8;

    private static final Pattern CLASS_MAPPING = Pattern.compile(
            "@RequestMapping\\s*\\(\\s*"
                    + "(?:(?:value|path)\\s*=\\s*)?"
                    + "\"([^\"\\r\\n]+)\""
    );

    private static final Pattern METHOD_MAPPING = Pattern.compile(
            "@(Get|Post|Put|Delete|Patch)Mapping\\s*"
                    + "\\(\\s*"
                    + "(?:"
                    + "(?:(?:value|path)\\s*=\\s*)?"
                    + "\"([^\"\\r\\n]+)\"[^)]*"
                    + ")?"
                    + "\\)"
    );

    private record RouteKey(
            String method,
            String path
    ) {
    }

    private record RouteScan(
            Map<RouteKey, String> routes,
            int scannedCount,
            boolean complete
    ) {
    }

    private final GitHubJavaTreeClient treeClient;
    private final GitHubJavaSourceClient sourceClient;

    private final GitHubUrlParser urlParser = new GitHubUrlParser();

    @Autowired
    public JavaCodeComparisonService(
            GitHubJavaTreeClient treeClient,
            GitHubJavaSourceClient sourceClient
    ) {
        this.treeClient = treeClient;
        this.sourceClient = sourceClient;
    }

    // Preserve compatibility with older unit tests.
    // Route scanning is unavailable through this constructor.
    public JavaCodeComparisonService(
            GitHubJavaTreeClient treeClient
    ) {
        this.treeClient = treeClient;
        this.sourceClient = null;
    }

    public JavaCodeComparisonResponse compare(
            String existingRepositoryUrl,
            String incomingRepositoryUrl
    ) {

        // Validate both before making network requests.
        urlParser.parse(existingRepositoryUrl);
        urlParser.parse(incomingRepositoryUrl);

        GitHubJavaTree existing =
                treeClient.getJavaTree(existingRepositoryUrl);

        GitHubJavaTree incoming =
                treeClient.getJavaTree(incomingRepositoryUrl);

        Map<String, String> existingFiles = existing.javaFiles();
        Map<String, String> incomingFiles = incoming.javaFiles();

        List<JavaSourceCollision> pathCollisions =
                new ArrayList<>();

        int matchingPathCount = 0;

        for (String path : new TreeSet<>(existingFiles.keySet())) {

            String incomingSha = incomingFiles.get(path);

            if (incomingSha == null) {
                continue;
            }

            matchingPathCount++;

            if (!existingFiles.get(path)
                    .equalsIgnoreCase(incomingSha)) {

                pathCollisions.add(new JavaSourceCollision(
                        path,
                        "REVIEW",
                        "Different Java source files share the same "
                                + "repository-relative path. This may "
                                + "require reconciliation when merging "
                                + "source trees."
                ));
            }
        }

        List<JavaClassCollision> classCollisions =
                compareInferredClasses(existingFiles, incomingFiles);

        RouteScan existingRoutes = scanRoutes(
                existingRepositoryUrl,
                existingFiles
        );

        RouteScan incomingRoutes = scanRoutes(
                incomingRepositoryUrl,
                incomingFiles
        );

        List<SpringRouteCollision> routeCollisions =
                compareRoutes(existingRoutes, incomingRoutes);

        int totalCollisions =
                pathCollisions.size()
                        + classCollisions.size()
                        + routeCollisions.size();

        return new JavaCodeComparisonResponse(
                existing.repository(),
                incoming.repository(),
                existingFiles.size(),
                incomingFiles.size(),
                matchingPathCount,
                totalCollisions,
                pathCollisions,
                classCollisions,
                routeCollisions,
                existingRoutes.scannedCount(),
                incomingRoutes.scannedCount(),
                existingRoutes.complete()
                        && incomingRoutes.complete()
        );
    }

    private List<JavaClassCollision> compareInferredClasses(
            Map<String, String> existingFiles,
            Map<String, String> incomingFiles
    ) {

        Map<String, String> existingClasses =
                indexInferredClasses(existingFiles);

        Map<String, String> incomingClasses =
                indexInferredClasses(incomingFiles);

        List<JavaClassCollision> collisions =
                new ArrayList<>();

        for (String className :
                new TreeSet<>(existingClasses.keySet())) {

            String existingPath = existingClasses.get(className);
            String incomingPath = incomingClasses.get(className);

            if (incomingPath == null) {
                continue;
            }

            // The original path analysis already handles
            // files occupying exactly the same relative path.
            if (existingPath.equals(incomingPath)) {
                continue;
            }

            collisions.add(new JavaClassCollision(
                    className,
                    existingPath,
                    incomingPath,
                    "The source paths suggest the same fully qualified "
                            + "Java class exists in both repositories. "
                            + "Verify package declarations before "
                            + "combining the projects."
            ));
        }

        return List.copyOf(collisions);
    }

    private Map<String, String> indexInferredClasses(
            Map<String, String> files
    ) {

        Map<String, String> classes = new TreeMap<>();

        for (String path : new TreeSet<>(files.keySet())) {

            String inferredClassName =
                    inferClassName(path);

            if (inferredClassName != null) {
                classes.putIfAbsent(
                        inferredClassName,
                        path
                );
            }
        }

        return classes;
    }

    private String inferClassName(String path) {

        String marker = "src/main/java/";

        int position = path.indexOf(marker);

        if (position < 0 || !path.endsWith(".java")) {
            return null;
        }

        String relativeName =
                path.substring(position + marker.length());

        if (relativeName.equals("module-info.java")
                || relativeName.endsWith("/package-info.java")
                || relativeName.equals("package-info.java")) {
            return null;
        }

        return relativeName
                .substring(0, relativeName.length() - 5)
                .replace('/', '.');
    }

    private RouteScan scanRoutes(
            String repositoryUrl,
            Map<String, String> files
    ) {

        if (sourceClient == null) {
            return new RouteScan(Map.of(), 0, false);
        }

        List<String> candidates = new ArrayList<>();

        for (String path : new TreeSet<>(files.keySet())) {
            if (path.endsWith("Controller.java")
                    || path.endsWith("Resource.java")) {
                candidates.add(path);
            }
        }

        boolean complete =
                candidates.size() <= MAX_CONTROLLERS_PER_REPOSITORY;

        Map<RouteKey, String> routes =
                new LinkedHashMap<>();

        int scanned = 0;

        for (String path : candidates) {

            if (scanned >= MAX_CONTROLLERS_PER_REPOSITORY) {
                break;
            }

            String source = sourceClient.readJavaBlob(
                    repositoryUrl,
                    files.get(path)
            );

            scanned++;

            extractRoutes(source, path, routes);
        }

        return new RouteScan(
                Map.copyOf(routes),
                scanned,
                complete
        );
    }

    private void extractRoutes(
            String source,
            String filePath,
            Map<RouteKey, String> routes
    ) {

        if (!source.contains("@RestController")) {
            return;
        }

        int classPosition = source.indexOf("class ");

        String classAnnotations = classPosition >= 0
                ? source.substring(0, classPosition)
                : "";

        Matcher prefixMatcher =
                CLASS_MAPPING.matcher(classAnnotations);

        String prefix = "";

        if (prefixMatcher.find()) {
            prefix = prefixMatcher.group(1);
        }

        Matcher matcher = METHOD_MAPPING.matcher(source);

        while (matcher.find()) {

            String method =
                    matcher.group(1).toUpperCase();

            String endpoint = matcher.group(2);

            // @GetMapping() and similar mappings use
            // an empty method-level path.
            if (endpoint == null) {
                endpoint = "";
            }

            // Ignore dynamic paths or unresolved placeholders.
            if (prefix.contains("${")
                    || endpoint.contains("${")) {
                continue;
            }

            RouteKey key = new RouteKey(
                    method,
                    normalizePath(prefix, endpoint)
            );

            routes.putIfAbsent(key, filePath);
        }
    }

    private String normalizePath(
            String prefix,
            String endpoint
    ) {

        String path = "/" + prefix + "/" + endpoint;

        path = path.replaceAll("/+", "/");

        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return path;
    }

    private List<SpringRouteCollision> compareRoutes(
            RouteScan existing,
            RouteScan incoming
    ) {

        List<SpringRouteCollision> collisions =
                new ArrayList<>();

        List<RouteKey> sortedKeys = new ArrayList<>(
                existing.routes().keySet()
        );

        sortedKeys.sort(
                java.util.Comparator
                        .comparing(RouteKey::method)
                        .thenComparing(RouteKey::path)
        );

        for (RouteKey key : sortedKeys) {

            String incomingFile =
                    incoming.routes().get(key);

            if (incomingFile == null) {
                continue;
            }

            collisions.add(new SpringRouteCollision(
                    key.method(),
                    key.path(),
                    existing.routes().get(key),
                    incomingFile,
                    "Both repositories appear to declare the same "
                            + "HTTP method and Spring route. Review "
                            + "controller mappings before combining "
                            + "them into one application."
            ));
        }

        return List.copyOf(collisions);
    }
}
