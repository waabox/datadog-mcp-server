package co.fanki.datadog.traceinspector.config;

import com.datadog.api.client.ApiClient;

import java.util.HashMap;
import java.util.Objects;

/**
 * Configuration holder for Datadog API credentials and settings.
 *
 * <p>This record reads configuration from environment variables and provides
 * validated access to Datadog API settings. Required environment variables
 * are validated at construction time.</p>
 *
 * <p>Environment variables:
 * <ul>
 *   <li>DATADOG_API_KEY - Required. The Datadog API key for authentication.</li>
 *   <li>DATADOG_APP_KEY - Required. The Datadog application key.</li>
 *   <li>DATADOG_SITE - Optional. The Datadog site (default: datadoghq.com).</li>
 *   <li>DATADOG_ENV_DEFAULT - Optional. The default environment (default: prod).</li>
 * </ul>
 * </p>
 *
 * @param apiKey the Datadog API key
 * @param appKey the Datadog application key
 * @param site the Datadog site domain
 * @param defaultEnv the default environment for queries
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record DatadogConfig(
        String apiKey,
        String appKey,
        String site,
        String defaultEnv
) {

    private static final String DEFAULT_SITE = "datadoghq.com";
    private static final String DEFAULT_ENV = "prod";

    /**
     * Creates a DatadogConfig with validated parameters.
     *
     * @param apiKey the Datadog API key, must not be null or blank
     * @param appKey the Datadog application key, must not be null or blank
     * @param site the Datadog site domain, must not be null or blank
     * @param defaultEnv the default environment, must not be null or blank
     *
     * @throws IllegalArgumentException if apiKey or appKey is null or blank
     */
    public DatadogConfig {
        Objects.requireNonNull(apiKey, "apiKey must not be null");
        Objects.requireNonNull(appKey, "appKey must not be null");
        Objects.requireNonNull(site, "site must not be null");
        Objects.requireNonNull(defaultEnv, "defaultEnv must not be null");

        if (apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        if (appKey.isBlank()) {
            throw new IllegalArgumentException("appKey must not be blank");
        }
    }

    /**
     * Creates a DatadogConfig from environment variables.
     *
     * <p>Reads the following environment variables:
     * <ul>
     *   <li>DATADOG_API_KEY - Required</li>
     *   <li>DATADOG_APP_KEY - Required</li>
     *   <li>DATADOG_SITE - Optional, defaults to datadoghq.com</li>
     *   <li>DATADOG_ENV_DEFAULT - Optional, defaults to prod</li>
     * </ul>
     * </p>
     *
     * @return a new DatadogConfig instance
     *
     * @throws IllegalStateException if required environment variables are missing
     */
    public static DatadogConfig fromEnvironment() {
        final String apiKey = getRequiredEnv("DATADOG_API_KEY");
        final String appKey = getRequiredEnv("DATADOG_APP_KEY");
        final String site = getOptionalEnv("DATADOG_SITE", DEFAULT_SITE);
        final String defaultEnv = getOptionalEnv("DATADOG_ENV_DEFAULT", DEFAULT_ENV);

        return new DatadogConfig(apiKey, appKey, site, defaultEnv);
    }

    /**
     * Returns the base URL for Datadog API requests.
     *
     * @return the base URL including the scheme and API subdomain
     */
    public String baseUrl() {
        return "https://api." + site;
    }

    /**
     * Builds and configures an ApiClient instance for the Datadog SDK.
     *
     * <p>The client is configured with:
     * <ul>
     *   <li>API and Application keys for authentication</li>
     *   <li>Base path matching the configured site</li>
     *   <li>Connection timeout of 10 seconds</li>
     *   <li>Read timeout of 30 seconds</li>
     * </ul>
     * </p>
     *
     * @return a configured ApiClient instance ready for use with Datadog APIs
     */
    public ApiClient buildApiClient() {
        final ApiClient client = ApiClient.getDefaultApiClient();

        final HashMap<String, String> secrets = new HashMap<>();
        secrets.put("apiKeyAuth", apiKey);
        secrets.put("appKeyAuth", appKey);
        client.configureApiKeys(secrets);

        client.setServerIndex(0);
        client.setServerVariables(new HashMap<>() {{
            put("site", site);
        }});
        client.setConnectTimeout(10_000);
        client.setReadTimeout(30_000);

        return client;
    }

    private static String getRequiredEnv(final String name) {
        final String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required environment variable %s is not set".formatted(name)
            );
        }
        return value;
    }

    private static String getOptionalEnv(final String name, final String defaultValue) {
        final String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
