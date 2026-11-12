package edu.physics.ai;

import edu.physics.ai.gemini.GeminiProvider;
import edu.physics.ai.gemini.GeminiProviderConfiguration;
import edu.physics.ai.gemini.GoogleGenAiTransport;
import edu.physics.ai.local.LocalAIProvider;
import edu.physics.ai.ollama.HttpOllamaTransport;
import edu.physics.ai.ollama.OllamaConfiguration;
import edu.physics.ai.ollama.OllamaProvider;
import edu.physics.ai.ollama.OllamaTransport;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Factory creating {@link AIProviderManager} from environment configuration without
 * performing any network calls at startup and keeping Ollama strictly opt-in.
 */
public final class AIProviderManagerFactory {
    private AIProviderManagerFactory() {}

    public static AIProviderManager createFromEnvironment() {
        return createFromEnvironment(System.getenv());
    }

    public static AIProviderManager createFromEnvironment(Map<String, String> environment) {
        Map<String, String> env = environment != null ? environment : Map.of();
        ProviderMode mode = resolveMode(env);
        boolean ollamaEnabled = resolveOllamaEnabled(env);

        AIProvider gemini = createGeminiIfConfigured(mode, env);
        AIProvider ollama = createOllamaIfEnabled(mode, ollamaEnabled, env);
        AIProvider local = new LocalAIProvider();

        return new AIProviderManager(gemini, ollama, local,
                new AIProviderManager.Configuration(mode, ollamaEnabled),
                AIProviderManager.ProviderOwnership.OWNED);
    }

    private static ProviderMode resolveMode(Map<String, String> env) {
        String rawMode = env.get("PHYSORA_AI_MODE");
        if (rawMode != null && !rawMode.isBlank()) {
            return ProviderMode.parse(rawMode);
        }
        String legacyProvider = env.get("PHYSORA_AI_PROVIDER");
        if (legacyProvider == null || legacyProvider.isBlank()
                || "gemini".equalsIgnoreCase(legacyProvider.trim())) {
            return ProviderMode.AUTO;
        }
        if ("local".equalsIgnoreCase(legacyProvider.trim())) {
            return ProviderMode.OFFLINE;
        }
        throw new IllegalArgumentException("Unsupported PHYSORA_AI_PROVIDER: " + legacyProvider);
    }

    private static boolean resolveOllamaEnabled(Map<String, String> env) {
        String raw = env.get("PHYSORA_OLLAMA_ENABLED");
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) return true;
        if ("false".equals(normalized)) return false;
        throw new IllegalArgumentException("PHYSORA_OLLAMA_ENABLED must be true or false");
    }

    private static AIProvider createGeminiIfConfigured(ProviderMode mode, Map<String, String> env) {
        if (mode != ProviderMode.AUTO && mode != ProviderMode.CLOUD_ONLY) {
            return null;
        }
        if (!GeminiProviderConfiguration.apiKeyConfigured(env)) {
            return null;
        }
        Map<String, String> geminiEnv = env;
        String rawMode = env.get("PHYSORA_AI_MODE");
        if (rawMode != null && !rawMode.isBlank() && env.containsKey("PHYSORA_AI_PROVIDER")) {
            geminiEnv = new HashMap<>(env);
            geminiEnv.put("PHYSORA_AI_PROVIDER", "gemini");
        }
        GeminiProviderConfiguration config = GeminiProviderConfiguration.fromEnvironment(geminiEnv);
        return new GeminiProvider(new GoogleGenAiTransport(config), config);
    }

    private static AIProvider createOllamaIfEnabled(ProviderMode mode, boolean ollamaEnabled, Map<String, String> env) {
        if (!ollamaEnabled) {
            return null;
        }
        if (mode != ProviderMode.AUTO && mode != ProviderMode.LOCAL_AI) {
            return null;
        }
        OllamaConfiguration config = OllamaConfiguration.fromEnvironment(env);
        // Lazy transport avoids opening Windows NIO Selector loopback sockets during startup.
        return new OllamaProvider(config, new LazyOllamaTransport(config.baseUrl()));
    }

    private static final class LazyOllamaTransport implements OllamaTransport {
        private final String baseUrl;
        private volatile OllamaTransport delegate;

        private LazyOllamaTransport(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        private OllamaTransport delegate() {
            OllamaTransport current = delegate;
            if (current != null) {
                return current;
            }
            synchronized (this) {
                if (delegate == null) {
                    delegate = new HttpOllamaTransport(baseUrl);
                }
                return delegate;
            }
        }

        @Override
        public CompletableFuture<Response> get(String relativePath, Duration timeout) {
            return delegate().get(relativePath, timeout);
        }

        @Override
        public CompletableFuture<Response> post(String relativePath, String jsonBody, Duration timeout) {
            return delegate().post(relativePath, jsonBody, timeout);
        }

        @Override
        public void close() {
            OllamaTransport current = delegate;
            if (current != null) {
                current.close();
            }
        }
    }
}
