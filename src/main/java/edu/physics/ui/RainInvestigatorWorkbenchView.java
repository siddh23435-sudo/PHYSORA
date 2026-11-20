package edu.physics.ui;

import edu.physics.relative.RainInvestigatorPreset;
import edu.physics.relative.RainInvestigatorResult;
import edu.physics.relative.RainInvestigatorSessionModel;
import edu.physics.relative.RainInvestigatorStage;
import edu.physics.relative.RainInvestigatorStatus;
import edu.physics.relative.RainVerificationSnapshot;
import edu.physics.relative.RainVerificationStatus;
import edu.physics.simulation.Vector2D;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
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
 * Interactive visual workbench for Phase 2B.2: Inverse Rain Investigator.
 *
 * <p>Enables students to reconstruct the true ground-frame rain velocity vector
 * using observations of umbrella tilt made by two observers walking at different speeds.
 * Structured as a guided 5-stage physics investigation:
 * <ol>
 *   <li><b>Evidence:</b> Enter two observers' walking speeds and apparent umbrella angles.</li>
 *   <li><b>Ambiguity:</b> Discover why 1 observation leaves infinitely many possible velocities.</li>
 *   <li><b>Geometry:</b> Intersect observation lines in velocity space (v_rx, v_ry).</li>
 *   <li><b>Reconstruction:</b> Step-by-step mathematical derivation and sensitivity diagnostics.</li>
 *   <li><b>Verification:</b> Round-trip forward simulation verification and residual checks.</li>
 * </ol>
 * </p>
 */
public final class RainInvestigatorWorkbenchView extends BorderPane {

    private final RainInvestigatorSessionModel model;
    private final RainInvestigatorCanvasRenderer canvasRenderer = new RainInvestigatorCanvasRenderer();

    // Top Header & Stage Navigation
    private final Button returnButton = new Button("⚗ RETURN TO CHAPTER 02");
    private final Button stageBtn1 = new Button("1. EVIDENCE");
    private final Button stageBtn2 = new Button("2. AMBIGUITY");
    private final Button stageBtn3 = new Button("3. GEOMETRY");
    private final Button stageBtn4 = new Button("4. RECONSTRUCTION");
    private final Button stageBtn5 = new Button("5. VERIFICATION");
    private final Button prevStageBtn = new Button("◀ PREVIOUS");
    private final Button nextStageBtn = new Button("NEXT ▶");

    private final ComboBox<String> presetCombo = new ComboBox<>();

    // Numeric Parameter Inputs
    private final NumericParameterRow v1Row;
    private final NumericParameterRow phi1Row;
    private final NumericParameterRow v2Row;
    private final NumericParameterRow phi2Row;

    // Stage 2 Candidate Explorer
    private final Slider candidateSlider = new Slider(2.0, 20.0, 8.0);
    private final Label candidateSpeedReadout = new Label("8.0 m/s");
    private final Label candidateResultLabel = new Label();

    // Global Result Badges
    private final Label evaluatorStatusBadge = new Label();
    private final Label conditionNumberBadge = new Label();
    private final Label reconstructedVelocityBadge = new Label();

    // Verification Readouts
    private final Label verificationStatusBadge = new Label();
    private final Label valResidual1 = new Label("0.0000°");
    private final Label valResidual2 = new Label("0.0000°");
    private final Label valMaxResidual = new Label("0.0000°");
    private final Label verificationSummaryLabel = new Label();

    // Derivation and Geometry labels
    private final VBox stageSpecificContent = new VBox(10);
    private final VBox obs2Container = new VBox(6);

    private VBox sidebar;
    private Runnable onReturnToKinematics;
    private boolean updatingInputsFromModel = false;

    public RainInvestigatorWorkbenchView() {
        this(new RainInvestigatorSessionModel());
    }

    public RainInvestigatorWorkbenchView(RainInvestigatorSessionModel model) {
        this.model = model != null ? model : new RainInvestigatorSessionModel();

        setId("rainInvestigatorWorkbenchView");
        setStyle("-fx-background-color: #f5f1e5;");
        setPadding(new Insets(10, 16, 10, 16));

        // Initialize NumericParameterRows
        this.v1Row = new NumericParameterRow(
                "Observer 1 Speed (v₁):", "m/s", "v1Input",
                this.model.vPerson1(), -25.0, 25.0, "%.1f",
                val -> onObservationInputChanged());

        this.phi1Row = new NumericParameterRow(
                "Observer 1 Angle (φ₁):", "°", "phi1Input",
                this.model.angleDeg1(), -89.9, 89.9, "%.1f",
                val -> onObservationInputChanged());

        this.v2Row = new NumericParameterRow(
                "Observer 2 Speed (v₂):", "m/s", "v2Input",
                this.model.vPerson2(), -25.0, 25.0, "%.1f",
                val -> onObservationInputChanged());

        this.phi2Row = new NumericParameterRow(
                "Observer 2 Angle (φ₂):", "°", "phi2Input",
                this.model.angleDeg2(), -89.9, 89.9, "%.1f",
                val -> onObservationInputChanged());

        setTop(buildTopBar());
        setCenter(buildCenterArea());

        initListeners();
        updateUIFromModel();
        renderActiveState();
    }

    public RainInvestigatorSessionModel model() {
        return model;
    }

    public RainInvestigatorCanvasRenderer canvasRenderer() {
        return canvasRenderer;
    }

    public Button returnButton() {
        return returnButton;
    }

    public void setOnReturnToKinematics(Runnable callback) {
        this.onReturnToKinematics = callback;
    }

    public void dispose() {
        // Clear listeners or background resources
    }

    @Override
    public Node lookup(String selector) {
        Node found = super.lookup(selector);
        if (found != null) {
            return found;
        }
        if (sidebar != null) {
            found = sidebar.lookup(selector);
            if (found != null) return found;
        }
        if (stageSpecificContent != null) {
            found = stageSpecificContent.lookup(selector);
            if (found != null) return found;
        }
        return null;
    }

    private VBox buildTopBar() {
        // 1. Eyebrow & Title
        Label eyebrow = new Label("PHYSORA CHAPTER 02 / RELATIVE MOTION");
        eyebrow.getStyleClass().add("eyebrow");
        eyebrow.setTextFill(PhysoraTheme.MUTED);

        Label title = new Label("INVERSE RAIN INVESTIGATOR — GROUND RAIN RECONSTRUCTION");
        title.setFont(Font.font("Georgia", FontWeight.BOLD, 19));
        title.setTextFill(PhysoraTheme.INK);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        returnButton.setId("returnToKinematicsButton");
        returnButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #2453d4; -fx-font-weight: bold; " +
                "-fx-border-color: #2453d4; -fx-border-radius: 4px; -fx-padding: 5 12 5 12; -fx-font-size: 11px;");
        returnButton.setOnAction(e -> {
            if (onReturnToKinematics != null) {
                onReturnToKinematics.run();
            }
        });

        HBox titleRow = new HBox(12, spacer, returnButton);
        titleRow.setAlignment(Pos.CENTER_RIGHT);

        // 2. Stage Navigation Bar
        stageBtn1.setId("stageBtn1");
        stageBtn2.setId("stageBtn2");
        stageBtn3.setId("stageBtn3");
        stageBtn4.setId("stageBtn4");
        stageBtn5.setId("stageBtn5");
        prevStageBtn.setId("prevStageBtn");
        nextStageBtn.setId("nextStageBtn");

        stageBtn1.setOnAction(e -> model.setStage(RainInvestigatorStage.STAGE_1_EVIDENCE));
        stageBtn2.setOnAction(e -> model.setStage(RainInvestigatorStage.STAGE_2_AMBIGUITY));
        stageBtn3.setOnAction(e -> model.setStage(RainInvestigatorStage.STAGE_3_GEOMETRY));
        stageBtn4.setOnAction(e -> model.setStage(RainInvestigatorStage.STAGE_4_RECONSTRUCTION));
        stageBtn5.setOnAction(e -> model.setStage(RainInvestigatorStage.STAGE_5_VERIFICATION));

        prevStageBtn.setOnAction(e -> {
            int current = model.stage().ordinal();
            if (current > 0) {
                model.setStage(RainInvestigatorStage.values()[current - 1]);
            }
        });

        nextStageBtn.setOnAction(e -> {
            int current = model.stage().ordinal();
            if (current < RainInvestigatorStage.values().length - 1) {
                model.setStage(RainInvestigatorStage.values()[current + 1]);
            }
        });

        HBox stageNav = new HBox(6,
                prevStageBtn,
                stageBtn1, stageBtn2, stageBtn3, stageBtn4, stageBtn5,
                nextStageBtn
        );
        stageNav.setAlignment(Pos.CENTER_LEFT);
        stageNav.setPadding(new Insets(6, 0, 4, 0));

        // 3. Preset Selector Bar
        Label presetLabel = new Label("CURATED JEE PRESET:");
        presetLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
        presetLabel.setStyle("-fx-text-fill: #334155;");

        presetCombo.setId("presetCombo");
        presetCombo.getItems().addAll(
                "Standard JEE Case (0 m/s @ 0°, 10 m/s @ 45°)",
                "Windy JEE Classic (0 m/s @ -30°, 10 m/s @ +30°)",
                "Near-Parallel — Ill-Conditioned (2 m/s @ 20°, 2.2 m/s @ 21°)",
                "Identical Speeds — Degenerate (5 m/s @ 15°, 5 m/s @ 30°)",
                "Inconsistent Direction — Upward Rain (0 m/s @ 45°, 5 m/s @ 15°)"
        );
        presetCombo.setValue("Standard JEE Case (0 m/s @ 0°, 10 m/s @ 45°)");
        presetCombo.setStyle("-fx-font-size: 11px; -fx-padding: 2 6 2 6;");
        presetCombo.setPrefWidth(380);

        presetCombo.valueProperty().addListener((obs, oldVal, selected) -> {
            if (selected == null || updatingInputsFromModel) return;
            if (selected.contains("Standard JEE")) {
                model.loadPreset(RainInvestigatorPreset.STANDARD_JEE);
            } else if (selected.contains("Windy JEE Classic")) {
                model.loadPreset(RainInvestigatorPreset.WINDY_JEE);
            } else if (selected.contains("Near-Parallel")) {
                model.loadPreset(RainInvestigatorPreset.NEAR_PARALLEL);
            } else if (selected.contains("Identical Speeds")) {
                model.loadPreset(RainInvestigatorPreset.IDENTICAL_SPEEDS);
            } else if (selected.contains("Inconsistent Direction")) {
                model.loadPreset(RainInvestigatorPreset.INCONSISTENT_DIRECTION);
            }
        });

        HBox presetRow = new HBox(10, presetLabel, presetCombo);
        presetRow.setAlignment(Pos.CENTER_LEFT);
        presetRow.setPadding(new Insets(2, 0, 6, 0));

        return new VBox(4, eyebrow, title, titleRow, stageNav, presetRow);
    }

    private BorderPane buildCenterArea() {
        BorderPane center = new BorderPane();

        // Canvas container
        StackPane canvasHost = new StackPane(canvasRenderer.canvas());
        canvasHost.setAlignment(Pos.CENTER);
        canvasHost.setStyle("-fx-background-color: #0f172a; -fx-border-color: #334155; -fx-border-radius: 6px; -fx-background-radius: 6px;");
        canvasHost.setPadding(new Insets(6));
        canvasHost.setMinSize(440, 260);

        canvasRenderer.canvas().widthProperty().bind(
                javafx.beans.binding.Bindings.max(400.0, canvasHost.widthProperty().subtract(14.0)));
        canvasRenderer.canvas().heightProperty().bind(
                javafx.beans.binding.Bindings.max(240.0, canvasHost.heightProperty().subtract(14.0)));

        canvasRenderer.canvas().widthProperty().addListener(obs -> renderActiveState());
        canvasRenderer.canvas().heightProperty().addListener(obs -> renderActiveState());

        center.setCenter(canvasHost);

        // Sidebar
        this.sidebar = buildSidebar();
        sidebar.setPadding(new Insets(0, 0, 0, 10));
        sidebar.setPrefWidth(380);
        sidebar.setMinWidth(350);

        ScrollPane sidebarScroll = new ScrollPane(sidebar);
        sidebarScroll.getStyleClass().add("sidebar-scroll");
        sidebarScroll.setFitToWidth(true);
        sidebarScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sidebarScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sidebarScroll.setStyle("-fx-background-color: #f5f1e5; -fx-background: #f5f1e5; -fx-border-color: transparent;");
        sidebarScroll.setPrefWidth(390);
        sidebarScroll.setMinWidth(360);
        sidebarScroll.setMaxWidth(415);
        center.setRight(sidebarScroll);

        return center;
    }

    private VBox buildSidebar() {
        // 1. Reconstructed Rain Summary Card
        VBox summaryCard = buildSummaryCard();

        // 2. Observations Input Card
        VBox obsCard = buildObservationsCard();

        // 3. Stage-Specific Content Card
        VBox stageCard = buildStageCard();

        return new VBox(10, summaryCard, obsCard, stageCard);
    }

    private VBox buildSummaryCard() {
        Label heading = new Label("RECONSTRUCTION STATUS");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        heading.setStyle("-fx-text-fill: #334155;");

        evaluatorStatusBadge.setId("evaluatorStatusBadge");
        evaluatorStatusBadge.setStyle("-fx-padding: 3 8 3 8; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-font-weight: bold; -fx-font-size: 10.5px;");

        conditionNumberBadge.setId("conditionNumberBadge");
        conditionNumberBadge.setFont(Font.font("Consolas", FontWeight.BOLD, 10.5));
        conditionNumberBadge.setStyle("-fx-text-fill: #475569;");

        HBox badgeRow = new HBox(8, evaluatorStatusBadge, conditionNumberBadge);
        badgeRow.setAlignment(Pos.CENTER_LEFT);

        reconstructedVelocityBadge.setId("reconstructedVelocityBadge");
        reconstructedVelocityBadge.setFont(Font.font("Consolas", FontWeight.BOLD, 13));
        reconstructedVelocityBadge.setStyle("-fx-text-fill: #065f46; -fx-padding: 4 8 4 8; -fx-background-color: #d1fae5; -fx-background-radius: 4px; -fx-border-color: #a7f3d0; -fx-border-radius: 4px;");

        VBox card = new VBox(6, heading, badgeRow, reconstructedVelocityBadge);
        card.setStyle("-fx-background-color: #ffffff; -fx-padding: 10; -fx-border-color: #cbc4b4; -fx-border-radius: 6px; -fx-background-radius: 6px;");
        return card;
    }

    private VBox buildObservationsCard() {
        Label heading = new Label("ENTERED OBSERVATIONS");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        heading.setStyle("-fx-text-fill: #334155;");

        // Observer 1
        Label obs1Label = new Label("OBSERVER 1 (BLUE)");
        obs1Label.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        obs1Label.setStyle("-fx-text-fill: #2563eb;");
        VBox obs1Box = new VBox(4, obs1Label, v1Row, phi1Row);
        obs1Box.setStyle("-fx-background-color: #eff6ff; -fx-padding: 8; -fx-border-color: #bfdbfe; -fx-border-radius: 4px; -fx-background-radius: 4px;");

        // Observer 2 (Hidden during Stage 2 Ambiguity)
        obs2Container.setId("obs2Container");
        Label obs2Label = new Label("OBSERVER 2 (ORANGE)");
        obs2Label.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        obs2Label.setStyle("-fx-text-fill: #d97706;");
        obs2Container.getChildren().setAll(obs2Label, v2Row, phi2Row);
        obs2Container.setStyle("-fx-background-color: #fffbeb; -fx-padding: 8; -fx-border-color: #fde68a; -fx-border-radius: 4px; -fx-background-radius: 4px;");

        VBox card = new VBox(6, heading, obs1Box, obs2Container);
        card.setStyle("-fx-background-color: #ffffff; -fx-padding: 10; -fx-border-color: #cbc4b4; -fx-border-radius: 6px; -fx-background-radius: 6px;");
        return card;
    }

    private VBox buildStageCard() {
        VBox card = new VBox(8, stageSpecificContent);
        card.setStyle("-fx-background-color: #ffffff; -fx-padding: 12; -fx-border-color: #cbc4b4; -fx-border-radius: 6px; -fx-background-radius: 6px;");
        return card;
    }

    private void initListeners() {
        model.addChangeListener(m -> updateUIFromModel());

        // Stage 2 Candidate Slider Listener
        candidateSlider.setId("candidateVDownSlider");
        candidateSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double vd = newVal.doubleValue();
            canvasRenderer.setCandidateVDown(vd);
            candidateSpeedReadout.setText(String.format(Locale.ROOT, "%.1f m/s", vd));
            updateStage2CandidateDisplay(vd);
            renderActiveState();
        });
    }

    private void onObservationInputChanged() {
        if (updatingInputsFromModel) return;

        double v1 = v1Row.getValue();
        double phi1Deg = phi1Row.getValue();
        double v2 = v2Row.getValue();
        double phi2Deg = phi2Row.getValue();

        model.setObservations(v1, Math.toRadians(phi1Deg), v2, Math.toRadians(phi2Deg));
    }

    private void updateUIFromModel() {
        updatingInputsFromModel = true;
        try {
            // Update input values
            v1Row.setValue(model.vPerson1());
            phi1Row.setValue(model.angleDeg1());
            v2Row.setValue(model.vPerson2());
            phi2Row.setValue(model.angleDeg2());

            // Preset ComboBox sync
            if (model.activePreset().isPresent()) {
                RainInvestigatorPreset p = model.activePreset().get();
                for (String item : presetCombo.getItems()) {
                    if (item.contains(p.title()) || (p == RainInvestigatorPreset.STANDARD_JEE && item.contains("Standard JEE"))
                            || (p == RainInvestigatorPreset.WINDY_JEE && item.contains("Windy JEE"))
                            || (p == RainInvestigatorPreset.NEAR_PARALLEL && item.contains("Near-Parallel"))
                            || (p == RainInvestigatorPreset.IDENTICAL_SPEEDS && item.contains("Identical Speeds"))
                            || (p == RainInvestigatorPreset.INCONSISTENT_DIRECTION && item.contains("Inconsistent Direction"))) {
                        presetCombo.setValue(item);
                        break;
                    }
                }
            } else {
                presetCombo.setValue(null);
            }

            // Sync Stage 2 observation 2 container visibility
            boolean obs2Vis = model.isObservation2Visible();
            obs2Container.setVisible(obs2Vis);
            obs2Container.setManaged(obs2Vis);
            v2Row.setVisible(obs2Vis);
            v2Row.setManaged(obs2Vis);
            phi2Row.setVisible(obs2Vis);
            phi2Row.setManaged(obs2Vis);

            // Update Stage Buttons Style
            updateStageButtonStyles();

            // Update Global Summary Card
            updateSummaryCard();

            // Update Stage-Specific Content
            updateStageSpecificContent();

        } finally {
            updatingInputsFromModel = false;
        }

        renderActiveState();
    }

    private void updateStageButtonStyles() {
        RainInvestigatorStage current = model.stage();

        styleNavButton(stageBtn1, current == RainInvestigatorStage.STAGE_1_EVIDENCE);
        styleNavButton(stageBtn2, current == RainInvestigatorStage.STAGE_2_AMBIGUITY);
        styleNavButton(stageBtn3, current == RainInvestigatorStage.STAGE_3_GEOMETRY);
        styleNavButton(stageBtn4, current == RainInvestigatorStage.STAGE_4_RECONSTRUCTION);
        styleNavButton(stageBtn5, current == RainInvestigatorStage.STAGE_5_VERIFICATION);

        prevStageBtn.setDisable(current == RainInvestigatorStage.STAGE_1_EVIDENCE);
        nextStageBtn.setDisable(current == RainInvestigatorStage.STAGE_5_VERIFICATION);

        prevStageBtn.setStyle("-fx-background-color: #e9e2d0; -fx-text-fill: #142e4b; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 5 10 5 10; -fx-border-color: #cbc4b4; -fx-border-radius: 4px;");
        nextStageBtn.setStyle("-fx-background-color: #2453d4; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 5 12 5 12; -fx-border-color: #1e40af; -fx-border-radius: 4px;");
    }

    private void styleNavButton(Button btn, boolean active) {
        if (active) {
            btn.setStyle("-fx-background-color: #2453d4; -fx-text-fill: white; -fx-font-weight: bold; " +
                    "-fx-font-size: 11px; -fx-padding: 5 11 5 11; -fx-border-color: #1e40af; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        } else {
            btn.setStyle("-fx-background-color: #e9e2d0; -fx-text-fill: #142e4b; -fx-font-weight: bold; " +
                    "-fx-font-size: 11px; -fx-padding: 5 11 5 11; -fx-border-color: #cbc4b4; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        }
    }

    private void updateSummaryCard() {
        RainInvestigatorResult res = model.evaluatorResult();
        if (res == null) return;

        RainInvestigatorStatus status = res.status();
        evaluatorStatusBadge.setText(status.name());

        if (status == RainInvestigatorStatus.VALID) {
            evaluatorStatusBadge.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #15803d; -fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 4px;");
        } else if (status == RainInvestigatorStatus.ILL_CONDITIONED) {
            evaluatorStatusBadge.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #b45309; -fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 4px;");
        } else {
            evaluatorStatusBadge.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #b91c1c; -fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 4px;");
        }

        if (res.isSolvable() && !Double.isNaN(res.downwardConditionNumber())) {
            String val = String.format(Locale.ROOT, "%.1f", res.downwardConditionNumber());
            conditionNumberBadge.setText("κ_down = " + val);
            conditionNumberBadge.setGraphic(MathView.flow("κ_{down} = " + val, 10.5, Color.web("#475569")));
        } else {
            conditionNumberBadge.setText("κ_down = N/A");
            conditionNumberBadge.setGraphic(MathView.flow("κ_{down} = N/A", 10.5, Color.web("#475569")));
        }

        if (res.isSolvable() && res.reconstructedRainVelocity() != null) {
            Vector2D rec = res.reconstructedRainVelocity();
            reconstructedVelocityBadge.setText(String.format(Locale.ROOT,
                    "v_r = (%.2f, %.2f) m/s  [|v_r| = %.2f m/s]",
                    rec.x(), rec.y(), rec.magnitude()));
            reconstructedVelocityBadge.setStyle("-fx-text-fill: #065f46; -fx-padding: 4 8 4 8; -fx-background-color: #d1fae5; -fx-background-radius: 4px; -fx-border-color: #a7f3d0; -fx-border-radius: 4px; -fx-font-weight: bold;");
        } else {
            reconstructedVelocityBadge.setText("Unsolvable: " + status.description());
            reconstructedVelocityBadge.setStyle("-fx-text-fill: #991b1b; -fx-padding: 4 8 4 8; -fx-background-color: #fee2e2; -fx-background-radius: 4px; -fx-border-color: #fca5a5; -fx-border-radius: 4px; -fx-font-weight: bold;");
        }
    }

    private void updateStageSpecificContent() {
        stageSpecificContent.getChildren().clear();

        RainInvestigatorStage stage = model.stage();
        switch (stage) {
            case STAGE_1_EVIDENCE -> buildStage1EvidenceView();
            case STAGE_2_AMBIGUITY -> buildStage2AmbiguityView();
            case STAGE_3_GEOMETRY -> buildStage3GeometryView();
            case STAGE_4_RECONSTRUCTION -> buildStage4ReconstructionView();
            case STAGE_5_VERIFICATION -> buildStage5VerificationView();
        }
    }

    // --- STAGE 1: EVIDENCE ---
    private void buildStage1EvidenceView() {
        Label title = new Label("STAGE 1: EMPIRICAL OBSERVATION EVIDENCE");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        title.setStyle("-fx-text-fill: #1e293b;");

        Label desc = new Label(
                "Two observers walk horizontally at known ground speeds v₁ and v₂, and record the angle φ at which falling rain strikes their umbrellas.\n\n" +
                "• Horizontal velocity vᵢ is signed (+ for rightward/eastward, - for leftward).\n" +
                "• Apparent tilt angle φᵢ is measured from upward vertical: + tilts forward into motion, - tilts backward.\n" +
                "• Governing relation: tan(φᵢ) = (vᵢ - vᵣₓ) / v_d\n\n" +
                "Review the entered values above or select a preset, then proceed to Stage 2 to inspect single-observation ambiguity."
        );
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #475569; -fx-line-spacing: 2px;");

        Button nextBtn = new Button("PROCEED TO STAGE 2: AMBIGUITY ▶");
        nextBtn.setStyle("-fx-background-color: #2453d4; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 12 6 12;");
        nextBtn.setOnAction(e -> model.setStage(RainInvestigatorStage.STAGE_2_AMBIGUITY));

        stageSpecificContent.getChildren().addAll(title, desc, nextBtn);
    }

    // --- STAGE 2: AMBIGUITY ---
    private void buildStage2AmbiguityView() {
        Label title = new Label("STAGE 2: SINGLE-OBSERVATION AMBIGUITY");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        title.setStyle("-fx-text-fill: #1e293b;");

        Label desc = new Label(
                "Observation 2 has been hidden. Why can't Observer 1 alone determine the rain velocity?\n\n" +
                "Observer 1's measurement gives only ONE equation in TWO unknown variables (vᵣₓ and v_d):\n" +
                "   vᵣₓ + v_d · tan(φ₁) = v₁\n\n" +
                "For every assumed downward speed v_d, there is a matching horizontal wind vᵣₓ that produces the exact same tilt angle φ₁!"
        );
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #475569; -fx-line-spacing: 2px;");

        Label sliderHeading = new Label("EXPLORE CANDIDATE DOWNWARD SPEEDS (v_d):");
        sliderHeading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
        sliderHeading.setStyle("-fx-text-fill: #0369a1;");

        HBox sliderRow = new HBox(8, candidateSlider, candidateSpeedReadout);
        sliderRow.setAlignment(Pos.CENTER_LEFT);

        candidateResultLabel.setId("candidateResultLabel");
        candidateResultLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
        candidateResultLabel.setStyle("-fx-background-color: #e0f2fe; -fx-text-fill: #0369a1; -fx-padding: 6; -fx-border-radius: 4px; -fx-background-radius: 4px;");

        updateStage2CandidateDisplay(candidateSlider.getValue());

        Button nextBtn = new Button("REVEAL OBSERVER 2 & SEE GEOMETRIC INTERSECTION ▶");
        nextBtn.setStyle("-fx-background-color: #2453d4; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 12 6 12;");
        nextBtn.setOnAction(e -> model.setStage(RainInvestigatorStage.STAGE_3_GEOMETRY));

        stageSpecificContent.getChildren().addAll(title, desc, sliderHeading, sliderRow, candidateResultLabel, nextBtn);
    }

    private void updateStage2CandidateDisplay(double vd) {
        try {
            Vector2D cand = model.getCandidateRainVelocityForDownwardSpeed(vd);
            candidateResultLabel.setText(String.format(Locale.ROOT,
                    "Assumed v_d = %.1f m/s  ⟹  Required vᵣₓ = %.2f m/s\n" +
                    "Candidate Vector: vᵣ = (%.2f, -%.2f) m/s\n" +
                    "Apparent angle for Obs 1: atan2(v₁ - vᵣₓ, v_d) = %.1f° (Constant!)",
                    vd, cand.x(), cand.x(), vd, model.angleDeg1()));
        } catch (Exception e) {
            candidateResultLabel.setText("Invalid candidate speed: " + e.getMessage());
        }
    }

    // --- STAGE 3: GEOMETRY ---
    private void buildStage3GeometryView() {
        Label title = new Label("STAGE 3: VELOCITY-SPACE GEOMETRY");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        title.setStyle("-fx-text-fill: #1e293b;");

        double tan1 = Math.tan(model.angleRad1());
        double tan2 = Math.tan(model.angleRad2());
        double deltaTan = tan2 - tan1;
        double deltaV = model.vPerson2() - model.vPerson1();

        Label equations = new Label(String.format(Locale.ROOT,
                "Observation Lines in (vᵣₓ, vᵣᵧ) Velocity Space:\n" +
                "• Line 1 (Blue):   vᵣₓ - vᵣᵧ · tan(%.1f°) = %.1f m/s\n" +
                "• Line 2 (Orange): vᵣₓ - vᵣᵧ · tan(%.1f°) = %.1f m/s\n\n" +
                "Slope Parameters:\n" +
                "• tan(φ₁) = %.4f,  tan(φ₂) = %.4f\n" +
                "• Δtan = tan(φ₂) - tan(φ₁) = %.4f\n" +
                "• Velocity Baseline Δv = v₂ - v₁ = %.1f m/s",
                model.angleDeg1(), model.vPerson1(),
                model.angleDeg2(), model.vPerson2(),
                tan1, tan2, deltaTan, deltaV));
        equations.setFont(Font.font("Consolas", FontWeight.NORMAL, 10.5));
        equations.setStyle("-fx-background-color: #f8fafc; -fx-padding: 8; -fx-border-color: #e2e8f0; -fx-border-radius: 4px;");

        Label outcomeTitle = new Label("GEOMETRIC OUTCOME:");
        outcomeTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
        outcomeTitle.setStyle("-fx-text-fill: #334155;");

        Label outcomeDesc = new Label();
        outcomeDesc.setWrapText(true);
        outcomeDesc.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");

        RainInvestigatorResult res = model.evaluatorResult();
        if (res.isSolvable()) {
            outcomeDesc.setText(
                    "The two observation lines intersect at a unique point in the downward falling rain half-plane (vᵣᵧ < 0). " +
                    "This unique intersection determines the ground-frame rain velocity!");
        } else if (res.status() == RainInvestigatorStatus.DEGENERATE_PARALLEL_OBSERVATIONS) {
            outcomeDesc.setText(
                    "Parallel observation lines! Because tan(φ₁) = tan(φ₂), the lines have identical slopes and do not intersect. No unique solution exists.");
        } else if (res.status() == RainInvestigatorStatus.DEGENERATE_IDENTICAL_SPEEDS) {
            outcomeDesc.setText(
                    "Identical observer velocities (v₁ = v₂). Both lines pass through the exact same point on the horizontal axis, providing zero vertical baseline resolution.");
        } else if (res.status() == RainInvestigatorStatus.INVALID_INCONSISTENT_DIRECTION) {
            outcomeDesc.setText(
                    "Inconsistent apparent angles! The lines intersect in the upper half-plane (vᵣᵧ > 0), which would mean rain falling upward. Unphysical!");
        } else {
            outcomeDesc.setText("Unsolvable: " + res.diagnosticMessage());
        }

        Button nextBtn = new Button("INSPECT ANALYTICAL RECONSTRUCTION ▶");
        nextBtn.setStyle("-fx-background-color: #2453d4; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 12 6 12;");
        nextBtn.setOnAction(e -> model.setStage(RainInvestigatorStage.STAGE_4_RECONSTRUCTION));

        stageSpecificContent.getChildren().addAll(title, equations, outcomeTitle, outcomeDesc, nextBtn);
    }

    // --- STAGE 4: RECONSTRUCTION ---
    private void buildStage4ReconstructionView() {
        Label title = new Label("STAGE 4: ANALYTICAL RECONSTRUCTION");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        title.setStyle("-fx-text-fill: #1e293b;");

        RainInvestigatorResult res = model.evaluatorResult();

        double v1 = model.vPerson1();
        double v2 = model.vPerson2();
        double tan1 = Math.tan(model.angleRad1());
        double tan2 = Math.tan(model.angleRad2());
        double deltaV = v2 - v1;
        double deltaTan = tan2 - tan1;

        if (res.isSolvable() && res.reconstructedRainVelocity() != null) {
            Vector2D rec = res.reconstructedRainVelocity();
            double vd = -rec.y();
            double vrx = rec.x();

            Label steps = new Label(String.format(Locale.ROOT,
                    "Step 1: Governing Linear System\n" +
                    "   (1) vᵣₓ + v_d · tan(φ₁) = v₁  ==>  vᵣₓ + v_d·(%.4f) = %.1f\n" +
                    "   (2) vᵣₓ + v_d · tan(φ₂) = v₂  ==>  vᵣₓ + v_d·(%.4f) = %.1f\n\n" +
                    "Step 2: Subtract (1) from (2) to eliminate vᵣₓ\n" +
                    "   v_d · (tan φ₂ - tan φ₁) = v₂ - v₁\n" +
                    "   v_d · (%.4f) = %.1f\n\n" +
                    "Step 3: Solve for Downward Speed (v_d)\n" +
                    "   v_d = (v₂ - v₁) / (tan φ₂ - tan φ₁) = %.1f / %.4f = %.4f m/s\n\n" +
                    "Step 4: Back-Substitute for Horizontal Wind (vᵣₓ)\n" +
                    "   vᵣₓ = v₁ - v_d · tan(φ₁) = %.1f - (%.4f)·(%.4f) = %.4f m/s\n\n" +
                    "Step 5: Ground-Frame Rain Velocity Vector\n" +
                    "   vᵣ = (vᵣₓ, -v_d) = (%.2f î - %.2f ĵ) m/s\n" +
                    "   Speed |vᵣ| = √(vᵣₓ² + v_d²) = %.2f m/s",
                    tan1, v1, tan2, v2,
                    deltaTan, deltaV,
                    deltaV, deltaTan, vd,
                    v1, vd, tan1, vrx,
                    vrx, vd, vrx, vd, rec.magnitude()));
            steps.setFont(Font.font("Consolas", FontWeight.NORMAL, 10.5));
            steps.setStyle("-fx-background-color: #f8fafc; -fx-padding: 8; -fx-border-color: #e2e8f0; -fx-border-radius: 4px;");

            Label condLabel = new Label(String.format(Locale.ROOT,
                    "Conditioning Diagnostic:\n" +
                    "• Condition Number κ_down = %.2f\n" +
                    "• Estimated relative uncertainty ~%.1f%% per degree of angle uncertainty.\n" +
                    "%s",
                    res.downwardConditionNumber(),
                    res.estimatedDownwardRelError() * 100.0,
                    res.status() == RainInvestigatorStatus.ILL_CONDITIONED
                            ? "⚠ Warning: High sensitivity to angle error (near-parallel rays)."
                            : "✓ Numerically stable: small measurement errors do not distort reconstruction."));
            condLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
            condLabel.setStyle(res.status() == RainInvestigatorStatus.ILL_CONDITIONED
                    ? "-fx-background-color: #fef3c7; -fx-text-fill: #92400e; -fx-padding: 6; -fx-border-radius: 4px;"
                    : "-fx-background-color: #ecfdf5; -fx-text-fill: #065f46; -fx-padding: 6; -fx-border-radius: 4px;");
            condLabel.setWrapText(true);

            Button nextBtn = new Button("RUN FORWARD VERIFICATION CHECK ▶");
            nextBtn.setStyle("-fx-background-color: #2453d4; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 12 6 12;");
            nextBtn.setOnAction(e -> model.setStage(RainInvestigatorStage.STAGE_5_VERIFICATION));

            stageSpecificContent.getChildren().addAll(title, steps, condLabel, nextBtn);
        } else {
            Label failLabel = new Label("Reconstruction cannot be calculated: " + res.diagnosticMessage());
            failLabel.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-padding: 8; -fx-font-weight: bold;");
            stageSpecificContent.getChildren().addAll(title, failLabel);
        }
    }

    // --- STAGE 5: VERIFICATION ---
    private void buildStage5VerificationView() {
        Label title = new Label("STAGE 5: ROUND-TRIP FORWARD VERIFICATION");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        title.setStyle("-fx-text-fill: #1e293b;");

        RainVerificationSnapshot snap = model.verificationSnapshot();
        verificationStatusBadge.setId("verificationStatusBadge");
        verificationStatusBadge.setText(snap.status().label());
        verificationStatusBadge.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-font-weight: bold; -fx-padding: 4 10 4 10; -fx-border-radius: 4px; -fx-background-radius: 4px;",
                snap.status().backgroundColor(), snap.status().textColor()));

        GridPane table = new GridPane();
        table.setHgap(12);
        table.setVgap(6);
        table.setStyle("-fx-background-color: #f8fafc; -fx-padding: 10; -fx-border-color: #e2e8f0; -fx-border-radius: 4px;");

        Label hObs = new Label("Observer");
        Label hExp = new Label("Entered φ");
        Label hCalc = new Label("Forward φ");
        Label hRes = new Label("Residual |Δφ|");
        for (Label l : new Label[]{hObs, hExp, hCalc, hRes}) {
            l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
            l.setStyle("-fx-text-fill: #334155;");
        }

        table.addRow(0, hObs, hExp, hCalc, hRes);

        valResidual1.setId("valResidual1");
        valResidual2.setId("valResidual2");
        valMaxResidual.setId("valMaxResidual");

        if (snap.status() != RainVerificationStatus.NOT_VERIFIABLE) {
            valResidual1.setText(String.format(Locale.ROOT, "%.6f°", snap.residualDeg1()));
            valResidual2.setText(String.format(Locale.ROOT, "%.6f°", snap.residualDeg2()));
            valMaxResidual.setText(String.format(Locale.ROOT, "%.6f°", snap.maxResidualDeg()));

            table.addRow(1,
                    new Label("Observer 1"),
                    new Label(String.format(Locale.ROOT, "%.2f°", model.angleDeg1())),
                    new Label(String.format(Locale.ROOT, "%.4f°", snap.calculatedAngleDeg1())),
                    valResidual1);

            table.addRow(2,
                    new Label("Observer 2"),
                    new Label(String.format(Locale.ROOT, "%.2f°", model.angleDeg2())),
                    new Label(String.format(Locale.ROOT, "%.4f°", snap.calculatedAngleDeg2())),
                    valResidual2);
        } else {
            valResidual1.setText("N/A");
            valResidual2.setText("N/A");
            valMaxResidual.setText("N/A");
            table.addRow(1, new Label("Observer 1"), new Label("N/A"), new Label("N/A"), valResidual1);
            table.addRow(2, new Label("Observer 2"), new Label("N/A"), new Label("N/A"), valResidual2);
        }

        verificationSummaryLabel.setText(snap.summaryMessage());
        verificationSummaryLabel.setWrapText(true);
        verificationSummaryLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");

        Label note = new Label(
                "Pedagogical Authority Note:\n" +
                "Forward simulation verification passes the reconstructed vector through the authoritative RainManSolver. " +
                "Numerical verification verifies mathematical self-consistency (residuals ≤ 1e-4°), which is independent of sensitivity to measurement error (κ_down)."
        );
        note.setWrapText(true);
        note.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #64748b; -fx-background-color: #f1f5f9; -fx-padding: 8; -fx-border-radius: 4px;");

        stageSpecificContent.getChildren().addAll(title, verificationStatusBadge, table, verificationSummaryLabel, note);
    }

    private void renderActiveState() {
        canvasRenderer.render(model);
    }
}

