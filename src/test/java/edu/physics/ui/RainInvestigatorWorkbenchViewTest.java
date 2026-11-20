package edu.physics.ui;

import edu.physics.problem.PhysicsCapabilityRegistry;
import edu.physics.relative.RainInvestigatorPreset;
import edu.physics.relative.RainInvestigatorSessionModel;
import edu.physics.relative.RainInvestigatorStage;
import edu.physics.relative.RainInvestigatorStatus;
import edu.physics.relative.RainVerificationStatus;
import edu.physics.simulation.Vector2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RainInvestigatorWorkbenchView - UI Controls, Five-Stage Progression & Preset Acceptance Tests")
class RainInvestigatorWorkbenchViewTest {

    @BeforeAll
    static void initJavaFx() throws Exception {
        FxTestRuntime.ensureStarted();
    }

    private void runOnFxThread(Runnable action) throws Exception {
        FxTestRuntime.runOnFxThread(() -> {
            action.run();
            return null;
        });
    }

    @Test
    @DisplayName("Workbench constructs cleanly with all primary controls, badges, and initial default state")
    void testWorkbenchConstruction() throws Exception {
        runOnFxThread(() -> {
            RainInvestigatorSessionModel model = new RainInvestigatorSessionModel();
            RainInvestigatorWorkbenchView view = new RainInvestigatorWorkbenchView(model);

            assertNotNull(view.lookup("#rainInvestigatorWorkbenchView"));
            assertNotNull(view.lookup("#returnToKinematicsButton"));
            assertNotNull(view.lookup("#stageBtn1"));
            assertNotNull(view.lookup("#stageBtn2"));
            assertNotNull(view.lookup("#stageBtn3"));
            assertNotNull(view.lookup("#stageBtn4"));
            assertNotNull(view.lookup("#stageBtn5"));
            assertNotNull(view.lookup("#prevStageBtn"));
            assertNotNull(view.lookup("#nextStageBtn"));
            assertNotNull(view.lookup("#presetCombo"));
            assertNotNull(view.lookup("#v1Input"));
            assertNotNull(view.lookup("#phi1Input"));
            assertNotNull(view.lookup("#v2Input"));
            assertNotNull(view.lookup("#phi2Input"));
            assertNotNull(view.lookup("#evaluatorStatusBadge"));
            assertNotNull(view.lookup("#conditionNumberBadge"));
            assertNotNull(view.lookup("#reconstructedVelocityBadge"));
            assertNotNull(view.lookup("#rainInvestigatorCanvas"));

            // Default preset is Standard JEE
            Label statusBadge = (Label) view.lookup("#evaluatorStatusBadge");
            assertEquals("VALID", statusBadge.getText().trim());

            Label velBadge = (Label) view.lookup("#reconstructedVelocityBadge");
            assertTrue(velBadge.getText().contains("v_r = (0.00, -10.00) m/s"),
                    "Standard JEE default should yield v_r = (0, -10) m/s");

            view.dispose();
        });
    }

    @Test
    @DisplayName("Five-stage progression toggles Observation 2 visibility and renders appropriate stage view")
    void testFiveStageProgressionAndObservation2Visibility() throws Exception {
        runOnFxThread(() -> {
            RainInvestigatorSessionModel model = new RainInvestigatorSessionModel();
            RainInvestigatorWorkbenchView view = new RainInvestigatorWorkbenchView(model);

            // STAGE 1: Evidence (Default)
            assertEquals(RainInvestigatorStage.STAGE_1_EVIDENCE, model.stage());
            assertTrue(model.isObservation2Visible(), "Observation 2 must be visible in Stage 1");
            Node obs2Container = view.lookup("#obs2Container");
            assertNotNull(obs2Container);
            assertTrue(obs2Container.isVisible());

            // STAGE 2: Ambiguity (Observation 2 must be hidden!)
            Button btnStage2 = (Button) view.lookup("#stageBtn2");
            btnStage2.fire();
            assertEquals(RainInvestigatorStage.STAGE_2_AMBIGUITY, model.stage());
            assertFalse(model.isObservation2Visible(), "Observation 2 must be hidden in Stage 2 Ambiguity!");
            assertFalse(obs2Container.isVisible(), "Observation 2 container must be hidden in Stage 2");

            Slider candSlider = (Slider) view.lookup("#candidateVDownSlider");
            assertNotNull(candSlider);
            Label candResult = (Label) view.lookup("#candidateResultLabel");
            assertNotNull(candResult);
            assertTrue(candResult.getText().contains("Candidate Vector:"), "Candidate vector readout should be visible");

            // Move candidate slider
            candSlider.setValue(12.0);
            assertEquals(12.0, view.canvasRenderer().candidateVDown(), 1e-4);
            assertTrue(candResult.getText().contains("v_d = 12.0 m/s"));

            // STAGE 3: Geometry (Observation 2 restored)
            Button btnStage3 = (Button) view.lookup("#stageBtn3");
            btnStage3.fire();
            assertEquals(RainInvestigatorStage.STAGE_3_GEOMETRY, model.stage());
            assertTrue(model.isObservation2Visible(), "Observation 2 must be restored in Stage 3");
            assertTrue(obs2Container.isVisible());

            // STAGE 4: Reconstruction
            Button btnStage4 = (Button) view.lookup("#stageBtn4");
            btnStage4.fire();
            assertEquals(RainInvestigatorStage.STAGE_4_RECONSTRUCTION, model.stage());
            assertTrue(model.isObservation2Visible());

            // STAGE 5: Verification
            Button btnStage5 = (Button) view.lookup("#stageBtn5");
            btnStage5.fire();
            assertEquals(RainInvestigatorStage.STAGE_5_VERIFICATION, model.stage());
            assertTrue(model.isObservation2Visible());

            Label verBadge = (Label) view.lookup("#verificationStatusBadge");
            assertNotNull(verBadge);
            assertEquals("Reconstruction Verified", verBadge.getText().trim());

            Label res1 = (Label) view.lookup("#valResidual1");
            Label res2 = (Label) view.lookup("#valResidual2");
            assertEquals("0.000000°", res1.getText().trim());
            assertEquals("0.000000°", res2.getText().trim());

            view.dispose();
        });
    }

    @Test
    @DisplayName("Previous and Next stepper buttons step sequentially across the 5 stages")
    void testStageStepperButtons() throws Exception {
        runOnFxThread(() -> {
            RainInvestigatorSessionModel model = new RainInvestigatorSessionModel();
            RainInvestigatorWorkbenchView view = new RainInvestigatorWorkbenchView(model);

            Button prevBtn = (Button) view.lookup("#prevStageBtn");
            Button nextBtn = (Button) view.lookup("#nextStageBtn");

            assertEquals(RainInvestigatorStage.STAGE_1_EVIDENCE, model.stage());
            assertTrue(prevBtn.isDisable(), "Previous button should be disabled at Stage 1");
            assertFalse(nextBtn.isDisable());

            // Next -> Stage 2
            nextBtn.fire();
            assertEquals(RainInvestigatorStage.STAGE_2_AMBIGUITY, model.stage());
            assertFalse(prevBtn.isDisable());

            // Next -> Stage 3
            nextBtn.fire();
            assertEquals(RainInvestigatorStage.STAGE_3_GEOMETRY, model.stage());

            // Next -> Stage 4
            nextBtn.fire();
            assertEquals(RainInvestigatorStage.STAGE_4_RECONSTRUCTION, model.stage());

            // Next -> Stage 5
            nextBtn.fire();
            assertEquals(RainInvestigatorStage.STAGE_5_VERIFICATION, model.stage());
            assertTrue(nextBtn.isDisable(), "Next button should be disabled at Stage 5");

            // Prev -> Stage 4
            prevBtn.fire();
            assertEquals(RainInvestigatorStage.STAGE_4_RECONSTRUCTION, model.stage());

            view.dispose();
        });
    }

    @Test
    @DisplayName("Curated Presets: Evaluates and verifies all 5 presets with correct classifications")
    void testAllFivePresets() throws Exception {
        runOnFxThread(() -> {
            RainInvestigatorSessionModel model = new RainInvestigatorSessionModel();
            RainInvestigatorWorkbenchView view = new RainInvestigatorWorkbenchView(model);

            Label statusBadge = (Label) view.lookup("#evaluatorStatusBadge");
            Label velBadge = (Label) view.lookup("#reconstructedVelocityBadge");
            Label condBadge = (Label) view.lookup("#conditionNumberBadge");

            // 1. Standard JEE
            model.loadPreset(RainInvestigatorPreset.STANDARD_JEE);
            assertEquals(RainInvestigatorStatus.VALID, model.evaluatorResult().status());
            assertEquals("VALID", statusBadge.getText().trim());
            Vector2D r1 = model.evaluatorResult().reconstructedRainVelocity();
            assertEquals(0.0, r1.x(), 1e-6);
            assertEquals(-10.0, r1.y(), 1e-6);
            assertEquals(RainVerificationStatus.VERIFIED, model.verificationSnapshot().status());

            // 2. Windy JEE Classic
            model.loadPreset(RainInvestigatorPreset.WINDY_JEE);
            assertEquals(RainInvestigatorStatus.VALID, model.evaluatorResult().status());
            assertEquals("VALID", statusBadge.getText().trim());
            Vector2D r2 = model.evaluatorResult().reconstructedRainVelocity();
            assertEquals(5.0, r2.x(), 1e-2);
            assertEquals(-8.66, r2.y(), 1e-2);
            assertEquals(RainVerificationStatus.VERIFIED, model.verificationSnapshot().status());

            // 3. Near-Parallel (Ill-Conditioned)
            model.loadPreset(RainInvestigatorPreset.NEAR_PARALLEL);
            assertEquals(RainInvestigatorStatus.ILL_CONDITIONED, model.evaluatorResult().status());
            assertEquals("ILL_CONDITIONED", statusBadge.getText().trim());
            assertTrue(model.evaluatorResult().downwardConditionNumber() > 25.0, "Near-parallel should have kappa > 25");
            assertTrue(condBadge.getText().contains("κ_down ="));
            assertEquals(RainVerificationStatus.VERIFIED, model.verificationSnapshot().status(),
                    "Forward simulation still mathematically reproduces the angles");

            // 4. Identical Speeds (Degenerate)
            model.loadPreset(RainInvestigatorPreset.IDENTICAL_SPEEDS);
            assertEquals(RainInvestigatorStatus.DEGENERATE_IDENTICAL_SPEEDS, model.evaluatorResult().status());
            assertEquals("DEGENERATE_IDENTICAL_SPEEDS", statusBadge.getText().trim());
            assertNull(model.evaluatorResult().reconstructedRainVelocity());
            assertEquals(RainVerificationStatus.NOT_VERIFIABLE, model.verificationSnapshot().status());
            assertTrue(velBadge.getText().contains("Unsolvable"));

            // 5. Inconsistent Direction (Upward Rain)
            model.loadPreset(RainInvestigatorPreset.INCONSISTENT_DIRECTION);
            assertEquals(RainInvestigatorStatus.INVALID_INCONSISTENT_DIRECTION, model.evaluatorResult().status());
            assertEquals("INVALID_INCONSISTENT_DIRECTION", statusBadge.getText().trim());
            assertNull(model.evaluatorResult().reconstructedRainVelocity());
            assertEquals(RainVerificationStatus.NOT_VERIFIABLE, model.verificationSnapshot().status());
            assertTrue(velBadge.getText().contains("Unsolvable"));

            view.dispose();
        });
    }

    @Test
    @DisplayName("PlannedChapterView hosts Experiment 2.4 and switches between workbenches seamlessly")
    void testPlannedChapterViewIntegration() throws Exception {
        runOnFxThread(() -> {
            PlannedChapterView plannedView = new PlannedChapterView();
            plannedView.setChapter(PhysicsCapabilityRegistry.findChapter(2).orElseThrow());

            // Check all 4 sub-nav experiment buttons
            assertNotNull(plannedView.lookup("#btnExpTwoProjectile"));
            assertNotNull(plannedView.lookup("#btnExpRiverBoat"));
            assertNotNull(plannedView.lookup("#btnExpRainMan"));
            assertNotNull(plannedView.lookup("#btnExpRainInvestigator"));

            // Switch to Experiment 2.4 (Inverse Rain Investigator)
            Button btnInv = (Button) plannedView.lookup("#btnExpRainInvestigator");
            btnInv.fire();
            assertTrue(plannedView.rainInvestigatorWorkbench().isVisible());

            // Switch to Forward Rain-Man (Exp 2.3)
            Button btnRainMan = (Button) plannedView.lookup("#btnExpRainMan");
            btnRainMan.fire();
            assertTrue(plannedView.rainManWorkbench().isVisible());

            // Click the launch inverse problem button in forward Rain-Man
            Button openInv = (Button) plannedView.rainManWorkbench().lookup("#openInverseInvestigatorBtn");
            assertNotNull(openInv, "Forward Rain-Man should have link to Inverse Investigator");
            openInv.fire();

            assertTrue(plannedView.rainInvestigatorWorkbench().isVisible(),
                    "Clicking open inverse button should navigate to Experiment 2.4");

            plannedView.dispose();
        });
    }
}

