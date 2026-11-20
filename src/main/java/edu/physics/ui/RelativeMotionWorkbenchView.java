package edu.physics.ui;

import edu.physics.relative.ReferenceFrame;
import edu.physics.relative.TwoProjectilePursuitModel;
import edu.physics.simulation.Vector2D;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
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
    private final Label valRelativeAccel = new Label();
    private final Label valCollinearity = new Label();
    private final Label framePrincipleCard = new Label();

    private final Label valueGuideTitle = new Label("💡 WHAT DO THESE VALUES SHOW? (STEP-BY-STEP BREAKDOWN)");
    private final Label valueGuideBody = new Label();
    private final VBox valueGuideCard = new VBox(4, valueGuideTitle, valueGuideBody);

    private final Button returnButton = new Button("⚗ RETURN TO KINEMATICS");
    private Runnable onReturnToKinematics;
    private VBox sidebar;

    public RelativeMotionWorkbenchView(RelativeMotionSimulationController controller) {
        this.controller = controller;

        setId("relativeMotionWorkbenchView");
        setStyle("-fx-background-color: #f5f1e5;");
        setPadding(new Insets(10, 16, 10, 16));

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

    public LiveVelocityTrianglePane velocityTrianglePane() {
        return velocityTrianglePane;
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

        Label title = new Label("TWO-PROJECTILE PURSUIT ACROSS REFERENCE FRAMES");
        title.setFont(Font.font("Georgia", FontWeight.BOLD, 20));
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
        controlRow.setPadding(new Insets(6, 0, 6, 0));

        VBox topBar = new VBox(2, eyebrow, title, controlRow);
        return topBar;
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

        // Scrollable Sidebar with Presets, Dynamic Vector Triangle, and Structured Inspector
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
        Label presetTitle = new Label("SCENARIO PRESETS (JEE BENCHMARKS)");
        presetTitle.getStyleClass().add("inspector-heading");
        presetTitle.setTextFill(Color.web("#334155"));

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
        inspectorTitle.getStyleClass().add("inspector-heading");
        inspectorTitle.setTextFill(Color.web("#334155"));

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
        addMetricRow(metricsGrid, 3, "Relative Accel:", valRelativeAccel);
        addMetricRow(metricsGrid, 4, "Collision Rule:", valCollinearity);

        framePrincipleCard.setFont(Font.font("Segoe UI", 10.5));
        framePrincipleCard.setStyle("-fx-background-color: #eff6ff; -fx-text-fill: #1e40af; -fx-border-color: #bfdbfe; " +
                "-fx-border-radius: 4px; -fx-padding: 6 8 6 8;");
        framePrincipleCard.setWrapText(true);

        valueGuideTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        valueGuideTitle.setStyle("-fx-text-fill: #1e293b;");
        valueGuideBody.setFont(Font.font("Segoe UI", 10));
        valueGuideBody.setStyle("-fx-text-fill: #1e293b; -fx-line-spacing: 2.2px;");
        valueGuideBody.setWrapText(true);
        valueGuideCard.setStyle("-fx-background-color: #ffffff; -fx-padding: 10 12 10 12; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-background-radius: 6px;");

        VBox inspectorBox = new VBox(8, inspectorTitle, analyticalStatusBadge, metricsGrid, framePrincipleCard, valueGuideCard);
        inspectorBox.setStyle("-fx-background-color: #fcfbf7; -fx-border-color: #cbc4b4; -fx-border-radius: 6px; -fx-padding: 10;");

        VBox sidebar = new VBox(10, presetBox, velocityTrianglePane, inspectorBox);
        return sidebar;
    }

    private void addMetricRow(GridPane grid, int row, String labelText, Label valLabel) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("metric-name");
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        lbl.setStyle("-fx-text-fill: #1e293b; -fx-font-weight: bold;");
        lbl.setMinWidth(90);

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
        transport.setPadding(new Insets(8, 0, 0, 0));
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

        // Contextual Frame Principle: Explicit real vs fictitious acceleration & gravity balance
        String framePrinciple = switch (activeFrame) {
            case GROUND ->
                    "💡 INERTIAL LABORATORY FRAME S\n" +
                    "• Reference: Fixed ground coordinates (0, 0)\n" +
                    "• Real forces: Both projectiles accelerate under uniform downward gravity (g = 9.8 m/s²)\n" +
                    "• Trajectories: Independent downward-curving parabolas\n" +
                    "• Galilean vector sum: v_B(t) = v_A(t) + v_{B/A} holds at every instant";
            case OBSERVER_A ->
                    "💡 NON-INERTIAL (ACCELERATING) FRAME S_A\n" +
                    "• Reference: Origin locked to Projectile A (accelerates downward at a_frame = -g j)\n" +
                    "• Fictitious acceleration: a_fict = -a_frame = +g j balances downward gravity (-g j)\n" +
                    "• Net acceleration: a_eff = a_real + a_fict = 0 ⟹ Projectile B moves at constant velocity along a straight line\n" +
                    "• Invariance: v_{B/A} = v_B(t) - v_A(t) is strictly constant in time";
            case OBSERVER_B ->
                    "💡 NON-INERTIAL (ACCELERATING) FRAME S_B\n" +
                    "• Reference: Origin locked to Projectile B (accelerates downward at a_frame = -g j)\n" +
                    "• Fictitious acceleration: a_fict = -a_frame = +g j balances downward gravity (-g j)\n" +
                    "• Net acceleration: a_eff = a_real + a_fict = 0 ⟹ Projectile A moves at constant velocity along a straight line\n" +
                    "• Invariance: v_{A/B} = v_A(t) - v_B(t) = -v_{B/A} is strictly constant in time";
        };
        framePrincipleCard.setText(framePrinciple);

        valRelativeAccel.setText("a_rel = a_B - a_A = 0.00 m/s² (Identical gravity g ↓)");

        if (m.hasCollision() && m.collisionTime().isPresent()) {
            double tCol = m.collisionTime().getAsDouble();
            Vector2D colPos = m.collisionPositionGround();

            analyticalStatusBadge.setText("★ MID-AIR COLLISION DETECTED");
            analyticalStatusBadge.setStyle("-fx-background-color: #fef08a; -fx-text-fill: #854d0e; -fx-padding: 4 8 4 8; -fx-border-color: #facc15; -fx-border-radius: 4px;");

            valEventTime.setText(String.format(Locale.ROOT, "t* = %.3f s (Invariant across frames)", tCol));

            String locText = switch (activeFrame) {
                case GROUND -> String.format(Locale.ROOT, "(%.1f, %.1f) m [Ground S]", colPos.x(), colPos.y());
                case OBSERVER_A -> "(0.0, 0.0) m [Observer A Origin]";
                case OBSERVER_B -> "(0.0, 0.0) m [Observer B Origin]";
            };
            valEventLocation.setText(locText);
            valRelativeSpeed.setText(String.format(Locale.ROOT, "|v_B/A| = %.2f m/s (Constant: a_rel = 0)", m.relativeVelocityBA(0.0).magnitude()));
            valCollinearity.setText("Direct Intercept: v_B/A is anti-parallel to r_B/A(0); t* = d0/|v_rel| <= min(t_land,A, t_land,B)");
        } else {
            double dMin = m.analyticalResult().dMinInterval();
            double tMin = m.analyticalResult().tMinInterval();

            analyticalStatusBadge.setText("NEAR MISS / CONTINUOUS FLIGHT");
            analyticalStatusBadge.setStyle("-fx-background-color: #e2e8f0; -fx-text-fill: #334155; -fx-padding: 4 8 4 8; -fx-border-color: #cbd5e1; -fx-border-radius: 4px;");

            valEventTime.setText(String.format(Locale.ROOT, "t* = %.3f s (Closest Approach)", tMin));

            String locText = switch (activeFrame) {
                case GROUND -> String.format(Locale.ROOT, "Separation d_min = %.2f m [Ground S]", dMin);
                case OBSERVER_A -> String.format(Locale.ROOT, "Distance to Origin = %.2f m [S_A]", dMin);
                case OBSERVER_B -> String.format(Locale.ROOT, "Distance to Origin = %.2f m [S_B]", dMin);
            };
            valEventLocation.setText(locText);
            valRelativeSpeed.setText(String.format(Locale.ROOT, "|v_B/A| = %.2f m/s (Constant: a_rel = 0)", m.relativeVelocityBA(0.0).magnitude()));
            valCollinearity.setText(String.format(Locale.ROOT, "Non-Intercept: Closest separation d_min = %.2f m at t = %.3f s <= min(t_land,A, t_land,B)", dMin, tMin));
        }

        // Detailed Step-by-Step Value Decomposition Guide
        Vector2D pA0 = m.bodyA().initialPosition();
        Vector2D vA0 = m.bodyA().initialVelocity();
        Vector2D pB0 = m.bodyB().initialPosition();
        Vector2D vB0 = m.bodyB().initialVelocity();
        double d0 = pA0.distanceTo(pB0);
        Vector2D vBA = m.relativeVelocityBA(0.0);
        double vRelMag = vBA.magnitude();
        double tFlight = m.effectiveDuration();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
                "1. GIVEN LAUNCH CONDITIONS & SEPARATION:\n" +
                "   • Projectile A: Launched from (%.1f, %.1f) m @ (%+.1f i + %.1f j) m/s (|vA| = %.1f m/s).\n" +
                "   • Projectile B: Launched from (%.1f, %.1f) m @ (%+.1f i + %.1f j) m/s (|vB| = %.1f m/s).\n" +
                "   • Initial separation: d0 = |r_B(0) - r_A(0)| = %.1f m.\n\n",
                pA0.x(), pA0.y(), vA0.x(), vA0.y(), vA0.magnitude(),
                pB0.x(), pB0.y(), vB0.x(), vB0.y(), vB0.magnitude(),
                d0));

        sb.append(String.format(Locale.ROOT,
                "2. WHY RELATIVE MOTION IS A PURE STRAIGHT LINE:\n" +
                "   • Both projectiles accelerate under identical gravity: a_A = a_B = -g j.\n" +
                "   • Relative acceleration: a_{B/A} = a_B - a_A = (-g j) - (-g j) = 0.00 m/s².\n" +
                "   • Relative velocity is strictly CONSTANT in time: v_{B/A} = (%+.1f i + %+.1f j) m/s (|v_rel| = %.2f m/s).\n" +
                "   • In Observer B frame (or A), the projectile does not curve; it moves in a pure straight line!\n\n",
                vBA.x(), vBA.y(), vRelMag));

        if (m.hasCollision() && m.collisionTime().isPresent()) {
            double tCol = m.collisionTime().getAsDouble();
            sb.append(String.format(Locale.ROOT,
                    "3. COLLISION TIME DERIVATION:\n" +
                    "   • Relative velocity vector v_{B/A} points directly along the line of sight toward origin.\n" +
                    "   • Time to collision: t* = d0 / |v_rel| = %.1f m / %.2f m/s = %.3f s.\n\n",
                    d0, vRelMag, tCol));

            sb.append(String.format(Locale.ROOT,
                    "4. FLIGHT TIME GROUND VALIDITY:\n" +
                    "   • Ground flight duration: t_flight = %.2f s.\n" +
                    "   • Mid-air condition: t* <= min(t_land,A, t_land,B) ⟹ %.3f s <= %.2f s ⟹ VALID MID-AIR COLLISION!",
                    tFlight, tCol, tFlight));
        } else {
            double dMin = m.analyticalResult().dMinInterval();
            double tMin = m.analyticalResult().tMinInterval();
            sb.append(String.format(Locale.ROOT,
                    "3. CLOSEST APPROACH CALCULATION:\n" +
                    "   • Relative velocity is not directly anti-parallel to line of sight.\n" +
                    "   • Closest approach occurs at t* = %.3f s with minimum distance d_min = %.2f m.\n\n",
                    tMin, dMin));

            sb.append(String.format(Locale.ROOT,
                    "4. FLIGHT TIME GROUND VALIDITY:\n" +
                    "   • Ground flight duration: t_flight = %.2f s.\n" +
                    "   • Closest approach occurs before landing: t* = %.3f s <= %.2f s.",
                    tFlight, tMin, tFlight));
        }

        valueGuideBody.setText(MathTypography.formatText(sb.toString()));
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
