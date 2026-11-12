package edu.physics.ui;

import edu.physics.ai.AIConversationService;
import edu.physics.ai.AIProblemWorkflow;
import edu.physics.ai.AIWorkflowResult;
import edu.physics.ai.ExplanationDraft;
import edu.physics.ai.ExplanationVerifier;
import edu.physics.ai.InterpretationDraft;
import edu.physics.ai.SolutionVerifier;
import edu.physics.ai.ProblemValidator;
import edu.physics.ai.SolverOrchestrator;
import edu.physics.ai.AIProvider;
import edu.physics.ai.AIProviderException;
import edu.physics.ai.AIProviderManagerFactory;
import edu.physics.ai.ProviderMode;
import edu.physics.ai.ProviderResult;
import edu.physics.ai.gemini.GeminiProviderConfiguration;
import edu.physics.ai.ollama.OllamaConfiguration;
import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.ProjectileSequenceSolution;
import edu.physics.problem.ProjectileSolution;
import edu.physics.problem.ProjectileProblemSolver;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Minimal natural-language workflow UI; all model work runs asynchronously off the JavaFX thread. */
final class AIProblemView extends BorderPane {
    private static final String DEFAULT_GEMINI_MODEL = "gemini-3.1-flash-lite";
    private static final Pattern STACK_TRACE_BOUNDARY = Pattern.compile("\\R\\s*at\\s+|\\s+at\\s+[a-z_][\\w.$]*\\([^)]+\\)");
    private static final Pattern EXCEPTION_PREFIX = Pattern.compile("(?:[a-z_][\\w$]*\\.)*[A-Z][\\w$]*(?:Exception|Error)\\b\\s*:?\\s*");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s)]+");
    private static final Pattern EMPTY_PARENS = Pattern.compile("\\(\\s*[:;,-]?\\s*\\)");

    private final Map<String, String> baseEnvironment;
    private final Function<Map<String, String>, AIProvider> providerFactory;
    private final Function<AIProvider, AIConversationService> conversationServiceFactory;
    private final boolean environmentBacked;
    private final ComboBox<ProviderMode> modeSelector = new ComboBox<>();
    private final CheckBox ollamaToggle = new CheckBox("Enable optional Ollama (local LLM)");
    private final TextField ollamaModelInput = new TextField();
    private final HBox ollamaModelRow = new HBox(8);
    private final Label modeDescriptionLabel = new Label();
    private final Label configSummaryLabel = new Label();
    private final GridPane attributionPanel = new GridPane();
    private final Label interpretationAttributionBody = new Label();
    private final Label explanationAttributionBody = new Label();
    private final Label numericalAttributionBody = new Label();
    private final VBox interpretationCard = attributionCard(
            "aiInterpretationAttribution", "PROBLEM INTERPRETATION", interpretationAttributionBody);
    private final VBox explanationCard = attributionCard(
            "aiExplanationAttribution", "SOLUTION EXPLANATION", explanationAttributionBody);
    private final VBox numericalCard = attributionCard(
            "aiNumericalAttribution", "NUMERICAL VERIFICATION", numericalAttributionBody);
    private final TextArea questionInput = new TextArea();
    private final Label statusLabel = new Label("Enter a projectile-motion question to begin.");
    private final VBox clarificationBox = new VBox(10);
    private final VBox clarificationFields = new VBox(9);
    private final TextArea solutionOutput = new TextArea();
    private final Button submitButton = new Button("INTERPRET AND SOLVE");
    private final Button answerButton = new Button("SUBMIT CLARIFICATION");
    private final Button cancelButton = new Button("CANCEL REQUEST");
    private AIConversationService conversationService;
    private AIProvider provider;
    private UUID sessionId;
    private long viewRevision;
    private boolean disposed;
    private boolean requestInProgress;
    private boolean initializingControls;

    AIProblemView() {
        this(System.getenv());
    }

    AIProblemView(Map<String, String> baseEnvironment) {
        this(baseEnvironment, AIProviderManagerFactory::createFromEnvironment);
    }

    AIProblemView(Map<String, String> baseEnvironment,
                  Function<Map<String, String>, AIProvider> providerFactory) {
        this(baseEnvironment, providerFactory, AIProblemView::defaultConversationService, true);
    }

    AIProblemView(Map<String, String> baseEnvironment,
                  Function<Map<String, String>, AIProvider> providerFactory,
                  Function<AIProvider, AIConversationService> conversationServiceFactory) {
        this(baseEnvironment, providerFactory, conversationServiceFactory, true);
    }

    AIProblemView(Supplier<AIProvider> providerSupplier) {
        this(providerSupplier, AIProblemView::defaultConversationService);
    }

    AIProblemView(Supplier<AIProvider> providerSupplier,
                  Function<AIProvider, AIConversationService> conversationServiceFactory) {
        this(Map.of(), adaptSupplier(providerSupplier), conversationServiceFactory, false);
    }

    private AIProblemView(Map<String, String> baseEnvironment,
                          Function<Map<String, String>, AIProvider> providerFactory,
                          Function<AIProvider, AIConversationService> conversationServiceFactory,
                          boolean environmentBacked) {
        if (providerFactory == null) {
            throw new IllegalArgumentException("AI provider factory is required");
        }
        if (conversationServiceFactory == null) {
            throw new IllegalArgumentException("Conversation service factory is required");
        }
        this.baseEnvironment = baseEnvironment == null
                ? Map.of()
                : Collections.unmodifiableMap(new HashMap<>(baseEnvironment));
        this.providerFactory = providerFactory;
        this.conversationServiceFactory = conversationServiceFactory;
        this.environmentBacked = environmentBacked;
        setId("aiProblemView");
        getStyleClass().add("ai-problem-view");
        setPadding(new Insets(8, 20, 8, 20));

        Label title = new Label("ASK THE PROJECTILE MODEL");
        title.getStyleClass().add("simulation-heading");
        Label subtitle = new Label("Interpret the question, confirm unclear details, then inspect the verified calculation.");
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("muted-copy");

        VBox configSection = buildConfigurationSection();
        buildAttributionPanel();
        resetAttributionDisplay(false);

        questionInput.setId("aiQuestionInput");
        questionInput.setPromptText("Example: A projectile is launched at 20 m/s at 45 degrees. Find its range.");
        questionInput.setWrapText(true);
        questionInput.setPrefRowCount(3);
        questionInput.setMinHeight(58);
        questionInput.setPrefHeight(60);

        submitButton.setId("aiSubmitButton");
        submitButton.getStyleClass().add("transport-primary");
        submitButton.setOnAction(event -> submitQuestion());
        cancelButton.setId("aiCancelButton");
        cancelButton.getStyleClass().add("transport-secondary");
        cancelButton.setDisable(true);
        cancelButton.setOnAction(event -> cancelRequest());
        answerButton.setId("aiClarificationButton");
        answerButton.getStyleClass().add("transport-primary");
        answerButton.setOnAction(event -> submitClarification());
        answerButton.setVisible(false);
        answerButton.setManaged(false);
        HBox actions = new HBox(9, submitButton, answerButton, cancelButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        statusLabel.setId("aiStatus");
        statusLabel.setWrapText(true);
        statusLabel.getStyleClass().add("ai-status");

        clarificationBox.setId("aiClarificationBox");
        clarificationBox.setVisible(false);
        clarificationBox.setManaged(false);
        clarificationBox.getStyleClass().add("ai-clarification-box");
        clarificationBox.setPadding(new Insets(10));
        clarificationBox.getChildren().addAll(new Label("CLARIFICATION NEEDED"), clarificationFields, answerButton);

        solutionOutput.setId("aiSolutionOutput");
        solutionOutput.setEditable(false);
        solutionOutput.setWrapText(true);
        solutionOutput.setPrefRowCount(7);
        solutionOutput.setMinHeight(85);
        solutionOutput.getStyleClass().add("ai-solution-output");

        VBox inputSection = new VBox(5, eyebrow("PROBLEM INPUT"), questionInput, actions, statusLabel, clarificationBox);
        inputSection.getStyleClass().add("ai-input-section");
        inputSection.setPadding(new Insets(8, 10, 8, 10));
        VBox resultSection = new VBox(5, eyebrow("VERIFIED SOLUTION"), attributionPanel, solutionOutput);
        VBox.setVgrow(solutionOutput, Priority.ALWAYS);
        VBox content = new VBox(6, new VBox(2, title, subtitle), configSection, inputSection, resultSection);
        VBox.setVgrow(resultSection, Priority.ALWAYS);
        setCenter(content);
    }

    private void buildAttributionPanel() {
        attributionPanel.setId("aiAttributionPanel");
        attributionPanel.getStyleClass().add("ai-attribution-panel");
        attributionPanel.setHgap(8);
        attributionPanel.getColumnConstraints().clear();
        for (int i = 0; i < 3; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(100.0 / 3.0);
            col.setHgrow(Priority.ALWAYS);
            col.setFillWidth(true);
            attributionPanel.getColumnConstraints().add(col);
        }
        interpretationCard.setMaxHeight(Double.MAX_VALUE);
        explanationCard.setMaxHeight(Double.MAX_VALUE);
        numericalCard.setMaxHeight(Double.MAX_VALUE);
        attributionPanel.addRow(0, interpretationCard, explanationCard, numericalCard);
    }

    private static VBox attributionCard(String id, String titleText, Label bodyLabel) {
        Label heading = new Label(titleText);
        heading.getStyleClass().add("ai-attribution-title");
        bodyLabel.setId(id + "Text");
        bodyLabel.setWrapText(true);
        bodyLabel.getStyleClass().add("ai-attribution-body");
        VBox card = new VBox(2, heading, bodyLabel);
        card.setId(id);
        card.getStyleClass().add("ai-attribution-card");
        return card;
    }

    private VBox buildConfigurationSection() {
        initializingControls = true;
        List<String> startupWarnings = new ArrayList<>();
        ProviderMode initialMode = resolveInitialMode(baseEnvironment, startupWarnings);
        boolean initialOllamaEnabled = resolveInitialOllamaEnabled(baseEnvironment, startupWarnings);
        String initialOllamaModel = resolveInitialOllamaModel(baseEnvironment);

        modeSelector.setId("aiModeSelector");
        modeSelector.getStyleClass().add("ai-mode-combo");
        modeSelector.getItems().setAll(ProviderMode.AUTO, ProviderMode.CLOUD_ONLY, ProviderMode.LOCAL_AI, ProviderMode.OFFLINE);
        modeSelector.setConverter(new StringConverter<>() {
            @Override
            public String toString(ProviderMode mode) {
                return mode == null ? "" : modeLabel(mode);
            }

            @Override
            public ProviderMode fromString(String value) {
                return ProviderMode.parse(value);
            }
        });
        modeSelector.setValue(initialMode);
        modeSelector.valueProperty().addListener((obs, oldValue, newValue) -> handleConfigurationChanged(true));

        ollamaToggle.setId("aiOllamaToggle");
        ollamaToggle.getStyleClass().add("vector-toggle");
        ollamaToggle.setSelected(initialOllamaEnabled);
        ollamaToggle.selectedProperty().addListener((obs, oldValue, newValue) -> handleConfigurationChanged(true));

        HBox primaryControls = new HBox(12, modeSelector, ollamaToggle);
        primaryControls.setAlignment(Pos.CENTER_LEFT);

        Label modelLabel = new Label("Ollama model");
        modelLabel.getStyleClass().add("parameter-name");
        ollamaModelInput.setId("aiOllamaModelInput");
        ollamaModelInput.setPromptText(OllamaConfiguration.DEFAULT_MODEL);
        ollamaModelInput.setText(initialOllamaModel);
        ollamaModelInput.setPrefColumnCount(20);
        ollamaModelInput.textProperty().addListener((obs, oldValue, newValue) -> handleConfigurationChanged(false));
        ollamaModelRow.getChildren().setAll(modelLabel, ollamaModelInput);
        ollamaModelRow.setAlignment(Pos.CENTER_LEFT);

        modeDescriptionLabel.setId("aiModeDescription");
        modeDescriptionLabel.setWrapText(true);
        modeDescriptionLabel.getStyleClass().add("muted-copy");

        configSummaryLabel.setId("aiConfigSummary");
        configSummaryLabel.setWrapText(true);
        configSummaryLabel.getStyleClass().add("ai-config-summary");

        VBox section = new VBox(6, eyebrow("PROVIDER SETTINGS"), primaryControls, ollamaModelRow,
                modeDescriptionLabel, configSummaryLabel);
        section.getStyleClass().add("ai-config-section");

        refreshConfigurationDisplay();
        initializingControls = false;

        if (!startupWarnings.isEmpty()) {
            setError("Ignored invalid environment setting (" + String.join("; ", startupWarnings)
                    + "). Using " + initialMode.name() + " with Ollama "
                    + (initialOllamaEnabled ? "enabled" : "disabled") + ".");
        }
        return section;
    }

    private void handleConfigurationChanged(boolean resetStatusMessage) {
        if (initializingControls) {
            return;
        }
        refreshConfigurationDisplay();
        if (requestInProgress) {
            return;
        }
        closeActiveSession();
        hideClarifications();
        resetAttributionDisplay(false);
        if (resetStatusMessage || statusLabel.getStyleClass().contains("ai-status-error")) {
            setStatus("Configuration updated (" + selectedMode().name()
                    + "). Submit a question to initialize lazily.", false);
        }
    }

    private void refreshConfigurationDisplay() {
        ProviderMode mode = selectedMode();
        boolean applicable = ollamaApplicable(mode);
        boolean ollamaEnabled = ollamaToggle.isSelected();
        boolean showOllamaModel = applicable && ollamaEnabled;

        modeSelector.setDisable(requestInProgress);
        ollamaToggle.setDisable(requestInProgress || !applicable);
        ollamaModelInput.setDisable(requestInProgress || !showOllamaModel);
        ollamaModelInput.setVisible(showOllamaModel);
        ollamaModelInput.setManaged(showOllamaModel);
        ollamaModelRow.setVisible(showOllamaModel);
        ollamaModelRow.setManaged(showOllamaModel);

        modeDescriptionLabel.setText(modeDescription(mode));
        configSummaryLabel.setText(buildConfigSummary(mode, ollamaEnabled, applicable));
    }

    private String buildConfigSummary(ProviderMode mode, boolean ollamaEnabled, boolean applicable) {
        boolean keyConfigured = GeminiProviderConfiguration.apiKeyConfigured(baseEnvironment);
        String geminiKeyText = keyConfigured ? "Configured" : "Not configured";
        String geminiModelText = formatGeminiModel(baseEnvironment);
        String ollamaText;
        if (!applicable) {
            ollamaText = "Ollama: Inactive in " + mode.name()
                    + " (configured: " + (ollamaEnabled ? "Enabled" : "Disabled") + ")";
        } else if (!ollamaEnabled) {
            ollamaText = "Ollama: Disabled (optional; runtime verification deferred until request)";
        } else {
            String modelText = trimmedOllamaModel();
            String modelDisplay = modelText.isEmpty() ? "Not set (enter model name)" : modelText;
            ollamaText = "Ollama: Enabled · Ollama model: " + modelDisplay
                    + " (runtime verification deferred until request)";
        }
        return "Gemini key: " + geminiKeyText + " · Gemini model: " + geminiModelText + "\n" + ollamaText;
    }

    private ProviderMode selectedMode() {
        ProviderMode value = modeSelector.getValue();
        return value != null ? value : ProviderMode.AUTO;
    }

    private String trimmedOllamaModel() {
        String raw = ollamaModelInput.getText();
        return raw == null ? "" : raw.trim();
    }

    private Map<String, String> effectiveEnvironment() {
        Map<String, String> effective = new HashMap<>(baseEnvironment);
        ProviderMode mode = selectedMode();
        boolean ollamaEnabled = ollamaToggle.isSelected();
        effective.put("PHYSORA_AI_MODE", mode.name());
        effective.put("PHYSORA_OLLAMA_ENABLED", Boolean.toString(ollamaEnabled));
        if (ollamaEnabled && ollamaApplicable(mode)) {
            effective.put("PHYSORA_OLLAMA_MODEL", trimmedOllamaModel());
        }
        return Collections.unmodifiableMap(effective);
    }

    private void validateUiConfiguration() {
        ProviderMode mode = selectedMode();
        if (ollamaApplicable(mode) && ollamaToggle.isSelected() && trimmedOllamaModel().isEmpty()) {
            throw new IllegalArgumentException(
                    "Ollama model is required when Ollama is enabled (set PHYSORA_OLLAMA_MODEL or enter a model name)");
        }
    }

    private static Function<Map<String, String>, AIProvider> adaptSupplier(Supplier<AIProvider> providerSupplier) {
        if (providerSupplier == null) {
            throw new IllegalArgumentException("AI provider supplier is required");
        }
        return env -> providerSupplier.get();
    }

    private static ProviderMode resolveInitialMode(Map<String, String> env, List<String> warnings) {
        String rawMode = env.get("PHYSORA_AI_MODE");
        if (rawMode != null && !rawMode.isBlank()) {
            try {
                return ProviderMode.parse(rawMode);
            } catch (IllegalArgumentException ex) {
                warnings.add(ex.getMessage());
                return ProviderMode.AUTO;
            }
        }
        String legacy = env.get("PHYSORA_AI_PROVIDER");
        if (legacy == null || legacy.isBlank() || "gemini".equalsIgnoreCase(legacy.trim())) {
            return ProviderMode.AUTO;
        }
        if ("local".equalsIgnoreCase(legacy.trim())) {
            return ProviderMode.OFFLINE;
        }
        warnings.add("Unsupported PHYSORA_AI_PROVIDER: " + legacy);
        return ProviderMode.AUTO;
    }

    private static boolean resolveInitialOllamaEnabled(Map<String, String> env, List<String> warnings) {
        String raw = env.get("PHYSORA_OLLAMA_ENABLED");
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        warnings.add("PHYSORA_OLLAMA_ENABLED must be true or false");
        return false;
    }

    private static String resolveInitialOllamaModel(Map<String, String> env) {
        if (!env.containsKey("PHYSORA_OLLAMA_MODEL")) {
            return OllamaConfiguration.DEFAULT_MODEL;
        }
        String configured = env.get("PHYSORA_OLLAMA_MODEL");
        return configured == null ? "" : configured.trim();
    }

    private static String formatGeminiModel(Map<String, String> env) {
        if (!env.containsKey("PHYSORA_GEMINI_MODEL")) {
            return DEFAULT_GEMINI_MODEL + " (default)";
        }
        String configured = env.get("PHYSORA_GEMINI_MODEL");
        return configured == null || configured.isBlank()
                ? "Not set (invalid blank PHYSORA_GEMINI_MODEL)"
                : configured.trim();
    }

    private static boolean ollamaApplicable(ProviderMode mode) {
        return mode == ProviderMode.AUTO || mode == ProviderMode.LOCAL_AI;
    }

    private static String modeLabel(ProviderMode mode) {
        return switch (mode) {
            case AUTO -> "Auto (AUTO)";
            case CLOUD_ONLY -> "Cloud Only (CLOUD_ONLY)";
            case LOCAL_AI -> "Local AI (LOCAL_AI)";
            case OFFLINE -> "Offline (OFFLINE)";
        };
    }

    private static String modeDescription(ProviderMode mode) {
        return switch (mode) {
            case AUTO -> "Selected mode: AUTO — Tries Google Gemini (if configured), then optional Ollama (if enabled), then the offline parser. Selecting AUTO does not guarantee Gemini or Ollama was used; actual provider choice is resolved per request.";
            case CLOUD_ONLY -> "Selected mode: CLOUD_ONLY — Uses Google Gemini exclusively (requires GOOGLE_API_KEY). Never falls back silently to Ollama or offline parsing.";
            case LOCAL_AI -> "Selected mode: LOCAL_AI — Tries optional local Ollama (if enabled) with fallback to the offline parser. Never contacts Google Gemini.";
            case OFFLINE -> "Selected mode: OFFLINE — Uses only the built-in rule-based local parser with zero network calls and no external AI service.";
        };
    }

    private void submitQuestion() {
        String question = questionInput.getText() == null ? "" : questionInput.getText().trim();
        if (question.isBlank()) {
            setError("Enter a question before submitting.");
            return;
        }
        if (!initializeProvider()) {
            return;
        }
        setBusy(true, "Interpreting and solving… please wait");
        viewRevision++;
        if (sessionId == null) {
            AIConversationService.Submission submission;
            try {
                submission = conversationService.submit(question);
            } catch (RuntimeException ex) {
                setBusy(false, "");
                setError("Could not start the problem-solving request.");
                return;
            }
            sessionId = submission.sessionId();
            observe(submission.completion(), viewRevision);
        } else {
            try {
                observe(conversationService.revise(sessionId, question), viewRevision);
            } catch (RuntimeException ex) {
                setBusy(false, "");
                setError("Could not update the problem-solving request.");
                return;
            }
        }
        hideClarifications();
        solutionOutput.clear();
        resetAttributionDisplay(true);
    }

    private void submitClarification() {
        if (sessionId == null || conversationService == null) return;
        List<String> answers = new ArrayList<>();
        for (var node : clarificationFields.getChildren()) {
            if (node instanceof VBox wrapper && wrapper.getChildren().size() > 1
                    && wrapper.getChildren().get(1) instanceof TextField field) {
                String answer = field.getText() == null ? "" : field.getText().trim();
                if (answer.isBlank()) {
                    setError("Answer each clarification before continuing.");
                    return;
                }
                answers.add(answer);
            }
        }
        viewRevision++;
        setBusy(true, "Updating the interpretation and recalculating…");
        resetAttributionDisplay(true);
        try {
            observe(conversationService.answerClarifications(sessionId, answers), viewRevision);
        } catch (RuntimeException ex) {
            setBusy(false, "");
            setError("Could not submit clarifications.");
            resetAttributionDisplay(false);
        }
    }

    private boolean initializeProvider() {
        if (conversationService != null) return true;
        AIProvider createdProvider = null;
        try {
            if (environmentBacked) {
                validateUiConfiguration();
            }
            createdProvider = providerFactory.apply(effectiveEnvironment());
            if (createdProvider == null) {
                throw new IllegalStateException("AI provider factory returned null.");
            }
            AIConversationService createdService = conversationServiceFactory.apply(createdProvider);
            if (createdService == null) {
                throw new IllegalStateException("Conversation service factory returned null.");
            }
            this.provider = createdProvider;
            this.conversationService = createdService;
            return true;
        } catch (AIProviderException ex) {
            closeProviderOnFailure(createdProvider, ex);
            clearInitializationState();
            String message = ex.getMessage();
            setError(message != null && !message.isBlank() ? message : "The AI provider could not be initialized.");
            return false;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            closeProviderOnFailure(createdProvider, ex);
            clearInitializationState();
            setError(configurationErrorMessage(ex));
            return false;
        } catch (RuntimeException ex) {
            closeProviderOnFailure(createdProvider, ex);
            clearInitializationState();
            throw ex;
        }
    }

    private static AIConversationService defaultConversationService(AIProvider activeProvider) {
        AIProblemWorkflow workflow = new AIProblemWorkflow(activeProvider, new ProblemValidator(),
                new SolverOrchestrator(new ProjectileProblemSolver(), new SolutionVerifier()),
                new ExplanationVerifier());
        return new AIConversationService(workflow);
    }

    private static void closeProviderOnFailure(AIProvider createdProvider, RuntimeException primaryFailure) {
        if (createdProvider == null) return;
        try {
            createdProvider.close();
        } catch (RuntimeException closeFailure) {
            primaryFailure.addSuppressed(closeFailure);
        }
    }

    private void clearInitializationState() {
        provider = null;
        conversationService = null;
        sessionId = null;
    }

    private static String configurationErrorMessage(RuntimeException ex) {
        String detail = ex != null && ex.getMessage() != null ? ex.getMessage().trim() : "";
        if (detail.endsWith(".")) {
            detail = detail.substring(0, detail.length() - 1).trim();
        }
        String guidance = "Check PHYSORA_AI_MODE, PHYSORA_OLLAMA_ENABLED, GOOGLE_API_KEY, PHYSORA_GEMINI_MODEL, "
                + "or PHYSORA_OLLAMA_MODEL, or clear invalid settings to use defaults.";
        return detail.isEmpty()
                ? "Provider configuration is invalid or incomplete. " + guidance
                : "Provider configuration error: " + detail + ". " + guidance;
    }

    private void observe(java.util.concurrent.CompletableFuture<AIConversationService.SessionSnapshot> future,
                         long revision) {
        future.whenComplete((snapshot, failure) -> Platform.runLater(() -> {
            if (revision != viewRevision || isDisposed()) return;
            if (failure != null) {
                setBusy(false, "");
                setError("The request could not be completed.");
                resetAttributionDisplay(false);
                return;
            }
            if (!snapshot.applied()) return;
            setBusy(snapshot.inProgress(), snapshot.inProgress() ? "Working…" : "");
            render(snapshot.latest());
        }));
    }

    void renderWorkflowResult(AIWorkflowResult result) {
        render(result);
    }

    private void render(AIWorkflowResult result) {
        if (result == null) return;
        if (result.status() != AIWorkflowResult.Status.NEEDS_CLARIFICATION) {
            hideClarifications();
        }
        renderAttribution(result);
        switch (result.status()) {
            case NEEDS_CLARIFICATION -> {
                statusLabel.setText("The question has more than one plausible interpretation. Answer these before solving.");
                showClarifications(result.validation().clarifications());
            }
            case INVALID_PROBLEM -> {
                statusLabel.setText("The interpreted problem is invalid or incomplete.");
                solutionOutput.setText(String.join("\n", result.validation().errors()));
            }
            case SOLVER_REJECTED -> {
                statusLabel.setText("The deterministic solver did not produce a verifiable solution.");
                solutionOutput.setText(result.orchestration().verification() == null ? result.messageCode()
                        : String.join("\n", result.orchestration().verification().issues()));
            }
            case PROVIDER_ERROR -> {
                setError(providerMessage(result.providerError()));
                solutionOutput.setText(result.detailMessage() != null ? result.detailMessage() : "");
            }
            case EXPLANATION_REJECTED -> {
                statusLabel.setText("The calculation is verified, but the generated explanation contained unsupported claims.");
                solutionOutput.setText(renderSolution(result));
            }
            case COMPLETE -> {
                statusLabel.setText("Interpretation, deterministic solution, and explanation passed validation.");
                solutionOutput.setText(renderSolution(result));
            }
        }
    }

    private void renderAttribution(AIWorkflowResult result) {
        ProviderResult.ProviderAttribution interpretation = result.interpretationAttribution();
        ProviderResult.ProviderAttribution explanation = result.explanationAttribution();

        if (interpretation == null) {
            String missingText = result.status() == AIWorkflowResult.Status.PROVIDER_ERROR
                    ? "Provider: Unavailable (interpretation failed or not recorded)\nModel: Unavailable\nFallback: Unknown"
                    : "Provider: Unavailable (not recorded)\nModel: Unavailable\nFallback: Unknown";
            setAttributionCard(interpretationCard, interpretationAttributionBody, missingText, false, true);
        } else {
            setAttributionCard(
                    interpretationCard,
                    interpretationAttributionBody,
                    formatStageAttribution(interpretation),
                    interpretation.fallbackUsed(),
                    false);
        }

        if (explanation == null) {
            boolean failedInExplanation = result.status() == AIWorkflowResult.Status.PROVIDER_ERROR && interpretation != null;
            boolean missingOnCompletedStage = result.status() == AIWorkflowResult.Status.COMPLETE
                    || result.status() == AIWorkflowResult.Status.EXPLANATION_REJECTED;
            String missingText;
            if (result.status() == AIWorkflowResult.Status.PROVIDER_ERROR) {
                missingText = interpretation == null
                        ? "Provider: Not run (interpretation failed first)\nModel: Unavailable\nFallback: Not applicable"
                        : "Provider: Unavailable (explanation generation failed)\nModel: Unavailable\nFallback: Unknown";
            } else if (missingOnCompletedStage) {
                missingText = "Provider: Unavailable (not recorded)\nModel: Unavailable\nFallback: Unknown";
            } else {
                missingText = "Provider: Not run (stage not reached)\nModel: Unavailable\nFallback: Not applicable";
            }
            setAttributionCard(
                    explanationCard,
                    explanationAttributionBody,
                    missingText,
                    false,
                    failedInExplanation || missingOnCompletedStage);
        } else {
            setAttributionCard(
                    explanationCard,
                    explanationAttributionBody,
                    formatStageAttribution(explanation),
                    explanation.fallbackUsed(),
                    false);
        }

        boolean numericalRejected = result.status() == AIWorkflowResult.Status.SOLVER_REJECTED;
        String numericalText = switch (result.status()) {
            case COMPLETE ->
                    "Authority: PHYSORA Deterministic Physics Solver\nStatus: Verified equations, units, and numerical results";
            case EXPLANATION_REJECTED ->
                    "Authority: PHYSORA Deterministic Physics Solver\nStatus: Numerical results verified (AI explanation rejected for unsupported claims)";
            case SOLVER_REJECTED ->
                    "Authority: PHYSORA Deterministic Physics Solver\nStatus: Rejected — no numerical answer verified or accepted";
            case NEEDS_CLARIFICATION ->
                    "Authority: PHYSORA Deterministic Physics Solver\nStatus: Not run yet — awaiting clarification (no answer generated)";
            case INVALID_PROBLEM ->
                    "Authority: PHYSORA Deterministic Physics Solver\nStatus: Not run — invalid or incomplete problem (no answer generated)";
            case PROVIDER_ERROR ->
                    "Authority: PHYSORA Deterministic Physics Solver\nStatus: Not run — AI stage failed (no answer generated)";
        };
        setAttributionCard(numericalCard, numericalAttributionBody, numericalText, false, numericalRejected);
    }

    private void resetAttributionDisplay(boolean inProgress) {
        String interp = inProgress ? "Pending — interpreting question…" : "Not run yet";
        String explain = inProgress ? "Pending — waiting for verified solution…" : "Not run yet";
        String numerical = inProgress
                ? "Authority: PHYSORA Deterministic Physics Solver\nStatus: Waiting for validated physics problem…"
                : "Authority: PHYSORA Deterministic Physics Solver\nStatus: Ready (numerical results are always computed deterministically, never by AI)";
        setAttributionCard(interpretationCard, interpretationAttributionBody, interp, false, false);
        setAttributionCard(explanationCard, explanationAttributionBody, explain, false, false);
        setAttributionCard(numericalCard, numericalAttributionBody, numerical, false, false);
    }

    private static void setAttributionCard(VBox card, Label body, String text, boolean fallback, boolean error) {
        body.setText(text);
        card.getStyleClass().remove("ai-attribution-card-fallback");
        card.getStyleClass().remove("ai-attribution-card-error");
        if (error) {
            card.getStyleClass().add("ai-attribution-card-error");
        } else if (fallback) {
            card.getStyleClass().add("ai-attribution-card-fallback");
        }
    }

    private static String formatStageAttribution(ProviderResult.ProviderAttribution attribution) {
        String providerText = providerDisplayName(attribution.provider());
        String modelText = attribution.model() == null || attribution.model().isBlank()
                ? "Unavailable"
                : attribution.model().trim();
        return "Provider: " + providerText + "\nModel: " + modelText + "\n" + formatFallbackLine(attribution);
    }

    private static String formatFallbackLine(ProviderResult.ProviderAttribution attribution) {
        if (!attribution.fallbackUsed()) {
            return "Fallback: No";
        }
        List<ProviderResult.FallbackHop> trail = attribution.fallbackTrail();
        if (trail != null && !trail.isEmpty()) {
            StringBuilder summary = new StringBuilder("Fallback: Yes (");
            for (int i = 0; i < trail.size(); i++) {
                if (i > 0) {
                    summary.append(" -> ");
                }
                summary.append(i + 1).append(". ").append(formatFallbackHop(trail.get(i)));
            }
            summary.append(')');
            return summary.toString();
        }
        String previous = attribution.previousProvider() != null
                ? hopProviderName(attribution.previousProvider())
                : null;
        String detail = sanitizeFallbackDetail(attribution.fallbackReason());
        if (previous != null && !detail.isEmpty()) {
            return "Fallback: Yes (from " + previous + ": " + detail + ")";
        }
        if (previous != null) {
            return "Fallback: Yes (from " + previous + ")";
        }
        if (!detail.isEmpty()) {
            return "Fallback: Yes (" + detail + ")";
        }
        return "Fallback: Yes (reason not recorded)";
    }

    private static String formatFallbackHop(ProviderResult.FallbackHop hop) {
        String provider = hopProviderName(hop.provider());
        String kindText = failureKindSummary(hop.failureKind());
        String detail = sanitizeFallbackDetail(hop.reason());
        if (detail.isEmpty() || detail.equalsIgnoreCase(kindText)) {
            return provider + " — " + kindText;
        }
        return provider + " — " + kindText + ": " + detail;
    }

    private static String providerDisplayName(ProviderResult.ProviderId id) {
        if (id == null || id == ProviderResult.ProviderId.UNKNOWN) {
            return "Unknown provider (attribution recorded, identity unrecognized)";
        }
        return switch (id) {
            case GEMINI -> "Google Gemini";
            case OLLAMA -> "Ollama (local AI)";
            case LOCAL_RULE_ENGINE -> "Built-in local rule engine";
            case UNKNOWN -> "Unknown provider (attribution recorded, identity unrecognized)";
        };
    }

    private static String hopProviderName(ProviderResult.ProviderId id) {
        if (id == null || id == ProviderResult.ProviderId.UNKNOWN) {
            return "Unknown provider";
        }
        return switch (id) {
            case GEMINI -> "Google Gemini";
            case OLLAMA -> "Ollama (local AI)";
            case LOCAL_RULE_ENGINE -> "Built-in local rule engine";
            case UNKNOWN -> "Unknown provider";
        };
    }

    private static String failureKindSummary(AIProviderException.Kind kind) {
        if (kind == null) {
            return "provider error";
        }
        return switch (kind) {
            case AUTHENTICATION -> "not configured or authentication failed";
            case INVALID_REQUEST -> "request or model setting rejected";
            case RATE_LIMITED -> "rate limit reached";
            case TIMEOUT -> "request timed out";
            case NETWORK -> "service unreachable";
            case REFUSAL -> "request declined";
            case INVALID_RESPONSE -> "invalid response format";
            case PROVIDER_FAILURE -> "service unavailable";
        };
    }

    private static String sanitizeFallbackDetail(String rawReason) {
        if (rawReason == null || rawReason.isBlank()) {
            return "";
        }
        String firstSegment = STACK_TRACE_BOUNDARY.split(rawReason, 2)[0];
        String cleaned = firstSegment.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        cleaned = URL_PATTERN.matcher(cleaned).replaceAll("configured endpoint");
        cleaned = EXCEPTION_PREFIX.matcher(cleaned).replaceAll("");
        cleaned = EMPTY_PARENS.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replaceAll("\\s{2,}", " ").trim();
        while (cleaned.endsWith(":") || cleaned.endsWith("-") || cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        return cleaned;
    }

    private static String renderSolution(AIWorkflowResult result) {
        StringBuilder text = new StringBuilder();
        text.append("Original question\n").append(result.originalQuestion()).append("\n\n");
        text.append("Assumptions\n");
        result.validation().assumptions().forEach(value -> text.append("• ").append(value).append('\n'));
        for (ProjectileSequenceSolution.PartResult part : result.orchestration().solution().completedParts()) {
            ProjectileSolution solution = part.solution();
            text.append("\nPart ").append(part.index() + 1).append(" — ").append(solution.target())
                    .append(" [").append(solution.status()).append("]\n");
            solution.steps().forEach(step -> {
                text.append(step.equation().render()).append('\n');
                step.substitutions().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                        text.append("  ").append(entry.getKey()).append(" = ").append(format(entry.getValue())).append('\n'));
                step.intermediateResults().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                        text.append("  ").append(entry.getKey()).append(" = ").append(format(entry.getValue())).append(" (intermediate)\n"));
                step.results().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                        text.append("  ").append(entry.getKey()).append(" = ").append(format(entry.getValue())).append('\n'));
            });
            for (int candidateIndex = 0; candidateIndex < solution.candidates().size(); candidateIndex++) {
                text.append("Candidate ").append(candidateIndex + 1).append('\n');
                solution.candidates().get(candidateIndex).entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                        text.append("  ").append(entry.getKey()).append(" = ").append(format(entry.getValue())).append('\n'));
            }
        }
        if (result.explanation() != null) {
            text.append("\nExplanation\n");
            for (ExplanationDraft.Section section : result.explanation().content().sections()) {
                text.append(section.heading()).append('\n');
                for (ExplanationDraft.Claim claim : section.claims()) {
                    text.append("• ").append(claim.text());
                    if (claim.resultKey() != null) {
                        text.append(" [verified result: ").append(claim.resultKey()).append(" = ")
                                .append(findResult(result.orchestration().solution(), claim.resultKey())).append(']');
                    }
                    text.append('\n');
                }
            }
        }
        return text.toString();
    }

    private static String findResult(ProjectileSequenceSolution solution, String key) {
        for (ProjectileSequenceSolution.PartResult part : solution.completedParts()) {
            for (Map<String, PhysicalQuantity> candidate : part.solution().candidates()) {
                PhysicalQuantity value = candidate.get(key);
                if (value != null) return format(value);
            }
            for (var step : part.solution().steps()) {
                PhysicalQuantity value = step.results().get(key);
                if (value != null) return format(value);
                value = step.intermediateResults().get(key);
                if (value != null) return format(value);
                value = step.substitutions().get(key);
                if (value != null) return format(value);
            }
        }
        return "unavailable";
    }

    private static String format(PhysicalQuantity quantity) {
        return String.format(java.util.Locale.ROOT, "%.6g %s", quantity.value(), quantity.unit().symbol());
    }

    private void showClarifications(List<InterpretationDraft.Clarification> questions) {
        clarificationFields.getChildren().clear();
        for (int index = 0; index < questions.size(); index++) {
            InterpretationDraft.Clarification question = questions.get(index);
            Label prompt = new Label((index + 1) + ". " + question.prompt() + "\n" + question.reason());
            prompt.setWrapText(true);
            prompt.getStyleClass().add("muted-copy");
            TextField answer = new TextField();
            answer.setId("aiClarificationAnswer" + index);
            answer.setPromptText(question.options().isEmpty() ? "Your clarification" : String.join(" / ", question.options()));
            clarificationFields.getChildren().add(new VBox(4, prompt, answer));
        }
        clarificationBox.setVisible(true);
        clarificationBox.setManaged(true);
        answerButton.setVisible(true);
        answerButton.setManaged(true);
    }

    private void hideClarifications() {
        clarificationBox.setVisible(false);
        clarificationBox.setManaged(false);
        answerButton.setVisible(false);
        answerButton.setManaged(false);
        clarificationFields.getChildren().clear();
    }

    private void cancelRequest() {
        viewRevision++;
        if (conversationService != null && sessionId != null) conversationService.cancel(sessionId);
        setBusy(false, "Request cancelled.");
        resetAttributionDisplay(false);
    }

    private void setBusy(boolean busy, String status) {
        requestInProgress = busy;
        setStatus(status, false);
        submitButton.setDisable(busy);
        answerButton.setDisable(busy);
        cancelButton.setDisable(!busy);
        refreshConfigurationDisplay();
    }

    private void setError(String message) {
        setStatus(message, true);
    }

    private void setStatus(String message, boolean error) {
        statusLabel.setText(message);
        if (error) {
            if (!statusLabel.getStyleClass().contains("ai-status-error")) {
                statusLabel.getStyleClass().add("ai-status-error");
            }
        } else {
            statusLabel.getStyleClass().remove("ai-status-error");
        }
    }

    private boolean isDisposed() { return disposed || getScene() == null; }

    private static String providerMessage(AIProviderException.Kind error) {
        if (error == null) return "The AI provider request failed. No calculation was accepted.";
        return switch (error) {
            case AUTHENTICATION ->
                    "AI provider authentication failed. Set GOOGLE_API_KEY in your environment, or use PHYSORA_AI_MODE=auto or offline.";
            case INVALID_REQUEST ->
                    "The AI provider rejected the request or model configuration. Check PHYSORA_GEMINI_MODEL or PHYSORA_OLLAMA_MODEL.";
            case RATE_LIMITED -> "The AI provider rate limit was reached. Try again shortly.";
            case TIMEOUT -> "The AI provider took too long to respond. You can retry.";
            case NETWORK -> "The AI provider could not be reached. Check your network or local service and retry.";
            case REFUSAL -> "The AI provider declined the request.";
            case INVALID_RESPONSE -> "The AI provider returned a response that did not match the required structure.";
            case PROVIDER_FAILURE -> "The AI provider is temporarily unavailable. No calculation was accepted.";
        };
    }

    private static Label eyebrow(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("eyebrow");
        return label;
    }

    void dispose() {
        disposed = true;
        requestInProgress = false;
        closeActiveSession();
    }

    private void closeActiveSession() {
        viewRevision++;
        AIConversationService serviceToClose = conversationService;
        AIProvider providerToClose = provider;
        conversationService = null;
        provider = null;
        sessionId = null;
        try {
            if (serviceToClose != null) serviceToClose.close();
        } finally {
            if (providerToClose != null) providerToClose.close();
        }
    }
}
