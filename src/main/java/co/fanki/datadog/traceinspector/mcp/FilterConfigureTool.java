package co.fanki.datadog.traceinspector.mcp;

import co.fanki.datadog.traceinspector.config.FilterConfigStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MCP tool for configuring stack trace filters.
 *
 * <p>This tool allows configuring global and per-project filters for stack traces.
 * When searching logs, if no configuration exists, Claude should use this tool
 * to ask the user how they want to filter stack traces:</p>
 *
 * <ul>
 *   <li>Use global filters (applies to all projects)</li>
 *   <li>Use project-specific filters (saved for future use)</li>
 *   <li>No filtering (show full stack traces)</li>
 * </ul>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class FilterConfigureTool implements McpTool {

    private static final String TOOL_NAME = "filter.configure";
    private static final String TOOL_DESCRIPTION =
            "Configure stack trace filters for log searches. "
            + "IMPORTANT: Before searching logs, check if filters are configured using "
            + "action='status'. If not configured, ask the user if they want to: "
            + "(1) set global filters, (2) set project-specific filters, or "
            + "(3) show full stack traces without filtering.";

    private final FilterConfigStore filterConfigStore;

    /**
     * Creates a new FilterConfigureTool.
     *
     * @param filterConfigStore the store for filter configuration
     */
    public FilterConfigureTool(final FilterConfigStore filterConfigStore) {
        this.filterConfigStore = Objects.requireNonNull(
                filterConfigStore, "filterConfigStore must not be null"
        );
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return TOOL_DESCRIPTION;
    }

    @Override
    public Map<String, Object> inputSchema() {
        final Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        final Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("action", Map.of(
                "type", "string",
                "enum", List.of("status", "set_global", "set_project", "set_no_filter", "clear_project"),
                "description", "Action to perform: "
                        + "'status' returns current configuration and whether setup is needed, "
                        + "'set_global' sets global package filters, "
                        + "'set_project' sets filters for current project, "
                        + "'set_no_filter' marks current project as not wanting filters, "
                        + "'clear_project' removes project-specific configuration"
        ));

        properties.put("packages", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "description", "Package prefixes to keep in stack traces "
                        + "(e.g., ['co.fanki', 'com.mycompany']). "
                        + "Required for 'set_global' and 'set_project' actions."
        ));

        properties.put("projectName", Map.of(
                "type", "string",
                "description", "Project name. If not provided, auto-detects from git repo or directory name."
        ));

        schema.put("properties", properties);
        schema.put("required", List.of("action"));

        return schema;
    }

    @Override
    public Map<String, Object> execute(final Map<String, Object> arguments) {
        Objects.requireNonNull(arguments, "arguments must not be null");

        final String action = getRequiredString(arguments, "action");

        return switch (action) {
            case "status" -> executeStatus(arguments);
            case "set_global" -> executeSetGlobal(arguments);
            case "set_project" -> executeSetProject(arguments);
            case "set_no_filter" -> executeSetNoFilter(arguments);
            case "clear_project" -> executeClearProject(arguments);
            default -> throw new McpToolException(TOOL_NAME, "Unknown action: " + action);
        };
    }

    private Map<String, Object> executeStatus(final Map<String, Object> arguments) {
        final String projectName = detectProjectName(arguments);
        final boolean isConfigured = filterConfigStore.isConfigured();
        final boolean isProjectConfigured = filterConfigStore.isProjectConfigured(projectName);

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("configured", isConfigured);
        result.put("currentProject", projectName);
        result.put("projectConfigured", isProjectConfigured);

        if (isConfigured) {
            result.put("globalPackages", filterConfigStore.getGlobalPackages());
            result.put("projectPackages", filterConfigStore.getProjectPackages(projectName));
            result.put("effectivePackages", filterConfigStore.getRelevantPackages(projectName));
            result.put("configuredProjects", new ArrayList<>(filterConfigStore.getConfiguredProjects()));
        }

        // Include guidance for Claude
        if (!isConfigured) {
            result.put("setupRequired", true);
            result.put("message", "No filter configuration found. Ask the user if they want to: "
                    + "(1) Set global filters that apply to all projects, "
                    + "(2) Set project-specific filters for '" + projectName + "', or "
                    + "(3) Show full stack traces without any filtering.");
        } else if (!isProjectConfigured && filterConfigStore.getGlobalPackages().isEmpty()) {
            result.put("setupRequired", true);
            result.put("message", "No filters configured for project '" + projectName + "' "
                    + "and no global filters set. Ask the user if they want to configure filters.");
        } else {
            result.put("setupRequired", false);
        }

        return result;
    }

    private Map<String, Object> executeSetGlobal(final Map<String, Object> arguments) {
        final List<String> packages = getRequiredStringList(arguments, "packages");

        filterConfigStore.setGlobalPackages(packages);

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("action", "set_global");
        result.put("packages", packages);
        result.put("message", "Global filters configured. These will apply to all projects "
                + "unless overridden with project-specific settings.");

        return result;
    }

    private Map<String, Object> executeSetProject(final Map<String, Object> arguments) {
        final String projectName = detectProjectName(arguments);
        final List<String> packages = getRequiredStringList(arguments, "packages");

        filterConfigStore.setProjectPackages(projectName, packages);

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("action", "set_project");
        result.put("projectName", projectName);
        result.put("packages", packages);
        result.put("message", "Filters configured for project '" + projectName + "'. "
                + "These settings will be used for future log searches in this project.");

        return result;
    }

    private Map<String, Object> executeSetNoFilter(final Map<String, Object> arguments) {
        final String projectName = detectProjectName(arguments);

        filterConfigStore.setProjectNoFilter(projectName);

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("action", "set_no_filter");
        result.put("projectName", projectName);
        result.put("message", "Project '" + projectName + "' configured to show full stack traces "
                + "without filtering. This setting will be remembered for future log searches.");

        return result;
    }

    private Map<String, Object> executeClearProject(final Map<String, Object> arguments) {
        final String projectName = detectProjectName(arguments);

        filterConfigStore.clearProjectConfig(projectName);

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("action", "clear_project");
        result.put("projectName", projectName);
        result.put("message", "Project-specific configuration cleared for '" + projectName + "'. "
                + "Global filters will be used instead.");

        return result;
    }

    private String detectProjectName(final Map<String, Object> arguments) {
        final Object projectName = arguments.get("projectName");
        if (projectName != null && !projectName.toString().isBlank()) {
            return projectName.toString();
        }

        final String detected = FilterConfigStore.detectCurrentProject();
        if (detected != null) {
            return detected;
        }

        return "unknown-project";
    }

    private String getRequiredString(
            final Map<String, Object> args,
            final String key
    ) {
        final Object value = args.get(key);
        if (value == null) {
            throw new McpToolException(TOOL_NAME, "Missing required parameter: " + key);
        }
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> getRequiredStringList(
            final Map<String, Object> args,
            final String key
    ) {
        final Object value = args.get(key);
        if (value == null) {
            throw new McpToolException(TOOL_NAME, "Missing required parameter: " + key);
        }

        if (value instanceof List<?> list) {
            final List<String> result = new ArrayList<>();
            for (final Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }

        // Single string as single-item list
        return List.of(value.toString());
    }
}
