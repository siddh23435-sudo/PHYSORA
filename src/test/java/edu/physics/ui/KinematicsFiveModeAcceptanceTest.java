package edu.physics.ui;

import edu.physics.ai.ProviderMode;
import edu.physics.problem.PredictionChallenge;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kinematics Five-Mode Comprehensive Acceptance Audit")
class KinematicsFiveModeAcceptanceTest {

    private static final String ARTIFACT_DIR =
            "C:/Users/Siddhant Gangwal/.gemini/antigravity/brain/77ba2716-d4c5-462f-8fcc-a4f036a0efc2/screenshots";
    private static final String ROOT_ARTIFACT_DIR =
            "C:/Users/Siddhant Gangwal/.gemini/antigravity/brain/77ba2716-d4c5-462f-8fcc-a4f036a0efc2";

    private static final AtomicReference<Stage> stageRef = new AtomicReference<>();

    @BeforeAll
    static void setup() throws Exception {
        FxTestRuntime.ensureStarted();
        Files.createDirectories(Paths.get(ARTIFACT_DIR));
        Files.createDirectories(Paths.get(ROOT_ARTIFACT_DIR));
    }

    @AfterAll
    static void tearDown() throws Exception {
        runOnFxThread(() -> {
            Stage stage = stageRef.get();
            if (stage != null) {
                stage.close();
            }
            return null;
        });
    }

    private static <T> T runOnFxThread(Callable<T> work) throws Exception {
        return FxTestRuntime.runOnFxThread(work);
    }

    private static BufferedImage capture(Scene scene) throws Exception {
        return runOnFxThread(() -> {
            WritableImage image = scene.snapshot(null);
            int width = (int) image.getWidth();
            int height = (int) image.getHeight();
            BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            PixelReader reader = image.getPixelReader();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    bufferedImage.setRGB(x, y, reader.getArgb(x, y));
                }
            }
            return bufferedImage;
        });
    }

    private static void saveSnapshot(BufferedImage image, String filename) throws IOException {
        File subDirTarget = new File(ARTIFACT_DIR, filename);
        ImageIO.write(image, "png", subDirTarget);
        File rootTarget = new File(ROOT_ARTIFACT_DIR, filename);
        Files.copy(subDirTarget.toPath(), rootTarget.toPath(), StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Saved screenshot: " + filename);
    }

    private static void waitUntilFx(Callable<Boolean> condition) throws Exception {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadlineNanos) {
            if (Boolean.TRUE.equals(runOnFxThread(condition))) {
                return;
            }
            Thread.sleep(30);
        }
        fail("Timed out waiting for JavaFX condition");
    }

    @SuppressWarnings("unchecked")
    private static <T extends Node> T node(Node root, String id, Class<T> type) {
        Node found = root.lookup("#" + id);
        assertNotNull(found, "Node with id #" + id + " must be present in scene graph");
        assertTrue(type.isInstance(found), "Node #" + id + " must be of type " + type.getSimpleName());
        return (T) found;
    }

    @Test
    @DisplayName("Audit all five modes at 1600x900 desktop resolution")
    void auditAllFiveModesDesktop1600x900() throws Exception {
        AtomicReference<PhysoraApplicationShell> shellRef = new AtomicReference<>();
        AtomicReference<Scene> sceneRef = new AtomicReference<>();

        runOnFxThread(() -> {
            PhysoraApplicationShell shell = new PhysoraApplicationShell();
            shellRef.set(shell);

            Scene scene = new Scene(shell, 1600, 900);
            var css = getClass().getResource("/edu/physics/ui/physora.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }
            sceneRef.set(scene);

            Stage stage = new Stage();
            stage.setScene(scene);
            stage.setTitle("PHYSORA — Kinematics Five-Mode Acceptance Audit (1600x900)");
            stage.show();
            stageRef.set(stage);
            return null;
        });

        PhysoraApplicationShell shell = shellRef.get();
        Scene scene = sceneRef.get();

        // Ensure Chapter 1 is active
        runOnFxThread(() -> {
            shell.selectChapter(1);
            return null;
        });

        Thread.sleep(150);

        // ==========================================
        // MODE 1: EXPERIMENT (1600x900)
        // ==========================================
        runOnFxThread(() -> {
            ToggleButton expTab = node(shell, "simulationViewTab", ToggleButton.class);
            expTab.setSelected(true);
            expTab.getOnAction().handle(null);

            // Configure physics parameters: v0 = 20 m/s, theta = 45 deg, g = 9.81 m/s^2
            shell.controller().launchSpeedProperty().set(20.0);
            shell.controller().launchAngleProperty().set(45.0);
            shell.controller().gravityProperty().set(9.81);
            shell.controller().initialXProperty().set(0.0);
            shell.controller().initialYProperty().set(0.0);
            shell.controller().landingYProperty().set(0.0);

            // Step to apex for visual demonstration of vectors
            shell.controller().seekToApex();
            return null;
        });

        Thread.sleep(150);
        saveSnapshot(capture(scene), "kinematics_mode1_experiment_1600x900.png");

        // Verify Experiment calculations
        runOnFxThread(() -> {
            double v0 = shell.controller().launchSpeedProperty().get();
            double angle = shell.controller().launchAngleProperty().get();
            double g = shell.controller().gravityProperty().get();
            assertEquals(20.0, v0, 1e-6);
            assertEquals(45.0, angle, 1e-6);
            assertEquals(9.81, g, 1e-6);

            // H_max = (v0 sin theta)^2 / (2g) = (20 * sin45)^2 / (2 * 9.81) = 200 / 19.62 = 10.1936 m
            double expectedHMax = Math.pow(v0 * Math.sin(Math.toRadians(angle)), 2) / (2 * g);
            assertTrue(shell.controller().model().maximumHeight().isPresent());
            assertEquals(expectedHMax, shell.controller().model().maximumHeight().getAsDouble(), 1e-3);

            // Range = v0^2 sin(2*theta) / g = 400 / 9.81 = 40.7747 m
            double expectedRange = Math.pow(v0, 2) * Math.sin(Math.toRadians(2 * angle)) / g;
            assertTrue(shell.controller().model().horizontalRange().isPresent());
            assertEquals(expectedRange, shell.controller().model().horizontalRange().getAsDouble(), 1e-3);
            return null;
        });

        // ==========================================
        // MODE 2: PREDICT (1600x900)
        // ==========================================
        runOnFxThread(() -> {
            ToggleButton predictTab = node(shell, "predictTab", ToggleButton.class);
            predictTab.setSelected(true);
            predictTab.getOnAction().handle(null);

            // Formulate hypothesis
            TextField hField = node(shell, "predictMaxHeightField", TextField.class);
            TextField tField = node(shell, "predictFlightTimeField", TextField.class);
            TextField rField = node(shell, "predictRangeField", TextField.class);
            hField.setText("10.0");
            tField.setText("2.9");
            rField.setText("41.0");

            Button lockButton = node(shell, "lockPredictionsButton", Button.class);
            lockButton.fire();
            return null;
        });

        Thread.sleep(150);
        saveSnapshot(capture(scene), "kinematics_mode2_predict_locked_1600x900.png");

        // Reveal & Evaluate
        runOnFxThread(() -> {
            // Finish trajectory touchdown to unlock evaluation
            shell.controller().seekToLanding();
            Button evalButton = node(shell, "evaluatePredictionsButton", Button.class);
            evalButton.fire();
            return null;
        });

        Thread.sleep(150);
        saveSnapshot(capture(scene), "kinematics_mode2_predict_evaluated_1600x900.png");

        // ==========================================
        // MODE 3: COMPARE (1600x900)
        // ==========================================
        runOnFxThread(() -> {
            ToggleButton compareTab = node(shell, "compareTab", ToggleButton.class);
            compareTab.setSelected(true);
            compareTab.getOnAction().handle(null);

            Button demoButton = node(shell, "loadComplementaryDemoButton", Button.class);
            demoButton.fire();
            return null;
        });

        Thread.sleep(150);
        saveSnapshot(capture(scene), "kinematics_mode3_compare_1600x900.png");

        // ==========================================
        // MODE 4: UNDERSTAND (1600x900)
        // ==========================================
        runOnFxThread(() -> {
            ToggleButton logicTab = node(shell, "logicViewTab", ToggleButton.class);
            logicTab.setSelected(true);
            logicTab.getOnAction().handle(null);
            return null;
        });

        Thread.sleep(150);
        saveSnapshot(capture(scene), "kinematics_mode4_understand_1600x900.png");

        // ==========================================
        // MODE 5: ASK AI (1600x900)
        // ==========================================
        runOnFxThread(() -> {
            ToggleButton aiTab = node(shell, "aiProblemTab", ToggleButton.class);
            aiTab.setSelected(true);
            aiTab.getOnAction().handle(null);

            @SuppressWarnings("unchecked")
            ComboBox<ProviderMode> modeSelector = (ComboBox<ProviderMode>) shell.lookup("#aiModeSelector");
            modeSelector.setValue(ProviderMode.OFFLINE);

            TextArea qArea = node(shell, "aiQuestionInput", TextArea.class);
            qArea.setText("A ball is kicked from level ground with an initial speed of 20 m/s at an angle of 30\u00b0 above the horizontal. Taking gravity (g) as 10 m/s\u00b2, what is the maximum height the ball reaches?");

            Button submit = node(shell, "aiSubmitButton", Button.class);
            submit.fire();
            return null;
        });

        waitUntilFx(() -> {
            Button submit = (Button) shell.lookup("#aiSubmitButton");
            Label status = (Label) shell.lookup("#aiStatus");
            return submit != null && !submit.isDisabled()
                    && status != null && "Interpretation, deterministic solution, and explanation passed validation.".equals(status.getText());
        });

        Thread.sleep(200);
        saveSnapshot(capture(scene), "kinematics_mode5_ask_ai_1600x900.png");
    }

    @Test
    @DisplayName("Audit responsive layouts across 1366x768 desktop resolution")
    void auditResponsiveLayouts1366x768() throws Exception {
        AtomicReference<PhysoraApplicationShell> shellRef = new AtomicReference<>();
        AtomicReference<Scene> sceneRef = new AtomicReference<>();

        runOnFxThread(() -> {
            PhysoraApplicationShell shell = new PhysoraApplicationShell();
            shellRef.set(shell);

            Scene scene = new Scene(shell, 1366, 768);
            var css = getClass().getResource("/edu/physics/ui/physora.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }
            sceneRef.set(scene);

            Stage stage = new Stage();
            stage.setScene(scene);
            stage.setTitle("PHYSORA — Kinematics Responsive Audit (1366x768)");
            stage.show();
            stageRef.set(stage);
            return null;
        });

        PhysoraApplicationShell shell = shellRef.get();
        Scene scene = sceneRef.get();

        runOnFxThread(() -> {
            shell.selectChapter(1);
            return null;
        });

        Thread.sleep(150);

        // 1. Experiment at 1366x768
        runOnFxThread(() -> {
            ToggleButton expTab = node(shell, "simulationViewTab", ToggleButton.class);
            expTab.setSelected(true);
            expTab.getOnAction().handle(null);
            shell.controller().seekToApex();
            return null;
        });
        Thread.sleep(100);
        saveSnapshot(capture(scene), "kinematics_mode1_experiment_1366x768.png");

        // 2. Predict at 1366x768
        runOnFxThread(() -> {
            ToggleButton predictTab = node(shell, "predictTab", ToggleButton.class);
            predictTab.setSelected(true);
            predictTab.getOnAction().handle(null);
            return null;
        });
        Thread.sleep(100);
        saveSnapshot(capture(scene), "kinematics_mode2_predict_1366x768.png");

        // 3. Compare at 1366x768
        runOnFxThread(() -> {
            ToggleButton compareTab = node(shell, "compareTab", ToggleButton.class);
            compareTab.setSelected(true);
            compareTab.getOnAction().handle(null);
            Button demo = node(shell, "loadComplementaryDemoButton", Button.class);
            demo.fire();
            return null;
        });
        Thread.sleep(100);
        saveSnapshot(capture(scene), "kinematics_mode3_compare_1366x768.png");

        // 4. Understand at 1366x768
        runOnFxThread(() -> {
            ToggleButton logicTab = node(shell, "logicViewTab", ToggleButton.class);
            logicTab.setSelected(true);
            logicTab.getOnAction().handle(null);
            return null;
        });
        Thread.sleep(100);
        saveSnapshot(capture(scene), "kinematics_mode4_understand_1366x768.png");

        // 5. Ask AI at 1366x768
        runOnFxThread(() -> {
            ToggleButton aiTab = node(shell, "aiProblemTab", ToggleButton.class);
            aiTab.setSelected(true);
            aiTab.getOnAction().handle(null);
            return null;
        });
        Thread.sleep(100);
        saveSnapshot(capture(scene), "kinematics_mode5_ask_ai_1366x768.png");
    }
}

