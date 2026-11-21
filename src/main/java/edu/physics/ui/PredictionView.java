package edu.physics.ui;

import edu.physics.problem.PredictionChallenge;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Locale;

/**
 * Interactive learning view for "Predict Before You Simulate".
 *
 * <p>Enforces prediction integrity:
 * 1. Predictions must be entered and locked before the solver reveal.
 * 2. Inputs cannot be prefilled with solver values.
 * 3. Parameter changes after locking warn the student and mark the prediction invalidated.
 * 4. Constructive physics explanations compare estimates against deterministic truth.
 */
final class PredictionView extends BorderPane {

    private final ProjectileSimulationController controller;
    private PredictionChallenge challenge;

    private final Label paramsLabel = new Label();
    private final CheckBox heightCheck = new CheckBox("Maximum Height (H_max)");
    private final TextField heightField = new TextField();
    private final CheckBox timeCheck = new CheckBox("Flight Time (T)");
    private final TextField timeField = new TextField();
    private final CheckBox rangeCheck = new CheckBox("Horizontal Range (R)");
    private final TextField rangeField = new TextField();

    private final Button lockButton = new Button("🔒  LOCK PREDICTIONS");
    private final Button newAttemptButton = new Button("↺  NEW ATTEMPT");
    private final Button evaluateButton = new Button("⚗  REVEAL & COMPARE");
    private final Label statusLabel = new Label();
    private final Label invalidWarningLabel = new Label("⚠ Parameters changed after locking! Your prediction was locked for the previous configuration.");

    private final VBox resultsContainer = new VBox(14);
    private final Button tryAnotherButton = new Button("↺  TRY ANOTHER PREDICTION");

    PredictionView(ProjectileSimulationController controller) {
        this.controller = controller;
        setId("predictionView");
        setPadding(new Insets(12, 20, 12, 20));
        getStyleClass().add("prediction-view");

        // Header
        Label eyebrow = eyebrow("PREDICT   /   PREDICT BEFORE YOU SIMULATE");
        Label title = new Label("ACTIVE PREDICTION CHALLENGE");
        title.getStyleClass().add("simulation-heading");
        Label subtitle = new Label("Formulate a scientific hypothesis before running the simulation. Compare your estimates against the deterministic solver.");
        subtitle.getStyleClass().add("muted-copy");
        setTop(new VBox(3, eyebrow, title, subtitle));

        // Content
        VBox content = buildContent();
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0;");
        setCenter(scroll);

        // Listeners for simulation parameter changes to check validity
        controller.launchSpeedProperty().addListener((obs, o, n) -> onSimulationParamsModified());
        controller.launchAngleProperty().addListener((obs, o, n) -> onSimulationParamsModified());
        controller.gravityProperty().addListener((obs, o, n) -> onSimulationParamsModified());
        controller.initialXProperty().addListener((obs, o, n) -> onSimulationParamsModified());
        controller.initialYProperty().addListener((obs, o, n) -> onSimulationParamsModified());
        controller.landingYProperty().addListener((obs, o, n) -> onSimulationParamsModified());

        // Automatically enable or reveal when simulation reaches completed landed state if challenge is locked
        controller.statusProperty().addListener((obs, oldStatus, newStatus) -> {
            if (challenge != null && challenge.state() == PredictionChallenge.State.LOCKED) {
                if (newStatus == ProjectileSimulationController.SimulationStatus.LANDED) {
                    evaluateButton.setDisable(false);
                    evaluatePredictions();
                } else if (!isExperimentComplete()) {
                    evaluateButton.setDisable(true);
                }
            }
        });

        startNewAttempt();
    }

    private VBox buildContent() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(10, 0, 16, 0));

        // 1. Parameter Summary Card
        VBox paramCard = new VBox(6);
        paramCard.setStyle("-fx-background-color: #e9e2d0; -fx-padding: 10 14 10 14; " +
                "-fx-border-color: #cbc4b4; -fx-border-radius: 2px; -fx-background-radius: 2px;");
        Label paramTitle = new Label("ACTIVE EXPERIMENT CONFIGURATION");
        paramTitle.setStyle("-fx-font-family: Consolas; -fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #2453d4;");
        paramsLabel.setStyle("-fx-font-family: Consolas; -fx-font-size: 11px; -fx-text-fill: #142e4b;");
        paramCard.getChildren().addAll(paramTitle, paramsLabel);

        // 2. Prediction Inputs Card
        VBox inputCard = new VBox(12);
        inputCard.setId("predictionInputCard");
        inputCard.setStyle("-fx-background-color: #f5f1e5; -fx-padding: 14 16 14 16; " +
                "-fx-border-color: #cbc4b4; -fx-border-radius: 2px; -fx-background-radius: 2px;");

        Label inputTitle = new Label("ENTER YOUR PREDICTIONS");
        inputTitle.setStyle("-fx-font-family: Georgia; -fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #000000;");

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(10);

        heightCheck.setId("predictMaxHeightCheck");
        heightCheck.getStyleClass().add("prediction-checkbox");
        heightCheck.setStyle("-fx-text-fill: #000000; -fx-font-size: 12px; -fx-font-weight: bold; -fx-opacity: 1.0;");
        heightCheck.setTextFill(javafx.scene.paint.Color.BLACK);
        heightCheck.setSelected(true);
        heightCheck.disabledProperty().addListener((obs, o, n) -> heightCheck.setOpacity(1.0));

        heightField.setId("predictMaxHeightField");
        heightField.getStyleClass().add("prediction-field");
        heightField.setStyle("-fx-text-fill: #000000; -fx-font-family: 'Consolas'; -fx-font-size: 12px; -fx-font-weight: bold; -fx-opacity: 1.0;");
        heightField.setPromptText("e.g. 15.0");
        heightField.setPrefWidth(120);
        heightField.disabledProperty().addListener((obs, o, n) -> heightField.setOpacity(1.0));

        timeCheck.setId("predictFlightTimeCheck");
        timeCheck.getStyleClass().add("prediction-checkbox");
        timeCheck.setStyle("-fx-text-fill: #000000; -fx-font-size: 12px; -fx-font-weight: bold; -fx-opacity: 1.0;");
        timeCheck.setTextFill(javafx.scene.paint.Color.BLACK);
        timeCheck.setSelected(true);
        timeCheck.disabledProperty().addListener((obs, o, n) -> timeCheck.setOpacity(1.0));

        timeField.setId("predictFlightTimeField");
        timeField.getStyleClass().add("prediction-field");
        timeField.setStyle("-fx-text-fill: #000000; -fx-font-family: 'Consolas'; -fx-font-size: 12px; -fx-font-weight: bold; -fx-opacity: 1.0;");
        timeField.setPromptText("e.g. 2.5");
        timeField.setPrefWidth(120);
        timeField.disabledProperty().addListener((obs, o, n) -> timeField.setOpacity(1.0));

        rangeCheck.setId("predictRangeCheck");
        rangeCheck.getStyleClass().add("prediction-checkbox");
        rangeCheck.setStyle("-fx-text-fill: #000000; -fx-font-size: 12px; -fx-font-weight: bold; -fx-opacity: 1.0;");
        rangeCheck.setTextFill(javafx.scene.paint.Color.BLACK);
        rangeCheck.setSelected(true);
        rangeCheck.disabledProperty().addListener((obs, o, n) -> rangeCheck.setOpacity(1.0));

        rangeField.setId("predictRangeField");
        rangeField.getStyleClass().add("prediction-field");
        rangeField.setStyle("-fx-text-fill: #000000; -fx-font-family: 'Consolas'; -fx-font-size: 12px; -fx-font-weight: bold; -fx-opacity: 1.0;");
        rangeField.setPromptText("e.g. 40.0");
        rangeField.setPrefWidth(120);
        rangeField.disabledProperty().addListener((obs, o, n) -> rangeField.setOpacity(1.0));

        Label unitHeight = new Label("m");
        unitHeight.getStyleClass().add("prediction-unit-label");
        unitHeight.setStyle("-fx-text-fill: #000000; -fx-font-family: 'Consolas'; -fx-font-size: 12px; -fx-font-weight: bold; -fx-opacity: 1.0;");
        unitHeight.setTextFill(javafx.scene.paint.Color.BLACK);

        Label unitTime = new Label("s");
        unitTime.getStyleClass().add("prediction-unit-label");
        unitTime.setStyle("-fx-text-fill: #000000; -fx-font-family: 'Consolas'; -fx-font-size: 12px; -fx-font-weight: bold; -fx-opacity: 1.0;");
        unitTime.setTextFill(javafx.scene.paint.Color.BLACK);

        Label unitRange = new Label("m");
        unitRange.getStyleClass().add("prediction-unit-label");
        unitRange.setStyle("-fx-text-fill: #000000; -fx-font-family: 'Consolas'; -fx-font-size: 12px; -fx-font-weight: bold; -fx-opacity: 1.0;");
        unitRange.setTextFill(javafx.scene.paint.Color.BLACK);

        grid.add(heightCheck, 0, 0);
        grid.add(new HBox(6, heightField, unitHeight), 1, 0);
        grid.add(timeCheck, 0, 1);
        grid.add(new HBox(6, timeField, unitTime), 1, 1);
        grid.add(rangeCheck, 0, 2);
        grid.add(new HBox(6, rangeField, unitRange), 1, 2);

        lockButton.setId("lockPredictionsButton");
        lockButton.getStyleClass().add("transport-primary");
        lockButton.setStyle("-fx-font-size: 10px; -fx-padding: 6 14 6 14; -fx-cursor: hand;");
        lockButton.setOnAction(e -> lockPredictions());

        newAttemptButton.setId("newPredictionAttemptButton");
        newAttemptButton.getStyleClass().add("transport-secondary");
        newAttemptButton.setStyle("-fx-font-size: 10px; -fx-padding: 6 12 6 12;");
        newAttemptButton.setOnAction(e -> startNewAttempt());

        evaluateButton.setId("evaluatePredictionsButton");
        evaluateButton.getStyleClass().add("transport-secondary");
        evaluateButton.setStyle("-fx-font-size: 10px; -fx-padding: 6 12 6 12;");
        evaluateButton.setOnAction(e -> evaluatePredictions());
        evaluateButton.setDisable(true);

        statusLabel.setId("predictionStatusLabel");
        statusLabel.setStyle("-fx-font-family: Consolas; -fx-font-size: 10.5px; -fx-text-fill: #000000;");

        invalidWarningLabel.setId("predictionInvalidWarning");
        invalidWarningLabel.setStyle("-fx-font-family: Consolas; -fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #b45309; -fx-background-color: #fef9ee; -fx-padding: 6 8 6 8; -fx-border-color: #b45309; -fx-border-width: 1;");
        invalidWarningLabel.setVisible(false);
        invalidWarningLabel.setManaged(false);

        HBox actionRow = new HBox(10, lockButton, evaluateButton, newAttemptButton);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        inputCard.getChildren().addAll(inputTitle, grid, actionRow, statusLabel, invalidWarningLabel);

        // 3. Results Container Card
        resultsContainer.setId("predictionResultsCard");
        resultsContainer.setStyle("-fx-background-color: #fdfaf0; -fx-padding: 14 16 14 16; " +
                "-fx-border-color: #2453d4; -fx-border-width: 1 1 1 2; -fx-background-radius: 2px;");
        resultsContainer.setVisible(false);
        resultsContainer.setManaged(false);

        tryAnotherButton.setId("tryAnotherPredictionButton");
        tryAnotherButton.getStyleClass().add("transport-secondary");
        tryAnotherButton.setOnAction(e -> startNewAttempt());

        box.getChildren().addAll(paramCard, inputCard, resultsContainer);
        return box;
    }

    void startNewAttempt() {
        ExperimentSnapshot snapshot = ExperimentSnapshot.fromController(controller, "01 · KINEMATICS", "Ideal Projectile Motion");
        challenge = new PredictionChallenge(snapshot);

        updateParamsLabel();
        heightField.setDisable(false);
        heightField.setOpacity(1.0);
        heightField.clear();
        timeField.setDisable(false);
        timeField.setOpacity(1.0);
        timeField.clear();
        rangeField.setDisable(false);
        rangeField.setOpacity(1.0);
        rangeField.clear();

        heightCheck.setDisable(false);
        heightCheck.setOpacity(1.0);
        timeCheck.setDisable(false);
        timeCheck.setOpacity(1.0);
        rangeCheck.setDisable(false);
        rangeCheck.setOpacity(1.0);

        lockButton.setDisable(false);
        evaluateButton.setDisable(true);
        invalidWarningLabel.setVisible(false);
        invalidWarningLabel.setManaged(false);

        resultsContainer.setVisible(false);
        resultsContainer.setManaged(false);
        resultsContainer.getChildren().clear();

        statusLabel.setText(challenge.statusMessage());
    }

    private void updateParamsLabel() {
        if (challenge == null) return;
        ExperimentSnapshot s = challenge.baselineSnapshot();
        paramsLabel.setText(String.format(Locale.ROOT,
                "v0 = %.2f m/s   ·   θ = %.1f°   ·   g = %.2f m/s²   ·   launch (%.1f, %.1f) m   ·   landing y = %.1f m",
                s.initialSpeed(), s.launchAngleDegrees(), s.gravity(), s.initialX(), s.initialY(), s.landingY()));
    }

    void lockPredictions() {
        if (challenge == null) return;

        try {
            if (heightCheck.isSelected()) {
                double val = parsePositive(heightField.getText(), "Maximum Height");
                challenge.setPrediction(PredictionChallenge.Quantity.MAX_HEIGHT, val);
            }
            if (timeCheck.isSelected()) {
                double val = parsePositive(timeField.getText(), "Flight Time");
                challenge.setPrediction(PredictionChallenge.Quantity.FLIGHT_TIME, val);
            }
            if (rangeCheck.isSelected()) {
                double val = parsePositive(rangeField.getText(), "Horizontal Range");
                challenge.setPrediction(PredictionChallenge.Quantity.HORIZONTAL_RANGE, val);
            }
            challenge.lock();

            heightField.setDisable(true);
            heightField.setOpacity(1.0);
            timeField.setDisable(true);
            timeField.setOpacity(1.0);
            rangeField.setDisable(true);
            rangeField.setOpacity(1.0);
            heightCheck.setDisable(true);
            heightCheck.setOpacity(1.0);
            timeCheck.setDisable(true);
            timeCheck.setOpacity(1.0);
            rangeCheck.setDisable(true);
            rangeCheck.setOpacity(1.0);

            lockButton.setDisable(true);
            boolean readyForEvaluation = isExperimentComplete();
            evaluateButton.setDisable(!readyForEvaluation);
            if (readyForEvaluation) {
                statusLabel.setText("Experiment complete. Click 'REVEAL & COMPARE' to evaluate your hypothesis.");
            } else {
                statusLabel.setText("Predictions locked. Run the simulation in EXPERIMENT mode until touchdown to reveal the comparison.");
            }
        } catch (IllegalArgumentException ex) {
            statusLabel.setText("❌ " + ex.getMessage());
        }
    }

    boolean isExperimentComplete() {
        if (controller.status() == ProjectileSimulationController.SimulationStatus.LANDED) {
            return true;
        }
        return controller.model().timeOfFlight().isEmpty()
                && controller.simulationTimeProperty().get() >= 1.0;
    }

    void evaluatePredictions() {
        if (challenge == null || challenge.state() != PredictionChallenge.State.LOCKED) return;
        if (!isExperimentComplete()) {
            statusLabel.setText("Predictions locked. Run the simulation in EXPERIMENT mode until touchdown to reveal the comparison.");
            return;
        }

        challenge.evaluate(controller.model());
        evaluateButton.setDisable(true);
        statusLabel.setText(challenge.statusMessage());

        buildResultsCard();
    }

    private void buildResultsCard() {
        resultsContainer.getChildren().clear();

        Label cardHeader = new Label("PREDICTION EVALUATION & PHYSICAL COMPARISON");
        cardHeader.setStyle("-fx-font-family: Georgia; -fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #142e4b;");
        resultsContainer.getChildren().add(cardHeader);

        for (PredictionChallenge.PredictionEvaluation eval : challenge.evaluations()) {
            VBox card = new VBox(8);
            card.setStyle("-fx-background-color: #ffffff; -fx-padding: 12 16 12 16; " +
                    "-fx-border-color: #cbc4b4; -fx-border-radius: 2px; -fx-background-radius: 2px;");

            Label title = new Label(eval.quantity().title().toUpperCase() + "  (" + eval.quantity().symbol() + ")");
            title.setStyle("-fx-font-family: Consolas; -fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #2453d4;");

            String actualStr = eval.actualValue().isPresent()
                    ? String.format(Locale.ROOT, "%.2f %s", eval.actualValue().getAsDouble(), eval.quantity().unit())
                    : "Unbounded / No landing";

            String pctStr = eval.percentageError().isPresent()
                    ? String.format(Locale.ROOT, "%.1f%% difference", eval.percentageError().getAsDouble())
                    : (eval.actualValue().isPresent() ? "Near zero datum" : "N/A");

            // Telemetry row with 4 distinct metric pills
            HBox metricsRow = new HBox(10);
            metricsRow.setAlignment(Pos.CENTER_LEFT);

            VBox predPill = metricPill("PREDICTED", String.format(Locale.ROOT, "%.2f %s", eval.predictedValue(), eval.quantity().unit()), "#142e4b", "#f5f1e5");
            VBox actPill = metricPill("ACTUAL SOLVER", actualStr, "#2453d4", "#edf2fe");
            VBox absPill = metricPill("ABSOLUTE ERROR", String.format(Locale.ROOT, "%.2f %s", eval.absoluteError(), eval.quantity().unit()), "#556270", "#f5f1e5");
            VBox pctPill = metricPill("ACCURACY", pctStr, "#92400e", "#fef3c7");
            metricsRow.getChildren().addAll(predPill, actPill, absPill, pctPill);

            Label explanation = new Label(eval.explanation());
            explanation.setWrapText(true);
            explanation.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 11.5px; -fx-text-fill: #475569; -fx-line-spacing: 2px; -fx-padding: 4 0 0 0;");

            card.getChildren().addAll(title, metricsRow, explanation);
            resultsContainer.getChildren().add(card);
        }

        resultsContainer.getChildren().add(tryAnotherButton);
        resultsContainer.setVisible(true);
        resultsContainer.setManaged(true);
    }

    private static VBox metricPill(String caption, String value, String textColor, String bgColor) {
        Label cap = new Label(caption);
        cap.setStyle("-fx-font-family: Consolas; -fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #66707a;");
        Label val = new Label(value);
        val.setStyle("-fx-font-family: Consolas; -fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + textColor + ";");
        VBox box = new VBox(2, cap, val);
        box.setStyle("-fx-background-color: " + bgColor + "; -fx-padding: 4 10 4 10; -fx-border-color: #cbc4b4; -fx-border-radius: 2px; -fx-background-radius: 2px;");
        return box;
    }

    private void onSimulationParamsModified() {
        if (challenge != null && challenge.state() == PredictionChallenge.State.LOCKED) {
            ExperimentSnapshot current = ExperimentSnapshot.fromController(controller, "01 · KINEMATICS", "Ideal Projectile Motion");
            boolean valid = challenge.checkValidity(current);
            if (!valid) {
                invalidWarningLabel.setVisible(true);
                invalidWarningLabel.setManaged(true);
                statusLabel.setText(challenge.statusMessage());
            }
        }
    }

    private static double parsePositive(String text, String name) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Enter a numerical value for " + name);
        }
        try {
            double v = Double.parseDouble(text.trim());
            if (v < 0.0 || Double.isNaN(v) || Double.isInfinite(v)) {
                throw new IllegalArgumentException(name + " must be a positive finite number");
            }
            return v;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " contains an invalid number: '" + text.trim() + "'");
        }
    }

    private static Label eyebrow(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("eyebrow");
        return label;
    }

    PredictionChallenge currentChallenge() {
        return challenge;
    }
}

