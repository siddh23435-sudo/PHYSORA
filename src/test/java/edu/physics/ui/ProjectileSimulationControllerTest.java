package edu.physics.ui;

import edu.physics.projectile.ProjectileMotion;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.Locale;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

class ProjectileSimulationControllerTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        FxTestRuntime.ensureStarted();
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
    void realAnimationTimerAdvancesDisplayedTimeAndCanBeDisposed() throws Exception {
        ProjectileSimulationController controller = runOnFxThread(() -> {
            ProjectileSimulationController value = new ProjectileSimulationController();
            value.playbackSpeedProperty().set(2.0);
            value.play();
            return value;
        });
        Thread.sleep(250);
        runOnFxThread(() -> {
            assertTrue(controller.simulationTimeProperty().get() > 0.0,
                    "AnimationTimer pulses should advance display time");
            assertTrue(controller.simulationTimeProperty().get() < controller.model().timeOfFlight().orElseThrow());
            controller.dispose();
            assertFalse(controller.playingProperty().get());
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

    @Test
    void applicationStartsAndViewNavigationCanBeRepeated() throws Exception {
        withApplication((stage, shell, root) -> {
            assertTrue(stage.isShowing());
            assertNotNull(node(root, "launchSpeedSlider", Slider.class));
            assertNotNull(node(root, "initialXInput", TextField.class));
            assertNotNull(node(root, "initialYInput", TextField.class));
            assertNotNull(node(root, "landingYInput", TextField.class));
            assertTrue(node(root, "pauseButton", Button.class).isDisabled());

            ToggleButton simulation = node(root, "simulationViewTab", ToggleButton.class);
            ToggleButton logic = node(root, "logicViewTab", ToggleButton.class);
            ToggleButton ai = node(root, "aiProblemTab", ToggleButton.class);
            Node simulationView = node(root, "simulationView", Node.class);
            Node logicView = node(root, "logicView", Node.class);
            ai.fire();
            Node aiView = node(root, "aiProblemView", Node.class);
            assertNotNull(node(root, "aiQuestionInput", TextArea.class));
            assertNotNull(node(root, "aiSubmitButton", Button.class));
            assertTrue(aiView.isVisible());
            simulation.fire();
            for (int i = 0; i < 3; i++) {
                logic.fire();
                assertTrue(logicView.isVisible());
                assertFalse(simulationView.isManaged());
                simulation.fire();
                assertTrue(simulationView.isVisible());
                assertFalse(logicView.isManaged());
                ai.fire();
                assertTrue(aiView.isVisible());
                assertTrue(aiView.isManaged());
                assertNull(root.lookup("#playButton"));
                simulation.fire();
                assertTrue(simulationView.isVisible());
                assertNotNull(root.lookup("#playButton"));
            }
            return null;
        });
    }

    @Test
    void coordinateInputsUpdateTheModelAndRejectInvalidNumbersClearly() throws Exception {
        withApplication((stage, shell, root) -> {
            TextField initialX = node(root, "initialXInput", TextField.class);
            TextField initialY = node(root, "initialYInput", TextField.class);
            TextField landingY = node(root, "landingYInput", TextField.class);
            initialX.setText("3.5");
            initialY.setText("4");
            landingY.setText("2");

            ProjectileMotion configured = shell.controller().model();
            assertEquals(3.5, configured.initialX(), 0.0);
            assertEquals(4.0, configured.initialY(), 0.0);
            assertEquals(2.0, configured.landingY(), 0.0);
            assertEquals(4.0, configured.stateAt(0).y(), 0.0);
            String assumptions = node(root, "assumptionsOutput", Label.class).getText();
            assertTrue(assumptions.contains("Launch (3.50, 4.00) m"));
            assertTrue(assumptions.contains("Landing surface y = 2.00 m"));
            assertFalse(assumptions.contains("\\n"), "Assumption lines should render as separate lines");
            double flightTime = configured.timeOfFlight().orElseThrow();
            assertTrue(flightTime > 0.0);
            assertEquals(2.0, configured.stateAt(flightTime).y(), 1e-9);

            ProjectileMotion validModel = shell.controller().model();
            initialX.setText("NaN");
            assertSame(validModel, shell.controller().model());
            Label error = node(root, "initialXInputError", Label.class);
            assertTrue(error.isVisible());
            assertTrue(error.getText().contains("finite number"));
            node(root, "restoreDefaultsButton", Button.class).fire();
            assertEquals("0.00", initialX.getText());
            assertFalse(error.isVisible());
            initialX.setText("3.5");
            initialY.setText("4");
            landingY.setText("2");

            initialX.setText("5.25");
            assertEquals(5.25, shell.controller().model().initialX(), 0.0);
            assertFalse(error.isVisible());
            assertTrue(node(root, "positionOutput", Label.class).getText().contains("x 5.25 m"));

            node(root, "logicViewTab", ToggleButton.class).fire();
            String logicState = node(root, "logicCurrentState", Label.class).getText();
            assertTrue(logicState.contains("x 5.25 m"));
            assertTrue(logicState.contains("y 4.00 m"));
            String stepDetails = node(root, "logicStepDetails0", Label.class).getText();
            assertTrue(stepDetails.contains("Substitutions: "));
            assertTrue(stepDetails.contains("Intermediate results: "));
            assertTrue(stepDetails.contains("horizontalDisplacement = 0.00 m"));
            assertEquals(String.format(Locale.ROOT, "%.2f m",
                            shell.controller().model().horizontalRange().orElseThrow()),
                    node(root, "rangeOutput", Label.class).getText());
            return null;
        });
    }

    @Test
    void playbackScrubbingAndResetWorkThroughApplicationControls() throws Exception {
        withApplication((stage, shell, root) -> {
            Button play = node(root, "playButton", Button.class);
            Button pause = node(root, "pauseButton", Button.class);
            Button reset = node(root, "resetButton", Button.class);
            Slider timeline = node(root, "timeSlider", Slider.class);
            Slider rate = node(root, "playbackRateSlider", Slider.class);
            assertTrue(pause.isDisabled());
            play.fire();
            assertTrue(shell.controller().playingProperty().get());
            pause.fire();
            assertFalse(shell.controller().playingProperty().get());
            play.fire(); // resume after pause
            assertTrue(shell.controller().playingProperty().get());

            rate.setValue(2.0);
            assertEquals(2.0, shell.controller().playbackSpeedProperty().get(), 0.0);
            pause.fire();
            timeline.setValue(0.75);
            assertEquals(0.75, shell.controller().simulationTimeProperty().get(), 1e-12);

            node(root, "launchSpeedSlider", Slider.class).setValue(30.0);
            node(root, "initialXInput", TextField.class).setText("8");
            reset.fire();
            assertEquals(0.0, shell.controller().simulationTimeProperty().get(), 0.0);
            assertFalse(shell.controller().playingProperty().get());
            assertEquals(30.0, shell.controller().launchSpeedProperty().get(), 0.0);
            assertEquals(8.0, shell.controller().initialXProperty().get(), 0.0);
            assertEquals("8.00", node(root, "initialXInput", TextField.class).getText());

            Button restoreDefaults = node(root, "restoreDefaultsButton", Button.class);
            restoreDefaults.fire();
            assertEquals(ProjectileSimulationController.DEFAULT_LAUNCH_SPEED,
                    shell.controller().launchSpeedProperty().get(), 0.0);
            assertEquals(ProjectileSimulationController.DEFAULT_LAUNCH_ANGLE,
                    shell.controller().launchAngleProperty().get(), 0.0);
            assertEquals(ProjectileSimulationController.DEFAULT_GRAVITY,
                    shell.controller().gravityProperty().get(), 0.0);
            assertEquals(0.0, shell.controller().initialXProperty().get(), 0.0);
            assertEquals(0.0, shell.controller().initialYProperty().get(), 0.0);
            assertEquals(0.0, shell.controller().landingYProperty().get(), 0.0);
            assertEquals(1.0, shell.controller().playbackSpeedProperty().get(), 0.0);
            assertEquals(0.0, shell.controller().simulationTimeProperty().get(), 0.0);
            assertFalse(shell.controller().playingProperty().get());
            assertEquals("0.00", node(root, "initialXInput", TextField.class).getText());

            play.fire();
            assertTrue(shell.controller().playingProperty().get());
            stage.close();
            assertFalse(shell.controller().playingProperty().get());
            return null;
        });
    }

    @Test
    void capturesRealApplicationScenesForPhaseOneVisualAcceptance() throws Exception {
        Path output = Path.of("target", "phase1-visual-check");
        Files.createDirectories(output);
        withApplication((stage, shell, root) -> {
            capture(root, output.resolve("01-default-launch.png"));

            node(root, "initialXInput", TextField.class).setText("3");
            node(root, "initialYInput", TextField.class).setText("6");
            assertTrue(node(root, "positionOutput", Label.class).getText().contains("x 3.00 m   /   y 6.00 m"));
            capture(root, output.resolve("02-offset-launch-ground-landing.png"));

            node(root, "initialXInput", TextField.class).setText("0");
            node(root, "initialYInput", TextField.class).setText("0");
            node(root, "landingYInput", TextField.class).setText("5");
            node(root, "launchAngleSlider", Slider.class).setValue(60);
            ProjectileMotion higherPlatform = shell.controller().model();
            double higherLandingTime = higherPlatform.timeOfFlight().orElseThrow();
            assertTrue(higherLandingTime > higherPlatform.timeToApex().orElseThrow());
            assertEquals(5.0, higherPlatform.stateAt(higherLandingTime).y(), 1e-9);
            assertTrue(higherPlatform.stateAt(higherLandingTime).vy() < 0.0);
            assertEquals(String.format(Locale.ROOT, "%.2f s", higherLandingTime),
                    node(root, "flightOutput", Label.class).getText());
            assertEquals(String.format(Locale.ROOT, "%.2f m", higherPlatform.horizontalRange().orElseThrow()),
                    node(root, "rangeOutput", Label.class).getText());
            capture(root, output.resolve("03-higher-landing.png"));

            node(root, "initialYInput", TextField.class).setText("5");
            node(root, "landingYInput", TextField.class).setText("-3");
            node(root, "launchAngleSlider", Slider.class).setValue(40);
            ProjectileMotion lowerPlatform = shell.controller().model();
            double lowerLandingTime = lowerPlatform.timeOfFlight().orElseThrow();
            assertEquals(-3.0, lowerPlatform.stateAt(lowerLandingTime).y(), 1e-9);
            assertTrue(lowerPlatform.stateAt(lowerLandingTime).vy() < 0.0);
            assertEquals(String.format(Locale.ROOT, "%.2f s", lowerLandingTime),
                    node(root, "flightOutput", Label.class).getText());
            capture(root, output.resolve("04-lower-landing.png"));

            node(root, "launchSpeedSlider", Slider.class).setValue(15);
            node(root, "launchAngleSlider", Slider.class).setValue(30);
            capture(root, output.resolve("05-speed-and-angle-change.png"));

            node(root, "logicViewTab", ToggleButton.class).fire();
            assertTrue(node(root, "logicCurrentState", Label.class).getText().contains("y 5.00 m"));
            capture(root, output.resolve("06-logic-view.png"));
            node(root, "simulationViewTab", ToggleButton.class).fire();
            stage.setWidth(1120);
            stage.setHeight(700);
            root.resize(1120, 700);
            capture(root, output.resolve("07-minimum-window-size.png"));
            return null;
        });
        try (var images = Files.list(output)) {
            assertEquals(7, images.filter(path -> path.getFileName().toString().endsWith(".png")).count());
        }
    }

    @Test
    void restartAnimationPreservesParametersWhileRestoreDefaultsResetsThem() throws Exception {
        runOnFxThread(() -> {
            ProjectileSimulationController controller = new ProjectileSimulationController();
            controller.launchSpeedProperty().set(32.5);
            controller.launchAngleProperty().set(60.0);
            controller.gravityProperty().set(14.2);
            controller.initialXProperty().set(5.0);
            controller.initialYProperty().set(8.0);
            controller.landingYProperty().set(2.0);
            controller.playbackSpeedProperty().set(2.0);

            controller.play();
            controller.advanceByElapsedSeconds(0.8);
            assertTrue(controller.simulationTimeProperty().get() > 0.0);
            assertTrue(controller.playingProperty().get());

            // Non-destructive restart
            controller.restartAnimation();
            assertEquals(0.0, controller.simulationTimeProperty().get(), 0.0);
            assertFalse(controller.playingProperty().get());
            assertEquals(32.5, controller.launchSpeedProperty().get(), 0.0);
            assertEquals(60.0, controller.launchAngleProperty().get(), 0.0);
            assertEquals(14.2, controller.gravityProperty().get(), 0.0);
            assertEquals(5.0, controller.initialXProperty().get(), 0.0);
            assertEquals(8.0, controller.initialYProperty().get(), 0.0);
            assertEquals(2.0, controller.landingYProperty().get(), 0.0);
            assertEquals(2.0, controller.playbackSpeedProperty().get(), 0.0);
            assertEquals(8.0, controller.model().stateAt(0.0).y(), 0.0);

            // Restore defaults
            controller.restoreDefaults();
            assertEquals(ProjectileSimulationController.DEFAULT_LAUNCH_SPEED, controller.launchSpeedProperty().get(), 0.0);
            assertEquals(ProjectileSimulationController.DEFAULT_LAUNCH_ANGLE, controller.launchAngleProperty().get(), 0.0);
            assertEquals(ProjectileSimulationController.DEFAULT_GRAVITY, controller.gravityProperty().get(), 0.0);
            assertEquals(0.0, controller.initialXProperty().get(), 0.0);
            assertEquals(0.0, controller.initialYProperty().get(), 0.0);
            assertEquals(0.0, controller.landingYProperty().get(), 0.0);
            assertEquals(1.0, controller.playbackSpeedProperty().get(), 0.0);
            assertEquals(0.0, controller.simulationTimeProperty().get(), 0.0);
            assertFalse(controller.playingProperty().get());

            controller.dispose();
            return null;
        });
    }

    @Test
    void numericInputEnterAndFocusLossCommitWhileEscapeReverts() throws Exception {
        withApplication((stage, shell, root) -> {
            TextField initialX = node(root, "initialXInput", TextField.class);
            TextField initialY = node(root, "initialYInput", TextField.class);

            // 1. Enter commits when focused
            initialX.requestFocus();
            assertTrue(initialX.isFocused());
            initialX.setText("15.5");
            // Model should not be updated yet while user is typing in focused field
            assertEquals(0.0, shell.controller().initialXProperty().get(), 0.0);
            // Simulate pressing Enter
            initialX.fireEvent(new ActionEvent());
            assertEquals(15.5, shell.controller().initialXProperty().get(), 0.0);
            assertEquals("15.50", initialX.getText());

            // 2. Focus loss commits
            initialY.requestFocus();
            assertTrue(initialY.isFocused());
            initialY.setText("9.75");
            assertEquals(0.0, shell.controller().initialYProperty().get(), 0.0);
            // Switch focus away
            initialX.requestFocus();
            assertFalse(initialY.isFocused());
            assertEquals(9.75, shell.controller().initialYProperty().get(), 0.0);
            assertEquals("9.75", initialY.getText());

            // 3. Escape reverts to last valid property value
            initialY.requestFocus();
            initialY.setText("99.9");
            KeyEvent escEvent = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false);
            initialY.fireEvent(escEvent);
            assertEquals("9.75", initialY.getText());
            assertEquals(9.75, shell.controller().initialYProperty().get(), 0.0);

            // 4. Invalid input does not corrupt model property
            initialY.setText("not-a-number");
            initialY.fireEvent(new ActionEvent());
            assertEquals(9.75, shell.controller().initialYProperty().get(), 0.0);
            assertTrue(initialY.getStyleClass().contains("input-invalid"));

            return null;
        });
    }

    @Test
    void fitTrajectoryButtonTriggersRedrawWithoutThrowing() throws Exception {
        withApplication((stage, shell, root) -> {
            Button fitBtn = node(root, "fitTrajectoryButton", Button.class);
            assertNotNull(fitBtn);
            assertDoesNotThrow(fitBtn::fire);
            return null;
        });
    }

    private static void capture(Parent root, Path target) throws IOException {
        root.applyCss();
        root.layout();
        WritableImage image = root.snapshot(null, null);
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage pixels = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            }
        }
        ImageIO.write(pixels, "png", target.toFile());
    }

    private static <T extends Node> T node(Parent root, String id, Class<T> type) {
        Node found = root.lookup("#" + id);
        assertNotNull(found, "Missing JavaFX node with id " + id);
        return type.cast(found);
    }

    private static <T> T withApplication(ApplicationWork<T> work) throws Exception {
        return runOnFxThread(() -> {
            Stage stage = new Stage();
            ProjectileSimulatorApp app = new ProjectileSimulatorApp();
            app.start(stage);
            PhysoraApplicationShell shell = (PhysoraApplicationShell) stage.getScene().getRoot();
            try {
                return work.run(stage, shell, (Parent) stage.getScene().getRoot());
            } finally {
                if (stage.isShowing()) stage.close();
                shell.dispose();
            }
        });
    }

    @FunctionalInterface
    private interface ApplicationWork<T> {
        T run(Stage stage, PhysoraApplicationShell shell, Parent root) throws Exception;
    }

    private static <T> T runOnFxThread(Callable<T> work) throws Exception {
        return FxTestRuntime.runOnFxThread(work);
    }
}
