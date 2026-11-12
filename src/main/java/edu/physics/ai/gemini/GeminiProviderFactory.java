package edu.physics.ai.gemini;

import edu.physics.ai.AIProvider;
import edu.physics.ai.AIProviderException;
import edu.physics.ai.FallbackAIProvider;
import edu.physics.ai.local.LocalAIProvider;

import java.util.Map;

/** Creates the configured Google Gemini adapter with transparent local fallback. */
public final class GeminiProviderFactory {
    private GeminiProviderFactory() {}

    public static AIProvider createFromEnvironment() {
        return createFromEnvironment(System.getenv());
    }

    public static AIProvider createFromEnvironment(Map<String, String> environment) {
        String providerName = environment.getOrDefault("PHYSORA_AI_PROVIDER", "gemini");
        if ("local".equalsIgnoreCase(providerName)) {
            return new LocalAIProvider();
        }
        GeminiProviderConfiguration configuration = GeminiProviderConfiguration.fromEnvironment(environment);
        if (!GeminiProviderConfiguration.apiKeyConfigured(environment)) {
            throw new AIProviderException(AIProviderException.Kind.AUTHENTICATION, false,
                    "Google Gemini is not configured; set GOOGLE_API_KEY in the local environment");
        }
        GeminiProvider gemini = new GeminiProvider(new GoogleGenAiTransport(configuration), configuration);
        return new FallbackAIProvider(gemini, new LocalAIProvider());
    }
}
