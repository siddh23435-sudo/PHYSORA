package edu.physics.ui;

import edu.physics.projectile.ProjectileMotion;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ProjectileSimulationControllerTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        assertTrue(started.await(10, TimeUnit.SECONDS), "JavaFX toolkit should start");
        Platform.setImplicitExit(false);
    }

    @AfterAll
    static void stopJavaFx() throws Exception {
        runOnFxThread(() -> {
            Platform.exit();
            return null;
        });
    }

    @Test
    void playbackRateScalesClockButDoesNotChangeTheAnalyticalModel() throws Exception {
        runOnFxThread(() -> {
            ProjectileSimulationController controller = new ProjectileSimulationController();
            ProjectileMotion originalModel = controller.model();
            controller.playbackSpeedProperty().set(2.0);
            controller.advanceByElapsedSeconds(0.05);

            assertEquals(0.1, controller.simulationTimeProperty().get(), 1e-12);
            assertSame(originalModel, controller.model());
            assertEquals(originalModel.stateAt(0.1), controller.model().stateAt(controller.simulationTimeProperty().get()));
            controller.dispose();
            return null;
        });
    }

    @Test
    void frameStallsAreCappedAndInvalidElapsedValuesAreRejected() throws Exception {
        runOnFxThread(() -> {
            ProjectileSimulationController controller = new ProjectileSimulationController();
            controller.advanceByElapsedSeconds(10.0);
            assertEquals(0.1, controller.simulationTimeProperty().get(), 1e-12);
            assertThrows(IllegalArgumentException.class, () -> controller.advanceByElapsedSeconds(Double.NaN));
            assertThrows(IllegalArgumentException.class, () -> controller.advanceByElapsedSeconds(-0.1));
            controller.dispose();
            return null;
        });
    }

    @Test
    void parameterChangesPauseAndRebuildWhileResetRestoresLaunchState() throws Exception {
        runOnFxThread(() -> {
            ProjectileSimulationController controller = new ProjectileSimulationController();
            ProjectileMotion oldModel = controller.model();
            controller.play();
            assertTrue(controller.playingProperty().get());
            controller.simulationTimeProperty().set(0.6);
            controller.gravityProperty().set(12.0);
            assertFalse(controller.playingProperty().get());
            assertEquals(0.0, controller.simulationTimeProperty().get(), 0.0);
            assertNotSame(oldModel, controller.model());
            assertEquals(12.0, controller.model().gravity(), 0.0);

            controller.seek(0.7);
            controller.reset();
            assertEquals(0.0, controller.simulationTimeProperty().get(), 0.0);
            assertFalse(controller.playingProperty().get());
            controller.dispose();
            return null;
        });
    }

    @Test
    void completionStopsAtLandingAndPlayRestartsFromTheLaunch() throws Exception {
        runOnFxThread(() -> {
            ProjectileSimulationController controller = new ProjectileSimulationController();
            controller.launchSpeedProperty().set(0.1);
            controller.launchAngleProperty().set(90.0);
            controller.gravityProperty().set(20.0);
            double flightTime = controller.model().timeOfFlight().orElseThrow();
            controller.play();
            controller.advanceByElapsedSeconds(0.1);
            assertEquals(flightTime, controller.simulationTimeProperty().get(), 1e-12);
            assertFalse(controller.playingProperty().get());

            controller.play();
            assertTrue(controller.playingProperty().get());
            assertEquals(0.0, controller.simulationTimeProperty().get(), 0.0);
            controller.dispose();
            return null;
        });
    }

    private static <T> T runOnFxThread(Callable<T> work) throws Exception {
        if (Platform.isFxApplicationThread()) return work.call();
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
