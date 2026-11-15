package edu.physics.ui;

import edu.physics.problem.PredictionChallenge;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Signature Learning Features UI Integration Tests (Predict + Compare)")
class SignatureLearningFeaturesTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxTestRuntime.ensureStarted();
    }

    private static <T> T runOnFxThread(Callable<T> work) throws Exception {
        return FxTestRuntime.runOnFxThread(work);
    }

    @FunctionalInterface
    private interface ApplicationAction<T> {
        T run(Stage stage, PhysoraApplicationShell shell, Parent root) throws Exception;
    }

    private static <T> T withApplication(ApplicationAction<T> action) throws Exception {
        return runOnFxThread(() -> {
            Stage stage = new Stage();
            PhysoraApplicationShell shell = new PhysoraApplicationShell();
            Scene scene = new Scene(shell, 1200, 780);
            scene.getStylesheets().add(SignatureLearningFeaturesTest.class
                    .getResource("/edu/physics/ui/physora.css").toExternalForm());
            stage.setScene(scene);
            stage.show();
            try {
                return action.run(stage, shell, shell);
            } finally {
                shell.dispose();
                stage.close();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T extends Node> T node(Parent parent, String id, Class<T> type) {
        Node found = parent.lookup("#" + id);
        assertNotNull(found, "Node with id #" + id + " must be present in the scene graph");
        assertTrue(type.isInstance(found), "Node #" + id + " must be an instance of " + type.getSimpleName());
        return (T) found;
    }

    @Test
    @DisplayName("Predict and Compare tabs switch workspace view visibility correctly")
    void tabsSwitchPredictAndCompareViews() throws Exception {
        withApplication((stage, shell, root) -> {
            ToggleButton predictTab = node(root, "predictTab", ToggleButton.class);
            ToggleButton compareTab = node(root, "compareTab", ToggleButton.class);
            ToggleButton simTab = node(root, "simulationViewTab", ToggleButton.class);

            PredictionView predictionView = node(root, "predictionView", PredictionView.class);
            ComparisonView comparisonView = node(root, "comparisonView", ComparisonView.class);
            ProjectileSimulationView simView = node(root, "simulationView", ProjectileSimulationView.class);

            // Initially, SIMULATION is visible
            assertTrue(simView.isVisible());
            assertFalse(predictionView.isVisible());
            assertFalse(comparisonView.isVisible());

            // Switch to PREDICT
            predictTab.setSelected(true);
            predictTab.getOnAction().handle(null);
            assertTrue(predictionView.isVisible());
            assertFalse(simView.isVisible());
            assertFalse(comparisonView.isVisible());

            // Switch to COMPARE
            compareTab.setSelected(true);
            compareTab.getOnAction().handle(null);
            assertTrue(comparisonView.isVisible());
            assertFalse(predictionView.isVisible());
            assertFalse(simView.isVisible());

            // Switch back to EXPERIMENT
            simTab.setSelected(true);
            simTab.getOnAction().handle(null);
            assertTrue(simView.isVisible());
            assertFalse(predictionView.isVisible());
            assertFalse(comparisonView.isVisible());

            return null;
        });
    }

    @Test
    @DisplayName("Prediction workflow: input, lock, parameter modification invalidation warning")
    void predictionWorkflowLockingAndInvalidation() throws Exception {
        withApplication((stage, shell, root) -> {
            ToggleButton predictTab = node(root, "predictTab", ToggleButton.class);
            predictTab.setSelected(true);
            predictTab.getOnAction().handle(null);

            PredictionView predictionView = node(root, "predictionView", PredictionView.class);
            TextField heightField = node(root, "predictMaxHeightField", TextField.class);
            TextField timeField = node(root, "predictFlightTimeField", TextField.class);
            TextField rangeField = node(root, "predictRangeField", TextField.class);
            Button lockButton = node(root, "lockPredictionsButton", Button.class);
            Button evaluateButton = node(root, "evaluatePredictionsButton", Button.class);
            Label warningLabel = node(root, "predictionInvalidWarning", Label.class);

            assertFalse(heightField.isDisable());
            assertFalse(lockButton.isDisable());
            assertTrue(evaluateButton.isDisable());
            assertFalse(warningLabel.isVisible());

            // Fill predictions
            heightField.setText("12.5");
            timeField.setText("2.8");
            rangeField.setText("42.0");

            // Lock predictions
            lockButton.fire();

            assertTrue(heightField.isDisable());
            assertTrue(timeField.isDisable());
            assertTrue(rangeField.isDisable());
            assertTrue(lockButton.isDisable());
            assertTrue(evaluateButton.isDisable(), "Evaluate button must be disabled before simulation lands");
            assertEquals(PredictionChallenge.State.LOCKED, predictionView.currentChallenge().state());

            // Modify parameter on controller -> triggers invalidation check
            shell.controller().launchSpeedProperty().set(35.0);

            assertTrue(warningLabel.isVisible(), "Invalidation warning must become visible when parameters change after lock");
            assertEquals(PredictionChallenge.State.INVALIDATED, predictionView.currentChallenge().state());

            return null;
        });
    }

    @Test
    @DisplayName("Prediction workflow: evaluate reveals results card and constructive explanations")
    void predictionWorkflowEvaluationAndReset() throws Exception {
        withApplication((stage, shell, root) -> {
            ToggleButton predictTab = node(root, "predictTab", ToggleButton.class);
            predictTab.setSelected(true);
            predictTab.getOnAction().handle(null);

            PredictionView predictionView = node(root, "predictionView", PredictionView.class);
            TextField heightField = node(root, "predictMaxHeightField", TextField.class);
            TextField timeField = node(root, "predictFlightTimeField", TextField.class);
            TextField rangeField = node(root, "predictRangeField", TextField.class);
            Button lockButton = node(root, "lockPredictionsButton", Button.class);
            Button evaluateButton = node(root, "evaluatePredictionsButton", Button.class);
            Button newAttemptButton = node(root, "newPredictionAttemptButton", Button.class);
            VBox resultsCard = node(root, "predictionResultsCard", VBox.class);

            // New attempt cleans up
            newAttemptButton.fire();
            assertFalse(resultsCard.isVisible());

            heightField.setText("10.0");
            timeField.setText("2.5");
            rangeField.setText("35.0");

            lockButton.fire();
            assertEquals(PredictionChallenge.State.LOCKED, predictionView.currentChallenge().state());
            assertTrue(evaluateButton.isDisable(), "Evaluate button must be disabled before touchdown");

            // Run simulation to landing to reach terminal state
            shell.controller().seekToLanding();

            // When landed, evaluation executes (or evaluate button is enabled)
            if (!evaluateButton.isDisable()) {
                evaluateButton.fire();
            }

            assertEquals(PredictionChallenge.State.EVALUATED, predictionView.currentChallenge().state());
            assertTrue(resultsCard.isVisible(), "Results card must be visible after evaluation");
            assertFalse(resultsCard.getChildren().isEmpty(), "Results card must contain evaluated rows");
            assertTrue(evaluateButton.isDisable(), "Evaluate button must be disabled once evaluated");

            return null;
        });
    }

    @Test
    @DisplayName("Comparison workflow: load complementary demo populates table, overlay, and academic insight")
    void comparisonWorkflowComplementaryDemo() throws Exception {
        withApplication((stage, shell, root) -> {
            ToggleButton compareTab = node(root, "compareTab", ToggleButton.class);
            compareTab.setSelected(true);
            compareTab.getOnAction().handle(null);

            ComparisonView compView = node(root, "comparisonView", ComparisonView.class);
            Button demoButton = node(root, "loadComplementaryDemoButton", Button.class);
            Label insightText = node(root, "comparisonInsightText", Label.class);
            VBox tableContainer = node(root, "comparisonTableContainer", VBox.class);

            demoButton.fire();

            assertEquals(30.0, compView.experimentA().launchAngleDegrees(), 1e-4);
            assertEquals(60.0, compView.experimentB().launchAngleDegrees(), 1e-4);
            assertEquals(compView.experimentA().initialSpeed(), compView.experimentB().initialSpeed(), 1e-4);

            assertFalse(tableContainer.getChildren().isEmpty(), "Numerical comparison table must be populated");
            String insight = insightText.getText();
            assertTrue(insight.contains("COMPLEMENTARY ANGLES (θA + θB = 90°) ON LEVEL GROUND"));
            assertTrue(insight.contains("IDENTICAL"));

            return null;
        });
    }

    @Test
    @DisplayName("Comparison workflow: capture A and capture B updates experiment snapshots and delta table")
    void comparisonWorkflowCaptureCurrentState() throws Exception {
        withApplication((stage, shell, root) -> {
            ToggleButton compareTab = node(root, "compareTab", ToggleButton.class);
            compareTab.setSelected(true);
            compareTab.getOnAction().handle(null);

            ComparisonView compView = node(root, "comparisonView", ComparisonView.class);
            Button captureA = node(root, "captureExperimentAButton", Button.class);
            Button captureB = node(root, "captureExperimentBButton", Button.class);
            Label statusLabel = node(root, "comparisonStatusLabel", Label.class);

            // Configure experiment A
            shell.controller().launchSpeedProperty().set(22.0);
            shell.controller().launchAngleProperty().set(35.0);
            captureA.fire();

            assertEquals(22.0, compView.experimentA().initialSpeed(), 1e-4);
            assertEquals(35.0, compView.experimentA().launchAngleDegrees(), 1e-4);
            assertTrue(statusLabel.getText().contains("Experiment A captured"));

            // Configure experiment B
            shell.controller().launchSpeedProperty().set(30.0);
            shell.controller().launchAngleProperty().set(55.0);
            captureB.fire();

            assertEquals(30.0, compView.experimentB().initialSpeed(), 1e-4);
            assertEquals(55.0, compView.experimentB().launchAngleDegrees(), 1e-4);
            assertTrue(statusLabel.getText().contains("Comparison active"));

            return null;
        });
    }

    @Test
    @DisplayName("Prediction cannot reveal before touchdown; seeking or pausing preserves locked state")
    void cannotRevealPredictionBeforeTouchdownOrWhilePaused() throws Exception {
        withApplication((stage, shell, root) -> {
            ToggleButton predictTab = node(root, "predictTab", ToggleButton.class);
            predictTab.setSelected(true);
            predictTab.getOnAction().handle(null);

            PredictionView predictionView = node(root, "predictionView", PredictionView.class);
            TextField heightField = node(root, "predictMaxHeightField", TextField.class);
            TextField timeField = node(root, "predictFlightTimeField", TextField.class);
            TextField rangeField = node(root, "predictRangeField", TextField.class);
            Button lockButton = node(root, "lockPredictionsButton", Button.class);
            Button evaluateButton = node(root, "evaluatePredictionsButton", Button.class);

            heightField.setText("15.0");
            timeField.setText("2.8");
            rangeField.setText("40.0");
            lockButton.fire();
            assertEquals(PredictionChallenge.State.LOCKED, predictionView.currentChallenge().state());

            // 1. In READY state: disabled
            assertTrue(evaluateButton.isDisable());

            // 2. Mid-flight / seeking to apex: still disabled
            shell.controller().seekToApex();
            assertEquals(ProjectileSimulationController.SimulationStatus.AT_APEX, shell.controller().status());
            assertTrue(evaluateButton.isDisable(), "Must remain disabled at apex");

            // 3. Pause mid-flight: still disabled
            shell.controller().pause();
            assertTrue(evaluateButton.isDisable(), "Must remain disabled when paused mid-flight");

            // 4. Seek backwards to time 0.5: still disabled
            shell.controller().seek(0.5);
            assertTrue(evaluateButton.isDisable(), "Must remain disabled when seeking mid-flight");

            // 5. Seek to touchdown: enabled and evaluates
            shell.controller().seekToLanding();
            assertEquals(ProjectileSimulationController.SimulationStatus.LANDED, shell.controller().status());
            assertEquals(PredictionChallenge.State.EVALUATED, predictionView.currentChallenge().state());

            return null;
        });
    }

    @Test
    @DisplayName("Snapshot A remains strictly immutable when live simulation parameters change repeatedly")
    void snapshotARemainsImmutableAcrossParameterChanges() throws Exception {
        withApplication((stage, shell, root) -> {
            ToggleButton compareTab = node(root, "compareTab", ToggleButton.class);
            compareTab.setSelected(true);
            compareTab.getOnAction().handle(null);

            ComparisonView compView = node(root, "comparisonView", ComparisonView.class);
            Button captureA = node(root, "captureExperimentAButton", Button.class);

            // Capture Experiment A at 20 m/s and 30 deg
            shell.controller().launchSpeedProperty().set(20.0);
            shell.controller().launchAngleProperty().set(30.0);
            captureA.fire();

            ExperimentSnapshot snapA = compView.experimentA();
            assertEquals(20.0, snapA.initialSpeed(), 1e-4);
            assertEquals(30.0, snapA.launchAngleDegrees(), 1e-4);

            // Now repeatedly mutate controller parameters
            shell.controller().launchSpeedProperty().set(45.0);
            shell.controller().launchAngleProperty().set(75.0);
            shell.controller().gravityProperty().set(15.0);
            shell.controller().initialYProperty().set(8.0);
            shell.controller().landingYProperty().set(2.0);

            // Experiment A must remain strictly untouched
            assertSame(snapA, compView.experimentA());
            assertEquals(20.0, compView.experimentA().initialSpeed(), 1e-4);
            assertEquals(30.0, compView.experimentA().launchAngleDegrees(), 1e-4);
            assertEquals(9.81, compView.experimentA().gravity(), 1e-4);
            assertEquals(0.0, compView.experimentA().initialY(), 1e-4);
            assertEquals(0.0, compView.experimentA().landingY(), 1e-4);

            return null;
        });
    }
}
