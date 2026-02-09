package co.fanki.datadog.traceinspector.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Stores and retrieves filter configuration for stack trace filtering.
 *
 * <p>Supports both global filters and per-project filters. Configuration is
 * persisted to {@code ~/.claude/mcp/waabox-mcp-server/filter-config.json}.</p>
 *
 * <p>Filter resolution order:
 * <ol>
 *   <li>If project has explicit "no filter" setting, returns empty list</li>
 *   <li>If project has specific filters, returns those</li>
 *   <li>Otherwise returns global filters</li>
 * </ol>
 * </p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class FilterConfigStore {

    private static final String CONFIG_DIR = ".claude/mcp/waabox-mcp-server";
    private static final String CONFIG_FILE = "filter-config.json";

    private final Path configPath;
    private final ObjectMapper objectMapper;

    private FilterConfig currentConfig;

    /**
     * Creates a FilterConfigStore using the default location.
     */
    public FilterConfigStore() {
        this(resolveDefaultConfigPath());
    }

    /**
     * Creates a FilterConfigStore with a custom config path.
     *
     * @param configPath the path to the configuration file
     */
    public FilterConfigStore(final Path configPath) {
        this.configPath = Objects.requireNonNull(configPath, "configPath must not be null");
        this.objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
        this.currentConfig = load();
    }

    /**
     * Checks if the configuration has been initialized.
     *
     * <p>Returns false if no configuration exists, meaning Claude should
     * ask the user how they want to configure filters.</p>
     *
     * @return true if configuration exists, false otherwise
     */
    public boolean isConfigured() {
        return Files.exists(configPath);
    }

    /**
     * Checks if filters are configured for a specific project.
     *
     * @param projectName the project name
     *
     * @return true if the project has explicit configuration (filters or no-filter)
     */
    public boolean isProjectConfigured(final String projectName) {
        if (projectName == null || projectName.isBlank()) {
            return false;
        }
        return currentConfig.noFilterProjects().contains(projectName)
                || currentConfig.projectPackages().containsKey(projectName);
    }

    /**
     * Returns the relevant packages for a specific project.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>If project has "no filter" setting, returns empty list</li>
     *   <li>If project has specific filters, returns those</li>
     *   <li>Otherwise returns global filters</li>
     * </ol>
     * </p>
     *
     * @param projectName the project name (can be null for global only)
     *
     * @return an immutable list of package prefixes
     */
    public List<String> getRelevantPackages(final String projectName) {
        if (projectName != null && !projectName.isBlank()) {
            // Check if project explicitly wants no filters
            if (currentConfig.noFilterProjects().contains(projectName)) {
                return Collections.emptyList();
            }

            // Check for project-specific filters
            final List<String> projectPackages = currentConfig.projectPackages().get(projectName);
            if (projectPackages != null && !projectPackages.isEmpty()) {
                return projectPackages;
            }
        }

        // Fall back to global filters
        return currentConfig.globalPackages();
    }

    /**
     * Returns the relevant packages using global configuration only.
     *
     * @return an immutable list of global package prefixes
     */
    public List<String> getRelevantPackages() {
        return currentConfig.globalPackages();
    }

    /**
     * Returns the global package filters.
     *
     * @return an immutable list of global package prefixes
     */
    public List<String> getGlobalPackages() {
        return currentConfig.globalPackages();
    }

    /**
     * Sets the global package filters.
     *
     * @param packages the list of package prefixes
     */
    public void setGlobalPackages(final List<String> packages) {
        Objects.requireNonNull(packages, "packages must not be null");
        this.currentConfig = new FilterConfig(
                List.copyOf(packages),
                currentConfig.projectPackages(),
                currentConfig.noFilterProjects()
        );
        save();
    }

    /**
     * Returns the package filters for a specific project.
     *
     * @param projectName the project name
     *
     * @return an immutable list of package prefixes, or empty if not configured
     */
    public List<String> getProjectPackages(final String projectName) {
        Objects.requireNonNull(projectName, "projectName must not be null");
        return currentConfig.projectPackages().getOrDefault(projectName, Collections.emptyList());
    }

    /**
     * Sets the package filters for a specific project.
     *
     * @param projectName the project name
     * @param packages the list of package prefixes
     */
    public void setProjectPackages(final String projectName, final List<String> packages) {
        Objects.requireNonNull(projectName, "projectName must not be null");
        Objects.requireNonNull(packages, "packages must not be null");

        final Map<String, List<String>> updated = new HashMap<>(currentConfig.projectPackages());
        updated.put(projectName, List.copyOf(packages));

        // Remove from noFilterProjects if setting packages
        final Set<String> noFilter = new HashSet<>(currentConfig.noFilterProjects());
        noFilter.remove(projectName);

        this.currentConfig = new FilterConfig(
                currentConfig.globalPackages(),
                updated,
                noFilter
        );
        save();
    }

    /**
     * Marks a project as explicitly not wanting any filters.
     *
     * @param projectName the project name
     */
    public void setProjectNoFilter(final String projectName) {
        Objects.requireNonNull(projectName, "projectName must not be null");

        // Remove from project packages
        final Map<String, List<String>> packages = new HashMap<>(currentConfig.projectPackages());
        packages.remove(projectName);

        // Add to no filter list
        final Set<String> noFilter = new HashSet<>(currentConfig.noFilterProjects());
        noFilter.add(projectName);

        this.currentConfig = new FilterConfig(
                currentConfig.globalPackages(),
                packages,
                noFilter
        );
        save();
    }

    /**
     * Removes all configuration for a specific project.
     *
     * @param projectName the project name
     */
    public void clearProjectConfig(final String projectName) {
        Objects.requireNonNull(projectName, "projectName must not be null");

        final Map<String, List<String>> packages = new HashMap<>(currentConfig.projectPackages());
        packages.remove(projectName);

        final Set<String> noFilter = new HashSet<>(currentConfig.noFilterProjects());
        noFilter.remove(projectName);

        this.currentConfig = new FilterConfig(
                currentConfig.globalPackages(),
                packages,
                noFilter
        );
        save();
    }

    /**
     * Returns all configured project names.
     *
     * @return a set of project names with explicit configuration
     */
    public Set<String> getConfiguredProjects() {
        final Set<String> projects = new HashSet<>();
        projects.addAll(currentConfig.projectPackages().keySet());
        projects.addAll(currentConfig.noFilterProjects());
        return projects;
    }

    /**
     * Returns the full current configuration.
     *
     * @return the current filter configuration
     */
    public FilterConfig getConfig() {
        return currentConfig;
    }

    /**
     * Returns the path to the configuration file.
     *
     * @return the configuration file path
     */
    public Path getConfigPath() {
        return configPath;
    }

    /**
     * Detects the current project name from the working directory.
     *
     * <p>Tries to get the git repository name first, falls back to directory name.</p>
     *
     * @return the detected project name, or null if detection fails
     */
    public static String detectCurrentProject() {
        // Try git remote name first
        try {
            final ProcessBuilder pb = new ProcessBuilder(
                    "git", "remote", "get-url", "origin"
            );
            pb.redirectErrorStream(true);
            final Process process = pb.start();

            try (final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                final String url = reader.readLine();
                if (url != null && !url.isBlank()) {
                    return extractRepoName(url);
                }
            }
        } catch (final IOException ignored) {
            // Git not available or not a git repo
        }

        // Fall back to current directory name
        try {
            return Path.of(System.getProperty("user.dir")).getFileName().toString();
        } catch (final Exception e) {
            return null;
        }
    }

    private static String extractRepoName(final String gitUrl) {
        String name = gitUrl.trim();

        // Remove .git suffix
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }

        // Extract repo name from URL
        final int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }

        // Handle SSH format (git@github.com:user/repo)
        final int colon = name.lastIndexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }

        return name.isBlank() ? null : name;
    }

    private FilterConfig load() {
        if (!Files.exists(configPath)) {
            return FilterConfig.empty();
        }

        try {
            return objectMapper.readValue(configPath.toFile(), FilterConfig.class);
        } catch (final IOException e) {
            System.err.println("Warning: Could not load filter config from "
                    + configPath + ": " + e.getMessage());
            return FilterConfig.empty();
        }
    }

    private void save() {
        try {
            Files.createDirectories(configPath.getParent());
            objectMapper.writeValue(configPath.toFile(), currentConfig);
        } catch (final IOException e) {
            System.err.println("Warning: Could not save filter config to "
                    + configPath + ": " + e.getMessage());
        }
    }

    private static Path resolveDefaultConfigPath() {
        final String userHome = System.getProperty("user.home");
        return Path.of(userHome, CONFIG_DIR, CONFIG_FILE);
    }

    /**
     * Immutable configuration record for filter settings.
     */
    public record FilterConfig(
            @JsonProperty("globalPackages") List<String> globalPackages,
            @JsonProperty("projectPackages") Map<String, List<String>> projectPackages,
            @JsonProperty("noFilterProjects") Set<String> noFilterProjects
    ) {
        /**
         * Creates a FilterConfig with validated parameters.
         */
        public FilterConfig {
            globalPackages = globalPackages != null
                    ? List.copyOf(globalPackages)
                    : Collections.emptyList();

            if (projectPackages != null) {
                final Map<String, List<String>> copy = new HashMap<>();
                projectPackages.forEach((k, v) -> copy.put(k, List.copyOf(v)));
                projectPackages = Map.copyOf(copy);
            } else {
                projectPackages = Collections.emptyMap();
            }

            noFilterProjects = noFilterProjects != null
                    ? Set.copyOf(noFilterProjects)
                    : Collections.emptySet();
        }

        /**
         * Creates an empty filter configuration.
         *
         * @return a FilterConfig with no settings
         */
        public static FilterConfig empty() {
            return new FilterConfig(
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    Collections.emptySet()
            );
        }
    }
}
