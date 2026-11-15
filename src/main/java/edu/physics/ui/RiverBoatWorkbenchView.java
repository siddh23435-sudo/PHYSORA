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
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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

    // Parameter Sliders & Readouts
    private final Slider widthSlider = new Slider(40.0, 400.0, 100.0);
    private final Label widthLabel = new Label("100 m");

    private final Slider riverSpeedSlider = new Slider(0.0, 10.0, 3.0);
    private final Label riverSpeedLabel = new Label("3.0 m/s");

    private final Slider boatSpeedSlider = new Slider(1.0, 12.0, 5.0);
    private final Label boatSpeedLabel = new Label("5.0 m/s");

    private final Slider headingSlider = new Slider(-80.0, 80.0, 36.87);
    private final Label headingLabel = new Label("36.9°");

    private final ComboBox<String> presetCombo = new ComboBox<>();

    // Analytical Inspector Structured Elements
    private final Label analyticalStatusBadge = new Label();
    private final Label valCrossingTime = new Label();
    private final Label valDownstreamDrift = new Label();
    private final Label valGroundSpeed = new Label();
    private final Label valAcrossVelocity = new Label();

    private final Button returnButton = new Button("⚗ RETURN TO KINEMATICS");
    private Runnable onReturnToKinematics;

    private boolean updatingSlidersFromModel = false;

    public RiverBoatWorkbenchView(RiverBoatSimulationController controller) {
        this.controller = controller;

        setId("riverBoatWorkbenchView");
        setStyle("-fx-background-color: #f5f1e5;");
        setPadding(new Insets(16, 24, 16, 24));

        setTop(buildTopBar());
        setCenter(buildCenterArea());
        setBottom(buildTransportBar());

        initListeners();
        syncSlidersToModel(controller.model());
        updateAnalyticalDisplay();
        renderActiveState();
    }

    public RiverBoatSimulationController controller() {
        return controller;
    }

    public Button returnButton() {
        return returnButton;
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
        title.setFont(Font.font("Georgia", FontWeight.BOLD, 22));
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

        // Canvas container
        VBox canvasContainer = new VBox(canvasRenderer.canvas());
        canvasContainer.setAlignment(Pos.CENTER);
        canvasContainer.setStyle("-fx-background-color: #ffffff; -fx-border-color: #cbc4b4; -fx-border-radius: 6px; -fx-background-radius: 6px;");
        canvasContainer.setPadding(new Insets(6));
        HBox.setHgrow(canvasContainer, Priority.ALWAYS);
        center.setCenter(canvasContainer);

        // Sidebar with Presets, Parameter Controls, Velocity Triangle, and Inspector
        VBox sidebar = buildSidebar();
        sidebar.setPadding(new Insets(0, 0, 0, 16));
        sidebar.setPrefWidth(440);
        sidebar.setMinWidth(420);
        center.setRight(sidebar);

        return center;
    }

    private VBox buildSidebar() {
        // 1. Scenario Preset Picker
        Label presetTitle = new Label("JEE SCENARIO PRESETS");
        presetTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        presetTitle.setTextFill(PhysoraTheme.MUTED);

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
            syncSlidersToModel(controller.model());
            updateAnalyticalDisplay();
        });

        VBox presetBox = new VBox(4, presetTitle, presetCombo);

        // 2. Interactive Parameter Sliders
        VBox controlsCard = buildSlidersCard();

        // 3. Structured Analytical Inspector Card
        VBox inspectorBox = buildAnalyticalCard();

        return new VBox(8, presetBox, controlsCard, velocityPane, inspectorBox);
    }

    private VBox buildSlidersCard() {
        Label title = new Label("PHYSICAL SCENARIO PARAMETERS");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        title.setTextFill(PhysoraTheme.MUTED);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(4);

        addSliderRow(grid, 0, "River Width (w):", widthSlider, widthLabel);
        addSliderRow(grid, 1, "River Current (u):", riverSpeedSlider, riverSpeedLabel);
        addSliderRow(grid, 2, "Boat Speed (vb):", boatSpeedSlider, boatSpeedLabel);
        addSliderRow(grid, 3, "Steering (θ):", headingSlider, headingLabel);

        VBox box = new VBox(4, title, grid);
        box.setStyle("-fx-background-color: #fcfbf7; -fx-border-color: #cbc4b4; -fx-border-radius: 6px; -fx-padding: 8;");
        return box;
    }

    private void addSliderRow(GridPane grid, int row, String name, Slider slider, Label valLabel) {
        Label nameLbl = new Label(name);
        nameLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
        nameLbl.setTextFill(Color.web("#334155"));
        nameLbl.setMinWidth(110);

        slider.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(slider, Priority.ALWAYS);

        valLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 10.5));
        valLabel.setTextFill(Color.web("#0f172a"));
        valLabel.setMinWidth(55);
        valLabel.setAlignment(Pos.CENTER_RIGHT);

        grid.add(nameLbl, 0, row);
        grid.add(slider, 1, row);
        grid.add(valLabel, 2, row);
    }

    private VBox buildAnalyticalCard() {
        Label inspectorTitle = new Label("ANALYTICAL KINEMATICS AUTHORITY");
        inspectorTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        inspectorTitle.setTextFill(PhysoraTheme.MUTED);

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

        VBox box = new VBox(4, inspectorTitle, analyticalStatusBadge, metricsGrid);
        box.setStyle("-fx-background-color: #fcfbf7; -fx-border-color: #cbc4b4; -fx-border-radius: 6px; -fx-padding: 8;");
        return box;
    }

    private void addMetricRow(GridPane grid, int row, String labelText, Label valLabel) {
        Label lbl = new Label(labelText);
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
        lbl.setTextFill(Color.web("#475569"));
        lbl.setMinWidth(110);

        valLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 10.5));
        valLabel.setTextFill(Color.web("#0f172a"));

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
        transportBar.setPadding(new Insets(12, 0, 0, 0));
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
            updateAnalyticalDisplay();
            renderActiveState();
        });

        // Slider interaction listeners (update model when user drags)
        widthSlider.valueProperty().addListener((obs, oldVal, newVal) -> onSliderValueChanged());
        riverSpeedSlider.valueProperty().addListener((obs, oldVal, newVal) -> onSliderValueChanged());
        boatSpeedSlider.valueProperty().addListener((obs, oldVal, newVal) -> onSliderValueChanged());
        headingSlider.valueProperty().addListener((obs, oldVal, newVal) -> onSliderValueChanged());
    }

    private void onSliderValueChanged() {
        if (updatingSlidersFromModel) return;

        double w = Math.round(widthSlider.getValue());
        double u = Math.round(riverSpeedSlider.getValue() * 10.0) / 10.0;
        double vb = Math.round(boatSpeedSlider.getValue() * 10.0) / 10.0;
        double deg = Math.round(headingSlider.getValue() * 10.0) / 10.0;

        widthLabel.setText(String.format(Locale.US, "%.0f m", w));
        riverSpeedLabel.setText(String.format(Locale.US, "%.1f m/s", u));
        boatSpeedLabel.setText(String.format(Locale.US, "%.1f m/s", vb));
        headingLabel.setText(String.format(Locale.US, "%.1f°", deg));

        RiverBoatSimulationModel newModel = RiverBoatSimulationModel.ofDegrees(w, u, vb, deg);
        controller.setModel(newModel);
    }

    private void syncSlidersToModel(RiverBoatSimulationModel model) {
        if (model == null) return;
        updatingSlidersFromModel = true;
        try {
            widthSlider.setValue(model.riverWidth());
            widthLabel.setText(String.format(Locale.US, "%.0f m", model.riverWidth()));

            riverSpeedSlider.setValue(model.riverSpeed());
            riverSpeedLabel.setText(String.format(Locale.US, "%.1f m/s", model.riverSpeed()));

            boatSpeedSlider.setValue(model.boatSpeed());
            boatSpeedLabel.setText(String.format(Locale.US, "%.1f m/s", model.boatSpeed()));

            headingSlider.setValue(model.headingAngleDeg());
            headingLabel.setText(String.format(Locale.US, "%.1f°", model.headingAngleDeg()));
        } finally {
            updatingSlidersFromModel = false;
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

        // 2. Metrics
        if (res.crossingTime().isPresent()) {
            valCrossingTime.setText(String.format(Locale.US, "%.2f s", res.crossingTime().getAsDouble()));
        } else {
            valCrossingTime.setText("∞ (never lands)");
        }

        if (res.downstreamDrift().isPresent()) {
            valDownstreamDrift.setText(String.format(Locale.US, "%.2f m", res.downstreamDrift().getAsDouble()));
        } else {
            valDownstreamDrift.setText("Undefined");
        }

        Vector2D vG = m.velocityBoatGround();
        valGroundSpeed.setText(String.format(Locale.US, "%.2f m/s", vG.magnitude()));
        valAcrossVelocity.setText(String.format(Locale.US, "%.2f m/s", vG.y()));

        // Update velocity triangle widget
        velocityPane.update(m);
    }

    private void renderActiveState() {
        canvasRenderer.render(controller.model(), controller.simulationTime());
    }

    public ComboBox<String> presetCombo() {
        return presetCombo;
    }

    public Slider headingSlider() {
        return headingSlider;
    }
}
