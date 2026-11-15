package edu.physics.ui;

import edu.physics.relative.ReferenceFrame;
import edu.physics.relative.TwoProjectilePursuitModel;
import edu.physics.simulation.Vector2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RelativeMotionWorkbenchView & Simulation Controller UI Integration Tests")
class RelativeMotionWorkbenchTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxTestRuntime.ensureStarted();
    }

    @Test
    @DisplayName("Frame switching strictly preserves simulation time and play/pause state")
    void testFrameSwitchingPreservesTimeAndPlaybackState() throws Exception {
        FxTestRuntime.runOnFxThread(() -> {
            RelativeMotionSimulationController controller = new RelativeMotionSimulationController();
            RelativeMotionWorkbenchView view = new RelativeMotionWorkbenchView(controller);

            // Seek to 1.75 seconds and start playing
            controller.seek(1.75);
            controller.play();

            assertEquals(1.75, controller.simulationTime(), 1e-9);
            assertTrue(controller.isPlaying());
            assertEquals(ReferenceFrame.GROUND, controller.activeFrame());

            // Switch to Observer A frame
            Button observerABtn = (Button) view.lookup("#frameObserverABtn");
            assertNotNull(observerABtn);
            observerABtn.fire();

            assertEquals(ReferenceFrame.OBSERVER_A, controller.activeFrame());
            assertEquals(1.75, controller.simulationTime(), 1e-9, "Time must be preserved across frame switch");
            assertTrue(controller.isPlaying(), "Play state must be preserved across frame switch");

            // Switch to Observer B frame
            Button observerBBtn = (Button) view.lookup("#frameObserverBBtn");
            assertNotNull(observerBBtn);
            observerBBtn.fire();

            assertEquals(ReferenceFrame.OBSERVER_B, controller.activeFrame());
            assertEquals(1.75, controller.simulationTime(), 1e-9, "Time must be preserved across frame switch");
            assertTrue(controller.isPlaying(), "Play state must be preserved across frame switch");

            // Switch back to Ground frame
            Button groundBtn = (Button) view.lookup("#frameGroundBtn");
            assertNotNull(groundBtn);
            groundBtn.fire();

            assertEquals(ReferenceFrame.GROUND, controller.activeFrame());
            assertEquals(1.75, controller.simulationTime(), 1e-9);
            assertTrue(controller.isPlaying());

            controller.dispose();
            return null;
        });
    }

    @Test
    @DisplayName("Timeline transport controls: seek, step, pause, and reset")
    void testTimelineTransportControls() throws Exception {
        FxTestRuntime.runOnFxThread(() -> {
            RelativeMotionSimulationController controller = new RelativeMotionSimulationController();
            RelativeMotionWorkbenchView view = new RelativeMotionWorkbenchView(controller);

            Slider slider = (Slider) view.lookup("#timeSlider");
            Button playPauseBtn = (Button) view.lookup("#playPauseButton");
            Button stepBackBtn = (Button) view.lookup("#stepBackButton");
            Button stepForwardBtn = (Button) view.lookup("#stepForwardButton");
            Button resetBtn = (Button) view.lookup("#resetButton");
            Label timeLabel = (Label) view.lookup("#timeReadoutLabel");

            // Initial state
            assertEquals(0.0, controller.simulationTime(), 1e-9);
            assertFalse(controller.isPlaying());
            assertEquals("▶ PLAY", playPauseBtn.getText());

            // Seeking via slider
            slider.setValue(2.0);
            assertEquals(2.0, controller.simulationTime(), 1e-9);
            assertTrue(timeLabel.getText().contains("2.000 s"));

            // Step Forward
            stepForwardBtn.fire();
            assertEquals(2.1, controller.simulationTime(), 1e-6);

            // Step Backward
            stepBackBtn.fire();
            assertEquals(2.0, controller.simulationTime(), 1e-6);

            // Play/Pause toggle
            playPauseBtn.fire();
            assertTrue(controller.isPlaying());
            assertEquals("⏸ PAUSE", playPauseBtn.getText());

            // Reset
            resetBtn.fire();
            assertFalse(controller.isPlaying());
            assertEquals(0.0, controller.simulationTime(), 1e-9);
            assertEquals("▶ PLAY", playPauseBtn.getText());

            controller.dispose();
            return null;
        });
    }

    @Test
    @DisplayName("Return button fires return to Kinematics callback")
    void testReturnToKinematicsCallback() throws Exception {
        FxTestRuntime.runOnFxThread(() -> {
            RelativeMotionSimulationController controller = new RelativeMotionSimulationController();
            RelativeMotionWorkbenchView view = new RelativeMotionWorkbenchView(controller);

            AtomicBoolean called = new AtomicBoolean(false);
            view.setOnReturnToKinematics(() -> called.set(true));

            Button returnBtn = (Button) view.lookup("#returnToKinematicsButton");
            assertNotNull(returnBtn);
            returnBtn.fire();

            assertTrue(called.get(), "Return callback must be invoked");

            controller.dispose();
            return null;
        });
    }

    @Test
    @DisplayName("Full application shell navigation into Chapter 2 displays Relative Motion Workbench")
    void testShellNavigationIntoChapter2() throws Exception {
        FxTestRuntime.runOnFxThread(() -> {
            Stage stage = new Stage();
            PhysoraApplicationShell shell = new PhysoraApplicationShell();
            Scene scene = new Scene(shell, 1200, 780);
            stage.setScene(scene);
            stage.show();

            try {
                // Navigate to Chapter 2
                shell.selectChapter(2);
                assertEquals(2, shell.chapterRail().selectedChapter().number());

                // PlannedChapterView is the center node, hosting the relative motion workbench
                PlannedChapterView plannedView = shell.plannedChapterView();
                assertSame(plannedView, shell.getCenter());
                assertNotNull(plannedView.relativeMotionWorkbench());

                // Return to Kinematics button works
                Button returnBtn = (Button) shell.lookup("#returnToKinematicsButton");
                assertNotNull(returnBtn);
                returnBtn.fire();

                assertEquals(1, shell.chapterRail().selectedChapter().number());
                assertSame(shell.normalBenchView(), shell.getCenter());
            } finally {
                shell.dispose();
                stage.close();
            }
            return null;
        });
    }

    @Test
    @DisplayName("Live velocity triangle updates and maintains vector addition identity across frames")
    void testVelocityTriangleUpdates() throws Exception {
        FxTestRuntime.runOnFxThread(() -> {
            RelativeMotionSimulationController controller = new RelativeMotionSimulationController();
            RelativeMotionWorkbenchView view = new RelativeMotionWorkbenchView(controller);
            LiveVelocityTrianglePane trianglePane = (LiveVelocityTrianglePane) view.lookup("#liveVelocityTrianglePane");
            assertNotNull(trianglePane);

            TwoProjectilePursuitModel model = controller.model();

            // At t = 1.0 s
            controller.seek(1.0);
            Vector2D vA = model.velocityA(1.0);
            Vector2D vB = model.velocityB(1.0);
            Vector2D vBA = model.relativeVelocityBA(1.0);

            assertEquals(vB.x(), vA.x() + vBA.x(), 1e-12);
            assertEquals(vB.y(), vA.y() + vBA.y(), 1e-12);

            // At t = 2.5 s (collision)
            controller.seek(2.5);
            Vector2D vACol = model.velocityA(2.5);
            Vector2D vBCol = model.velocityB(2.5);
            Vector2D vBACol = model.relativeVelocityBA(2.5);

            assertEquals(vBCol.x(), vACol.x() + vBACol.x(), 1e-12);
            assertEquals(vBCol.y(), vACol.y() + vBACol.y(), 1e-12);

            // Relative velocity must be strictly identical at t=1.0 and t=2.5
            assertEquals(vBA.x(), vBACol.x(), 1e-12);
            assertEquals(vBA.y(), vBACol.y(), 1e-12);

            controller.dispose();
            return null;
        });
    }

    @Test
    @DisplayName("Preset selection loads corresponding model and updates analytical inspector")
    @SuppressWarnings("unchecked")
    void testScenarioPresetSwitching() throws Exception {
        FxTestRuntime.runOnFxThread(() -> {
            RelativeMotionSimulationController controller = new RelativeMotionSimulationController();
            RelativeMotionWorkbenchView view = new RelativeMotionWorkbenchView(controller);

            ComboBox<String> presetCombo = (ComboBox<String>) view.lookup("#scenarioPresetCombo");
            assertNotNull(presetCombo);

            // Select Near-Miss preset
            presetCombo.setValue("Near-Miss Pursuit (d_min = 2.00 m)");
            assertFalse(controller.model().hasCollision());
            assertEquals(2.0, controller.model().analyticalResult().dMinInterval(), 1e-9);

            // Select Angled Intercept preset
            presetCombo.setValue("Angled Intercept (Elevated Platform Launch)");
            assertTrue(controller.model().hasCollision());
            assertEquals(3.0, controller.model().collisionTime().getAsDouble(), 1e-9);

            controller.dispose();
            return null;
        });
    }
}
