package edu.physics.ui;

import edu.physics.projectile.ProjectileMotion;
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
import java.util.ArrayList;
import java.util.List;

/** Live equation and assumption inspector for the same projectile model. */
final class ProjectileLogicView extends BorderPane {
    private final ProjectileSimulationController controller;
    private final VBox content = new VBox(4);
    private final List<Label> equationValues = new ArrayList<>();
    private final Label parameterValues = new Label();
    private final Label currentStateValue = new Label();
    private final Label maximumHeightValue = new Label();
    private final Label flightTimeValue = new Label();
    private final Label horizontalRangeValue = new Label();

    ProjectileLogicView(ProjectileSimulationController controller) {
        this.controller = controller;
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
                sectionTitle("INITIAL VELOCITY COMPONENTS"),
                equationRow("vx0 = v0 cos(theta)"),
                equationRow("vy0 = v0 sin(theta)"),
                horizontalRule(),
                sectionTitle("POSITION AS A FUNCTION OF TIME"),
                equationRow("x(t) = x0 + vx0 * t"),
                equationRow("y(t) = y0 + vy0 * t - 0.5 * g * t^2"),
                horizontalRule(),
                sectionTitle("VELOCITY AS A FUNCTION OF TIME"),
                equationRow("vx(t) = vx0"),
                equationRow("vy(t) = vy0 - g * t"),
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
        controller.simulationTimeProperty().addListener((obs, oldValue, newValue) -> refresh());
        refresh();
    }

    private VBox assumptionBlock() {
        VBox block = new VBox(6,
                sectionTitle("MODEL ASSUMPTIONS"),
                body("Two-dimensional point particle · constant downward gravity · air resistance ignored · launch and landing surface at y = 0."));
        block.getStyleClass().add("logic-assumptions");
        block.setPadding(new Insets(12, 14, 12, 14));
        return block;
    }

    private HBox equationRow(String equation) {
        Label formula = new Label(equation);
        formula.getStyleClass().add("equation-text");
        Label evaluated = new Label();
        evaluated.getStyleClass().add("equation-result");
        equationValues.add(evaluated);
        HBox row = new HBox(15, formula, spacer(), evaluated);
        row.setPadding(new Insets(1, 0, 1, 0));
        HBox.setHgrow(formula, Priority.ALWAYS);
        return row;
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
        double time = controller.simulationTimeProperty().get();
        ProjectileMotion.State initial = model.stateAt(0.0);
        ProjectileMotion.State state = model.stateAt(time);
        parameterValues.setText(String.format(Locale.ROOT,
                "v0 %.2f m/s    theta %.2f deg    g %.2f m/s^2", speed, angle, gravity));

        updateEquation(0, String.format(Locale.ROOT, "%.2f * cos(%.2f deg) = %.2f m/s", speed, angle, initial.vx()));
        updateEquation(1, String.format(Locale.ROOT, "%.2f * sin(%.2f deg) = %.2f m/s", speed, angle, initial.vy()));
        updateEquation(2, String.format(Locale.ROOT, "x(t) = %.2f + %.2f * t m", 0.0, initial.vx()));
        updateEquation(3, String.format(Locale.ROOT, "y(t) = %.2f + %.2f * t - %.3f * t^2 m", 0.0, initial.vy(), 0.5 * gravity));
        updateEquation(4, String.format(Locale.ROOT, "vx(t) = %.2f m/s", initial.vx()));
        updateEquation(5, String.format(Locale.ROOT, "vy(t) = %.2f - %.2f * t m/s", initial.vy(), gravity));

        currentStateValue.setText(String.format(Locale.ROOT,
                "%.2f s   |   x %.2f m   |   y %.2f m   |   vx %.2f m/s   |   vy %.2f m/s",
                time, state.x(), state.y(), state.vx(), state.vy()));
        setOptional(maximumHeightValue, model.maximumHeight(), "m", "Unbounded");
        setOptional(flightTimeValue, model.timeOfFlight(), "s", "No landing");
        setOptional(horizontalRangeValue, model.horizontalRange(), "m", "No landing");
    }

    private void updateEquation(int index, String text) {
        equationValues.get(index).setText(text);
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
