package edu.physics.ui;

import edu.physics.problem.PhysicsCapabilityRegistry;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RainManWorkbenchView - UI Controls, Alignment Feedback & Navigation Tests")
class RainManWorkbenchViewTest {

    @BeforeAll
    static void initJavaFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Already started
        }
    }

    private void runOnFxThread(Runnable action) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX operation timed out");
    }

    @Test
    @DisplayName("Workbench constructs with all controls and initial default readouts")
    void testWorkbenchConstruction() throws InterruptedException {
        runOnFxThread(() -> {
            RainManSimulationController controller = new RainManSimulationController();
            RainManWorkbenchView view = new RainManWorkbenchView(controller);

            assertNotNull(view.lookup("#rainManWorkbenchView"));
            assertNotNull(view.lookup("#scenarioPresetCombo"));
            assertNotNull(view.lookup("#vDownInput"));
            assertNotNull(view.lookup("#vRainXInput"));
            assertNotNull(view.lookup("#vPersonInput"));
            assertNotNull(view.lookup("#umbrellaAngleInput"));
            assertNotNull(view.lookup("#snapOptimalButton"));
            assertNotNull(view.lookup("#alignmentStatusBadge"));
            assertNotNull(view.lookup("#valOptimalAngle"));
            assertNotNull(view.lookup("#valActualAngle"));
            assertNotNull(view.lookup("#valAngularError"));
            assertNotNull(view.lookup("#valApparentSpeed"));
            assertNotNull(view.lookup("#valApparentVelocity"));
            assertNotNull(view.lookup("#rainManVelocityPane"));
            assertNotNull(view.lookup("#playPauseButton"));
            assertNotNull(view.lookup("#resetButton"));
            assertNotNull(view.lookup("#timeSlider"));

            Label badge = (Label) view.lookup("#alignmentStatusBadge");
            assertTrue(badge.getText().contains("MISALIGNED"), "Initial default angle (0 deg) should be misaligned");

            view.dispose();
        });
    }

    @Test
    @DisplayName("Snap to optimal button aligns umbrella and updates alignment status to WELL_ALIGNED")
    void testSnapToOptimalButton() throws InterruptedException {
        runOnFxThread(() -> {
            RainManSimulationController controller = new RainManSimulationController();
            RainManWorkbenchView view = new RainManWorkbenchView(controller);

            Button snapBtn = (Button) view.lookup("#snapOptimalButton");
            snapBtn.fire();

            Label badge = (Label) view.lookup("#alignmentStatusBadge");
            assertTrue(badge.getText().contains("WELL ALIGNED"), "After snap, status must be WELL ALIGNED");

            Label errLabel = (Label) view.lookup("#valAngularError");
            assertEquals("0.0°", errLabel.getText().trim());

            assertEquals(controller.model().optimalAngleRad(), controller.model().umbrellaAngleRad(), 1e-6);

            view.dispose();
        });
    }

    @Test
    @DisplayName("Changing numeric inputs updates simulation model and analytical readouts")
    void testSliderInteractions() throws InterruptedException {
        runOnFxThread(() -> {
            RainManSimulationController controller = new RainManSimulationController();
            RainManWorkbenchView view = new RainManWorkbenchView(controller);

            TextField vDownInput = (TextField) view.lookup("#vDownInput");
            TextField vPersonInput = (TextField) view.lookup("#vPersonInput");

            vDownInput.setText("10.0");
            vPersonInput.setText("0.0"); // Stationary person

            assertEquals(10.0, controller.model().vDown(), 1e-6);
            assertEquals(0.0, controller.model().vPerson(), 1e-6);

            Label optLabel = (Label) view.lookup("#valOptimalAngle");
            assertEquals("+0.0°", optLabel.getText().trim());

            Label speedLabel = (Label) view.lookup("#valApparentSpeed");
            assertEquals("10.00 m/s", speedLabel.getText().trim());

            view.dispose();
        });
    }

    @Test
    @DisplayName("Selecting scenario presets updates model and controls")
    void testPresetSelection() throws InterruptedException {
        runOnFxThread(() -> {
            RainManSimulationController controller = new RainManSimulationController();
            RainManWorkbenchView view = new RainManWorkbenchView(controller);

            @SuppressWarnings("unchecked")
            ComboBox<String> combo = (ComboBox<String>) view.lookup("#scenarioPresetCombo");

            combo.setValue("Tailwind Equal Speed (vrx = vm, Rain appears vertical)");
            assertEquals(4.0, controller.model().vRainX(), 1e-6);
            assertEquals(4.0, controller.model().vPerson(), 1e-6);
            assertEquals(0.0, controller.model().optimalAngleRad(), 1e-6);

            combo.setValue("Backward Walking (vm < 0)");
            assertEquals(-4.0, controller.model().vPerson(), 1e-6);
            assertTrue(controller.model().optimalAngleRad() < 0.0);

            view.dispose();
        });
    }

    @Test
    @DisplayName("PlannedChapterView hosts all three Chapter 02 experiments and switches seamlessly with mutual pausing")
    void testThreeExperimentsSwitchingInPlannedChapterView() throws InterruptedException {
        runOnFxThread(() -> {
            PlannedChapterView plannedView = new PlannedChapterView();
            plannedView.setChapter(PhysicsCapabilityRegistry.findChapter(2).orElseThrow());

            // Initial active experiment: Exp 2.1 (Two-Projectile)
            assertNotNull(plannedView.lookup("#btnExpTwoProjectile"));
            assertNotNull(plannedView.lookup("#btnExpRiverBoat"));
            assertNotNull(plannedView.lookup("#btnExpRainMan"));

            // Switch to Experiment 2.2 (River-Boat)
            Button btnRiver = (Button) plannedView.lookup("#btnExpRiverBoat");
            btnRiver.fire();
            assertTrue(plannedView.riverBoatWorkbench().isVisible());

            // Switch to Experiment 2.3 (Rain-Man)
            Button btnRain = (Button) plannedView.lookup("#btnExpRainMan");
            btnRain.fire();
            assertTrue(plannedView.rainManWorkbench().isVisible());

            // Play Rain-Man simulation
            plannedView.rainManWorkbench().controller().play();
            assertTrue(plannedView.rainManWorkbench().controller().isPlaying());

            // Switch back to Exp 2.1: Rain-Man must be paused
            Button btnTwoProj = (Button) plannedView.lookup("#btnExpTwoProjectile");
            btnTwoProj.fire();
            assertFalse(plannedView.rainManWorkbench().controller().isPlaying(),
                    "Switching away from Rain-Man must pause its simulation");

            // Switch back to Exp 2.3
            btnRain.fire();
            assertTrue(plannedView.rainManWorkbench().isVisible());

            plannedView.dispose();
        });
    }
}
