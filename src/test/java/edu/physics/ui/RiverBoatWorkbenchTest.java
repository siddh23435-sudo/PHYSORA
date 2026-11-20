package edu.physics.ui;

import edu.physics.relative.RiverBoatCrossingType;
import edu.physics.relative.RiverBoatSimulationModel;
import edu.physics.simulation.Vector2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RiverBoatWorkbenchView & Controller UI Integration Tests")
class RiverBoatWorkbenchTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxTestRuntime.ensureStarted();
    }

    @Test
    @DisplayName("Workbench initializes with default scenario and non-null components")
    void testWorkbenchInitialization() throws Exception {
        FxTestRuntime.runOnFxThread(() -> {
            RiverBoatSimulationController controller = new RiverBoatSimulationController();
            RiverBoatWorkbenchView view = new RiverBoatWorkbenchView(controller);

            assertNotNull(view.controller());
            assertNotNull(view.returnButton());
            assertNotNull(view.presetCombo());
            assertNotNull(view.headingInput());

            RiverBoatSimulationModel model = controller.model();
            assertNotNull(model);
            assertEquals(100.0, model.riverWidth(), 1e-6);
            assertEquals(3.0, model.riverSpeed(), 1e-6);
            assertEquals(5.0, model.boatSpeed(), 1e-6);

            view.dispose();
            return null;
        });
    }

    @Test
    @DisplayName("Preset selection correctly loads all 4 standard JEE scenarios")
    void testPresetSwitching() throws Exception {
        FxTestRuntime.runOnFxThread(() -> {
            RiverBoatSimulationController controller = new RiverBoatSimulationController();
            RiverBoatWorkbenchView view = new RiverBoatWorkbenchView(controller);
            ComboBox<String> combo = view.presetCombo();

            // 1. Shortest Time (theta = 0)
            combo.setValue("Shortest Time (θ = 0°, t_min = w/vb)");
            RiverBoatSimulationModel m1 = controller.model();
            assertEquals(0.0, m1.headingAngleRad(), 1e-6);
            assertEquals(RiverBoatCrossingType.SHORTEST_TIME, m1.analyticalResult().crossingType());
            assertEquals(20.0, m1.analyticalResult().crossingTime().getAsDouble(), 1e-6);
            assertEquals(60.0, m1.analyticalResult().downstreamDrift().getAsDouble(), 1e-6);

            // 2. Minimum Drift when vb < u
            combo.setValue("Minimum Drift Condition (vb < u, sin θ = vb/u)");
            RiverBoatSimulationModel m2 = controller.model();
            assertEquals(5.0, m2.riverSpeed(), 1e-6);
            assertEquals(3.0, m2.boatSpeed(), 1e-6);
            assertEquals(RiverBoatCrossingType.ZERO_DRIFT_UNACHIEVABLE, m2.analyticalResult().crossingType());
            assertTrue(m2.analyticalResult().downstreamDrift().isPresent());
            assertEquals(100.0 * (4.0 / 3.0), m2.analyticalResult().downstreamDrift().getAsDouble(), 1e-4);

            // 3. Equal Speed Asymptotic Boundary (vb = u)
            combo.setValue("Equal Speed Boundary (vb = u, Asymptotic Limit)");
            RiverBoatSimulationModel m3 = controller.model();
            assertEquals(4.0, m3.riverSpeed(), 1e-6);
            assertEquals(4.0, m3.boatSpeed(), 1e-6);
            assertEquals(RiverBoatCrossingType.ZERO_DRIFT_ONLY_ASYMPTOTIC, m3.analyticalResult().crossingType());
            assertFalse(m3.analyticalResult().crossingTime().isPresent());

            // 4. Zero Drift / Shortest Path (vb > u)
            combo.setValue("Zero Drift / Shortest Path (vb > u, sin θ = u/vb)");
            RiverBoatSimulationModel m4 = controller.model();
            assertEquals(RiverBoatCrossingType.ZERO_DRIFT_ACHIEVABLE, m4.analyticalResult().crossingType());
            assertEquals(25.0, m4.analyticalResult().crossingTime().getAsDouble(), 1e-6);
            assertEquals(0.0, m4.analyticalResult().downstreamDrift().getAsDouble(), 1e-6);

            view.dispose();
            return null;
        });
    }

    @Test
    @DisplayName("Interactive numeric input adjustments dynamically update model and vectors")
    void testSliderAdjustments() throws Exception {
        FxTestRuntime.runOnFxThread(() -> {
            RiverBoatSimulationController controller = new RiverBoatSimulationController();
            RiverBoatWorkbenchView view = new RiverBoatWorkbenchView(controller);

            // Adjust steering angle to 0 degrees via text field
            view.headingInput().setText("0.0");

            RiverBoatSimulationModel updated = controller.model();
            assertEquals(0.0, updated.headingAngleDeg(), 0.1);
            assertEquals(RiverBoatCrossingType.SHORTEST_TIME, updated.analyticalResult().crossingType());

            view.dispose();
            return null;
        });
    }

    @Test
    @DisplayName("Simulation playback auto-pauses and clamps when crossing completes")
    void testPlaybackClampingOnLanding() throws Exception {
        FxTestRuntime.runOnFxThread(() -> {
            RiverBoatSimulationController controller = new RiverBoatSimulationController();
            RiverBoatWorkbenchView view = new RiverBoatWorkbenchView(controller);

            // Load Shortest Time (t_cross = 20 s)
            controller.setModel(RiverBoatSimulationModel.presetShortestTime(100.0, 3.0, 5.0));
            assertEquals(20.0, controller.maxTime(), 1e-6);

            // Seek past crossing time
            controller.seek(25.0);
            assertEquals(20.0, controller.simulationTime(), 1e-6, "Time must clamp to maxTime on over-seek");
            assertTrue(controller.model().isLanded(controller.simulationTime()));

            view.dispose();
            return null;
        });
    }

    @Test
    @DisplayName("Chapter 02 navigation switches seamlessly between Experiment 2.1 and Experiment 2.2")
    void testChapter2ExperimentSwitching() throws Exception {
        FxTestRuntime.runOnFxThread(() -> {
            PlannedChapterView plannedView = new PlannedChapterView();

            // Verify both workbenches are present
            assertNotNull(plannedView.relativeMotionWorkbench());
            assertNotNull(plannedView.riverBoatWorkbench());

            // Switch to Experiment 2.2
            plannedView.selectChapter2Experiment(2);
            assertNotNull(plannedView.riverBoatWorkbench());

            // Switch back to Experiment 2.1
            plannedView.selectChapter2Experiment(1);
            assertNotNull(plannedView.relativeMotionWorkbench());

            plannedView.dispose();
            return null;
        });
    }

    @Test
    @DisplayName("Switching Chapter 02 experiments automatically pauses active playback")
    void testExperimentSwitchingPausesPlayback() throws Exception {
        FxTestRuntime.runOnFxThread(() -> {
            PlannedChapterView plannedView = new PlannedChapterView();

            // Start playing Experiment 2.1
            plannedView.relativeMotionWorkbench().controller().play();
            assertTrue(plannedView.relativeMotionWorkbench().controller().isPlaying());

            // Switch to Experiment 2.2
            plannedView.selectChapter2Experiment(2);
            assertFalse(plannedView.relativeMotionWorkbench().controller().isPlaying(), "Experiment 2.1 must pause when switching to 2.2");

            // Start playing Experiment 2.2
            plannedView.riverBoatWorkbench().controller().play();
            assertTrue(plannedView.riverBoatWorkbench().controller().isPlaying());

            // Switch back to Experiment 2.1
            plannedView.selectChapter2Experiment(1);
            assertFalse(plannedView.riverBoatWorkbench().controller().isPlaying(), "Experiment 2.2 must pause when switching to 2.1");

            plannedView.dispose();
            return null;
        });
    }

    @Test
    @DisplayName("Return to Kinematics callback triggers appropriately")
    void testReturnToKinematicsCallback() throws Exception {
        FxTestRuntime.runOnFxThread(() -> {
            RiverBoatSimulationController controller = new RiverBoatSimulationController();
            RiverBoatWorkbenchView view = new RiverBoatWorkbenchView(controller);

            AtomicBoolean called = new AtomicBoolean(false);
            view.setOnReturnToKinematics(() -> called.set(true));

            Button returnBtn = (Button) view.lookup("#returnToKinematicsButton");
            assertNotNull(returnBtn);
            returnBtn.fire();

            assertTrue(called.get(), "Return callback must be invoked when return button is pressed");

            view.dispose();
            return null;
        });
    }

    @Test
    @DisplayName("PhysoraApplicationShell navigation switches between Kinematics and Chapter 2 experiments")
    void testShellNavigationBetweenKinematicsAndChapter2Experiments() throws Exception {
        FxTestRuntime.runOnFxThread(() -> {
            PhysoraApplicationShell shell = new PhysoraApplicationShell();

            // Starts on Chapter 1 (Kinematics)
            assertEquals(1, shell.chapterRail().selectedChapter().number());
            assertSame(shell.normalBenchView(), shell.getCenter());

            // Select Chapter 2 via shell
            shell.selectChapter(2);
            assertEquals(2, shell.chapterRail().selectedChapter().number());
            assertSame(shell.plannedChapterView(), shell.getCenter());

            // By default, Experiment 2.1 (Two-Projectile) is active
            PlannedChapterView plannedView = shell.plannedChapterView();
            Button btnExpRiverBoat = (Button) plannedView.lookup("#btnExpRiverBoat");
            Button btnExpTwoProj = (Button) plannedView.lookup("#btnExpTwoProjectile");
            assertNotNull(btnExpRiverBoat);
            assertNotNull(btnExpTwoProj);

            // Click Experiment 2.2 button -> Switches to River-Boat workbench, NOT Kinematics
            btnExpRiverBoat.fire();
            assertSame(shell.plannedChapterView(), shell.getCenter());
            assertNotSame(shell.normalBenchView(), shell.getCenter());
            assertNotNull(shell.lookup("#riverBoatWorkbenchView"));

            // Click Experiment 2.1 button -> Switches to Two-Projectile workbench
            btnExpTwoProj.fire();
            assertSame(shell.plannedChapterView(), shell.getCenter());
            assertNotNull(shell.lookup("#relativeMotionWorkbenchView"));

            // Switch to Experiment 2.2 and click returnToKinematicsButton -> Returns to Kinematics
            btnExpRiverBoat.fire();
            Button returnBtn = (Button) shell.lookup("#returnToKinematicsButton");
            assertNotNull(returnBtn);
            returnBtn.fire();

            assertEquals(1, shell.chapterRail().selectedChapter().number());
            assertSame(shell.normalBenchView(), shell.getCenter());

            shell.dispose();
            return null;
        });
    }
}
