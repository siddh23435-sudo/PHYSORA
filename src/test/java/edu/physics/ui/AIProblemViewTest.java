package edu.physics.ui;

import edu.physics.ai.AIConversationService;
import edu.physics.ai.AIProblemWorkflow;
import edu.physics.ai.AIProvider;
import edu.physics.ai.AIProviderException;
import edu.physics.ai.AIProviderManager;
import edu.physics.ai.AIProviderManagerFactory;
import edu.physics.ai.AIWorkflowResult;
import edu.physics.ai.ExplanationDraft;
import edu.physics.ai.ExplanationRequest;
import edu.physics.ai.ExplanationVerifier;
import edu.physics.ai.InterpretationDraft;
import edu.physics.ai.InterpretationRequest;
import edu.physics.ai.ProblemValidationResult;
import edu.physics.ai.ProblemValidator;
import edu.physics.ai.ProviderMode;
import edu.physics.ai.ProviderResult;
import edu.physics.ai.SolutionVerifier;
import edu.physics.ai.SolverOrchestrator;
import edu.physics.ai.local.LocalAIProvider;
import edu.physics.problem.ProjectileProblemSolver;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AIProblemViewTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        FxTestRuntime.ensureStarted();
    }

    @Test
    void submissionThroughOfflineManagerSolvesAndRendersResult() throws Exception {
        AtomicInteger supplierCalls = new AtomicInteger();
        AIProblemView view = runOnFxThread(() -> {
            AIProblemView created = new AIProblemView(() -> {
                supplierCalls.incrementAndGet();
                return AIProviderManagerFactory.createFromEnvironment(Map.of("PHYSORA_AI_MODE", "offline"));
            });
            new Scene(created, 900, 650);
            assertEquals(0, supplierCalls.get(), "Provider should be constructed lazily on submit");
            lookup(created, "aiQuestionInput", TextArea.class)
                    .setText("A ball is projected at 20 m/s at 45 degrees with g = 10 m/s^2. Find its maximum height and horizontal range.");
            lookup(created, "aiSubmitButton", Button.class).fire();
            return created;
        });

        try {
            Label status = lookup(view, "aiStatus", Label.class);
            TextArea output = lookup(view, "aiSolutionOutput", TextArea.class);
            Button submit = lookup(view, "aiSubmitButton", Button.class);
            Button cancel = lookup(view, "aiCancelButton", Button.class);

            waitUntilFx(() -> !submit.isDisabled()
                    && "Interpretation, deterministic solution, and explanation passed validation.".equals(status.getText()));

            assertEquals(1, supplierCalls.get());
            assertFalse(status.getStyleClass().contains("ai-status-error"));
            assertTrue(cancel.isDisabled());
            assertTrue(output.getText().contains("40.0000 m") && output.getText().contains("10.0000 m"),
                    () -> "Expected solved output (range 40.0000 m, height 10.0000 m), got: " + output.getText());
        } finally {
            runOnFxThread(() -> {
                view.dispose();
                return null;
            });
        }
    }

    @Test
    void configurationFailurePreservesErrorBannerAndAllowsRetry() throws Exception {
        AIProblemView view = runOnFxThread(() -> {
            AIProblemView created = new AIProblemView(
                    () -> AIProviderManagerFactory.createFromEnvironment(Map.of("PHYSORA_AI_MODE", "invalid_mode")));
            new Scene(created, 900, 650);
            lookup(created, "aiQuestionInput", TextArea.class)
                    .setText("A ball is projected at 20 m/s at 45 degrees with g = 10 m/s^2. Find its range.");
            lookup(created, "aiSubmitButton", Button.class).fire();
            return created;
        });

        try {
            runOnFxThread(() -> {
                Label status = lookup(view, "aiStatus", Label.class);
                Button submit = lookup(view, "aiSubmitButton", Button.class);
                Button cancel = lookup(view, "aiCancelButton", Button.class);

                assertFalse(status.getText().isBlank(), "Configuration failure must not be wiped to blank");
                assertTrue(status.getText().contains("Unsupported provider mode: invalid_mode"));
                assertTrue(status.getText().contains("PHYSORA_AI_MODE"));
                assertTrue(status.getStyleClass().contains("ai-status-error"));
                assertFalse(submit.isDisabled());
                assertTrue(cancel.isDisabled());
                return null;
            });
        } finally {
            runOnFxThread(() -> {
                view.dispose();
                return null;
            });
        }
    }

    @Test
    void asynchronousProviderFailureDisplaysProviderNeutralErrorAndRestoresControls() throws Exception {
        AIProvider failingProvider = new AIProvider() {
            @Override
            public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
                return CompletableFuture.supplyAsync(() -> {
                    throw new AIProviderException(
                            AIProviderException.Kind.PROVIDER_FAILURE,
                            false,
                            "Local or remote AI service failed");
                });
            }

            @Override
            public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
                return CompletableFuture.failedFuture(new AssertionError("Should not explain after interpret failure"));
            }
        };

        AIProblemView view = runOnFxThread(() -> {
            AIProblemView created = new AIProblemView(() -> failingProvider);
            new Scene(created, 900, 650);
            lookup(created, "aiQuestionInput", TextArea.class)
                    .setText("A ball is projected at 20 m/s at 45 degrees with g = 10 m/s^2. Find its range.");
            lookup(created, "aiSubmitButton", Button.class).fire();
            return created;
        });

        try {
            Label status = lookup(view, "aiStatus", Label.class);
            Button submit = lookup(view, "aiSubmitButton", Button.class);
            Button cancel = lookup(view, "aiCancelButton", Button.class);

            waitUntilFx(() -> !submit.isDisabled() && status.getStyleClass().contains("ai-status-error"));

            runOnFxThread(() -> {
                assertTrue(status.getText().contains("AI provider"), () -> "Expected provider-neutral copy: " + status.getText());
                assertFalse(status.getText().toLowerCase().contains("gemini"),
                        () -> "Generic async failure must not mention Gemini: " + status.getText());
                assertTrue(cancel.isDisabled());
                return null;
            });
        } finally {
            runOnFxThread(() -> {
                view.dispose();
                return null;
            });
        }
    }

    @Test
    void disposalClosesOwnedProviderAndConversationServiceOnce() throws Exception {
        AtomicInteger ownedCloseCount = new AtomicInteger();
        AIProvider trackingLocal = new AIProvider() {
            private final LocalAIProvider delegate = new LocalAIProvider();

            @Override
            public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
                return delegate.interpret(request);
            }

            @Override
            public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
                return delegate.explain(request);
            }

            @Override
            public void close() {
                ownedCloseCount.incrementAndGet();
            }
        };
        AtomicReference<AIConversationService> capturedService = new AtomicReference<>();

        AIProblemView view = runOnFxThread(() -> {
            AIProblemView created = new AIProblemView(
                    () -> new AIProviderManager(
                            null,
                            null,
                            trackingLocal,
                            new AIProviderManager.Configuration(ProviderMode.OFFLINE, false),
                            AIProviderManager.ProviderOwnership.OWNED),
                    provider -> {
                        AIProblemWorkflow workflow = new AIProblemWorkflow(
                                provider,
                                new ProblemValidator(),
                                new SolverOrchestrator(new ProjectileProblemSolver(), new SolutionVerifier()),
                                new ExplanationVerifier());
                        AIConversationService service = new AIConversationService(workflow);
                        capturedService.set(service);
                        return service;
                    });
            new Scene(created, 900, 650);
            lookup(created, "aiQuestionInput", TextArea.class)
                    .setText("A ball is projected at 20 m/s at 45 degrees with g = 10 m/s^2. Find its range.");
            lookup(created, "aiSubmitButton", Button.class).fire();
            return created;
        });

        try {
            Label status = lookup(view, "aiStatus", Label.class);
            Button submit = lookup(view, "aiSubmitButton", Button.class);
            waitUntilFx(() -> !submit.isDisabled() && status.getText().contains("passed validation"));

            assertEquals(0, ownedCloseCount.get(), "Owned provider should remain open while view is active");
            assertNotNull(capturedService.get());

            runOnFxThread(() -> {
                view.dispose();
                view.dispose();
                return null;
            });

            assertEquals(1, ownedCloseCount.get(), "Repeated dispose must close owned manager/provider exactly once");
            assertThrows(IllegalStateException.class,
                    () -> capturedService.get().submit("A ball is projected at 10 m/s at 30 degrees with g = 10 m/s^2. Find its range."),
                    "Conversation service must be closed on disposal");
        } finally {
            runOnFxThread(() -> {
                view.dispose();
                return null;
            });
        }
    }

    @Test
    void partialInitializationFailureClosesProviderAndLeavesCleanRetryableState() throws Exception {
        AtomicInteger closeCount = new AtomicInteger();
        AtomicBoolean failWorkflowSetup = new AtomicBoolean(true);
        AtomicBoolean closeThrows = new AtomicBoolean(true);

        AIProvider firstProvider = new AIProvider() {
            @Override
            public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
                return CompletableFuture.failedFuture(new AssertionError("Should not run before init finishes"));
            }

            @Override
            public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
                return CompletableFuture.failedFuture(new AssertionError("Should not run before init finishes"));
            }

            @Override
            public void close() {
                closeCount.incrementAndGet();
                if (closeThrows.get()) {
                    throw new RuntimeException("Cleanup close error");
                }
            }
        };

        AIProblemView view = runOnFxThread(() -> {
            AIProblemView created = new AIProblemView(
                    () -> failWorkflowSetup.get()
                            ? firstProvider
                            : AIProviderManagerFactory.createFromEnvironment(Map.of("PHYSORA_AI_MODE", "offline")),
                    provider -> {
                        if (failWorkflowSetup.get()) {
                            throw new IllegalStateException("Original workflow construction failure");
                        }
                        AIProblemWorkflow workflow = new AIProblemWorkflow(
                                provider,
                                new ProblemValidator(),
                                new SolverOrchestrator(new ProjectileProblemSolver(), new SolutionVerifier()),
                                new ExplanationVerifier());
                        return new AIConversationService(workflow);
                    });
            new Scene(created, 900, 650);
            lookup(created, "aiQuestionInput", TextArea.class)
                    .setText("A ball is projected at 20 m/s at 45 degrees with g = 10 m/s^2. Find its range.");
            lookup(created, "aiSubmitButton", Button.class).fire();
            return created;
        });

        try {
            assertEquals(1, closeCount.get(), "Partially initialized provider must be closed");
            runOnFxThread(() -> {
                Label status = lookup(view, "aiStatus", Label.class);
                assertTrue(status.getText().contains("Original workflow construction failure"),
                        () -> "Original exception must stay visible even if close() throws: " + status.getText());
                assertFalse(status.getText().contains("Cleanup close error"));
                assertTrue(status.getStyleClass().contains("ai-status-error"));
                assertFalse(lookup(view, "aiSubmitButton", Button.class).isDisabled());
                return null;
            });

            // Disposing after a failed initialization must not attempt to close the failed provider again.
            // First verify retry works cleanly because no half-initialized fields were kept.
            failWorkflowSetup.set(false);
            runOnFxThread(() -> {
                lookup(view, "aiSubmitButton", Button.class).fire();
                return null;
            });

            Label status = lookup(view, "aiStatus", Label.class);
            Button submit = lookup(view, "aiSubmitButton", Button.class);
            waitUntilFx(() -> !submit.isDisabled() && status.getText().contains("passed validation"));

            assertEquals(1, closeCount.get(), "Failed provider must not be retained or closed again");
            assertFalse(status.getStyleClass().contains("ai-status-error"));
        } finally {
            runOnFxThread(() -> {
                view.dispose();
                return null;
            });
            assertEquals(1, closeCount.get());
        }
    }

    @Test
    void defaultConfigurationShowsDefaultsWithoutCreatingProvider() throws Exception {
        AtomicInteger factoryCalls = new AtomicInteger();
        AIProblemView view = runOnFxThread(() -> {
            AIProblemView created = new AIProblemView(Map.of(), env -> {
                factoryCalls.incrementAndGet();
                return AIProviderManagerFactory.createFromEnvironment(env);
            });
            new Scene(created, 900, 650);
            return created;
        });

        try {
            runOnFxThread(() -> {
                assertEquals(0, factoryCalls.get(), "View construction must not instantiate providers or probe services");
                @SuppressWarnings("unchecked")
                ComboBox<ProviderMode> modeSelector = lookup(view, "aiModeSelector", ComboBox.class);
                CheckBox ollamaToggle = lookup(view, "aiOllamaToggle", CheckBox.class);
                TextField ollamaModel = lookup(view, "aiOllamaModelInput", TextField.class);
                Label description = lookup(view, "aiModeDescription", Label.class);
                Label summary = lookup(view, "aiConfigSummary", Label.class);

                assertEquals(ProviderMode.AUTO, modeSelector.getValue());
                assertFalse(ollamaToggle.isSelected(), "Ollama must be disabled by default");
                assertFalse(ollamaModel.isVisible());
                assertFalse(ollamaModel.isManaged());
                assertTrue(description.getText().contains("AUTO"));
                assertTrue(description.getText().contains("does not guarantee Gemini or Ollama"));
                assertTrue(summary.getText().contains("Gemini key: Not configured"));
                assertTrue(summary.getText().contains("gemini-3.1-flash-lite (default)"));
                assertTrue(summary.getText().contains("Ollama: Disabled"));
                return null;
            });
        } finally {
            runOnFxThread(() -> {
                view.dispose();
                return null;
            });
        }
    }

    @Test
    void environmentConfigurationInitializesControlsWithoutExposingApiKeySecret() throws Exception {
        AtomicInteger factoryCalls = new AtomicInteger();
        Map<String, String> env = Map.of(
                "PHYSORA_AI_MODE", "local_ai",
                "PHYSORA_OLLAMA_ENABLED", "true",
                "GOOGLE_API_KEY", "secret-gemini-key-value",
                "PHYSORA_GEMINI_MODEL", "gemini-2.5-flash",
                "PHYSORA_OLLAMA_MODEL", "qwen2.5:7b"
        );
        AIProblemView view = runOnFxThread(() -> {
            AIProblemView created = new AIProblemView(env, passedEnv -> {
                factoryCalls.incrementAndGet();
                return AIProviderManagerFactory.createFromEnvironment(passedEnv);
            });
            new Scene(created, 900, 650);
            return created;
        });

        try {
            runOnFxThread(() -> {
                assertEquals(0, factoryCalls.get());
                @SuppressWarnings("unchecked")
                ComboBox<ProviderMode> modeSelector = lookup(view, "aiModeSelector", ComboBox.class);
                CheckBox ollamaToggle = lookup(view, "aiOllamaToggle", CheckBox.class);
                TextField ollamaModel = lookup(view, "aiOllamaModelInput", TextField.class);
                Label summary = lookup(view, "aiConfigSummary", Label.class);

                assertEquals(ProviderMode.LOCAL_AI, modeSelector.getValue());
                assertTrue(ollamaToggle.isSelected());
                assertTrue(ollamaModel.isVisible());
                assertTrue(ollamaModel.isManaged());
                assertEquals("qwen2.5:7b", ollamaModel.getText());
                assertTrue(summary.getText().contains("Gemini key: Configured"));
                assertTrue(summary.getText().contains("gemini-2.5-flash"));
                assertTrue(summary.getText().contains("Ollama: Enabled"));
                assertTrue(summary.getText().contains("Ollama model: qwen2.5:7b"));
                assertTrue(summary.getText().contains("runtime verification deferred until request"));
                assertFalse(summary.getText().contains("secret-gemini-key-value"),
                        "API key value must never be displayed in the UI");
                return null;
            });
        } finally {
            runOnFxThread(() -> {
                view.dispose();
                return null;
            });
        }
    }

    @Test
    void allFourModesShowDistinctExplanationsPropagateToFactoryAndLockControlsDuringSubmit() throws Exception {
        List<Map<String, String>> capturedEnvs = new ArrayList<>();
        AtomicReference<CountDownLatch> gate = new AtomicReference<>();

        AIProblemView view = runOnFxThread(() -> {
            AIProblemView created = new AIProblemView(Map.of(), env -> {
                capturedEnvs.add(env);
                AIProvider delegate = AIProviderManagerFactory.createFromEnvironment(
                        Map.of("PHYSORA_AI_MODE", "offline"));
                return new AIProvider() {
                    @Override
                    public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
                        CountDownLatch currentGate = gate.get();
                        if (currentGate == null) {
                            return delegate.interpret(request);
                        }
                        return CompletableFuture.supplyAsync(() -> {
                            try {
                                assertTrue(currentGate.await(5, TimeUnit.SECONDS));
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                            }
                            return delegate.interpret(request).toCompletableFuture().join();
                        });
                    }

                    @Override
                    public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
                        return delegate.explain(request);
                    }

                    @Override
                    public void close() {
                        delegate.close();
                    }
                };
            });
            new Scene(created, 900, 650);
            return created;
        });

        try {
            for (ProviderMode mode : ProviderMode.values()) {
                CountDownLatch latch = mode == ProviderMode.AUTO ? new CountDownLatch(1) : null;
                gate.set(latch);

                runOnFxThread(() -> {
                    @SuppressWarnings("unchecked")
                    ComboBox<ProviderMode> modeSelector = lookup(view, "aiModeSelector", ComboBox.class);
                    CheckBox ollamaToggle = lookup(view, "aiOllamaToggle", CheckBox.class);
                    Label description = lookup(view, "aiModeDescription", Label.class);

                    modeSelector.setValue(mode);
                    assertTrue(description.getText().contains(mode.name()));
                    if (mode == ProviderMode.CLOUD_ONLY) {
                        assertTrue(description.getText().contains("Never falls back silently"));
                        assertTrue(ollamaToggle.isDisabled());
                    } else if (mode == ProviderMode.LOCAL_AI) {
                        assertTrue(description.getText().contains("Never contacts Google Gemini"));
                        assertFalse(ollamaToggle.isDisabled());
                    } else if (mode == ProviderMode.OFFLINE) {
                        assertTrue(description.getText().contains("zero network calls"));
                        assertTrue(ollamaToggle.isDisabled());
                    }

                    lookup(view, "aiQuestionInput", TextArea.class)
                            .setText("A ball is projected at 20 m/s at 45 degrees with g = 10 m/s^2. Find its range.");
                    lookup(view, "aiSubmitButton", Button.class).fire();

                    if (latch != null) {
                        assertTrue(modeSelector.isDisabled(), "Mode selector must be locked while request is active");
                        assertTrue(ollamaToggle.isDisabled(), "Ollama toggle must be locked while request is active");
                    }
                    return null;
                });

                if (latch != null) {
                    latch.countDown();
                }

                Button submit = lookup(view, "aiSubmitButton", Button.class);
                Label status = lookup(view, "aiStatus", Label.class);
                waitUntilFx(() -> !submit.isDisabled() && status.getText().contains("passed validation"));

                runOnFxThread(() -> {
                    @SuppressWarnings("unchecked")
                    ComboBox<ProviderMode> modeSelector = lookup(view, "aiModeSelector", ComboBox.class);
                    assertFalse(modeSelector.isDisabled());
                    return null;
                });
            }

            assertEquals(4, capturedEnvs.size());
            assertEquals("AUTO", capturedEnvs.get(0).get("PHYSORA_AI_MODE"));
            assertEquals("CLOUD_ONLY", capturedEnvs.get(1).get("PHYSORA_AI_MODE"));
            assertEquals("LOCAL_AI", capturedEnvs.get(2).get("PHYSORA_AI_MODE"));
            assertEquals("OFFLINE", capturedEnvs.get(3).get("PHYSORA_AI_MODE"));
        } finally {
            if (gate.get() != null) {
                gate.get().countDown();
            }
            runOnFxThread(() -> {
                view.dispose();
                return null;
            });
        }
    }

    @Test
    void cloudOnlyWithoutGeminiKeyFailsExplicitlyWithoutSilentFallback() throws Exception {
        AIProblemView view = runOnFxThread(() -> {
            AIProblemView created = new AIProblemView(Map.of("PHYSORA_AI_MODE", "cloud_only"));
            new Scene(created, 900, 650);
            lookup(created, "aiQuestionInput", TextArea.class)
                    .setText("A ball is projected at 20 m/s at 45 degrees with g = 10 m/s^2. Find its range.");
            lookup(created, "aiSubmitButton", Button.class).fire();
            return created;
        });

        try {
            Label status = lookup(view, "aiStatus", Label.class);
            TextArea output = lookup(view, "aiSolutionOutput", TextArea.class);
            Button submit = lookup(view, "aiSubmitButton", Button.class);

            waitUntilFx(() -> !submit.isDisabled() && status.getStyleClass().contains("ai-status-error"));

            runOnFxThread(() -> {
                assertTrue(status.getText().contains("GOOGLE_API_KEY"));
                assertFalse(output.getText().contains("40.0000 m"),
                        "CLOUD_ONLY must never silently fall back to LocalAIProvider");
                return null;
            });
        } finally {
            runOnFxThread(() -> {
                view.dispose();
                return null;
            });
        }
    }

    @Test
    void configurationChangeAfterCompletedSubmissionClosesOldProviderAndLazilyCreatesNewOne() throws Exception {
        List<AtomicInteger> closeCounts = new ArrayList<>();
        List<Map<String, String>> capturedEnvs = new ArrayList<>();

        AIProblemView view = runOnFxThread(() -> {
            AIProblemView created = new AIProblemView(Map.of("PHYSORA_AI_MODE", "offline"), env -> {
                capturedEnvs.add(env);
                AtomicInteger closeCounter = new AtomicInteger();
                closeCounts.add(closeCounter);
                AIProvider manager = AIProviderManagerFactory.createFromEnvironment(env);
                return new AIProvider() {
                    @Override
                    public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
                        return manager.interpret(request);
                    }

                    @Override
                    public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
                        return manager.explain(request);
                    }

                    @Override
                    public void close() {
                        closeCounter.incrementAndGet();
                        manager.close();
                    }
                };
            });
            new Scene(created, 900, 650);
            lookup(created, "aiQuestionInput", TextArea.class)
                    .setText("A ball is projected at 20 m/s at 45 degrees with g = 10 m/s^2. Find its range.");
            lookup(created, "aiSubmitButton", Button.class).fire();
            return created;
        });

        try {
            Button submit = lookup(view, "aiSubmitButton", Button.class);
            Label status = lookup(view, "aiStatus", Label.class);
            waitUntilFx(() -> !submit.isDisabled() && status.getText().contains("passed validation"));

            assertEquals(1, capturedEnvs.size());
            assertEquals("OFFLINE", capturedEnvs.get(0).get("PHYSORA_AI_MODE"));
            assertEquals(0, closeCounts.get(0).get());

            runOnFxThread(() -> {
                @SuppressWarnings("unchecked")
                ComboBox<ProviderMode> modeSelector = lookup(view, "aiModeSelector", ComboBox.class);
                modeSelector.setValue(ProviderMode.AUTO);
                return null;
            });

            assertEquals(1, closeCounts.get(0).get(), "Changing mode while idle must close old provider once");
            assertEquals(1, capturedEnvs.size(), "Changing mode must keep recreation lazy until next submit");

            runOnFxThread(() -> {
                lookup(view, "aiQuestionInput", TextArea.class)
                        .setText("A ball is projected at 20 m/s at 45 degrees with g = 10 m/s^2. Find its maximum height.");
                lookup(view, "aiSubmitButton", Button.class).fire();
                return null;
            });

            waitUntilFx(() -> !submit.isDisabled() && status.getText().contains("passed validation"));

            assertEquals(2, capturedEnvs.size());
            assertEquals("AUTO", capturedEnvs.get(1).get("PHYSORA_AI_MODE"));
            assertEquals(1, closeCounts.get(0).get(), "Previous provider must not be double-closed");
            assertEquals(0, closeCounts.get(1).get(), "Newly active provider stays open until mode change or dispose");
        } finally {
            runOnFxThread(() -> {
                view.dispose();
                return null;
            });
            if (closeCounts.size() >= 2) {
                assertEquals(1, closeCounts.get(0).get());
                assertEquals(1, closeCounts.get(1).get());
            }
        }
    }

    @Test
    void ollamaOptInValidatesBlankModelAndPreservesTrimmedConfiguredModel() throws Exception {
        List<Map<String, String>> capturedEnvs = new ArrayList<>();

        AIProblemView view = runOnFxThread(() -> {
            AIProblemView created = new AIProblemView(Map.of("PHYSORA_AI_MODE", "local_ai"), env -> {
                capturedEnvs.add(env);
                return AIProviderManagerFactory.createFromEnvironment(
                        Map.of("PHYSORA_AI_MODE", "offline"));
            });
            new Scene(created, 900, 650);
            CheckBox ollamaToggle = lookup(created, "aiOllamaToggle", CheckBox.class);
            TextField ollamaModel = lookup(created, "aiOllamaModelInput", TextField.class);
            ollamaToggle.setSelected(true);
            ollamaModel.setText("   ");
            lookup(created, "aiQuestionInput", TextArea.class)
                    .setText("A ball is projected at 20 m/s at 45 degrees with g = 10 m/s^2. Find its range.");
            lookup(created, "aiSubmitButton", Button.class).fire();
            return created;
        });

        try {
            runOnFxThread(() -> {
                Label status = lookup(view, "aiStatus", Label.class);
                Button submit = lookup(view, "aiSubmitButton", Button.class);
                assertTrue(capturedEnvs.isEmpty(), "Blank Ollama model must fail before constructing provider");
                assertTrue(status.getStyleClass().contains("ai-status-error"));
                assertTrue(status.getText().contains("PHYSORA_OLLAMA_MODEL"));
                assertFalse(submit.isDisabled());
                return null;
            });

            // Disabling Ollama must allow other modes to work even if the hidden model field was left blank.
            runOnFxThread(() -> {
                CheckBox ollamaToggle = lookup(view, "aiOllamaToggle", CheckBox.class);
                ollamaToggle.setSelected(false);
                lookup(view, "aiSubmitButton", Button.class).fire();
                return null;
            });

            Button submit = lookup(view, "aiSubmitButton", Button.class);
            Label status = lookup(view, "aiStatus", Label.class);
            waitUntilFx(() -> !submit.isDisabled() && status.getText().contains("passed validation"));
            assertEquals(1, capturedEnvs.size());
            assertEquals("false", capturedEnvs.get(0).get("PHYSORA_OLLAMA_ENABLED"));

            // Re-enabling Ollama with a custom padded model must preserve the exact trimmed value.
            runOnFxThread(() -> {
                CheckBox ollamaToggle = lookup(view, "aiOllamaToggle", CheckBox.class);
                TextField ollamaModel = lookup(view, "aiOllamaModelInput", TextField.class);
                ollamaToggle.setSelected(true);
                ollamaModel.setText("  qwen2.5-math:7b  ");
                lookup(view, "aiSubmitButton", Button.class).fire();
                return null;
            });

            waitUntilFx(() -> !submit.isDisabled() && status.getText().contains("passed validation"));
            assertEquals(2, capturedEnvs.size());
            assertEquals("LOCAL_AI", capturedEnvs.get(1).get("PHYSORA_AI_MODE"));
            assertEquals("true", capturedEnvs.get(1).get("PHYSORA_OLLAMA_ENABLED"));
            assertEquals("qwen2.5-math:7b", capturedEnvs.get(1).get("PHYSORA_OLLAMA_MODEL"));
        } finally {
            runOnFxThread(() -> {
                view.dispose();
                return null;
            });
        }
    }

    @Test
    void invalidStartupEnvironmentRecoversSafelyViaControls() throws Exception {
        AIProblemView view = runOnFxThread(() -> {
            AIProblemView created = new AIProblemView(Map.of(
                    "PHYSORA_AI_MODE", "unsupported_mode",
                    "PHYSORA_OLLAMA_ENABLED", "not_a_boolean"));
            new Scene(created, 900, 650);
            return created;
        });

        try {
            runOnFxThread(() -> {
                @SuppressWarnings("unchecked")
                ComboBox<ProviderMode> modeSelector = lookup(view, "aiModeSelector", ComboBox.class);
                CheckBox ollamaToggle = lookup(view, "aiOllamaToggle", CheckBox.class);
                Label status = lookup(view, "aiStatus", Label.class);

                assertEquals(ProviderMode.AUTO, modeSelector.getValue());
                assertFalse(ollamaToggle.isSelected());
                assertTrue(status.getStyleClass().contains("ai-status-error"));
                assertTrue(status.getText().contains("Unsupported provider mode"));

                modeSelector.setValue(ProviderMode.OFFLINE);
                lookup(view, "aiQuestionInput", TextArea.class)
                        .setText("A ball is projected at 20 m/s at 45 degrees with g = 10 m/s^2. Find its range.");
                lookup(view, "aiSubmitButton", Button.class).fire();
                return null;
            });

            Button submit = lookup(view, "aiSubmitButton", Button.class);
            Label status = lookup(view, "aiStatus", Label.class);
            waitUntilFx(() -> !submit.isDisabled() && status.getText().contains("passed validation"));
            assertFalse(status.getStyleClass().contains("ai-status-error"));
        } finally {
            runOnFxThread(() -> {
                view.dispose();
                return null;
            });
        }
    }

    @Test
    void offlineModeExecutesZeroNetworkCallsAndPreservesSeparateStageAttribution() throws Exception {
        AtomicReference<ProviderResult.ProviderAttribution> interpretAttribution = new AtomicReference<>();
        AtomicReference<ProviderResult.ProviderAttribution> explainAttribution = new AtomicReference<>();

        AIProblemView view = runOnFxThread(() -> {
            // Setting a dummy URL for Ollama proves OFFLINE ignores Ollama/network completely even when toggled on.
            AIProblemView created = new AIProblemView(
                    Map.of(
                            "PHYSORA_AI_MODE", "offline",
                            "PHYSORA_OLLAMA_ENABLED", "true",
                            "PHYSORA_OLLAMA_URL", "http://127.0.0.1:1"
                    ),
                    AIProviderManagerFactory::createFromEnvironment,
                    provider -> new AIConversationService(new AIProblemWorkflow(
                            new AIProvider() {
                                @Override
                                public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
                                    return provider.interpret(request).thenApply(res -> {
                                        interpretAttribution.set(res.attribution());
                                        return res;
                                    });
                                }

                                @Override
                                public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
                                    return provider.explain(request).thenApply(res -> {
                                        explainAttribution.set(res.attribution());
                                        return res;
                                    });
                                }

                                @Override
                                public void close() {
                                    provider.close();
                                }
                            },
                            new ProblemValidator(),
                            new SolverOrchestrator(new ProjectileProblemSolver(), new SolutionVerifier()),
                            new ExplanationVerifier())));
            new Scene(created, 900, 650);
            lookup(created, "aiQuestionInput", TextArea.class)
                    .setText("A ball is projected at 20 m/s at 45 degrees with g = 10 m/s^2. Find its range.");
            lookup(created, "aiSubmitButton", Button.class).fire();
            return created;
        });

        try {
            Button submit = lookup(view, "aiSubmitButton", Button.class);
            Label status = lookup(view, "aiStatus", Label.class);
            TextArea output = lookup(view, "aiSolutionOutput", TextArea.class);

            waitUntilFx(() -> !submit.isDisabled() && status.getText().contains("passed validation"));
            assertTrue(output.getText().contains("40.0000 m"));
            assertNotNull(interpretAttribution.get());
            assertNotNull(explainAttribution.get());
            assertEquals(ProviderResult.ProviderId.LOCAL_RULE_ENGINE, interpretAttribution.get().provider());
            assertEquals(ProviderResult.ProviderId.LOCAL_RULE_ENGINE, explainAttribution.get().provider());
            assertFalse(interpretAttribution.get().fallbackUsed());
            assertFalse(explainAttribution.get().fallbackUsed());
        } finally {
            runOnFxThread(() -> {
                view.dispose();
                return null;
            });
        }
    }

    @Test
    void providerSettingsSectionFitsWithinMinimum1120x700Workspace() throws Exception {
        runOnFxThread(() -> {
            Stage stage = new Stage();
            ProjectileSimulatorApp app = new ProjectileSimulatorApp();
            app.start(stage);
            PhysoraApplicationShell shell = (PhysoraApplicationShell) stage.getScene().getRoot();
            try {
                Parent root = (Parent) stage.getScene().getRoot();
                ToggleButton aiTab = (ToggleButton) root.lookup("#aiProblemTab");
                assertNotNull(aiTab);
                aiTab.fire();

                stage.setWidth(1120);
                stage.setHeight(700);
                root.resize(1120, 700);
                root.applyCss();
                root.layout();

                AIProblemView aiView = (AIProblemView) root.lookup("#aiProblemView");
                assertNotNull(aiView);
                assertTrue(aiView.isVisible());

                // Verify both default (Ollama collapsed) and expanded (Ollama model field shown) fit cleanly.
                assertControlsFitInsideView(aiView);

                CheckBox ollamaToggle = (CheckBox) aiView.lookup("#aiOllamaToggle");
                ollamaToggle.setSelected(true);
                root.applyCss();
                root.layout();
                assertControlsFitInsideView(aiView);
                Node ollamaModel = aiView.lookup("#aiOllamaModelInput");
                assertNotNull(ollamaModel);
                assertTrue(ollamaModel.isVisible());
                Bounds modelBounds = aiView.sceneToLocal(ollamaModel.localToScene(ollamaModel.getBoundsInLocal()));
                assertTrue(modelBounds.getMaxX() <= aiView.getWidth() + 1.0);
                assertTrue(modelBounds.getMaxY() <= aiView.getHeight() + 1.0);
                return null;
            } finally {
                if (stage.isShowing()) {
                    stage.close();
                }
                shell.dispose();
            }
        });
    }

    private static void assertControlsFitInsideView(AIProblemView aiView) {
        double viewWidth = aiView.getWidth();
        double viewHeight = aiView.getHeight();
        assertTrue(viewWidth >= 450, "Expected usable AIProblemView width, got: " + viewWidth);
        assertTrue(viewHeight >= 450, "Expected usable AIProblemView height, got: " + viewHeight);

        String[] ids = {"#aiModeSelector", "#aiOllamaToggle", "#aiModeDescription",
                "#aiConfigSummary", "#aiQuestionInput", "#aiSubmitButton",
                "#aiAttributionPanel", "#aiSolutionOutput"};
        for (String id : ids) {
            Node node = aiView.lookup(id);
            assertNotNull(node, "Missing node " + id);
            assertTrue(node.isVisible(), "Node should be visible: " + id);
            Bounds inView = aiView.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
            assertTrue(inView.getWidth() > 0 && inView.getHeight() > 0,
                    () -> id + " has non-positive size: " + inView);
            assertTrue(inView.getMaxX() <= viewWidth + 1.0,
                    () -> id + " overflows right edge (" + inView.getMaxX() + " > " + viewWidth + ")");
            assertTrue(inView.getMaxY() <= viewHeight + 1.0,
                    () -> id + " overflows bottom edge (" + inView.getMaxY() + " > " + viewHeight + ")");
        }

        Node solution = aiView.lookup("#aiSolutionOutput");
        Node question = aiView.lookup("#aiQuestionInput");
        assertTrue(question.getBoundsInLocal().getHeight() >= 55,
                "Problem input height should stay usable at 1120x700: " + question.getBoundsInLocal().getHeight());
        assertTrue(solution.getBoundsInLocal().getHeight() >= 80,
                "Verified solution area should stay usable at 1120x700: " + solution.getBoundsInLocal().getHeight());
    }

    @Test
    void sameProviderUsedForBothStagesDisplaysProviderAndModelWithoutFallback() throws Exception {
        LocalAIProvider local = new LocalAIProvider();
        AIProvider geminiBoth = fixedAttributionProvider(
                local,
                new ProviderResult.ProviderAttribution(
                        ProviderResult.ProviderId.GEMINI, "gemini-3.1-flash-lite", false, null, null, List.of()),
                new ProviderResult.ProviderAttribution(
                        ProviderResult.ProviderId.GEMINI, "gemini-3.1-flash-lite", false, null, null, List.of()));

        AIProblemView view = runSolvedQuestion(geminiBoth);
        try {
            runOnFxThread(() -> {
                String interp = lookup(view, "aiInterpretationAttributionText", Label.class).getText();
                String explain = lookup(view, "aiExplanationAttributionText", Label.class).getText();
                String numerical = lookup(view, "aiNumericalAttributionText", Label.class).getText();

                assertTrue(interp.contains("Provider: Google Gemini"));
                assertTrue(interp.contains("Model: gemini-3.1-flash-lite"));
                assertTrue(interp.contains("Fallback: No"));
                assertTrue(explain.contains("Provider: Google Gemini"));
                assertTrue(explain.contains("Model: gemini-3.1-flash-lite"));
                assertTrue(explain.contains("Fallback: No"));
                assertTrue(numerical.contains("PHYSORA Deterministic Physics Solver"));
                assertTrue(numerical.contains("Verified"));
                assertFalse(lookup(view, "aiInterpretationAttribution", Node.class)
                        .getStyleClass().contains("ai-attribution-card-fallback"));
                assertFalse(lookup(view, "aiExplanationAttribution", Node.class)
                        .getStyleClass().contains("ai-attribution-card-fallback"));
                return null;
            });
        } finally {
            runOnFxThread(() -> {
                view.dispose();
                return null;
            });
        }
    }

    @Test
    void differentProvidersForInterpretationAndExplanationAreShownIndependently() throws Exception {
        LocalAIProvider local = new LocalAIProvider();
        AIProvider mixedProviders = fixedAttributionProvider(
                local,
                new ProviderResult.ProviderAttribution(
                        ProviderResult.ProviderId.GEMINI, "gemini-3.1-flash-lite", false, null, null, List.of()),
                new ProviderResult.ProviderAttribution(
                        ProviderResult.ProviderId.OLLAMA, "qwen2.5:7b", false, null, null, List.of()));

        AIProblemView view = runSolvedQuestion(mixedProviders);
        try {
            runOnFxThread(() -> {
                String interp = lookup(view, "aiInterpretationAttributionText", Label.class).getText();
                String explain = lookup(view, "aiExplanationAttributionText", Label.class).getText();

                assertTrue(interp.contains("Provider: Google Gemini"));
                assertTrue(interp.contains("Model: gemini-3.1-flash-lite"));
                assertTrue(interp.contains("Fallback: No"));
                assertFalse(interp.contains("Ollama"));

                assertTrue(explain.contains("Provider: Ollama (local AI)"));
                assertTrue(explain.contains("Model: qwen2.5:7b"));
                assertTrue(explain.contains("Fallback: No"));
                assertFalse(explain.contains("Google Gemini"));
                return null;
            });
        } finally {
            runOnFxThread(() -> {
                view.dispose();
                return null;
            });
        }
    }

    @Test
    void fallbackDuringInterpretationOnlyPreservesOrderedHopsWhileExplanationRemainsDirect() throws Exception {
        LocalAIProvider local = new LocalAIProvider();
        AIProvider gemini = new AIProvider() {
            @Override
            public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
                return CompletableFuture.failedFuture(new AIProviderException(
                        AIProviderException.Kind.RATE_LIMITED, true, "Gemini quota reached"));
            }

            @Override
            public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
                return local.explain(request).thenApply(res -> new ProviderResult<>(
                        res.content(), res.requestId(), "gemini-3.1-flash-lite", res.usage()));
            }
        };
        AIProvider ollama = failingProvider(
                AIProviderException.Kind.NETWORK, false, "Could not reach Ollama server");

        AIProviderManager manager = new AIProviderManager(
                gemini,
                ollama,
                local,
                new AIProviderManager.Configuration(ProviderMode.AUTO, true),
                AIProviderManager.ProviderOwnership.OWNED);

        AIProblemView view = runSolvedQuestion(manager);
        try {
            runOnFxThread(() -> {
                String interp = lookup(view, "aiInterpretationAttributionText", Label.class).getText();
                String explain = lookup(view, "aiExplanationAttributionText", Label.class).getText();

                assertTrue(interp.contains("Provider: Built-in local rule engine"));
                assertTrue(interp.contains("Model: local-physics-engine"));
                assertTrue(interp.contains("Fallback: Yes"));
                int firstHop = interp.indexOf("1. Google Gemini — rate limit reached: Gemini quota reached");
                int secondHop = interp.indexOf("2. Ollama (local AI) — service unreachable: Could not reach Ollama server");
                assertTrue(firstHop >= 0 && secondHop > firstHop,
                        () -> "Expected both ordered fallback hops, got: " + interp);
                assertTrue(lookup(view, "aiInterpretationAttribution", Node.class)
                        .getStyleClass().contains("ai-attribution-card-fallback"));

                assertTrue(explain.contains("Provider: Google Gemini"));
                assertTrue(explain.contains("Model: gemini-3.1-flash-lite"));
                assertTrue(explain.contains("Fallback: No"));
                assertFalse(lookup(view, "aiExplanationAttribution", Node.class)
                        .getStyleClass().contains("ai-attribution-card-fallback"));
                return null;
            });
        } finally {
            runOnFxThread(() -> {
                view.dispose();
                return null;
            });
        }
    }

    @Test
    void fallbackDuringExplanationOnlyShowsDirectInterpretationAndExplanationFallback() throws Exception {
        LocalAIProvider local = new LocalAIProvider();
        AIProvider gemini = new AIProvider() {
            @Override
            public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
                return local.interpret(request).thenApply(res -> new ProviderResult<>(
                        res.content(), res.requestId(), "gemini-3.1-flash-lite", res.usage()));
            }

            @Override
            public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
                return CompletableFuture.failedFuture(new AIProviderException(
                        AIProviderException.Kind.TIMEOUT, true, "Gemini response timed out"));
            }
        };

        AIProviderManager manager = new AIProviderManager(
                gemini,
                null,
                local,
                new AIProviderManager.Configuration(ProviderMode.AUTO, false),
                AIProviderManager.ProviderOwnership.OWNED);

        AIProblemView view = runSolvedQuestion(manager);
        try {
            runOnFxThread(() -> {
                String interp = lookup(view, "aiInterpretationAttributionText", Label.class).getText();
                String explain = lookup(view, "aiExplanationAttributionText", Label.class).getText();
                String numerical = lookup(view, "aiNumericalAttributionText", Label.class).getText();

                assertTrue(interp.contains("Provider: Google Gemini"));
                assertTrue(interp.contains("Fallback: No"));
                assertTrue(explain.contains("Provider: Built-in local rule engine"));
                assertTrue(explain.contains("Model: local-physics-engine"));
                assertTrue(explain.contains("Fallback: Yes"));
                assertTrue(explain.contains("1. Google Gemini — request timed out: Gemini response timed out"));
                assertTrue(numerical.contains("PHYSORA Deterministic Physics Solver"));
                assertTrue(numerical.contains("Verified"));
                return null;
            });
        } finally {
            runOnFxThread(() -> {
                view.dispose();
                return null;
            });
        }
    }

    @Test
    void fallbackDuringBothStagesPreservesIndependentMultiHopTrails() throws Exception {
        LocalAIProvider local = new LocalAIProvider();
        AIProvider gemini = new AIProvider() {
            @Override
            public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
                return CompletableFuture.failedFuture(new AIProviderException(
                        AIProviderException.Kind.RATE_LIMITED, true, "Rate limit during interpret"));
            }

            @Override
            public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
                return CompletableFuture.failedFuture(new AIProviderException(
                        AIProviderException.Kind.PROVIDER_FAILURE, true, "Service error during explain"));
            }
        };
        AIProvider ollama = new AIProvider() {
            @Override
            public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
                return CompletableFuture.failedFuture(new AIProviderException(
                        AIProviderException.Kind.PROVIDER_FAILURE, false, "Ollama model missing for interpret"));
            }

            @Override
            public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
                return CompletableFuture.failedFuture(new AIProviderException(
                        AIProviderException.Kind.TIMEOUT, true, "Ollama timed out during explain"));
            }
        };

        AIProviderManager manager = new AIProviderManager(
                gemini,
                ollama,
                local,
                new AIProviderManager.Configuration(ProviderMode.AUTO, true),
                AIProviderManager.ProviderOwnership.OWNED);

        AIProblemView view = runSolvedQuestion(manager);
        try {
            runOnFxThread(() -> {
                String interp = lookup(view, "aiInterpretationAttributionText", Label.class).getText();
                String explain = lookup(view, "aiExplanationAttributionText", Label.class).getText();

                assertTrue(interp.contains("1. Google Gemini — rate limit reached: Rate limit during interpret"));
                assertTrue(interp.contains("2. Ollama (local AI) — service unavailable: Ollama model missing for interpret"));
                assertTrue(interp.indexOf("1. Google Gemini") < interp.indexOf("2. Ollama (local AI)"));

                assertTrue(explain.contains("1. Google Gemini — service unavailable: Service error during explain"));
                assertTrue(explain.contains("2. Ollama (local AI) — request timed out: Ollama timed out during explain"));
                assertTrue(explain.indexOf("1. Google Gemini") < explain.indexOf("2. Ollama (local AI)"));
                return null;
            });
        } finally {
            runOnFxThread(() -> {
                view.dispose();
                return null;
            });
        }
    }

    @Test
    void missingAttributionAndUnknownProviderAreDistinctAndNeverInferredFromSelectedMode() throws Exception {
        LocalAIProvider local = new LocalAIProvider();
        // Select CLOUD_ONLY in environment while returning custom UNKNOWN attribution or null stages.
        AIProblemView view = runOnFxThread(() -> {
            AIProblemView created = new AIProblemView(
                    Map.of("PHYSORA_AI_MODE", "cloud_only"),
                    env -> fixedAttributionProvider(
                            local,
                            new ProviderResult.ProviderAttribution(
                                    ProviderResult.ProviderId.UNKNOWN, "custom-experimental-v1", false, null, null, List.of()),
                            new ProviderResult.ProviderAttribution(
                                    ProviderResult.ProviderId.UNKNOWN, "custom-experimental-v1", true,
                                    ProviderResult.ProviderId.GEMINI, "Primary declined", List.of())));
            new Scene(created, 900, 650);
            lookup(created, "aiQuestionInput", TextArea.class)
                    .setText("A ball is projected at 20 m/s at 45 degrees with g = 10 m/s^2. Find its range.");
            lookup(created, "aiSubmitButton", Button.class).fire();
            return created;
        });

        try {
            Button submit = lookup(view, "aiSubmitButton", Button.class);
            Label status = lookup(view, "aiStatus", Label.class);
            waitUntilFx(() -> !submit.isDisabled() && status.getText().contains("passed validation"));

            runOnFxThread(() -> {
                String interpUnknown = lookup(view, "aiInterpretationAttributionText", Label.class).getText();
                String explainUnknown = lookup(view, "aiExplanationAttributionText", Label.class).getText();
                assertTrue(interpUnknown.contains("Unknown provider (attribution recorded, identity unrecognized)"));
                assertTrue(interpUnknown.contains("Model: custom-experimental-v1"));
                assertFalse(interpUnknown.contains("Google Gemini"));
                assertTrue(explainUnknown.contains("Fallback: Yes (from Google Gemini: Primary declined)"));

                // Null interpretation and null explanation even with CLOUD_ONLY selected in #aiModeSelector.
                ProviderResult<InterpretationDraft> validInterp = local.interpret(
                        new InterpretationRequest("A ball is projected at 20 m/s at 45 degrees with g = 10 m/s^2. Find its range."))
                        .toCompletableFuture().join();
                ProblemValidationResult validProblem = new ProblemValidator().validate(
                        validInterp.content().normalizedQuestion(), validInterp.content());
                SolverOrchestrator.OrchestrationResult verifiedOrch = new SolverOrchestrator(
                        new ProjectileProblemSolver(), new SolutionVerifier()).solve(validProblem);
                AIWorkflowResult missingBoth = new AIWorkflowResult(
                        AIWorkflowResult.Status.COMPLETE,
                        validInterp.content().normalizedQuestion(),
                        null,
                        validProblem,
                        verifiedOrch,
                        null,
                        null,
                        null,
                        "complete");
                view.renderWorkflowResult(missingBoth);

                String interpMissing = lookup(view, "aiInterpretationAttributionText", Label.class).getText();
                String explainMissing = lookup(view, "aiExplanationAttributionText", Label.class).getText();
                assertTrue(interpMissing.contains("Unavailable (not recorded)"));
                assertFalse(interpMissing.contains("Google Gemini"));
                assertTrue(explainMissing.contains("Unavailable (not recorded)"));
                assertFalse(explainMissing.contains("Google Gemini"));
                return null;
            });
        } finally {
            runOnFxThread(() -> {
                view.dispose();
                return null;
            });
        }
    }

    @Test
    void providerErrorDistinguishesInterpretationFailureFromExplanationFailure() throws Exception {
        LocalAIProvider local = new LocalAIProvider();
        AIProvider failAtExplain = new AIProvider() {
            @Override
            public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
                return local.interpret(request).thenApply(res -> new ProviderResult<>(
                        res.content(), res.requestId(), "gemini-3.1-flash-lite", res.usage()));
            }

            @Override
            public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
                return CompletableFuture.failedFuture(new AIProviderException(
                        AIProviderException.Kind.PROVIDER_FAILURE, false, "Explanation backend failed"));
            }
        };

        AIProblemView view = runOnFxThread(() -> {
            AIProblemView created = new AIProblemView(
                    Map.of("PHYSORA_AI_MODE", "cloud_only"),
                    env -> failAtExplain);
            new Scene(created, 900, 650);
            lookup(created, "aiQuestionInput", TextArea.class)
                    .setText("A ball is projected at 20 m/s at 45 degrees with g = 10 m/s^2. Find its range.");
            lookup(created, "aiSubmitButton", Button.class).fire();
            return created;
        });

        try {
            Button submit = lookup(view, "aiSubmitButton", Button.class);
            Label status = lookup(view, "aiStatus", Label.class);
            waitUntilFx(() -> !submit.isDisabled() && status.getStyleClass().contains("ai-status-error"));

            runOnFxThread(() -> {
                // Stage 1 succeeded before Stage 2 failed -> interpretation must still display Gemini!
                String interp = lookup(view, "aiInterpretationAttributionText", Label.class).getText();
                String explain = lookup(view, "aiExplanationAttributionText", Label.class).getText();
                String numerical = lookup(view, "aiNumericalAttributionText", Label.class).getText();

                assertTrue(interp.contains("Provider: Google Gemini"));
                assertTrue(explain.contains("Unavailable (explanation generation failed)"));
                assertTrue(numerical.contains("Not run — AI stage failed (no answer generated)"));
                assertFalse(numerical.contains("Verified equations"));

                // Also verify interpretation failure case leaves both unattributed to Gemini even in CLOUD_ONLY.
                view.renderWorkflowResult(new AIWorkflowResult(
                        AIWorkflowResult.Status.PROVIDER_ERROR,
                        "Q",
                        null,
                        null,
                        null,
                        null,
                        null,
                        AIProviderException.Kind.NETWORK,
                        "provider-request-failed"));
                assertTrue(lookup(view, "aiInterpretationAttributionText", Label.class).getText()
                        .contains("Unavailable (interpretation failed or not recorded)"));
                assertTrue(lookup(view, "aiExplanationAttributionText", Label.class).getText()
                        .contains("Not run (interpretation failed first)"));
                assertFalse(lookup(view, "aiInterpretationAttributionText", Label.class).getText()
                        .contains("Google Gemini"));
                return null;
            });
        } finally {
            runOnFxThread(() -> {
                view.dispose();
                return null;
            });
        }
    }

    @Test
    void fallbackReasonSanitizationRemovesExceptionNamesStackTracesAndUrls() throws Exception {
        LocalAIProvider local = new LocalAIProvider();
        List<ProviderResult.FallbackHop> hops = List.of(
                new ProviderResult.FallbackHop(
                        ProviderResult.ProviderId.GEMINI,
                        AIProviderException.Kind.NETWORK,
                        true,
                        "Could not reach Gemini (UnknownHostException: generativelanguage.googleapis.com)\n\tat edu.physics.Fake.run(Fake.java:42)"),
                new ProviderResult.FallbackHop(
                        ProviderResult.ProviderId.OLLAMA,
                        AIProviderException.Kind.NETWORK,
                        true,
                        "java.net.ConnectException: Connection refused at http://127.0.0.1:11434"));

        AIProvider sanitizedProvider = fixedAttributionProvider(
                local,
                new ProviderResult.ProviderAttribution(
                        ProviderResult.ProviderId.LOCAL_RULE_ENGINE, "local-physics-engine", true,
                        ProviderResult.ProviderId.OLLAMA, "ignored", hops),
                new ProviderResult.ProviderAttribution(
                        ProviderResult.ProviderId.LOCAL_RULE_ENGINE, "local-physics-engine", false,
                        null, null, List.of()));

        AIProblemView view = runSolvedQuestion(sanitizedProvider);
        try {
            runOnFxThread(() -> {
                String interp = lookup(view, "aiInterpretationAttributionText", Label.class).getText();
                assertTrue(interp.contains("1. Google Gemini — service unreachable: Could not reach Gemini"));
                assertTrue(interp.contains("2. Ollama (local AI) — service unreachable: Connection refused at configured endpoint"));
                assertFalse(interp.contains("UnknownHostException"));
                assertFalse(interp.contains("ConnectException"));
                assertFalse(interp.contains("Fake.java"));
                assertFalse(interp.contains("http://127.0.0.1:11434"));
                return null;
            });
        } finally {
            runOnFxThread(() -> {
                view.dispose();
                return null;
            });
        }
    }

    @Test
    void numericalVerificationReflectsRejectedAndClarificationWorkflowOutcomes() throws Exception {
        LocalAIProvider local = new LocalAIProvider();
        AIProvider ungroundedExplanationProvider = new AIProvider() {
            @Override
            public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
                return local.interpret(request).thenApply(res -> new ProviderResult<>(
                        res.content(), res.requestId(), "qwen2.5:7b", res.usage()));
            }

            @Override
            public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
                ExplanationDraft invalidDraft = new ExplanationDraft(List.of(
                        new ExplanationDraft.Section("Claim", List.of(
                                new ExplanationDraft.Claim("The answer is 999 m.", null)))));
                return CompletableFuture.completedFuture(new ProviderResult<>(
                        invalidDraft, "req-bad-explain", "gemini-3.1-flash-lite", new ProviderResult.Usage(1, 1)));
            }
        };

        AIProblemView view = runOnFxThread(() -> {
            AIProblemView created = new AIProblemView(() -> ungroundedExplanationProvider);
            new Scene(created, 900, 650);
            lookup(created, "aiQuestionInput", TextArea.class)
                    .setText("A ball is projected at 20 m/s at 45 degrees with g = 10 m/s^2. Find its range.");
            lookup(created, "aiSubmitButton", Button.class).fire();
            return created;
        });

        try {
            Button submit = lookup(view, "aiSubmitButton", Button.class);
            Label status = lookup(view, "aiStatus", Label.class);
            waitUntilFx(() -> !submit.isDisabled() && status.getText().contains("unsupported claims"));

            runOnFxThread(() -> {
                String numerical = lookup(view, "aiNumericalAttributionText", Label.class).getText();
                String explain = lookup(view, "aiExplanationAttributionText", Label.class).getText();
                String interp = lookup(view, "aiInterpretationAttributionText", Label.class).getText();

                assertTrue(interp.contains("Ollama (local AI)"));
                assertTrue(explain.contains("Google Gemini"));
                assertTrue(numerical.contains("Numerical results verified"));
                assertTrue(numerical.contains("AI explanation rejected"));

                // Verify SOLVER_REJECTED never implies verified results exist.
                view.renderWorkflowResult(new AIWorkflowResult(
                        AIWorkflowResult.Status.SOLVER_REJECTED,
                        "Q",
                        new ProviderResult<>(
                                new InterpretationDraft("Q", List.of(), List.of(), List.of(), 0.9),
                                "r1",
                                "local-physics-engine",
                                new ProviderResult.Usage(0, 0)),
                        new ProblemValidationResult(ProblemValidationResult.Status.INVALID, null, List.of(), List.of("err"), List.of()),
                        new SolverOrchestrator.OrchestrationResult(SolverOrchestrator.Status.REJECTED, null, null, null, "solver-output-rejected"),
                        null,
                        null,
                        null,
                        "solver-output-rejected"));
                String rejectedNumerical = lookup(view, "aiNumericalAttributionText", Label.class).getText();
                assertTrue(rejectedNumerical.contains("Rejected — no numerical answer verified or accepted"));
                assertFalse(rejectedNumerical.contains("Verified equations"));
                assertTrue(lookup(view, "aiExplanationAttributionText", Label.class).getText()
                        .contains("Not run (stage not reached)"));
                assertTrue(lookup(view, "aiNumericalAttribution", Node.class)
                        .getStyleClass().contains("ai-attribution-card-error"));

                // Verify NEEDS_CLARIFICATION leaves numerical verification unverified.
                view.renderWorkflowResult(new AIWorkflowResult(
                        AIWorkflowResult.Status.NEEDS_CLARIFICATION,
                        "Q",
                        new ProviderResult<>(
                                new InterpretationDraft("Q", List.of(), List.of(), List.of(), 0.9),
                                "r2",
                                "local-physics-engine",
                                new ProviderResult.Usage(0, 0)),
                        new ProblemValidationResult(
                                ProblemValidationResult.Status.NEEDS_CLARIFICATION,
                                null,
                                List.of(new InterpretationDraft.Clarification("What is g?", "Missing g", List.of("9.81", "10"))),
                                List.of(),
                                List.of()),
                        null,
                        null,
                        null,
                        null,
                        "clarification-required"));
                assertTrue(lookup(view, "aiNumericalAttributionText", Label.class).getText()
                        .contains("Not run yet — awaiting clarification"));
                return null;
            });
        } finally {
            runOnFxThread(() -> {
                view.dispose();
                return null;
            });
        }
    }

    @Test
    void populatedTwoStageFallbackAttributionFitsWithinMinimum1120x700Workspace() throws Exception {
        runOnFxThread(() -> {
            Stage stage = new Stage();
            ProjectileSimulatorApp app = new ProjectileSimulatorApp();
            app.start(stage);
            PhysoraApplicationShell shell = (PhysoraApplicationShell) stage.getScene().getRoot();
            try {
                Parent root = (Parent) stage.getScene().getRoot();
                ToggleButton aiTab = (ToggleButton) root.lookup("#aiProblemTab");
                assertNotNull(aiTab);
                aiTab.fire();

                AIProblemView aiView = (AIProblemView) root.lookup("#aiProblemView");
                assertNotNull(aiView);

                List<ProviderResult.FallbackHop> twoHops = List.of(
                        new ProviderResult.FallbackHop(ProviderResult.ProviderId.GEMINI,
                                AIProviderException.Kind.RATE_LIMITED, true, "Rate limit reached"),
                        new ProviderResult.FallbackHop(ProviderResult.ProviderId.OLLAMA,
                                AIProviderException.Kind.NETWORK, true, "Local service unreachable"));
                LocalAIProvider local = new LocalAIProvider();
                ProviderResult<InterpretationDraft> interp = local.interpret(
                        new InterpretationRequest("A ball is projected at 20 m/s at 45 degrees with g = 10 m/s^2. Find its range."))
                        .toCompletableFuture().join()
                        .withAttribution(new ProviderResult.ProviderAttribution(
                                ProviderResult.ProviderId.LOCAL_RULE_ENGINE, "local-physics-engine",
                                true, ProviderResult.ProviderId.OLLAMA, "fallback", twoHops));
                ProblemValidationResult valid = new ProblemValidator().validate(interp.content().normalizedQuestion(), interp.content());
                SolverOrchestrator.OrchestrationResult orch = new SolverOrchestrator(
                        new ProjectileProblemSolver(), new SolutionVerifier()).solve(valid);
                ProviderResult<ExplanationDraft> explain = local.explain(
                        new ExplanationRequest(interp.content().normalizedQuestion(), orch.solution()))
                        .toCompletableFuture().join()
                        .withAttribution(new ProviderResult.ProviderAttribution(
                                ProviderResult.ProviderId.LOCAL_RULE_ENGINE, "local-physics-engine",
                                true, ProviderResult.ProviderId.GEMINI, "fallback", twoHops));

                aiView.renderWorkflowResult(new AIWorkflowResult(
                        AIWorkflowResult.Status.COMPLETE,
                        interp.content().normalizedQuestion(),
                        interp,
                        valid,
                        orch,
                        explain,
                        new ExplanationVerifier().verify(explain.content(), orch.solution()),
                        null,
                        "complete"));

                stage.setWidth(1120);
                stage.setHeight(700);
                root.resize(1120, 700);
                root.applyCss();
                root.layout();

                assertControlsFitInsideView(aiView);
                return null;
            } finally {
                if (stage.isShowing()) {
                    stage.close();
                }
                shell.dispose();
            }
        });
    }

    private static AIProblemView runSolvedQuestion(AIProvider provider) throws Exception {
        AIProblemView view = runOnFxThread(() -> {
            AIProblemView created = new AIProblemView(() -> provider);
            new Scene(created, 900, 650);
            lookup(created, "aiQuestionInput", TextArea.class)
                    .setText("A ball is projected at 20 m/s at 45 degrees with g = 10 m/s^2. Find its range.");
            lookup(created, "aiSubmitButton", Button.class).fire();
            return created;
        });
        Button submit = lookup(view, "aiSubmitButton", Button.class);
        Label status = lookup(view, "aiStatus", Label.class);
        waitUntilFx(() -> !submit.isDisabled() && status.getText().contains("passed validation"));
        return view;
    }

    private static AIProvider fixedAttributionProvider(LocalAIProvider delegate,
                                                       ProviderResult.ProviderAttribution interpAttribution,
                                                       ProviderResult.ProviderAttribution explainAttribution) {
        return new AIProvider() {
            @Override
            public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
                return delegate.interpret(request).thenApply(res -> new ProviderResult<>(
                        res.content(), res.requestId(), interpAttribution.model(), res.usage(), interpAttribution));
            }

            @Override
            public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
                return delegate.explain(request).thenApply(res -> new ProviderResult<>(
                        res.content(), res.requestId(), explainAttribution.model(), res.usage(), explainAttribution));
            }

            @Override
            public void close() {
                delegate.close();
            }
        };
    }

    private static AIProvider failingProvider(AIProviderException.Kind kind, boolean retryable, String message) {
        return new AIProvider() {
            @Override
            public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
                return CompletableFuture.failedFuture(new AIProviderException(kind, retryable, message));
            }

            @Override
            public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
                return CompletableFuture.failedFuture(new AIProviderException(kind, retryable, message));
            }
        };
    }

    private static <T extends Node> T lookup(AIProblemView root, String id, Class<T> type) throws Exception {
        return runOnFxThread(() -> {
            Node found = root.lookup("#" + id);
            assertNotNull(found, "Missing JavaFX node with id " + id);
            return type.cast(found);
        });
    }

    private static void waitUntilFx(Callable<Boolean> condition) throws Exception {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadlineNanos) {
            if (Boolean.TRUE.equals(runOnFxThread(condition))) {
                return;
            }
            Thread.sleep(25);
        }
        fail("Timed out waiting for JavaFX condition");
    }

    private static <T> T runOnFxThread(Callable<T> work) throws Exception {
        return FxTestRuntime.runOnFxThread(work);
    }
}

