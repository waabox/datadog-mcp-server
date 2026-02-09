package co.fanki.datadog.traceinspector.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Stores and retrieves filter configuration for stack trace filtering.
 *
 * <p>Configuration is persisted to {@code ~/.claude/mcp/waabox-mcp-server/filter-config.json}
 * to preserve user preferences across sessions.</p>
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
     *
     * <p>The default location is {@code ~/.claude/mcp/waabox-mcp-server/filter-config.json}</p>
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
     * Returns the configured relevant packages for stack trace filtering.
     *
     * @return an immutable list of package prefixes, never null
     */
    public List<String> getRelevantPackages() {
        return currentConfig.relevantPackages();
    }

    /**
     * Updates the relevant packages configuration.
     *
     * @param packages the list of package prefixes to keep in stack traces
     */
    public void setRelevantPackages(final List<String> packages) {
        Objects.requireNonNull(packages, "packages must not be null");
        this.currentConfig = new FilterConfig(List.copyOf(packages));
        save();
    }

    /**
     * Adds a package prefix to the relevant packages list if not already present.
     *
     * @param packagePrefix the package prefix to add (e.g., "co.fanki")
     *
     * @return true if the package was added, false if it was already present
     */
    public boolean addRelevantPackage(final String packagePrefix) {
        Objects.requireNonNull(packagePrefix, "packagePrefix must not be null");
        if (packagePrefix.isBlank()) {
            return false;
        }

        final List<String> current = currentConfig.relevantPackages();
        if (current.contains(packagePrefix)) {
            return false;
        }

        final var updated = new java.util.ArrayList<>(current);
        updated.add(packagePrefix);
        setRelevantPackages(updated);
        return true;
    }

    /**
     * Removes a package prefix from the relevant packages list.
     *
     * @param packagePrefix the package prefix to remove
     *
     * @return true if the package was removed, false if it was not present
     */
    public boolean removeRelevantPackage(final String packagePrefix) {
        Objects.requireNonNull(packagePrefix, "packagePrefix must not be null");

        final List<String> current = currentConfig.relevantPackages();
        if (!current.contains(packagePrefix)) {
            return false;
        }

        final var updated = new java.util.ArrayList<>(current);
        updated.remove(packagePrefix);
        setRelevantPackages(updated);
        return true;
    }

    /**
     * Clears all configured relevant packages.
     */
    public void clearRelevantPackages() {
        setRelevantPackages(Collections.emptyList());
    }

    /**
     * Returns the path to the configuration file.
     *
     * @return the configuration file path
     */
    public Path getConfigPath() {
        return configPath;
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
            @JsonProperty("relevantPackages") List<String> relevantPackages
    ) {
        /**
         * Creates a FilterConfig with validated parameters.
         */
        public FilterConfig {
            relevantPackages = relevantPackages != null
                    ? List.copyOf(relevantPackages)
                    : Collections.emptyList();
        }

        /**
         * Creates an empty filter configuration.
         *
         * @return a FilterConfig with no relevant packages
         */
        public static FilterConfig empty() {
            return new FilterConfig(Collections.emptyList());
        }
    }
}
