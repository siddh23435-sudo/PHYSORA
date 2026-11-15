package edu.physics.ui;

import edu.physics.problem.GuidedDerivations;
import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.PhysicalUnit;
import edu.physics.problem.ProjectileProblem;
import edu.physics.problem.ProjectileProblemSolver;
import edu.physics.problem.ProjectileSolution;
import edu.physics.projectile.ProjectileMotion;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Interactive step-by-step guided derivation and equation walkthrough for 2D Kinematics.
 * Follows the approved sequence:
 * GIVEN → PHYSICAL PRINCIPLE → FORMULA → SUBSTITUTION → CALCULATION → FINAL ANSWER & INTERPRETATION.
 */
final class ProjectileLogicView extends BorderPane {
    private final ProjectileSimulationController controller;
    private final ProjectileProblemSolver solver = new ProjectileProblemSolver();

    private final VBox derivationContainer = new VBox(10);
    private final VBox rawDerivationSteps = new VBox(6);
    private final Label parameterValues = new Label();
    private final Label currentStateValue = new Label();
    private final Label maximumHeightValue = new Label();
    private final Label flightTimeValue = new Label();
    private final Label horizontalRangeValue = new Label();
    private final Label assumptionValue = new Label();

    private GuidedDerivations.Topic activeTopic = GuidedDerivations.Topic.MAX_HEIGHT;
    private int activeStepIndex = 0; // 0..5
    private boolean showAllSteps = false;

    private final FlowPane topicBar = new FlowPane(6, 6);
    private final Label topicHeaderLabel = new Label();
    private final Label topicResultBadge = new Label();
    private final Label stepIndicatorLabel = new Label();
    private final Button prevStepButton = new Button("◀  PREVIOUS");
    private final Button nextStepButton = new Button("NEXT  ▶");
    private final ToggleButton showAllToggle = new ToggleButton("SHOW ALL STEPS");
    private final VBox stepsDisplayBox = new VBox(10);

    ProjectileLogicView(ProjectileSimulationController controller) {
        this.controller = controller;
        setId("logicView");
        currentStateValue.setId("logicCurrentState");
        setPadding(new Insets(12, 20, 10, 20));
        getStyleClass().add("logic-view");

        // Top: Student-facing header
        Label eyebrow = sectionTitle("UNDERSTAND   /   GUIDED MATHEMATICAL DERIVATIONS");
        Label title = new Label("HOW THE PHYSICS WORKS");
        title.getStyleClass().add("simulation-heading");
        Label description = new Label("Step-by-step mathematical reasoning synchronized with your active experiment parameters.");
        description.getStyleClass().add("muted-copy");
        setTop(new VBox(3, eyebrow, title, description));

        // Build interactive topic tabs
        buildTopicBar();

        // Build derivation card
        VBox guidedCard = buildGuidedCard();

        // Live analytical solver verification (preserves backward-compatible nodes)
        VBox solverSection = buildSolverSection();

        VBox contentBox = new VBox(14,
                topicBar,
                guidedCard,
                rule(),
                solverSection);
        contentBox.setPadding(new Insets(8, 0, 12, 0));

        ScrollPane scroll = new ScrollPane(contentBox);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0;");
        setCenter(scroll);

        controller.modelProperty().addListener((obs, oldModel, newModel) -> refresh());
        controller.launchSpeedProperty().addListener((obs, oldValue, newValue) -> refresh());
        controller.launchAngleProperty().addListener((obs, oldValue, newValue) -> refresh());
        controller.gravityProperty().addListener((obs, oldValue, newValue) -> refresh());
        controller.initialXProperty().addListener((obs, oldValue, newValue) -> refresh());
        controller.initialYProperty().addListener((obs, oldValue, newValue) -> refresh());
        controller.landingYProperty().addListener((obs, oldValue, newValue) -> refresh());
        controller.simulationTimeProperty().addListener((obs, oldValue, newValue) -> refresh());

        refresh();
    }

    private void buildTopicBar() {
        ToggleGroup group = new ToggleGroup();
        topicBar.setAlignment(Pos.CENTER_LEFT);
        topicBar.setPadding(new Insets(6, 0, 6, 0));

        for (GuidedDerivations.Topic t : GuidedDerivations.Topic.values()) {
            ToggleButton btn = new ToggleButton(t.title());
            btn.setId("derivationTopic_" + t.name());
            btn.setToggleGroup(group);
            btn.getStyleClass().addAll("transport-secondary", "derivation-topic-btn");
            btn.setStyle("-fx-font-size: 10px; -fx-padding: 5 10 5 10; -fx-cursor: hand;");
            if (t == activeTopic) {
                btn.setSelected(true);
            }
            btn.setOnAction(e -> {
                btn.setSelected(true);
                activeTopic = t;
                activeStepIndex = 0;
                refresh();
            });
            topicBar.getChildren().add(btn);
        }
    }

    private VBox buildGuidedCard() {
        VBox card = new VBox(12);
        card.setStyle("-fx-background-color: #fdfcf9; -fx-padding: 16 18 16 18; " +
                "-fx-border-color: #cbc4b4 #cbc4b4 #cbc4b4 #2453d4; -fx-border-width: 1 1 1 3.5; -fx-background-radius: 2px;");

        topicHeaderLabel.setStyle("-fx-font-family: Georgia; -fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #142e4b;");

        topicResultBadge.setStyle("-fx-background-color: #2453d4; -fx-text-fill: #ffffff; " +
                "-fx-font-family: Consolas; -fx-font-size: 11px; -fx-font-weight: bold; " +
                "-fx-padding: 4 9 4 9; -fx-background-radius: 2px;");

        HBox cardHeader = new HBox(12, topicHeaderLabel, spacer(), topicResultBadge);
        cardHeader.setAlignment(Pos.CENTER_LEFT);

        // Stepper navigation controls
        prevStepButton.setId("derivationPrevStep");
        nextStepButton.setId("derivationNextStep");
        showAllToggle.setId("derivationShowAllToggle");

        prevStepButton.getStyleClass().add("transport-secondary");
        nextStepButton.getStyleClass().add("transport-secondary");
        showAllToggle.getStyleClass().add("transport-secondary");

        prevStepButton.setStyle("-fx-font-size: 9.5px; -fx-padding: 5 10 5 10;");
        nextStepButton.setStyle("-fx-font-size: 9.5px; -fx-padding: 5 10 5 10;");
        showAllToggle.setStyle("-fx-font-size: 9.5px; -fx-padding: 5 10 5 10;");

        prevStepButton.setOnAction(e -> {
            if (activeStepIndex > 0) {
                activeStepIndex--;
                updateStepDisplay();
            }
        });
        nextStepButton.setOnAction(e -> {
            if (activeStepIndex < 5) {
                activeStepIndex++;
                updateStepDisplay();
            }
        });
        showAllToggle.setOnAction(e -> {
            showAllSteps = showAllToggle.isSelected();
            updateStepDisplay();
        });

        stepIndicatorLabel.setStyle("-fx-font-family: Consolas; -fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #66707a;");

        HBox stepperBar = new HBox(10, stepIndicatorLabel, spacer(), prevStepButton, nextStepButton, showAllToggle);
        stepperBar.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(cardHeader, rule(), stepperBar, stepsDisplayBox);
        return card;
    }

    private VBox buildSolverSection() {
        VBox section = new VBox(8);
        section.setStyle("-fx-background-color: #f0ece0; -fx-padding: 12 14 12 14; " +
                "-fx-border-color: #cbc4b4; -fx-border-radius: 2px; -fx-background-radius: 2px;");
        section.getChildren().addAll(
                sectionTitle("REFERENCE · DETERMINISTIC SOLVER VERIFICATION"),
                parameterLine(),
                sectionTitle("POSITION DERIVATION AT CURRENT CLOCK"),
                rawDerivationSteps,
                currentState(),
                resultLine("Maximum height", maximumHeightValue),
                resultLine("Flight time", flightTimeValue),
                resultLine("Horizontal range", horizontalRangeValue),
                assumptionBlock());
        return section;
    }

    private VBox assumptionBlock() {
        assumptionValue.setWrapText(true);
        assumptionValue.getStyleClass().add("muted-copy");
        VBox block = new VBox(4,
                sectionTitle("MODEL ASSUMPTIONS & CONSTRAINTS"),
                assumptionValue);
        block.getStyleClass().add("logic-assumptions");
        block.setPadding(new Insets(10, 12, 10, 12));
        return block;
    }

    private VBox parameterLine() {
        parameterValues.getStyleClass().add("logic-parameters");
        return new VBox(4, parameterValues);
    }

    private HBox currentState() {
        currentStateValue.getStyleClass().add("state-value");
        HBox row = new HBox(10, new Label("At t ="), currentStateValue);
        row.getStyleClass().add("state-line");
        return row;
    }

    private HBox resultLine(String title, Label value) {
        value.getStyleClass().add("logic-result-value");
        HBox row = new HBox(12, body(title), spacer(), value);
        row.getStyleClass().add("logic-result-line");
        return row;
    }

    private void refresh() {
        ProjectileMotion model = controller.model();
        double speed = controller.launchSpeedProperty().get();
        double angle = controller.launchAngleProperty().get();
        double gravity = controller.gravityProperty().get();
        double initialX = controller.initialXProperty().get();
        double initialY = controller.initialYProperty().get();
        double landingY = controller.landingYProperty().get();
        double time = controller.simulationTimeProperty().get();
        ProjectileMotion.State state = model.stateAt(time);

        parameterValues.setText(String.format(Locale.ROOT,
                "v0 %.2f m/s    theta %.2f deg    g %.2f m/s^2    (x0, y0) (%.2f, %.2f) m    landing y %.2f m",
                speed, angle, gravity, initialX, initialY, landingY));
        assumptionValue.setText(String.format(Locale.ROOT,
                "Two-dimensional point particle · constant downward gravity · air resistance ignored · launch (%.2f, %.2f) m · landing surface y = %.2f m.",
                initialX, initialY, landingY));

        // Evaluate solver steps for POSITION_AT_TIME (preserves logicStepDetails0)
        EnumMap<ProjectileProblem.Input, PhysicalQuantity> givens = new EnumMap<>(ProjectileProblem.Input.class);
        givens.put(ProjectileProblem.Input.INITIAL_SPEED, new PhysicalQuantity(speed, PhysicalUnit.METRE_PER_SECOND));
        givens.put(ProjectileProblem.Input.LAUNCH_ANGLE, new PhysicalQuantity(angle, PhysicalUnit.DEGREE));
        givens.put(ProjectileProblem.Input.GRAVITY, new PhysicalQuantity(gravity, PhysicalUnit.METRE_PER_SECOND_SQUARED));
        givens.put(ProjectileProblem.Input.INITIAL_X, new PhysicalQuantity(initialX, PhysicalUnit.METRE));
        givens.put(ProjectileProblem.Input.INITIAL_Y, new PhysicalQuantity(initialY, PhysicalUnit.METRE));
        givens.put(ProjectileProblem.Input.LANDING_Y, new PhysicalQuantity(landingY, PhysicalUnit.METRE));
        givens.put(ProjectileProblem.Input.TIME, new PhysicalQuantity(time, PhysicalUnit.SECOND));

        ProjectileSolution solution = solver.solve(new ProjectileProblem(
                "Active simulation state", ProjectileProblem.Target.POSITION_AT_TIME, givens));
        rawDerivationSteps.getChildren().clear();
        for (int i = 0; i < solution.steps().size(); i++) {
            rawDerivationSteps.getChildren().add(equationRow(solution.steps().get(i), i));
        }

        currentStateValue.setText(String.format(Locale.ROOT,
                "%.2f s   |   x %.2f m   |   y %.2f m   |   vx %.2f m/s   |   vy %.2f m/s",
                time, state.x(), state.y(), state.vx(), state.vy()));
        setOptional(maximumHeightValue, model.maximumHeight(), "m", "Unbounded");
        setOptional(flightTimeValue, model.timeOfFlight(), "s", "No landing");
        setOptional(horizontalRangeValue, model.horizontalRange(), "m", "No landing");

        updateStepDisplay();
    }

    private void updateStepDisplay() {
        ProjectileMotion model = controller.model();
        GuidedDerivations.Derivation d = switch (activeTopic) {
            case VELOCITY_COMPONENTS -> GuidedDerivations.deriveVelocityComponents(model);
            case TIME_TO_APEX -> GuidedDerivations.deriveTimeToApex(model);
            case MAX_HEIGHT -> GuidedDerivations.deriveMaxHeight(model);
            case TIME_OF_FLIGHT -> GuidedDerivations.deriveTimeOfFlight(model);
            case HORIZONTAL_RANGE -> GuidedDerivations.deriveHorizontalRange(model);
            case LANDING_VELOCITY -> GuidedDerivations.deriveLandingVelocity(model);
        };

        topicHeaderLabel.setText(d.title().toUpperCase() + "  (" + d.targetSymbol() + ")");
        topicResultBadge.setText("FINAL: " + d.finalResultFormatted());

        prevStepButton.setDisable(showAllSteps || activeStepIndex <= 0);
        nextStepButton.setDisable(showAllSteps || activeStepIndex >= d.steps().size() - 1);

        stepsDisplayBox.getChildren().clear();

        if (showAllSteps) {
            stepIndicatorLabel.setText("SHOWING ALL 6 STEPS PROGRESSIVELY");
            for (int i = 0; i < d.steps().size(); i++) {
                stepsDisplayBox.getChildren().add(createStepCard(d.steps().get(i), i + 1));
            }
        } else {
            stepIndicatorLabel.setText(String.format("STEP %d OF %d: %s",
                    activeStepIndex + 1, d.steps().size(), d.steps().get(activeStepIndex).kind().heading()));
            stepsDisplayBox.getChildren().add(createStepCard(d.steps().get(activeStepIndex), activeStepIndex + 1));
        }
    }

    private VBox createStepCard(GuidedDerivations.DerivationStep step, int stepNumber) {
        VBox card = new VBox(8);
        card.setStyle("-fx-background-color: #ffffff; -fx-padding: 14 16 14 16; " +
                "-fx-border-color: #cbc4b4; -fx-border-radius: 2px; -fx-background-radius: 2px;");

        Label badge = new Label(stepNumber + ". " + step.kind().heading());
        badge.setStyle("-fx-font-family: Consolas; -fx-font-size: 10px; -fx-font-weight: bold; " +
                "-fx-background-color: #142e4b; -fx-text-fill: #d9f26a; -fx-padding: 3 8 3 8; -fx-background-radius: 2px;");

        Label subtitle = new Label(step.title());
        subtitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        subtitle.setTextFill(PhysoraTheme.INK);

        HBox top = new HBox(10, badge, subtitle);
        top.setAlignment(Pos.CENTER_LEFT);

        Label content = new Label(step.content());
        if (step.kind() == GuidedDerivations.StepKind.FORMULA || step.kind() == GuidedDerivations.StepKind.CALCULATION) {
            content.setStyle("-fx-font-family: Consolas; -fx-font-size: 13.5px; -fx-font-weight: bold; " +
                    "-fx-text-fill: #142e4b; -fx-background-color: #f0f4fe; -fx-padding: 8 12 8 12; " +
                    "-fx-border-color: #c7d7fc; -fx-border-radius: 2px; -fx-background-radius: 2px;");
        } else if (step.kind() == GuidedDerivations.StepKind.FINAL_ANSWER) {
            content.setStyle("-fx-font-family: Consolas; -fx-font-size: 14px; -fx-font-weight: bold; " +
                    "-fx-text-fill: #2453d4; -fx-background-color: #eaf1fd; -fx-padding: 8 12 8 12; " +
                    "-fx-border-color: #2453d4; -fx-border-width: 1 1 1 3; -fx-background-radius: 2px;");
        } else {
            content.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 12.5px; " +
                    "-fx-text-fill: #142e4b; -fx-line-spacing: 2px; -fx-padding: 2 0 2 0;");
        }
        content.setWrapText(true);

        card.getChildren().addAll(top, content);

        if (!step.bulletDetails().isEmpty()) {
            VBox bullets = new VBox(4);
            bullets.setPadding(new Insets(4, 0, 0, 8));
            for (String detail : step.bulletDetails()) {
                Label b = new Label("•  " + detail);
                b.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #475569;");
                b.setWrapText(true);
                bullets.getChildren().add(b);
            }
            card.getChildren().add(bullets);
        }

        return card;
    }

    private VBox equationRow(ProjectileSolution.Step step, int index) {
        Label formula = new Label(step.equation().render());
        formula.getStyleClass().add("equation-text");
        String evaluatedText = step.results().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + " = " + format(entry.getValue(), step.precision()))
                .reduce((left, right) -> left + "    " + right).orElse("");
        Label evaluated = new Label(evaluatedText);
        evaluated.getStyleClass().add("equation-result");
        HBox row = new HBox(15, formula, spacer(), evaluated);
        row.setPadding(new Insets(1, 0, 1, 0));
        HBox.setHgrow(formula, Priority.ALWAYS);

        String substitutions = renderValues(step.substitutions(), step.precision());
        String intermediates = renderValues(step.intermediateResults(), step.precision());
        Label details = new Label("Substitutions: " + (substitutions.isEmpty() ? "none" : substitutions)
                + "\nIntermediate results: " + (intermediates.isEmpty() ? "none" : intermediates));
        details.setId("logicStepDetails" + index);
        details.setWrapText(true);
        details.getStyleClass().add("muted-copy");
        return new VBox(3, row, details);
    }

    private static String format(PhysicalQuantity quantity, ProjectileSolution.PrecisionPolicy precision) {
        String value = BigDecimal.valueOf(quantity.value())
                .setScale(precision.displayedDecimalPlaces(), precision.roundingMode()).toPlainString();
        return value + " " + quantity.unit().symbol();
    }

    private static String renderValues(Map<String, PhysicalQuantity> values,
                                       ProjectileSolution.PrecisionPolicy precision) {
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + " = " + format(entry.getValue(), precision))
                .reduce((left, right) -> left + "   ·   " + right).orElse("");
    }

    private static void setOptional(Label label, OptionalDouble result, String unit, String empty) {
        label.setText(result.isPresent()
                ? String.format(Locale.ROOT, "%.2f %s", result.getAsDouble(), unit) : empty);
    }

    private static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("eyebrow");
        return label;
    }

    private static Label body(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("muted-copy");
        return label;
    }

    private static Region rule() {
        Region rule = new Region();
        rule.setPrefHeight(1);
        rule.getStyleClass().add("horizontal-rule");
        return rule;
    }

    private static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }
}
