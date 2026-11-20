package edu.physics.ui;

import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SimulationLifecycleTest - Reciprocal Chapter Switching & Simulation Pausing Tests")
class SimulationLifecycleTest {

    @BeforeAll
    static void initFx() throws Exception {
        FxTestRuntime.ensureStarted();
    }

    private static <T> T runOnFxThread(Callable<T> work) throws Exception {
        return FxTestRuntime.runOnFxThread(work);
    }

    @FunctionalInterface
    private interface ApplicationAction<T> {
        T run(Stage stage, PhysoraApplicationShell shell) throws Exception;
    }

    private static <T> T withApplication(ApplicationAction<T> action) throws Exception {
        return runOnFxThread(() -> {
            Stage stage = new Stage();
            PhysoraApplicationShell shell = new PhysoraApplicationShell();
            Scene scene = new Scene(shell, 1200, 780);
            stage.setScene(scene);
            stage.show();
            try {
                return action.run(stage, shell);
            } finally {
                shell.dispose();
                stage.close();
            }
        });
    }

    @Test
    @DisplayName("Switching from Chapter 1 to Chapter 2 reciprocally pauses running kinematics simulation")
    void testSwitchingFromKinematicsToRelativeMotionPausesKinematics() throws Exception {
        withApplication((stage, shell) -> {
            ProjectileSimulationController kController = shell.controller();
            kController.play();
            assertTrue(kController.playingProperty().get(), "Kinematics simulation should be playing");

            // Switch to Chapter 2: Relative Motion
            shell.chapterRail().selectChapter(2);

            // Kinematics simulation must be automatically paused
            assertFalse(kController.playingProperty().get(),
                    "Kinematics simulation should be paused when navigating to Chapter 2");
            return null;
        });
    }

    @Test
    @DisplayName("Switching from Chapter 2 to Chapter 1 reciprocally pauses running Relative Motion simulations")
    void testSwitchingFromRelativeMotionToKinematicsPausesRelativeMotion() throws Exception {
        withApplication((stage, shell) -> {
            // First select Chapter 2
            shell.chapterRail().selectChapter(2);
            PlannedChapterView plannedView = shell.plannedChapterView();
            RelativeMotionWorkbenchView relWorkbench = plannedView.relativeMotionWorkbench();

            // Start Two-Projectile simulation running
            relWorkbench.controller().play();
            assertTrue(relWorkbench.controller().isPlaying(), "Relative motion simulation should be playing");

            // Navigate back to Chapter 1: Kinematics
            shell.chapterRail().selectChapter(1);

            // Relative motion simulation must be paused
            assertFalse(relWorkbench.controller().isPlaying(),
                    "Relative motion simulation should be paused when returning to Chapter 1");
            return null;
        });
    }

    @Test
    @DisplayName("PlannedChapterView.pauseAllSimulations() reliably pauses all Chapter 2 controllers")
    void testPlannedChapterPauseAllSimulations() throws Exception {
        runOnFxThread(() -> {
            PlannedChapterView view = new PlannedChapterView();
            view.relativeMotionWorkbench().controller().play();
            view.riverBoatWorkbench().controller().play();
            view.rainManWorkbench().controller().play();

            assertTrue(view.relativeMotionWorkbench().controller().isPlaying());
            assertTrue(view.riverBoatWorkbench().controller().isPlaying());
            assertTrue(view.rainManWorkbench().controller().isPlaying());

            view.pauseAllSimulations();

            assertFalse(view.relativeMotionWorkbench().controller().isPlaying());
            assertFalse(view.riverBoatWorkbench().controller().isPlaying());
            assertFalse(view.rainManWorkbench().controller().isPlaying());

            view.dispose();
            return null;
        });
    }
}

