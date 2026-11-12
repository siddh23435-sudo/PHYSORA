package edu.physics.ui;

import edu.physics.projectile.ProjectileMotion;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Locale;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.OptionalDouble;

/** Coordinates the chapter workspace, model inputs, and the two live model views. */
final class NormalBenchView extends BorderPane {
    private final ProjectileSimulationController controller;
    private final ProjectileSimulationView simulationView;
    private final ProjectileLogicView logicView;
    private final AIProblemView aiProblemView;
    private final StackPane viewStack;
    private final Label positionOutput = outputValue();
    private final Label velocityOutput = outputValue();
    private final Label peakOutput = outputValue();
    private final Label flightOutput = outputValue();
    private final Label rangeOutput = outputValue();
    private final Label clockOutput = new Label("t = 0.00 s");
    private final Map<TextField, javafx.beans.property.DoubleProperty> numericControls = new IdentityHashMap<>();
    private boolean updatingTimeSlider;
    private Node simulationFooter;

    NormalBenchView(ProjectileSimulationController controller) {
        this.controller = controller;
        positionOutput.setId("positionOutput");
        velocityOutput.setId("velocityOutput");
        peakOutput.setId("peakOutput");
        flightOutput.setId("flightOutput");
        rangeOutput.setId("rangeOutput");
        clockOutput.setId("clockOutput");
        simulationView = new ProjectileSimulationView(controller);
        logicView = new ProjectileLogicView(controller);
        aiProblemView = new AIProblemView();
        logicView.setId("logicView");
        viewStack = new StackPane(simulationView, logicView, aiProblemView);
        logicView.setVisible(false);
        logicView.setManaged(false);
        aiProblemView.setVisible(false);
        aiProblemView.setManaged(false);

        getStyleClass().add("bench-view");
        setTop(buildTop());
        HBox workArea = new HBox(20, buildViews(), buildInspector());
        workArea.setPadding(new Insets(0, 24, 8, 24));
        setCenter(workArea);
        simulationFooter = buildFooter();
        setBottom(simulationFooter);

        controller.modelProperty().addListener((obs, oldModel, newModel) -> refreshOutputs());
        controller.simulationTimeProperty().addListener((obs, oldTime, newTime) -> refreshOutputs());
        controller.launchSpeedProperty().addListener((obs, oldValue, newValue) -> refreshOutputs());
        controller.launchAngleProperty().addListener((obs, oldValue, newValue) -> refreshOutputs());
        controller.gravityProperty().addListener((obs, oldValue, newValue) -> refreshOutputs());
        controller.initialXProperty().addListener((obs, oldValue, newValue) -> refreshOutputs());
        controller.initialYProperty().addListener((obs, oldValue, newValue) -> refreshOutputs());
        controller.landingYProperty().addListener((obs, oldValue, newValue) -> refreshOutputs());
        refreshOutputs();
    }

    private VBox buildTop() {
        HBox masthead = new HBox(10,
                eyebrow("NORMAL BENCH"), rule(),
                muted("01   /   KINEMATICS"), spacer(), muted("MODEL 01   ·   IDEAL PROJECTILE"));
        masthead.setAlignment(Pos.CENTER_LEFT);
        masthead.setPadding(new Insets(4, 26, 4, 28));
        masthead.setStyle("-fx-border-color: transparent transparent #cbc4b4 transparent;");

        HBox hero = new HBox(22);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.setPadding(new Insets(4, 28, 4, 28));
        hero.setStyle("-fx-background-color: #e9e2d0;");

        Label chapter = new Label("01");
        chapter.setFont(Font.font("Georgia", FontWeight.BOLD, 42));
        chapter.setTextFill(PhysoraTheme.COBALT);
        Label vertical = new Label("KINEMATICS");
        vertical.getStyleClass().add("hero-kicker");
        VBox number = new VBox(-6, chapter, vertical);
        number.setAlignment(Pos.CENTER_LEFT);

        VBox titleBlock = new VBox(5,
                eyebrow("PROJECTILE MOTION   /   CONSTANT GRAVITY"),
                new Label("Motion is written\nin vectors."),
                muted("Change the launch. Read the path. Follow the model."));
        titleBlock.getChildren().get(1).getStyleClass().add("hero-title");
        HBox.setHgrow(titleBlock, Priority.ALWAYS);

        VBox notation = new VBox(7,
                eyebrow("INITIAL VELOCITY"),
                notation("v0 = (vx0, vy0)"),
                muted("horizontal  /  vertical"));
        notation.getStyleClass().add("hero-notation");
        notation.setAlignment(Pos.CENTER_LEFT);
        hero.getChildren().addAll(number, rule(), titleBlock, notation);

        HBox viewBar = buildViewBar();
        VBox top = new VBox(masthead, hero, viewBar);
        return top;
    }

    private HBox buildViewBar() {
        ToggleGroup group = new ToggleGroup();
        ToggleButton simulationTab = new ToggleButton("SIMULATION VIEW");
        ToggleButton logicTab = new ToggleButton("LOGIC VIEW");
        ToggleButton aiTab = new ToggleButton("AI PROBLEM");
        simulationTab.setId("simulationViewTab");
        logicTab.setId("logicViewTab");
        aiTab.setId("aiProblemTab");
        simulationTab.setToggleGroup(group);
        logicTab.setToggleGroup(group);
        aiTab.setToggleGroup(group);
        simulationTab.setSelected(true);
        simulationTab.getStyleClass().add("view-tab");
        logicTab.getStyleClass().add("view-tab");
        aiTab.getStyleClass().add("view-tab");
        simulationTab.setOnAction(event -> showView(ActiveView.SIMULATION));
        logicTab.setOnAction(event -> showView(ActiveView.LOGIC));
        aiTab.setOnAction(event -> showView(ActiveView.AI));

        Label mode = eyebrow("SIMULATE   /   INSPECT   /   ASK");
        HBox bar = new HBox(0, simulationTab, logicTab, aiTab, spacer(), mode);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 26, 0, 28));
        bar.setStyle("-fx-border-color: transparent transparent #cbc4b4 transparent;");
        return bar;
    }

    private Node buildViews() {
        viewStack.getStyleClass().add("workspace-stage");
        HBox.setHgrow(viewStack, Priority.ALWAYS);
        viewStack.setMinWidth(480);
        return viewStack;
    }

    private Node buildInspector() {
        VBox inspector = new VBox(19);
        inspector.setPadding(new Insets(20, 18, 18, 18));
        inspector.getStyleClass().add("model-inspector");

        Label heading = eyebrow("MODEL INSPECTOR");
        Label subheading = new Label("Initial conditions");
        subheading.getStyleClass().add("inspector-title");
        VBox intro = new VBox(5, heading, subheading);

        Slider speed = parameterSlider(0, 50, 20, 1);
        Slider angle = parameterSlider(0, 90, 45, 1);
        Slider gravity = parameterSlider(0, 20, 9.81, 0.1);
        speed.setId("launchSpeedSlider");
        angle.setId("launchAngleSlider");
        gravity.setId("gravitySlider");
        speed.valueProperty().bindBidirectional(controller.launchSpeedProperty());
        angle.valueProperty().bindBidirectional(controller.launchAngleProperty());
        gravity.valueProperty().bindBidirectional(controller.gravityProperty());

        VBox coordinates = new VBox(10,
                eyebrow("LAUNCH AND LANDING"),
                numericInput("Initial x", "m", "initialXInput", controller.initialXProperty()),
                numericInput("Initial y", "m", "initialYInput", controller.initialYProperty()),
                numericInput("Landing height", "m", "landingYInput", controller.landingYProperty()));

        VBox inputs = new VBox(20,
                parameter("Launch speed", "m/s", speed, controller.launchSpeedProperty()),
                parameter("Launch angle", "°", angle, controller.launchAngleProperty()),
                parameter("Gravity", "m/s²", gravity, controller.gravityProperty()));

        Node separator = separator();
        Label assumptionsTitle = eyebrow("MODEL ASSUMPTIONS");
        Label assumptions = new Label();
        assumptions.setId("assumptionsOutput");
        refreshAssumptions(assumptions);
        assumptions.getStyleClass().add("assumption-copy");
        VBox assumptionsBlock = new VBox(9, assumptionsTitle, assumptions);
        controller.modelProperty().addListener((obs, oldModel, newModel) -> refreshAssumptions(assumptions));

        Label vectorTitle = eyebrow("SIMULATION OVERLAY");
        javafx.scene.control.CheckBox vectors = new javafx.scene.control.CheckBox("Show velocity components");
        vectors.setSelected(true);
        vectors.getStyleClass().add("vector-toggle");
        vectors.selectedProperty().addListener((obs, oldValue, newValue) ->
                simulationView.setShowVelocityComponents(newValue));
        VBox overlayBlock = new VBox(8, vectorTitle, vectors);

        inspector.getChildren().addAll(intro, separator, inputs, separator(), coordinates, separator(), assumptionsBlock,
                separator(), overlayBlock);
        ScrollPane scroll = new ScrollPane(inspector);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportWidth(286);
        scroll.setMinWidth(260);
        scroll.setMaxHeight(Double.MAX_VALUE);
        scroll.getStyleClass().add("inspector-scroll");
        return scroll;
    }

    private VBox numericInput(String name, String unit, String id,
                              javafx.beans.property.DoubleProperty property) {
        Label label = new Label(name);
        label.getStyleClass().add("parameter-name");
        TextField field = new TextField(formatInput(property.get()));
        field.setId(id);
        field.setAccessibleText(name + " in " + unit);
        field.setPrefColumnCount(8);
        numericControls.put(field, property);
        Label unitLabel = new Label(unit);
        unitLabel.getStyleClass().add("parameter-value");
        Label error = new Label();
        error.setId(id + "Error");
        error.getStyleClass().add("validation-message");
        error.setVisible(false);
        error.setManaged(false);
        field.textProperty().addListener((obs, oldText, text) -> {
            try {
                if (text == null || text.isBlank()) throw new NumberFormatException();
                double value = Double.parseDouble(text.trim());
                if (!Double.isFinite(value)) throw new NumberFormatException();
                property.set(value);
                field.getStyleClass().remove("input-invalid");
                error.setText("");
                error.setVisible(false);
                error.setManaged(false);
            } catch (NumberFormatException ex) {
                if (!field.getStyleClass().contains("input-invalid")) field.getStyleClass().add("input-invalid");
                error.setText("Enter a finite number.");
                error.setVisible(true);
                error.setManaged(true);
            }
        });
        property.addListener((obs, oldValue, newValue) -> {
            if (!field.isFocused()) field.setText(formatInput(newValue.doubleValue()));
        });
        return new VBox(5, label, new HBox(8, field, unitLabel), error);
    }

    private void refreshAssumptions(Label assumptions) {
        ProjectileMotion model = controller.model();
        assumptions.setText(String.format(Locale.ROOT,
                "2D motion\nConstant downward gravity\nAir resistance ignored\nLaunch (%.2f, %.2f) m\nLanding surface y = %.2f m",
                model.initialX(), model.initialY(), model.landingY()));
    }

    private static String formatInput(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private VBox parameter(String name, String unit, Slider slider, javafx.beans.property.DoubleProperty property) {
        Label label = new Label(name);
        label.getStyleClass().add("parameter-name");
        Label value = new Label();
        value.getStyleClass().add("parameter-value");
        ChangeListener<Number> update = (obs, oldValue, newValue) ->
                value.setText(String.format(Locale.ROOT, "%.2f  %s", newValue.doubleValue(), unit));
        property.addListener(update);
        update.changed(null, property.get(), property.get());
        VBox item = new VBox(8, new HBoxWithTrailing(name, label, value), slider);
        return item;
    }

    private static final class HBoxWithTrailing extends HBox {
        HBoxWithTrailing(String name, Node left, Node right) {
            super(8, left, spacer(), right);
            setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(getChildren().get(1), Priority.ALWAYS);
        }
    }

    private VBox buildFooter() {
        HBox results = new HBox(0,
                result("POSITION", positionOutput),
                result("VELOCITY COMPONENTS", velocityOutput),
                result("MAX HEIGHT", peakOutput),
                result("FLIGHT TIME", flightOutput),
                result("RANGE", rangeOutput));
        results.getStyleClass().add("result-strip");
        for (Node child : results.getChildren()) {
            HBox.setHgrow(child, Priority.ALWAYS);
        }

        Button play = new Button("▶  PLAY");
        Button pause = new Button("Ⅱ  PAUSE");
        Button reset = new Button("↺  RESET");
        play.getStyleClass().add("transport-primary");
        pause.getStyleClass().add("transport-secondary");
        reset.getStyleClass().add("transport-secondary");
        play.setId("playButton");
        pause.setId("pauseButton");
        reset.setId("resetButton");
        play.setOnAction(event -> controller.play());
        pause.setOnAction(event -> controller.pause());
        reset.setOnAction(event -> {
            controller.reset();
            numericControls.forEach((field, property) -> field.setText(formatInput(property.get())));
        });
        updatePlayability(play);
        controller.playingProperty().addListener((obs, oldValue, newValue) -> updatePlayability(play));
        controller.modelProperty().addListener((obs, oldValue, newValue) -> updatePlayability(play));
        pause.disableProperty().bind(controller.playingProperty().not());

        Slider timeline = new Slider(0, controller.timelineDuration(), 0);
        timeline.setId("timeSlider");
        timeline.setMaxWidth(Double.MAX_VALUE);
        timeline.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!updatingTimeSlider) {
                controller.seek(newValue.doubleValue());
            }
        });
        controller.simulationTimeProperty().addListener((obs, oldValue, newValue) -> {
            updatingTimeSlider = true;
            timeline.setValue(newValue.doubleValue());
            updatingTimeSlider = false;
        });
        controller.modelProperty().addListener((obs, oldModel, newModel) ->
                timeline.setMax(controller.timelineDuration()));

        Slider playback = new Slider(0.25, 3.0, 1.0);
        playback.setId("playbackRateSlider");
        playback.setPrefWidth(130);
        playback.valueProperty().bindBidirectional(controller.playbackSpeedProperty());
        Label speedText = new Label("1.00×");
        speedText.getStyleClass().add("transport-time");
        playback.valueProperty().addListener((obs, oldValue, newValue) ->
                speedText.setText(String.format(Locale.ROOT, "%.2f×", newValue.doubleValue())));

        clockOutput.getStyleClass().add("transport-time");
        HBox transport = new HBox(12, play, pause, reset, timeline, clockOutput,
                eyebrow("RATE"), playback, speedText);
        transport.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(timeline, Priority.ALWAYS);
        transport.getStyleClass().add("transport-strip");
        controller.simulationTimeProperty().addListener((obs, oldValue, newValue) ->
                clockOutput.setText(String.format(Locale.ROOT, "t = %.2f s", newValue.doubleValue())));

        VBox footer = new VBox(0, results, transport);
        footer.setPadding(new Insets(0, 24, 8, 24));
        return footer;
    }

    private void updatePlayability(Button play) {
        OptionalDouble flightTime = controller.model().timeOfFlight();
        boolean immediateLanding = flightTime.isPresent() && flightTime.getAsDouble() <= 0.0;
        play.setDisable(controller.playingProperty().get() || immediateLanding);
    }

    private void refreshOutputs() {
        ProjectileMotion model = controller.model();
        ProjectileMotion.State state = model.stateAt(controller.simulationTimeProperty().get());
        positionOutput.setText(String.format(Locale.ROOT, "x %.2f m   /   y %.2f m", state.x(), state.y()));
        velocityOutput.setText(String.format(Locale.ROOT, "vx %.2f   /   vy %.2f m/s", state.vx(), state.vy()));
        peakOutput.setText(optional(model.maximumHeight(), "m", "unbounded"));
        flightOutput.setText(optional(model.timeOfFlight(), "s", "no landing"));
        rangeOutput.setText(optional(model.horizontalRange(), "m", "no landing"));
    }

    private void showView(ActiveView activeView) {
        boolean simulation = activeView == ActiveView.SIMULATION;
        boolean logic = activeView == ActiveView.LOGIC;
        boolean ai = activeView == ActiveView.AI;
        simulationView.setVisible(simulation);
        simulationView.setManaged(simulation);
        logicView.setVisible(logic);
        logicView.setManaged(logic);
        aiProblemView.setVisible(ai);
        aiProblemView.setManaged(ai);
        setBottom(ai ? null : simulationFooter);
    }

    void dispose() { aiProblemView.dispose(); }

    private enum ActiveView { SIMULATION, LOGIC, AI }

    private static VBox result(String title, Label value) {
        Label caption = eyebrow(title);
        VBox box = new VBox(5, caption, value);
        box.getStyleClass().add("result-cell");
        return box;
    }

    private static Slider parameterSlider(double min, double max, double value, double increment) {
        Slider slider = new Slider(min, max, value);
        slider.setBlockIncrement(increment);
        slider.setMajorTickUnit((max - min) / 3.0);
        slider.setMaxWidth(Double.MAX_VALUE);
        slider.getStyleClass().add("parameter-slider");
        return slider;
    }

    private static Label eyebrow(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("eyebrow");
        return label;
    }

    private static Label muted(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted-copy");
        return label;
    }

    private static Label notation(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("hero-notation-text");
        return label;
    }

    private static Label outputValue() {
        Label label = new Label("—");
        label.getStyleClass().add("result-value");
        return label;
    }

    private static Node rule() {
        Region rule = new Region();
        rule.setPrefSize(1, 32);
        rule.setMinWidth(1);
        rule.getStyleClass().add("vertical-rule");
        return rule;
    }

    private static Node separator() {
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

    private static String optional(OptionalDouble value, String unit, String empty) {
        return value.isPresent()
                ? String.format(Locale.ROOT, "%.2f %s", value.getAsDouble(), unit) : empty;
    }
}
