package edu.physics.ui;

import edu.physics.relative.RiverBoatCrossingType;
import edu.physics.relative.RiverBoatResult;
import edu.physics.relative.RiverBoatSimulationModel;
import edu.physics.simulation.Vector2D;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
 * Interactive visual workbench for Chapter 02: River-Boat Vector Crossing & Drift Optimization.
 *
 * <p>Enables students to explore across-river navigation, relative velocity addition,
 * and downstream drift optimization across all classical JEE scenarios.</p>
 */
public final class RiverBoatWorkbenchView extends BorderPane {

    private final RiverBoatSimulationController controller;
    private final RiverBoatCanvasRenderer canvasRenderer = new RiverBoatCanvasRenderer();
    private final RiverBoatVelocityPane velocityPane = new RiverBoatVelocityPane();

    // Transport Bar Controls
    private final Button playPauseBtn = new Button("▶ PLAY");
    private final Button stepBackBtn = new Button("⏮");
    private final Button stepForwardBtn = new Button("⏭");
    private final Button resetBtn = new Button("↺ RESET");
    private final Slider timeSlider = new Slider();
    private final Label timeReadout = new Label("t = 0.00 s / 25.00 s");

    // Physical Scenario Numeric Parameter Inputs (Standardized)
    private final NumericParameterRow widthRow = new NumericParameterRow(
            "River Width (w):", "m", "riverWidthInput", 100.0, 20.0, 500.0, "%.0f", v -> onParameterInputChanged());
    private final NumericParameterRow riverSpeedRow = new NumericParameterRow(
            "River Current (u):", "m/s", "riverSpeedInput", 3.0, 0.0, 20.0, "%.1f", v -> onParameterInputChanged());
    private final NumericParameterRow boatSpeedRow = new NumericParameterRow(
            "Boat Speed (vb):", "m/s", "boatSpeedInput", 5.0, 0.5, 25.0, "%.1f", v -> onParameterInputChanged());
    private final NumericParameterRow headingRow = new NumericParameterRow(
            "Steering (θ):", "°", "headingInput", 36.9, -85.0, 85.0, "%.1f", v -> onParameterInputChanged());

    private final ComboBox<String> presetCombo = new ComboBox<>();

    // Analytical Inspector Structured Elements
    private final Label analyticalStatusBadge = new Label();
    private final Label valCrossingTime = new Label();
    private final Label valDownstreamDrift = new Label();
    private final Label valGroundSpeed = new Label();
    private final Label valAcrossVelocity = new Label();
    private final Label valGroundCourse = new Label();

    private final Button returnButton = new Button("⚗ RETURN TO KINEMATICS");
    private Runnable onReturnToKinematics;
    private VBox sidebar;

    private boolean updatingInputsFromModel = false;

    public RiverBoatWorkbenchView(RiverBoatSimulationController controller) {
        this.controller = controller;

        setId("riverBoatWorkbenchView");
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

    public RiverBoatSimulationController controller() {
        return controller;
    }

    public Button returnButton() {
        return returnButton;
    }

    public RiverBoatVelocityPane velocityPane() {
        return velocityPane;
    }

    @Override
    public javafx.scene.Node lookup(String selector) {
        javafx.scene.Node found = super.lookup(selector);
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

    public void dispose() {
        controller.dispose();
    }

    private VBox buildTopBar() {
        Label eyebrow = new Label("PHYSORA CHAPTER 02 / RELATIVE MOTION");
        eyebrow.getStyleClass().add("eyebrow");
        eyebrow.setTextFill(PhysoraTheme.MUTED);

        Label title = new Label("RIVER-BOAT VECTOR CROSSING & PURSUIT DYNAMICS");
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
        canvasHost.setStyle("-fx-background-color: #ffffff; -fx-border-color: #cbc4b4; -fx-border-radius: 6px; -fx-background-radius: 6px;");
        canvasHost.setPadding(new Insets(6));
        canvasHost.setMinSize(440, 240);

        canvasRenderer.canvas().widthProperty().bind(
                javafx.beans.binding.Bindings.max(400.0, canvasHost.widthProperty().subtract(14.0)));
        canvasRenderer.canvas().heightProperty().bind(
                javafx.beans.binding.Bindings.max(220.0, canvasHost.heightProperty().subtract(14.0)));

        canvasRenderer.canvas().widthProperty().addListener(obs -> renderActiveState());
        canvasRenderer.canvas().heightProperty().addListener(obs -> renderActiveState());

        center.setCenter(canvasHost);

        // Scrollable Sidebar with Presets, Parameter Controls, Velocity Triangle, and Inspector
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
        // 1. Scenario Preset Picker
        Label presetTitle = new Label("JEE SCENARIO PRESETS");
        presetTitle.getStyleClass().add("inspector-heading");
        presetTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        presetTitle.setStyle("-fx-text-fill: #334155;");

        presetCombo.setId("riverBoatPresetCombo");
        presetCombo.getItems().addAll(
                "Shortest Time (θ = 0°, t_min = w/vb)",
                "Zero Drift / Shortest Path (vb > u, sin θ = u/vb)",
                "Minimum Drift Condition (vb < u, sin θ = vb/u)",
                "Equal Speed Boundary (vb = u, Asymptotic Limit)"
        );
        presetCombo.setValue("Zero Drift / Shortest Path (vb > u, sin θ = u/vb)");
        presetCombo.setMaxWidth(Double.MAX_VALUE);
        presetCombo.setStyle("-fx-font-size: 11.5px; -fx-padding: 3 6 3 6;");

        presetCombo.valueProperty().addListener((obs, oldVal, selected) -> {
            if (selected == null) return;
            if (selected.contains("Shortest Time")) {
                controller.setModel(RiverBoatSimulationModel.presetShortestTime(100.0, 3.0, 5.0));
            } else if (selected.contains("Zero Drift")) {
                controller.setModel(RiverBoatSimulationModel.presetShortestPathOrMinDrift(100.0, 3.0, 5.0));
            } else if (selected.contains("Minimum Drift")) {
                controller.setModel(RiverBoatSimulationModel.presetShortestPathOrMinDrift(100.0, 5.0, 3.0));
            } else if (selected.contains("Equal Speed")) {
                controller.setModel(RiverBoatSimulationModel.presetEqualSpeedBoundary(100.0, 4.0));
            }
            syncInputsToModel(controller.model());
            updateAnalyticalDisplay();
        });

        VBox presetBox = new VBox(4, presetTitle, presetCombo);

        // 2. Interactive Parameter Inputs
        VBox controlsCard = buildParametersCard();

        // 3. Structured Analytical Inspector Card
        VBox inspectorBox = buildAnalyticalCard();

        return new VBox(8, presetBox, controlsCard, velocityPane, inspectorBox);
    }

    private VBox buildParametersCard() {
        Label title = new Label("PHYSICAL SCENARIO PARAMETERS");
        title.getStyleClass().add("inspector-heading");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        title.setStyle("-fx-text-fill: #334155;");

        VBox box = new VBox(6, title, widthRow, riverSpeedRow, boatSpeedRow, headingRow);
        box.setStyle("-fx-background-color: #fcfbf7; -fx-border-color: #cbc4b4; -fx-border-radius: 6px; -fx-padding: 8;");
        return box;
    }

    private VBox buildAnalyticalCard() {
        Label inspectorTitle = new Label("ANALYTICAL KINEMATICS AUTHORITY");
        inspectorTitle.getStyleClass().add("inspector-heading");
        inspectorTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        inspectorTitle.setStyle("-fx-text-fill: #334155;");

        analyticalStatusBadge.setFont(Font.font("Consolas", FontWeight.BOLD, 11.5));
        analyticalStatusBadge.setStyle("-fx-padding: 4 8 4 8; -fx-background-radius: 4px;");

        GridPane metricsGrid = new GridPane();
        metricsGrid.setHgap(8);
        metricsGrid.setVgap(3);
        metricsGrid.setPadding(new Insets(2, 0, 2, 0));

        addMetricRow(metricsGrid, 0, "Crossing Time:", valCrossingTime);
        addMetricRow(metricsGrid, 1, "Downstream Drift:", valDownstreamDrift);
        addMetricRow(metricsGrid, 2, "Ground Speed:", valGroundSpeed);
        addMetricRow(metricsGrid, 3, "Across-Bank (vy):", valAcrossVelocity);
        addMetricRow(metricsGrid, 4, "Ground Course (α):", valGroundCourse);

        VBox box = new VBox(4, inspectorTitle, analyticalStatusBadge, metricsGrid);
        box.setStyle("-fx-background-color: #fcfbf7; -fx-border-color: #cbc4b4; -fx-border-radius: 6px; -fx-padding: 8;");
        return box;
    }

    private void addMetricRow(GridPane grid, int row, String labelText, Label valLabel) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("metric-name");
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
        lbl.setStyle("-fx-text-fill: #1e293b; -fx-font-weight: bold;");
        lbl.setMinWidth(110);

        valLabel.getStyleClass().add("metric-value");
        valLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
        valLabel.setStyle("-fx-text-fill: #0f172a;");

        grid.add(lbl, 0, row);
        grid.add(valLabel, 1, row);
    }

    private HBox buildTransportBar() {
        playPauseBtn.setId("playPauseButton");
        playPauseBtn.setStyle("-fx-background-color: #2453d4; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 8 18 8 18;");
        playPauseBtn.setOnAction(e -> controller.togglePlayPause());

        stepBackBtn.setId("stepBackButton");
        stepBackBtn.setStyle("-fx-padding: 8 14 8 14; -fx-font-size: 12px;");
        stepBackBtn.setOnAction(e -> controller.stepBackward(0.5));

        stepForwardBtn.setId("stepForwardButton");
        stepForwardBtn.setStyle("-fx-padding: 8 14 8 14; -fx-font-size: 12px;");
        stepForwardBtn.setOnAction(e -> controller.stepForward(0.5));

        resetBtn.setId("resetButton");
        resetBtn.setStyle("-fx-padding: 8 14 8 14; -fx-font-size: 12px;");
        resetBtn.setOnAction(e -> controller.reset());

        timeSlider.setId("timelineSlider");
        timeSlider.setMin(0.0);
        timeSlider.setMax(controller.maxTime());
        timeSlider.setValue(0.0);
        HBox.setHgrow(timeSlider, Priority.ALWAYS);

        timeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (timeSlider.isValueChanging()) {
                controller.seek(newVal.doubleValue());
            }
        });
        timeSlider.setOnMouseReleased(e -> controller.seek(timeSlider.getValue()));

        timeReadout.setId("timeReadout");
        timeReadout.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
        timeReadout.setTextFill(PhysoraTheme.INK);
        timeReadout.setMinWidth(160);
        timeReadout.setAlignment(Pos.CENTER_RIGHT);

        HBox transportBar = new HBox(10, playPauseBtn, stepBackBtn, stepForwardBtn, resetBtn, timeSlider, timeReadout);
        transportBar.setAlignment(Pos.CENTER_LEFT);
        transportBar.setPadding(new Insets(8, 0, 0, 0));
        return transportBar;
    }

    private void initListeners() {
        // Timeline updates
        controller.simulationTimeProperty().addListener((obs, oldVal, newVal) -> {
            double t = newVal.doubleValue();
            if (!timeSlider.isValueChanging()) {
                timeSlider.setValue(t);
            }
            timeReadout.setText(String.format(Locale.US, "t = %.2f s / %.2f s", t, controller.maxTime()));
            renderActiveState();
        });

        controller.isPlayingProperty().addListener((obs, wasPlaying, nowPlaying) -> {
            playPauseBtn.setText(nowPlaying ? "⏸ PAUSE" : "▶ PLAY");
            if (nowPlaying) {
                playPauseBtn.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 8 18 8 18;");
            } else {
                playPauseBtn.setStyle("-fx-background-color: #2453d4; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 8 18 8 18;");
            }
        });

        // Model updates
        controller.modelProperty().addListener((obs, oldModel, newModel) -> {
            timeSlider.setMax(controller.maxTime());
            timeSlider.setValue(0.0);
            timeReadout.setText(String.format(Locale.US, "t = 0.00 s / %.2f s", controller.maxTime()));
            syncInputsToModel(newModel);
            updateAnalyticalDisplay();
            renderActiveState();
        });
    }

    private void onParameterInputChanged() {
        if (updatingInputsFromModel) return;

        double w = widthRow.getValue();
        double u = riverSpeedRow.getValue();
        double vb = boatSpeedRow.getValue();
        double deg = headingRow.getValue();

        RiverBoatSimulationModel newModel = RiverBoatSimulationModel.ofDegrees(w, u, vb, deg);
        controller.setModel(newModel);
    }

    private void syncInputsToModel(RiverBoatSimulationModel model) {
        if (model == null) return;
        updatingInputsFromModel = true;
        try {
            widthRow.setValue(model.riverWidth());
            riverSpeedRow.setValue(model.riverSpeed());
            boatSpeedRow.setValue(model.boatSpeed());
            headingRow.setValue(model.headingAngleDeg());
        } finally {
            updatingInputsFromModel = false;
        }
    }

    private void updateAnalyticalDisplay() {
        RiverBoatSimulationModel m = controller.model();
        if (m == null) return;

        RiverBoatResult res = m.analyticalResult();
        RiverBoatCrossingType type = res.crossingType();

        // 1. Status badge
        switch (type) {
            case SHORTEST_TIME -> {
                analyticalStatusBadge.setText("SHORTEST TIME CROSSING");
                analyticalStatusBadge.setStyle("-fx-background-color: #d1fae5; -fx-text-fill: #065f46; -fx-padding: 4 8 4 8; -fx-background-radius: 4px;");
            }
            case ZERO_DRIFT_ACHIEVABLE -> {
                analyticalStatusBadge.setText("ZERO DRIFT ACHIEVABLE");
                analyticalStatusBadge.setStyle("-fx-background-color: #dbeafe; -fx-text-fill: #1e40af; -fx-padding: 4 8 4 8; -fx-background-radius: 4px;");
            }
            case ZERO_DRIFT_UNACHIEVABLE -> {
                analyticalStatusBadge.setText("MINIMUM DRIFT STRATEGY (vb < u)");
                analyticalStatusBadge.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-padding: 4 8 4 8; -fx-background-radius: 4px;");
            }
            case ZERO_DRIFT_ONLY_ASYMPTOTIC -> {
                analyticalStatusBadge.setText("ASYMPTOTIC ZERO DRIFT (vb = u)");
                analyticalStatusBadge.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e; -fx-padding: 4 8 4 8; -fx-background-radius: 4px;");
            }
            default -> {
                analyticalStatusBadge.setText("GENERAL VECTOR CROSSING");
                analyticalStatusBadge.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #334155; -fx-padding: 4 8 4 8; -fx-background-radius: 4px;");
            }
        }

        Vector2D vG = m.velocityBoatGround();

        // 2. Metrics
        if (res.crossingTime().isPresent()) {
            valCrossingTime.setText(String.format(Locale.US, "%.2f s (Governed solely by vy)", res.crossingTime().getAsDouble()));
        } else {
            valCrossingTime.setText("∞ (Cannot reach opposite bank)");
        }

        if (res.downstreamDrift().isPresent()) {
            double drift = res.downstreamDrift().getAsDouble();
            if (Math.abs(drift) < 1e-4) {
                valDownstreamDrift.setText("0.00 m (Zero Drift / Straight Across)");
            } else {
                valDownstreamDrift.setText(String.format(Locale.US, "%.2f m downstream", drift));
            }
        } else {
            valDownstreamDrift.setText("Undefined");
        }

        valGroundSpeed.setText(String.format(Locale.US, "%.2f m/s", vG.magnitude()));
        valAcrossVelocity.setText(String.format(Locale.US, "%.2f m/s (vy = vb cos θ)", vG.y()));

        double vxClean = Math.abs(vG.x()) < 1e-4 ? 0.0 : vG.x();
        double courseDeg = Math.toDegrees(Math.atan2(vxClean, Math.max(1e-6, vG.y())));
        if (Math.abs(courseDeg) < 0.05) {
            valGroundCourse.setText("0.0° (Perpendicular across bank)");
        } else {
            valGroundCourse.setText(String.format(Locale.US, "%+.1f° from normal", courseDeg));
        }

        // Update velocity triangle widget
        velocityPane.update(m);
    }

    private void renderActiveState() {
        canvasRenderer.render(controller.model(), controller.simulationTime());
    }

    public ComboBox<String> presetCombo() {
        return presetCombo;
    }

    public TextField headingInput() {
        return headingRow.valueField();
    }

    public TextField riverWidthInput() {
        return widthRow.valueField();
    }

    public TextField riverSpeedInput() {
        return riverSpeedRow.valueField();
    }

    public TextField boatSpeedInput() {
        return boatSpeedRow.valueField();
    }

    public NumericParameterRow headingRow() {
        return headingRow;
    }

    public NumericParameterRow widthRow() {
        return widthRow;
    }

    public NumericParameterRow riverSpeedRow() {
        return riverSpeedRow;
    }

    public NumericParameterRow boatSpeedRow() {
        return boatSpeedRow;
    }
}
