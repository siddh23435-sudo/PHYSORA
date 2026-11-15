package edu.physics.ui;

import edu.physics.relative.RainManSimulationModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RainManSimulationController - Playback Lifecycle & Time Management Tests")
class RainManSimulationControllerTest {

    @BeforeAll
    static void initJavaFx() {
        try {
            javafx.application.Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // JavaFX runtime already started
        }
    }

    @Test
    @DisplayName("Initial controller state has zero time, paused, 1.0x playback speed, and default model")
    void testInitialState() {
        RainManSimulationController controller = new RainManSimulationController();

        assertEquals(0.0, controller.simulationTime(), 1e-9);
        assertFalse(controller.isPlaying());
        assertEquals(1.0, controller.playbackSpeed(), 1e-9);
        assertNotNull(controller.model());
        assertEquals(30.0, controller.maxTime(), 1e-9);

        controller.dispose();
    }

    @Test
    @DisplayName("Play, pause, and toggle transitions update isPlaying property")
    void testPlayPauseTransitions() {
        RainManSimulationController controller = new RainManSimulationController();

        controller.play();
        assertTrue(controller.isPlaying());

        controller.pause();
        assertFalse(controller.isPlaying());

        controller.togglePlayPause();
        assertTrue(controller.isPlaying());

        controller.togglePlayPause();
        assertFalse(controller.isPlaying());

        controller.dispose();
    }

    @Test
    @DisplayName("Seek and step forward/backward update simulation time correctly clamped")
    void testSeekingAndStepping() {
        RainManSimulationController controller = new RainManSimulationController();

        controller.seek(5.5);
        assertEquals(5.5, controller.simulationTime(), 1e-9);

        controller.stepForward(1.2);
        assertEquals(6.7, controller.simulationTime(), 1e-9);
        assertFalse(controller.isPlaying(), "Stepping must pause playback");

        controller.stepBackward(2.0);
        assertEquals(4.7, controller.simulationTime(), 1e-9);

        // Clamping to [0, maxTime]
        controller.seek(-10.0);
        assertEquals(0.0, controller.simulationTime(), 1e-9);

        controller.seek(100.0);
        assertEquals(controller.maxTime(), controller.simulationTime(), 1e-9);

        controller.dispose();
    }

    @Test
    @DisplayName("Reset resets simulation time to 0.0 and pauses playback")
    void testReset() {
        RainManSimulationController controller = new RainManSimulationController();
        controller.seek(12.0);
        controller.play();

        controller.reset();
        assertEquals(0.0, controller.simulationTime(), 1e-9);
        assertFalse(controller.isPlaying());

        controller.dispose();
    }

    @Test
    @DisplayName("Updating model preserving time keeps simulation time intact")
    void testUpdateModelPreservingTime() {
        RainManSimulationController controller = new RainManSimulationController();
        controller.seek(8.5);

        RainManSimulationModel newModel = RainManSimulationModel.presetBackwardWalking();
        controller.updateModelPreservingTime(newModel);

        assertEquals(8.5, controller.simulationTime(), 1e-9);
        assertEquals(-4.0, controller.model().vPerson(), 1e-9);

        controller.dispose();
    }

    @Test
    @DisplayName("Playback speed validation rejects non-positive and non-finite values")
    void testPlaybackSpeedValidation() {
        RainManSimulationController controller = new RainManSimulationController();

        assertThrows(IllegalArgumentException.class, () -> controller.setPlaybackSpeed(0.0));
        assertThrows(IllegalArgumentException.class, () -> controller.setPlaybackSpeed(-1.5));
        assertThrows(IllegalArgumentException.class, () -> controller.setPlaybackSpeed(Double.NaN));

        controller.setPlaybackSpeed(2.0);
        assertEquals(2.0, controller.playbackSpeed(), 1e-9);

        controller.dispose();
    }
}
