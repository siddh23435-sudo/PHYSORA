package edu.physics.ui;

import edu.physics.relative.ReferenceFrame;
import edu.physics.relative.TwoProjectilePursuitModel;
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
 * Flagship interactive workbench for Chapter 02: Relative Motion.
 *
 * <p>Enables students to explore two-projectile pursuit simultaneously across three
 * reference frames: Ground Frame S (inertial laboratory), Observer A Frame S_A (non-inertial
 * translating/accelerating), and Observer B Frame S_B. Features synchronized timeline playback,
 * dynamic velocity vector triangle, and analytical collision/closest approach event tracking.</p>
 */
public final class RelativeMotionWorkbenchView extends BorderPane {

    private final RelativeMotionSimulationController controller;
    private final TwoProjectileCanvasRenderer canvasRenderer = new TwoProjectileCanvasRenderer();
    private final LiveVelocityTrianglePane velocityTrianglePane = new LiveVelocityTrianglePane();

    private final Button frameGroundBtn = new Button("GROUND (S)");
    private final Button frameObserverABtn = new Button("OBSERVER A (S_A)");
    private final Button frameObserverBBtn = new Button("OBSERVER B (S_B)");

    private final Button playPauseBtn = new Button("▶ PLAY");
    private final Button stepBackBtn = new Button("⏮");
    private final Button stepForwardBtn = new Button("⏭");
    private final Button resetBtn = new Button("↺ RESET");
    private final Slider timeSlider = new Slider();
    private final Label timeReadout = new Label("t = 0.000 s / 5.000 s");

    // Analytical Inspector Structured Elements
    private final Label analyticalStatusBadge = new Label();
    private final Label valEventTime = new Label();
    private final Label valEventLocation = new Label();
    private final Label valRelativeSpeed = new Label();
    private final Label valCollinearity = new Label();
    private final Label framePrincipleCard = new Label();

    private final Button returnButton = new Button("⚗ RETURN TO KINEMATICS");
    private Runnable onReturnToKinematics;

    public RelativeMotionWorkbenchView(RelativeMotionSimulationController controller) {
        this.controller = controller;

        setId("relativeMotionWorkbenchView");
        setStyle("-fx-background-color: #f5f1e5;");
        setPadding(new Insets(16, 24, 16, 24));

        setTop(buildTopBar());
        setCenter(buildCenterArea());
        setBottom(buildTransportBar());

        initListeners();
        updateAnalyticalDisplay();
        renderActiveState();
    }

    public RelativeMotionSimulationController controller() {
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

        Label title = new Label("TWO-PROJECTILE PURSUIT ACROSS REFERENCE FRAMES");
        title.setFont(Font.font("Georgia", FontWeight.BOLD, 22));
        title.setTextFill(PhysoraTheme.INK);

        // Frame switcher buttons
        frameGroundBtn.setId("frameGroundBtn");
        frameObserverABtn.setId("frameObserverABtn");
        frameObserverBBtn.setId("frameObserverBBtn");

        styleFrameButton(frameGroundBtn, true);
        styleFrameButton(frameObserverABtn, false);
        styleFrameButton(frameObserverBBtn, false);

        frameGroundBtn.setOnAction(e -> controller.setReferenceFrame(ReferenceFrame.GROUND));
        frameObserverABtn.setOnAction(e -> controller.setReferenceFrame(ReferenceFrame.OBSERVER_A));
        frameObserverBBtn.setOnAction(e -> controller.setReferenceFrame(ReferenceFrame.OBSERVER_B));

        HBox frameButtons = new HBox(8, frameGroundBtn, frameObserverABtn, frameObserverBBtn);
        frameButtons.setAlignment(Pos.CENTER_LEFT);
        frameButtons.setStyle("-fx-background-color: #e2e8f0; -fx-padding: 4 6 4 6; -fx-background-radius: 6px;");

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

        HBox controlRow = new HBox(12, frameButtons, spacer, returnButton);
        controlRow.setAlignment(Pos.CENTER_LEFT);
        controlRow.setPadding(new Insets(10, 0, 10, 0));

        VBox topBar = new VBox(4, eyebrow, title, controlRow);
        return topBar;
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

        // Sidebar with Presets, Dynamic Vector Triangle, and Structured Inspector
        VBox sidebar = buildSidebar();
        sidebar.setPadding(new Insets(0, 0, 0, 16));
        sidebar.setPrefWidth(440);
        sidebar.setMinWidth(420);
        center.setRight(sidebar);

        return center;
    }

    private VBox buildSidebar() {
        // 1. Scenario Preset Picker
        Label presetTitle = new Label("SCENARIO PRESETS (JEE BENCHMARKS)");
        presetTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        presetTitle.setTextFill(PhysoraTheme.MUTED);

        ComboBox<String> presetCombo = new ComboBox<>();
        presetCombo.setId("scenarioPresetCombo");
        presetCombo.getItems().addAll(
                "Flagship Mid-Air Collision (JEE Benchmark)",
                "Near-Miss Pursuit (d_min = 2.00 m)",
                "Overtaking Pursuit (Trailing Chaser)",
                "Angled Intercept (Elevated Platform Launch)"
        );
        presetCombo.setValue("Flagship Mid-Air Collision (JEE Benchmark)");
        presetCombo.setMaxWidth(Double.MAX_VALUE);
        presetCombo.setStyle("-fx-font-size: 12px; -fx-padding: 2 6 2 6;");

        presetCombo.valueProperty().addListener((obs, oldVal, selected) -> {
            if (selected == null) return;
            if (selected.contains("Near-Miss")) {
                controller.setModel(TwoProjectilePursuitModel.nearMissPreset());
            } else if (selected.contains("Overtaking")) {
                controller.setModel(TwoProjectilePursuitModel.overtakePreset());
            } else if (selected.contains("Angled")) {
                controller.setModel(TwoProjectilePursuitModel.angledCollisionPreset());
            } else {
                controller.setModel(TwoProjectilePursuitModel.flagshipCollisionPreset());
            }
            updateAnalyticalDisplay();
        });

        VBox presetBox = new VBox(6, presetTitle, presetCombo);
        presetBox.setPadding(new Insets(0, 0, 4, 0));

        // 2. Structured Analytical Inspector
        Label inspectorTitle = new Label("ANALYTICAL KINEMATICS AUTHORITY");
        inspectorTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        inspectorTitle.setTextFill(PhysoraTheme.MUTED);

        analyticalStatusBadge.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
        analyticalStatusBadge.setStyle("-fx-padding: 5 10 5 10; -fx-background-radius: 4px;");

        // Structured Metrics Table (GridPane)
        GridPane metricsGrid = new GridPane();
        metricsGrid.setHgap(8);
        metricsGrid.setVgap(4);
        metricsGrid.setPadding(new Insets(4, 0, 4, 0));

        addMetricRow(metricsGrid, 0, "Event Time:", valEventTime);
        addMetricRow(metricsGrid, 1, "Location:", valEventLocation);
        addMetricRow(metricsGrid, 2, "Relative Speed:", valRelativeSpeed);
        addMetricRow(metricsGrid, 3, "Geometry:", valCollinearity);

        framePrincipleCard.setFont(Font.font("Segoe UI", 10.5));
        framePrincipleCard.setStyle("-fx-background-color: #eff6ff; -fx-text-fill: #1e40af; -fx-border-color: #bfdbfe; " +
                "-fx-border-radius: 4px; -fx-padding: 6 8 6 8;");
        framePrincipleCard.setWrapText(true);

        VBox inspectorBox = new VBox(6, inspectorTitle, analyticalStatusBadge, metricsGrid, framePrincipleCard);
        inspectorBox.setStyle("-fx-background-color: #fcfbf7; -fx-border-color: #cbc4b4; -fx-border-radius: 6px; -fx-padding: 10;");

        VBox sidebar = new VBox(10, presetBox, velocityTrianglePane, inspectorBox);
        return sidebar;
    }

    private void addMetricRow(GridPane grid, int row, String labelText, Label valLabel) {
        Label lbl = new Label(labelText);
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        lbl.setTextFill(Color.web("#475569"));
        lbl.setMinWidth(90);

        valLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
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
        stepBackBtn.setOnAction(e -> controller.stepBackward(0.1));

        stepForwardBtn.setId("stepForwardButton");
        stepForwardBtn.setStyle("-fx-padding: 8 14 8 14; -fx-font-size: 12px;");
        stepForwardBtn.setOnAction(e -> controller.stepForward(0.1));

        resetBtn.setId("resetButton");
        resetBtn.setStyle("-fx-padding: 8 14 8 14; -fx-font-size: 12px; -fx-font-weight: bold;");
        resetBtn.setOnAction(e -> controller.reset());

        timeSlider.setId("timeSlider");
        timeSlider.setMin(0.0);
        timeSlider.setMax(controller.maxTime());
        timeSlider.setValue(0.0);
        HBox.setHgrow(timeSlider, Priority.ALWAYS);

        timeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (timeSlider.isValueChanging() || !controller.isPlaying()) {
                controller.seek(newVal.doubleValue());
            }
        });

        timeReadout.setId("timeReadoutLabel");
        timeReadout.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
        timeReadout.setTextFill(PhysoraTheme.INK);
        timeReadout.setMinWidth(180);
        timeReadout.setAlignment(Pos.CENTER_RIGHT);

        HBox transport = new HBox(12,
                playPauseBtn,
                stepBackBtn,
                stepForwardBtn,
                resetBtn,
                timeSlider,
                timeReadout
        );
        transport.setAlignment(Pos.CENTER_LEFT);
        transport.setPadding(new Insets(12, 0, 0, 0));
        return transport;
    }

    private void initListeners() {
        controller.simulationTimeProperty().addListener((obs, oldTime, newTime) -> {
            double t = newTime.doubleValue();
            if (!timeSlider.isValueChanging()) {
                timeSlider.setValue(t);
            }
            timeReadout.setText(String.format(Locale.ROOT, "t = %.3f s / %.3f s", t, controller.maxTime()));
            renderActiveState();
        });

        controller.activeFrameProperty().addListener((obs, oldFrame, newFrame) -> {
            updateFrameButtonStyles(newFrame);
            updateAnalyticalDisplay();
            renderActiveState();
        });

        controller.isPlayingProperty().addListener((obs, wasPlaying, isNowPlaying) -> {
            playPauseBtn.setText(isNowPlaying ? "⏸ PAUSE" : "▶ PLAY");
            if (isNowPlaying) {
                playPauseBtn.setStyle("-fx-background-color: #e56843; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 8 18 8 18;");
            } else {
                playPauseBtn.setStyle("-fx-background-color: #2453d4; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 8 18 8 18;");
            }
        });

        controller.modelProperty().addListener((obs, oldModel, newModel) -> {
            timeSlider.setMax(controller.maxTime());
            timeSlider.setValue(0.0);
            updateAnalyticalDisplay();
            renderActiveState();
        });
    }

    private void renderActiveState() {
        TwoProjectilePursuitModel model = controller.model();
        double time = controller.simulationTime();
        ReferenceFrame frame = controller.activeFrame();

        canvasRenderer.render(model, time, frame);
        velocityTrianglePane.update(model, time);
    }

    private void updateAnalyticalDisplay() {
        TwoProjectilePursuitModel m = controller.model();
        if (m == null) return;

        ReferenceFrame activeFrame = controller.activeFrame();
        timeSlider.setMax(m.effectiveDuration());

        // Perspective principle summary
        framePrincipleCard.setText(String.format(Locale.ROOT,
                "💡 Active Frame: %s\n%s",
                activeFrame.frameClassification(),
                activeFrame.pedagogicalPrinciple()));

        if (m.hasCollision() && m.collisionTime().isPresent()) {
            double tCol = m.collisionTime().getAsDouble();
            Vector2D colPos = m.collisionPositionGround();

            analyticalStatusBadge.setText("★ MID-AIR COLLISION DETECTED");
            analyticalStatusBadge.setStyle("-fx-background-color: #fef08a; -fx-text-fill: #854d0e; -fx-padding: 4 8 4 8; -fx-border-color: #facc15; -fx-border-radius: 4px;");

            valEventTime.setText(String.format(Locale.ROOT, "t* = %.3f s (Agrees across frames)", tCol));

            String locText = switch (activeFrame) {
                case GROUND -> String.format(Locale.ROOT, "(%.1f, %.1f) m (Ground S)", colPos.x(), colPos.y());
                case OBSERVER_A -> "(0.0, 0.0) m (Observer A Origin)";
                case OBSERVER_B -> "(0.0, 0.0) m (Observer B Origin)";
            };
            valEventLocation.setText(locText);
            valRelativeSpeed.setText(String.format(Locale.ROOT, "|v_{B/A}| = %.2f m/s (CONSTANT)", m.relativeVelocityBA(0.0).magnitude()));
            valCollinearity.setText("Collinear: r_{B/A}(0) \u2225 v_{B/A} (Intercept)");
        } else {
            double dMin = m.analyticalResult().dMinInterval();
            double tMin = m.analyticalResult().tMinInterval();

            analyticalStatusBadge.setText("NEAR MISS / CONTINUOUS FLIGHT");
            analyticalStatusBadge.setStyle("-fx-background-color: #e2e8f0; -fx-text-fill: #334155; -fx-padding: 4 8 4 8; -fx-border-color: #cbd5e1; -fx-border-radius: 4px;");

            valEventTime.setText(String.format(Locale.ROOT, "t* = %.3f s (Closest Approach)", tMin));

            String locText = switch (activeFrame) {
                case GROUND -> String.format(Locale.ROOT, "Separation d_min = %.2f m", dMin);
                case OBSERVER_A -> String.format(Locale.ROOT, "Distance to Origin = %.2f m", dMin);
                case OBSERVER_B -> String.format(Locale.ROOT, "Distance to Origin = %.2f m", dMin);
            };
            valEventLocation.setText(locText);
            valRelativeSpeed.setText(String.format(Locale.ROOT, "|v_{B/A}| = %.2f m/s (CONSTANT)", m.relativeVelocityBA(0.0).magnitude()));
            valCollinearity.setText(String.format(Locale.ROOT, "Non-Collinear: Misses Origin by %.2f m", dMin));
        }
    }

    private void updateFrameButtonStyles(ReferenceFrame active) {
        styleFrameButton(frameGroundBtn, active == ReferenceFrame.GROUND);
        styleFrameButton(frameObserverABtn, active == ReferenceFrame.OBSERVER_A);
        styleFrameButton(frameObserverBBtn, active == ReferenceFrame.OBSERVER_B);
    }

    private void styleFrameButton(Button btn, boolean isSelected) {
        if (isSelected) {
            btn.setStyle("-fx-background-color: #142e4b; -fx-text-fill: #d9f26a; -fx-font-weight: bold; " +
                    "-fx-font-size: 12px; -fx-padding: 7 14 7 14; -fx-background-radius: 4px;");
        } else {
            btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #475569; -fx-font-weight: normal; " +
                    "-fx-font-size: 12px; -fx-padding: 7 14 7 14; -fx-background-radius: 4px;");
        }
    }
}
