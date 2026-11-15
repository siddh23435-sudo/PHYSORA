package edu.physics.ui;

import edu.physics.problem.ExperimentComparison;
import edu.physics.projectile.ProjectileMotion;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Locale;
import java.util.OptionalDouble;

/**
 * Interactive learning view for "Compare Two Experiments".
 *
 * <p>Enforces comparison integrity:
 * 1. Exactly two experiments (A and B) on a single unified physical coordinate system.
 * 2. Experiment A snapshot is immutable after capture.
 * 3. Both trajectories are rendered with identical isotropic scaling (no distortion).
 * 4. Physics insights explicitly address whether complementary angle range symmetry holds.
 */
final class ComparisonView extends BorderPane {

    private final ProjectileSimulationController controller;
    private final ProjectileCanvasRenderer renderer = new ProjectileCanvasRenderer();
    private final Canvas canvas = renderer.canvas();

    private ExperimentSnapshot experimentA;
    private ExperimentSnapshot experimentB;
    private double[] samplesA_X;
    private double[] samplesA_Y;
    private double[] samplesB_X;
    private double[] samplesB_Y;

    private final Label statusLabel = new Label("Capture Experiment A and Experiment B to overlay trajectories and compare results.");
    private final Button captureAButton = new Button("📸  CAPTURE AS EXPERIMENT A");
    private final Button captureBButton = new Button("📸  CAPTURE AS EXPERIMENT B");
    private final Button complementaryDemoButton = new Button("⚡  LOAD 30° vs 60° COMPLEMENTARY DEMO");

    private final VBox tableContainer = new VBox(6);
    private final VBox insightCard = new VBox(6);
    private final Label insightText = new Label();

    ComparisonView(ProjectileSimulationController controller) {
        this.controller = controller;
        setId("comparisonView");
        setPadding(new Insets(10, 16, 12, 16));
        getStyleClass().add("comparison-view");

        // Header
        Label eyebrow = eyebrow("COMPARE   /   TRAJECTORY OVERLAY & PARAMETER DELTAS");
        Label title = new Label("COMPARE TWO EXPERIMENTS");
        title.getStyleClass().add("simulation-heading");
        Label subtitle = new Label("Analyze trade-offs between initial conditions on a unified physical coordinate system.");
        subtitle.getStyleClass().add("muted-copy");
        setTop(new VBox(3, eyebrow, title, subtitle));

        // Center: Canvas + Comparison Details inside ScrollPane
        VBox centerBox = buildCenterBox();
        ScrollPane scroll = new ScrollPane(centerBox);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0;");
        setCenter(scroll);

        // Auto-initialize with current controller as A, and complementary 60° as B
        loadComplementaryDemo();
    }

    private VBox buildCenterBox() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(10, 0, 12, 0));

        // Controls bar
        captureAButton.setId("captureExperimentAButton");
        captureAButton.getStyleClass().add("transport-primary");
        captureAButton.setStyle("-fx-font-size: 10px; -fx-padding: 5 12 5 12;");
        captureAButton.setOnAction(e -> captureCurrentAsA());

        captureBButton.setId("captureExperimentBButton");
        captureBButton.getStyleClass().add("transport-secondary");
        captureBButton.setStyle("-fx-font-size: 10px; -fx-padding: 5 12 5 12;");
        captureBButton.setOnAction(e -> captureCurrentAsB());

        complementaryDemoButton.setId("loadComplementaryDemoButton");
        complementaryDemoButton.getStyleClass().add("transport-secondary");
        complementaryDemoButton.setStyle("-fx-font-size: 10px; -fx-padding: 5 12 5 12;");
        complementaryDemoButton.setOnAction(e -> loadComplementaryDemo());

        statusLabel.setId("comparisonStatusLabel");
        statusLabel.setStyle("-fx-font-family: Consolas; -fx-font-size: 10px; -fx-text-fill: #142e4b;");

        HBox controls = new HBox(10, captureAButton, captureBButton, complementaryDemoButton);
        controls.setAlignment(Pos.CENTER_LEFT);

        // Canvas Host
        StackPane canvasHost = new StackPane(canvas);
        canvasHost.setId("comparisonCanvasHost");
        canvasHost.setMinSize(440, 240);
        canvasHost.setPrefHeight(270);
        canvas.widthProperty().bind(canvasHost.widthProperty());
        canvas.heightProperty().bind(canvasHost.heightProperty());
        canvasHost.getStyleClass().add("plot-surface");

        canvas.widthProperty().addListener(obs -> redrawComparison());
        canvas.heightProperty().addListener(obs -> redrawComparison());

        // Comparison Table Container
        tableContainer.setId("comparisonTableContainer");
        tableContainer.setStyle("-fx-background-color: #f5f1e5; -fx-padding: 12 14 12 14; " +
                "-fx-border-color: #cbc4b4; -fx-border-radius: 2px; -fx-background-radius: 2px;");

        // Insight Card
        insightCard.setId("comparisonInsightCard");
        insightCard.setStyle("-fx-background-color: #f0eddf; -fx-padding: 12 14 12 14; " +
                "-fx-border-color: #2453d4; -fx-border-width: 1 1 1 2; -fx-background-radius: 2px;");
        Label insightTitle = new Label("PHYSICS COMPARISON INSIGHT");
        insightTitle.setStyle("-fx-font-family: Consolas; -fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #2453d4;");
        insightText.setId("comparisonInsightText");
        insightText.setWrapText(true);
        insightText.setStyle("-fx-font-size: 11px; -fx-text-fill: #142e4b;");
        insightCard.getChildren().addAll(insightTitle, insightText);

        box.getChildren().addAll(controls, statusLabel, canvasHost, tableContainer, insightCard);
        return box;
    }

    void captureCurrentAsA() {
        experimentA = ExperimentSnapshot.fromController(controller, "01 · KINEMATICS", "Experiment A");
        sampleTrajectoryA(controller.model());
        statusLabel.setText("Experiment A captured (" + formatBrief(experimentA) + "). Now adjust parameters and capture B.");
        updateComparison();
    }

    void captureCurrentAsB() {
        experimentB = ExperimentSnapshot.fromController(controller, "01 · KINEMATICS", "Experiment B");
        sampleTrajectoryB(controller.model());
        statusLabel.setText("Experiment B captured (" + formatBrief(experimentB) + "). Comparison active.");
        updateComparison();
    }

    void loadComplementaryDemo() {
        // Set up Experiment A at 30°
        ProjectileMotion modelA = new ProjectileMotion(25.0, 30.0, 9.81, 0.0, 0.0, 0.0);
        experimentA = new ExperimentSnapshot(
                "kinematics", "01 · KINEMATICS", "Complementary A (30°)",
                25.0, 30.0, 9.81, 0.0, 0.0, 0.0, 0.0, "READY",
                modelA.maximumHeight(), modelA.horizontalRange(), modelA.timeOfFlight(), modelA.timeToApex(),
                0.0, 0.0, modelA.initialVx(), modelA.initialVy());
        sampleTrajectoryA(modelA);

        // Set up Experiment B at 60°
        ProjectileMotion modelB = new ProjectileMotion(25.0, 60.0, 9.81, 0.0, 0.0, 0.0);
        experimentB = new ExperimentSnapshot(
                "kinematics", "01 · KINEMATICS", "Complementary B (60°)",
                25.0, 60.0, 9.81, 0.0, 0.0, 0.0, 0.0, "READY",
                modelB.maximumHeight(), modelB.horizontalRange(), modelB.timeOfFlight(), modelB.timeToApex(),
                0.0, 0.0, modelB.initialVx(), modelB.initialVy());
        sampleTrajectoryB(modelB);

        statusLabel.setText("Complementary Angle Demo loaded (30° vs 60° on level ground).");
        updateComparison();
    }

    private void sampleTrajectoryA(ProjectileMotion model) {
        int samples = 120;
        OptionalDouble tof = model.timeOfFlight();
        double endT = tof.isPresent() ? tof.getAsDouble() : 3.0;
        samplesA_X = new double[samples + 1];
        samplesA_Y = new double[samples + 1];
        for (int i = 0; i <= samples; i++) {
            ProjectileMotion.State s = model.stateAt(endT * i / samples);
            samplesA_X[i] = s.x();
            samplesA_Y[i] = s.y();
        }
    }

    private void sampleTrajectoryB(ProjectileMotion model) {
        int samples = 120;
        OptionalDouble tof = model.timeOfFlight();
        double endT = tof.isPresent() ? tof.getAsDouble() : 3.0;
        samplesB_X = new double[samples + 1];
        samplesB_Y = new double[samples + 1];
        for (int i = 0; i <= samples; i++) {
            ProjectileMotion.State s = model.stateAt(endT * i / samples);
            samplesB_X[i] = s.x();
            samplesB_Y[i] = s.y();
        }
    }

    private void updateComparison() {
        if (experimentA == null || experimentB == null) return;

        redrawComparison();

        // Build comparison report
        ExperimentComparison.ComparisonReport report = ExperimentComparison.compare(experimentA, experimentB);

        // Update Table
        tableContainer.getChildren().clear();
        Label tableTitle = new Label("NUMERICAL COMPARISON TABLE");
        tableTitle.setStyle("-fx-font-family: Georgia; -fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #142e4b;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(6);

        ColumnConstraints c0 = new ColumnConstraints();
        c0.setPrefWidth(135);
        c0.setMinWidth(115);

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPrefWidth(85);
        c1.setMinWidth(70);

        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPrefWidth(85);
        c2.setMinWidth(70);

        ColumnConstraints c3 = new ColumnConstraints();
        c3.setPrefWidth(85);
        c3.setMinWidth(70);

        ColumnConstraints c4 = new ColumnConstraints();
        c4.setHgrow(Priority.ALWAYS);
        c4.setMinWidth(160);
        c4.setMaxWidth(Double.MAX_VALUE);
        grid.getColumnConstraints().setAll(c0, c1, c2, c3, c4);

        // Header row
        Label h0 = tableHeader("Metric");
        Label h1 = tableHeader("Experiment A (Blue)");
        Label h2 = tableHeader("Experiment B (Amber)");
        Label h3 = tableHeader("Difference (B - A)");
        Label h4 = tableHeader("Physical Insight");
        grid.addRow(0, h0, h1, h2, h3, h4);

        int rowIdx = 1;
        for (ExperimentComparison.MetricRow row : report.rows()) {
            Label name = tableCell(row.metricName(), true);
            Label valA = tableCell(row.valueA(), false);
            Label valB = tableCell(row.valueB(), false);
            Label delta = tableCell(row.delta(), true);
            if (row.delta().startsWith("+")) {
                delta.setTextFill(Color.web("#2453d4"));
            } else if (row.delta().startsWith("-")) {
                delta.setTextFill(Color.web("#c2410c"));
            }
            Label insight = tableCell(row.physicalInsight(), false);
            insight.setWrapText(true);
            insight.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 10px; -fx-text-fill: #44505c; -fx-line-spacing: 1px;");
            grid.addRow(rowIdx++, name, valA, valB, delta, insight);
        }

        tableContainer.getChildren().addAll(tableTitle, grid);

        // Update Summary Insight Card
        insightText.setText(report.summaryInsight());
    }

    private void redrawComparison() {
        if (experimentA == null || experimentB == null) return;
        if (canvas.getWidth() <= 0 || canvas.getHeight() <= 0) return;

        ProjectileMotion modelA = new ProjectileMotion(
                experimentA.initialSpeed(), experimentA.launchAngleDegrees(), experimentA.gravity(),
                experimentA.initialX(), experimentA.initialY(), experimentA.landingY());
        ProjectileMotion modelB = new ProjectileMotion(
                experimentB.initialSpeed(), experimentB.launchAngleDegrees(), experimentB.gravity(),
                experimentB.initialX(), experimentB.initialY(), experimentB.landingY());

        String labelA = String.format(Locale.ROOT, "%.0f m/s at %.0f°", experimentA.initialSpeed(), experimentA.launchAngleDegrees());
        String labelB = String.format(Locale.ROOT, "%.0f m/s at %.0f°", experimentB.initialSpeed(), experimentB.launchAngleDegrees());

        renderer.renderComparison(
                modelA, samplesA_X, samplesA_Y,
                modelB, samplesB_X, samplesB_Y,
                labelA, labelB);
    }

    private static String formatBrief(ExperimentSnapshot s) {
        return String.format(Locale.ROOT, "v0=%.1f m/s, θ=%.1f°", s.initialSpeed(), s.launchAngleDegrees());
    }

    private static Label tableHeader(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-family: Consolas; -fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #66707a;");
        return l;
    }

    private static Label tableCell(String text, boolean bold) {
        Label l = new Label(text);
        l.setStyle("-fx-font-family: Consolas; -fx-font-size: 10.5px; -fx-text-fill: #142e4b;" + (bold ? " -fx-font-weight: bold;" : ""));
        return l;
    }

    private static Label eyebrow(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("eyebrow");
        return label;
    }

    ExperimentSnapshot experimentA() { return experimentA; }
    ExperimentSnapshot experimentB() { return experimentB; }
}

