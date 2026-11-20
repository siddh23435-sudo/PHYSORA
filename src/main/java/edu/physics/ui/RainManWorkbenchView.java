package edu.physics.ui;

import edu.physics.relative.RainManSimulationModel;
import edu.physics.simulation.Vector2D;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
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

/**
 * Interactive visual workbench for Chapter 02: Rain-Man & Rain-Umbrella Relative Motion.
 *
 * <p>Enables students to explore how observer walking velocity transforms falling rain
 * in the observer reference frame, calculate the optimal umbrella shielding shaft angle,
 * and visualize the exact Galilean vector addition triangle.</p>
 */
public final class RainManWorkbenchView extends BorderPane {

    private final RainManSimulationController controller;
    private final RainManCanvasRenderer canvasRenderer = new RainManCanvasRenderer();
    private final RainManVelocityPane velocityPane = new RainManVelocityPane();

    // Transport Bar Controls
    private final Button playPauseBtn = new Button("▶ PLAY");
    private final Button stepBackBtn = new Button("⏮");
    private final Button stepForwardBtn = new Button("⏭");
    private final Button resetBtn = new Button("↺ RESET");
    private final Slider timeSlider = new Slider();
    private final Label timeReadout = new Label("t = 0.00 s / 30.00 s");

    // Physical Scenario Numeric Parameter Inputs (Standardized)
    private final NumericParameterRow vDownRow = new NumericParameterRow(
            "Downward Speed (v_d):", "m/s", "vDownInput", 8.0, 1.0, 30.0, "%.1f", v -> onParameterInputChanged());
    private final NumericParameterRow vRainXRow = new NumericParameterRow(
            "Horizontal Wind (v_rx):", "m/s", "vRainXInput", 0.0, -25.0, 25.0, "%.1f", v -> onParameterInputChanged());
    private final NumericParameterRow vPersonRow = new NumericParameterRow(
            "Walker Velocity (v_m):", "m/s", "vPersonInput", 3.0, -25.0, 25.0, "%.1f", v -> onParameterInputChanged());

    // Umbrella Angle Input & Controls
    private final NumericParameterRow umbrellaAngleRow = new NumericParameterRow(
            "Shaft Angle (φ):", "°", "umbrellaAngleInput", 0.0, -89.9, 89.9, "%.1f", v -> onParameterInputChanged());
    private final Button snapOptimalBtn = new Button("🎯 SNAP TO OPTIMAL ANGLE");

    private final ComboBox<String> presetCombo = new ComboBox<>();

    // Analytical Inspector Structured Elements
    private final Label alignmentStatusBadge = new Label();
    private final Label valOptimalAngle = new Label();
    private final Label valActualAngle = new Label();
    private final Label valAngularError = new Label();
    private final Label valApparentSpeed = new Label();
    private final Label valApparentVelocity = new Label();

    private final Button returnButton = new Button("⚗ RETURN TO KINEMATICS");
    private Runnable onReturnToKinematics;
    private Runnable onOpenInverseInvestigator;
    private VBox sidebar;

    private boolean updatingInputsFromModel = false;

    public RainManWorkbenchView(RainManSimulationController controller) {
        this.controller = controller;

        setId("rainManWorkbenchView");
        setStyle("-fx-background-color: #f5f1e5;");
        setPadding(new Insets(10, 16, 10, 16));

        setTop(buildTopBar());
        setCenter(buildCenterArea());
        setBottom(buildTransportBar());

        initListeners();
        syncInputsToModel(controller.model());
        updateAnalyticalDisplay();
        renderActiveState();
    }

    public RainManSimulationController controller() {
        return controller;
    }

    public Button returnButton() {
        return returnButton;
    }

    public RainManVelocityPane velocityPane() {
        return velocityPane;
    }

    @Override
    public Node lookup(String selector) {
        Node found = super.lookup(selector);
        if (found != null) {
            return found;
        }
        if (sidebar != null) {
            return sidebar.lookup(selector);
        }
        return null;
    }

    public void setOnReturnToKinematics(Runnable callback) {
        this.onReturnToKinematics = callback;
    }

    public void setOnOpenInverseInvestigator(Runnable callback) {
        this.onOpenInverseInvestigator = callback;
    }

    public Button openInverseInvestigatorButton() {
        return (Button) lookup("#openInverseInvestigatorBtn");
    }

    public void dispose() {
        controller.dispose();
    }

    private VBox buildTopBar() {
        Label eyebrow = new Label("PHYSORA CHAPTER 02 / RELATIVE MOTION");
        eyebrow.getStyleClass().add("eyebrow");
        eyebrow.setTextFill(PhysoraTheme.MUTED);

        Label title = new Label("RAIN-MAN & UMBRELLA RELATIVE MOTION WORKBENCH");
        title.setFont(Font.font("Georgia", FontWeight.BOLD, 20));
        title.setTextFill(PhysoraTheme.INK);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        returnButton.setId("returnToKinematicsButton");
        returnButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #2453d4; -fx-font-weight: bold; " +
                "-fx-border-color: #2453d4; -fx-border-radius: 4px; -fx-padding: 6 14 6 14; -fx-font-size: 11px;");
        returnButton.setOnAction(e -> {
            if (onReturnToKinematics != null) {
                onReturnToKinematics.run();
            }
        });

        HBox controlRow = new HBox(12, spacer, returnButton);
        controlRow.setAlignment(Pos.CENTER_RIGHT);
        controlRow.setPadding(new Insets(4, 0, 4, 0));

        return new VBox(2, eyebrow, title, controlRow);
    }

    private BorderPane buildCenterArea() {
        BorderPane center = new BorderPane();

        // Genuinely resizable canvas container
        StackPane canvasHost = new StackPane(canvasRenderer.canvas());
        canvasHost.setAlignment(Pos.CENTER);
        canvasHost.setStyle("-fx-background-color: #0f172a; -fx-border-color: #334155; -fx-border-radius: 6px; -fx-background-radius: 6px;");
        canvasHost.setPadding(new Insets(6));
        canvasHost.setMinSize(440, 240);

        canvasRenderer.canvas().widthProperty().bind(
                javafx.beans.binding.Bindings.max(400.0, canvasHost.widthProperty().subtract(14.0)));
        canvasRenderer.canvas().heightProperty().bind(
                javafx.beans.binding.Bindings.max(220.0, canvasHost.heightProperty().subtract(14.0)));

        canvasRenderer.canvas().widthProperty().addListener(obs -> renderActiveState());
        canvasRenderer.canvas().heightProperty().addListener(obs -> renderActiveState());

        center.setCenter(canvasHost);

        // Scrollable Sidebar with Presets, Parameters, Umbrella Controls, Velocity Triangle, and Inspector
        this.sidebar = buildSidebar();
        sidebar.setPadding(new Insets(0, 0, 0, 10));
        sidebar.setPrefWidth(375);
        sidebar.setMinWidth(345);

        ScrollPane sidebarScroll = new ScrollPane(sidebar);
        sidebarScroll.getStyleClass().add("sidebar-scroll");
        sidebarScroll.setFitToWidth(true);
        sidebarScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sidebarScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sidebarScroll.setStyle("-fx-background-color: #f5f1e5; -fx-background: #f5f1e5; -fx-border-color: transparent;");
        sidebarScroll.setPrefWidth(385);
        sidebarScroll.setMinWidth(355);
        sidebarScroll.setMaxWidth(405);
        center.setRight(sidebarScroll);

        return center;
    }

    private VBox buildSidebar() {
        // 1. Scenario Presets
        Label presetTitle = new Label("JEE SCENARIO PRESETS");
        presetTitle.getStyleClass().add("inspector-heading");
        presetTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        presetTitle.setStyle("-fx-text-fill: #334155;");

        presetCombo.setId("scenarioPresetCombo");
        presetCombo.getItems().addAll(
                "Vertical Rain — Stationary Observer (vm = 0)",
                "Vertical Rain — Forward Walking (Standard JEE)",
                "Tailwind Equal Speed (vrx = vm, Rain appears vertical)",
                "Headwind Walking (Wind opposes motion)",
                "Backward Walking (vm < 0)"
        );
        presetCombo.setValue("Vertical Rain — Forward Walking (Standard JEE)");
        presetCombo.setMaxWidth(Double.MAX_VALUE);
        presetCombo.setStyle("-fx-font-size: 11.5px; -fx-padding: 3 6 3 6;");

        presetCombo.valueProperty().addListener((obs, oldVal, selected) -> {
            if (selected == null) return;
            if (selected.contains("Stationary Observer")) {
                controller.setModel(RainManSimulationModel.presetStationaryVerticalRain());
            } else if (selected.contains("Forward Walking")) {
                controller.setModel(RainManSimulationModel.presetForwardWalkingVerticalRain());
            } else if (selected.contains("Tailwind Equal Speed")) {
                controller.setModel(RainManSimulationModel.presetTailwindZeroRelativeHorizontal());
            } else if (selected.contains("Headwind Walking")) {
                controller.setModel(RainManSimulationModel.presetHeadwindRunning());
            } else if (selected.contains("Backward Walking")) {
                controller.setModel(RainManSimulationModel.presetBackwardWalking());
            }
            syncInputsToModel(controller.model());
            updateAnalyticalDisplay();
            renderActiveState();
        });

        VBox presetBox = new VBox(4, presetTitle, presetCombo);

        // 2. Physical Parameters Card
        VBox paramsCard = buildParametersCard();

        // 3. Umbrella Angle Control Card
        VBox umbrellaCard = buildUmbrellaCard();

        // 4. Analytical Inspector Card
        VBox inspectorBox = buildAnalyticalCard();

        // 5. Guided Learning Card (Predict -> Experiment -> Understand -> Apply)
        VBox learningCard = buildGuidedLearningCard();

        return new VBox(8, presetBox, paramsCard, umbrellaCard, velocityPane, inspectorBox, learningCard);
    }

    private VBox buildParametersCard() {
        Label title = new Label("PHYSICAL SCENARIO PARAMETERS");
        title.getStyleClass().add("inspector-heading");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        title.setStyle("-fx-text-fill: #334155;");

        VBox box = new VBox(6, title, vDownRow, vRainXRow, vPersonRow);
        box.setStyle("-fx-background-color: #fcfbf7; -fx-border-color: #cbc4b4; -fx-border-radius: 6px; -fx-padding: 8;");
        return box;
    }

    private VBox buildUmbrellaCard() {
        Label title = new Label("UMBRELLA SHAFT ORIENTATION");
        title.getStyleClass().add("inspector-heading");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        title.setStyle("-fx-text-fill: #334155;");

        snapOptimalBtn.setId("snapOptimalButton");
        snapOptimalBtn.setMaxWidth(Double.MAX_VALUE);
        snapOptimalBtn.setStyle("-fx-background-color: #2453d4; -fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-font-size: 11px; -fx-padding: 6 10 6 10; -fx-background-radius: 4px;");
        snapOptimalBtn.setOnAction(e -> {
            RainManSimulationModel snapped = controller.model().snapToOptimal();
            controller.updateModelPreservingTime(snapped);
            syncInputsToModel(snapped);
            updateAnalyticalDisplay();
            renderActiveState();
        });

        VBox box = new VBox(6, title, umbrellaAngleRow, snapOptimalBtn);
        box.setStyle("-fx-background-color: #fcfbf7; -fx-border-color: #cbc4b4; -fx-border-radius: 6px; -fx-padding: 8;");
        return box;
    }

    private VBox buildAnalyticalCard() {
        Label title = new Label("RELATIVE MOTION & ALIGNMENT INSPECTOR");
        title.getStyleClass().add("inspector-heading");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        title.setStyle("-fx-text-fill: #334155;");

        alignmentStatusBadge.setId("alignmentStatusBadge");
        alignmentStatusBadge.setMaxWidth(Double.MAX_VALUE);
        alignmentStatusBadge.setAlignment(Pos.CENTER);
        alignmentStatusBadge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        alignmentStatusBadge.setPadding(new Insets(5, 8, 5, 8));

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(4);

        valOptimalAngle.setId("valOptimalAngle");
        valActualAngle.setId("valActualAngle");
        valAngularError.setId("valAngularError");
        valApparentSpeed.setId("valApparentSpeed");
        valApparentVelocity.setId("valApparentVelocity");

        addInspectorRow(grid, 0, "Optimal Tilt (φ_opt):", valOptimalAngle);
        addInspectorRow(grid, 1, "Actual Tilt (φ_act):", valActualAngle);
        addInspectorRow(grid, 2, "Angular Alignment Error (|Δφ|):", valAngularError);
        addInspectorRow(grid, 3, "Apparent Speed (|v_{r/m}|):", valApparentSpeed);
        addInspectorRow(grid, 4, "Apparent Velocity (v_{r/m}):", valApparentVelocity);

        VBox box = new VBox(6, title, alignmentStatusBadge, grid);
        box.setStyle("-fx-background-color: #fcfbf7; -fx-border-color: #cbc4b4; -fx-border-radius: 6px; -fx-padding: 8;");
        return box;
    }

    private void addInspectorRow(GridPane grid, int row, String name, Label valLabel) {
        Label nameLbl = new Label(name);
        nameLbl.getStyleClass().add("metric-name");
        nameLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
        nameLbl.setStyle("-fx-text-fill: #1e293b; -fx-font-weight: bold;");
        nameLbl.setMinWidth(140);

        valLabel.getStyleClass().add("metric-value");
        valLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
        valLabel.setStyle("-fx-text-fill: #0f172a;");
        valLabel.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(valLabel, Priority.ALWAYS);

        grid.add(nameLbl, 0, row);
        grid.add(valLabel, 1, row);
    }

    private VBox buildGuidedLearningCard() {
        Label title = new Label("💡 GUIDED LEARNING & VALUE INTERPRETATION");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        title.setStyle("-fx-text-fill: #1e293b;");

        Label concept = new Label(
                "• Why does rain appear slanted? In the walker frame, the ground rushes backward at -v_m, adding a relative horizontal wind.\n" +
                "• Why tilt forward? Incoming apparent rain approaches along v_{r/m}. The umbrella shaft must point in the exact opposite direction: u_incoming = -v_{r/m} = (v_m - v_rx, v_d).\n" +
                "• Optimal tilt angle formula: tan(φ_opt) = (v_m - v_rx) / v_d from upward vertical.\n" +
                "• Alignment rule: When actual tilt φ_act matches φ_opt (Error ≤ 1.0°), the umbrella shaft aligns parallel to the incoming apparent rain direction (-v_r/m). This models geometric orientation, not droplet interception or personal waterproofing."
        );
        concept.setStyle("-fx-font-size: 10px; -fx-text-fill: #334155; -fx-line-spacing: 2.5px;");
        concept.setWrapText(true);

        Button openInverseBtn = new Button("🔬 INVERSE PROBLEM: RECONSTRUCT RAIN FROM 2 OBSERVERS ▶");
        openInverseBtn.setId("openInverseInvestigatorBtn");
        openInverseBtn.setStyle("-fx-background-color: #2453d4; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 10px; -fx-padding: 5 10 5 10; -fx-border-radius: 4px;");
        openInverseBtn.setOnAction(e -> {
            if (onOpenInverseInvestigator != null) {
                onOpenInverseInvestigator.run();
            }
        });

        VBox box = new VBox(6, title, concept, openInverseBtn);
        box.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-padding: 8 10 8 10;");
        return box;
    }

    private BorderPane buildTransportBar() {
        playPauseBtn.setId("playPauseButton");
        stepBackBtn.setId("stepBackButton");
        stepForwardBtn.setId("stepForwardButton");
        resetBtn.setId("resetButton");
        timeSlider.setId("timeSlider");

        playPauseBtn.setStyle("-fx-background-color: #2453d4; -fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-padding: 6 14 6 14; -fx-background-radius: 4px;");
        stepBackBtn.setStyle("-fx-background-color: #e2e8f0; -fx-font-weight: bold; -fx-padding: 6 10 6 10; -fx-background-radius: 4px;");
        stepForwardBtn.setStyle("-fx-background-color: #e2e8f0; -fx-font-weight: bold; -fx-padding: 6 10 6 10; -fx-background-radius: 4px;");
        resetBtn.setStyle("-fx-background-color: #e2e8f0; -fx-font-weight: bold; -fx-padding: 6 12 6 12; -fx-background-radius: 4px;");

        playPauseBtn.setOnAction(e -> controller.togglePlayPause());
        stepBackBtn.setOnAction(e -> controller.stepBackward(0.25));
        stepForwardBtn.setOnAction(e -> controller.stepForward(0.25));
        resetBtn.setOnAction(e -> controller.reset());

        timeSlider.setMin(0.0);
        timeSlider.setMax(controller.maxTime());
        timeSlider.setValue(0.0);
        timeSlider.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(timeSlider, Priority.ALWAYS);

        timeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (timeSlider.isValueChanging()) {
                controller.seek(newVal.doubleValue());
            }
        });

        timeReadout.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
        timeReadout.setTextFill(PhysoraTheme.INK);
        timeReadout.setMinWidth(140);
        timeReadout.setAlignment(Pos.CENTER_RIGHT);

        HBox bar = new HBox(8, playPauseBtn, stepBackBtn, stepForwardBtn, resetBtn, timeSlider, timeReadout);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 0, 0, 0));

        BorderPane container = new BorderPane(bar);
        return container;
    }

    private void initListeners() {
        // Controller simulation time listener
        controller.simulationTimeProperty().addListener((obs, oldT, newT) -> {
            double t = newT.doubleValue();
            if (!timeSlider.isValueChanging()) {
                timeSlider.setValue(t);
            }
            timeReadout.setText(String.format(Locale.ROOT, "t = %.2f s / %.2f s", t, controller.maxTime()));
            renderActiveState();
        });

        // Controller isPlaying listener
        controller.isPlayingProperty().addListener((obs, oldP, isPlaying) -> {
            playPauseBtn.setText(isPlaying ? "⏸ PAUSE" : "▶ PLAY");
        });

        // Controller model listener
        controller.modelProperty().addListener((obs, oldM, newM) -> {
            if (newM != null) {
                syncInputsToModel(newM);
                updateAnalyticalDisplay();
                renderActiveState();
            }
        });
    }

    private void onParameterInputChanged() {
        if (updatingInputsFromModel) return;

        double vd = vDownRow.getValue();
        double vrx = vRainXRow.getValue();
        double vm = vPersonRow.getValue();
        double phi = umbrellaAngleRow.getValue();

        RainManSimulationModel updated = RainManSimulationModel.ofDegrees(vd, vrx, vm, phi);
        controller.updateModelPreservingTime(updated);

        updateAnalyticalDisplay();
        renderActiveState();
    }

    private void syncInputsToModel(RainManSimulationModel model) {
        if (model == null) return;
        updatingInputsFromModel = true;
        try {
            vDownRow.setValue(model.vDown());
            vRainXRow.setValue(model.vRainX());
            vPersonRow.setValue(model.vPerson());
            umbrellaAngleRow.setValue(model.umbrellaAngleDeg());
        } finally {
            updatingInputsFromModel = false;
        }
    }

    private void updateAnalyticalDisplay() {
        RainManSimulationModel m = controller.model();
        if (m == null) return;

        // Alignment Status Badge
        RainManSimulationModel.AlignmentStatus status = m.alignmentStatus();
        alignmentStatusBadge.setText("● Alignment Rating: " + status.label().toUpperCase(Locale.ROOT));
        alignmentStatusBadge.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-font-family: 'Segoe UI'; " +
                "-fx-font-size: 11px; -fx-font-weight: bold; -fx-border-color: %s; " +
                "-fx-border-radius: 4px; -fx-background-radius: 4px;",
                status.backgroundColor(), status.textColor(), status.textColor()
        ));

        double optDeg = Math.abs(m.optimalAngleDeg()) < 1e-6 ? 0.0 : m.optimalAngleDeg();
        double actDeg = Math.abs(m.umbrellaAngleDeg()) < 1e-6 ? 0.0 : m.umbrellaAngleDeg();
        double errDeg = Math.abs(m.angularErrorDeg()) < 1e-6 ? 0.0 : m.angularErrorDeg();

        // Readouts
        valOptimalAngle.setText(String.format(Locale.ROOT, "%+.1f°", optDeg));
        valActualAngle.setText(String.format(Locale.ROOT, "%+.1f°", actDeg));
        valAngularError.setText(String.format(Locale.ROOT, "%.1f°", errDeg));
        valApparentSpeed.setText(String.format(Locale.ROOT, "%.2f m/s", m.apparentRainSpeed()));

        Vector2D vrm = m.apparentRainVelocity();
        valApparentVelocity.setText(String.format(Locale.ROOT, "(%+.1f, %+.1f) m/s", vrm.x(), vrm.y()));

        // Velocity triangle update
        velocityPane.update(m);
    }

    private void renderActiveState() {
        canvasRenderer.render(controller.model(), controller.simulationTime());
    }

    public ComboBox<String> presetCombo() {
        return presetCombo;
    }

    public TextField vDownInput() {
        return vDownRow.valueField();
    }

    public TextField vRainXInput() {
        return vRainXRow.valueField();
    }

    public TextField vPersonInput() {
        return vPersonRow.valueField();
    }

    public TextField umbrellaAngleInput() {
        return umbrellaAngleRow.valueField();
    }

    public NumericParameterRow vDownRow() {
        return vDownRow;
    }

    public NumericParameterRow vRainXRow() {
        return vRainXRow;
    }

    public NumericParameterRow vPersonRow() {
        return vPersonRow;
    }

    public NumericParameterRow umbrellaAngleRow() {
        return umbrellaAngleRow;
    }
}
