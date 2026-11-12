package edu.physics.ui;

import edu.physics.projectile.ProjectileMotion;
import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.PhysicalUnit;
import edu.physics.problem.ProjectileProblem;
import edu.physics.problem.ProjectileProblemSolver;
import edu.physics.problem.ProjectileSolution;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Locale;
import java.util.OptionalDouble;
import java.util.EnumMap;
import java.util.Map;
import java.math.BigDecimal;

/** Live equation and assumption inspector for the same projectile model. */
final class ProjectileLogicView extends BorderPane {
    private final ProjectileSimulationController controller;
    private final VBox content = new VBox(4);
    private final VBox derivation = new VBox(6);
    private final ProjectileProblemSolver solver = new ProjectileProblemSolver();
    private final Label parameterValues = new Label();
    private final Label currentStateValue = new Label();
    private final Label maximumHeightValue = new Label();
    private final Label flightTimeValue = new Label();
    private final Label horizontalRangeValue = new Label();
    private final Label assumptionValue = new Label();

    ProjectileLogicView(ProjectileSimulationController controller) {
        this.controller = controller;
        currentStateValue.setId("logicCurrentState");
        setPadding(new Insets(12, 20, 10, 20));
        getStyleClass().add("logic-view");

        Label title = new Label("MODEL LOGIC");
        title.getStyleClass().add("simulation-heading");
        Label description = new Label("The values below are derived from the active projectile model.");
        description.getStyleClass().add("muted-copy");
        setTop(new VBox(4, title, description));

        content.setPadding(new Insets(10, 0, 6, 0));
        content.getChildren().addAll(
                assumptionBlock(),
                parameterLine(),
                sectionTitle("MODEL DERIVATION"),
                derivation,
                horizontalRule(),
                sectionTitle("CURRENT STATE"),
                currentState(),
                resultLine("Maximum height", maximumHeightValue),
                resultLine("Flight time", flightTimeValue),
                resultLine("Horizontal range", horizontalRangeValue));
        setCenter(content);
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

    private VBox assumptionBlock() {
        assumptionValue.setWrapText(true);
        assumptionValue.getStyleClass().add("muted-copy");
        VBox block = new VBox(6,
                sectionTitle("MODEL ASSUMPTIONS"),
                assumptionValue);
        block.getStyleClass().add("logic-assumptions");
        block.setPadding(new Insets(12, 14, 12, 14));
        return block;
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

    private HBox currentState() {
        currentStateValue.getStyleClass().add("state-value");
        HBox row = new HBox(10, new Label("At t ="), currentStateValue);
        row.getStyleClass().add("state-line");
        return row;
    }

    private VBox parameterLine() {
        parameterValues.getStyleClass().add("logic-parameters");
        return new VBox(5, sectionTitle("CURRENT PARAMETERS"), parameterValues);
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
        derivation.getChildren().clear();
        for (int i = 0; i < solution.steps().size(); i++) {
            derivation.getChildren().add(equationRow(solution.steps().get(i), i));
        }
        if (solution.status() != ProjectileSolution.Status.SOLVED) {
            derivation.getChildren().add(body("Unable to derive the current position: " + solution.status()));
        }

        currentStateValue.setText(String.format(Locale.ROOT,
                "%.2f s   |   x %.2f m   |   y %.2f m   |   vx %.2f m/s   |   vy %.2f m/s",
                time, state.x(), state.y(), state.vx(), state.vy()));
        setOptional(maximumHeightValue, model.maximumHeight(), "m", "Unbounded");
        setOptional(flightTimeValue, model.timeOfFlight(), "s", "No landing");
        setOptional(horizontalRangeValue, model.horizontalRange(), "m", "No landing");
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

    private static Node sectionTitle(String text) {
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

    private static Region horizontalRule() {
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
