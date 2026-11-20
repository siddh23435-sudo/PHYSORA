package edu.physics.ui;

import edu.physics.relative.RiverBoatSimulationModel;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Milestone 2.3 Visual Demonstration & Screenshot Capture")
class Milestone23ScreenshotCaptureTest {

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

    private static BufferedImage captureNode(Node node) throws Exception {
        return runOnFxThread(() -> {
            WritableImage image = node.snapshot(null, null);
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
    }

    private static void waitFrames(int count) throws Exception {
        for (int i = 0; i < count; i++) {
            Thread.sleep(30);
            runOnFxThread(() -> null);
        }
    }

    @Test
    @DisplayName("Capture all River-Boat scenarios, live vector additions, and shell integration")
    void captureRiverBoatDemonstrationEvidence() throws Exception {
        RiverBoatSimulationController controller = new RiverBoatSimulationController();
        controller.setModel(RiverBoatSimulationModel.presetShortestPathOrMinDrift(100.0, 3.0, 5.0));

        AtomicReference<RiverBoatWorkbenchView> workbenchRef = new AtomicReference<>();
        AtomicReference<Scene> sceneRef = new AtomicReference<>();

        runOnFxThread(() -> {
            RiverBoatWorkbenchView workbench = new RiverBoatWorkbenchView(controller);
            workbenchRef.set(workbench);

            Scene scene = new Scene(workbench, 1340, 780);
            sceneRef.set(scene);

            Stage stage = new Stage();
            stage.setScene(scene);
            stage.setTitle("PHYSORA - River-Boat Vector Workbench");
            stage.show();
            stageRef.set(stage);
            return null;
        });

        waitFrames(10);

        // 1. Snapshot: Shortest Path / Zero Drift (vb > u, t = 12.5 s)
        runOnFxThread(() -> {
            controller.seek(12.5);
            return null;
        });
        waitFrames(5);
        saveSnapshot(capture(sceneRef.get()), "milestone23_01_shortest_path_zero_drift.png");

        // 2. Snapshot: Shortest Time Crossing (theta = 0, t = 10.0 s)
        runOnFxThread(() -> {
            ComboBox<String> combo = workbenchRef.get().presetCombo();
            combo.setValue("Shortest Time (θ = 0°, t_min = w/vb)");
            controller.seek(10.0);
            return null;
        });
        waitFrames(5);
        saveSnapshot(capture(sceneRef.get()), "milestone23_02_shortest_time_crossing.png");

        // 3. Snapshot: Minimum Drift Condition (vb < u, t = 20.0 s)
        runOnFxThread(() -> {
            ComboBox<String> combo = workbenchRef.get().presetCombo();
            combo.setValue("Minimum Drift Condition (vb < u, sin θ = vb/u)");
            controller.seek(20.0);
            return null;
        });
        waitFrames(5);
        saveSnapshot(capture(sceneRef.get()), "milestone23_03_minimum_drift_condition.png");

        // 4. Snapshot: Equal Speed Asymptotic Boundary (vb = u, stationary upstream hold)
        runOnFxThread(() -> {
            ComboBox<String> combo = workbenchRef.get().presetCombo();
            combo.setValue("Equal Speed Boundary (vb = u, Asymptotic Limit)");
            controller.seek(15.0);
            return null;
        });
        waitFrames(5);
        saveSnapshot(capture(sceneRef.get()), "milestone23_04_equal_speed_asymptotic_hold.png");

        // 5. Snapshot: Close-up of Dynamic Velocity Triangle Pane
        runOnFxThread(() -> {
            ComboBox<String> combo = workbenchRef.get().presetCombo();
            combo.setValue("Zero Drift / Shortest Path (vb > u, sin θ = u/vb)");
            return null;
        });
        waitFrames(5);
        Node velPane = workbenchRef.get().lookup("#riverBoatVelocityPane");
        assertNotNull(velPane);
        saveSnapshot(captureNode(velPane), "milestone23_05_live_velocity_triangle.png");

        // 6. Snapshot: Chapter 02 Multi-Experiment Shell Integration
        AtomicReference<Scene> shellSceneRef = new AtomicReference<>();
        runOnFxThread(() -> {
            PhysoraApplicationShell shell = new PhysoraApplicationShell();
            shell.selectChapter(2); // Select Chapter 02 (Relative Motion)
            shell.plannedChapterView().selectChapter2Experiment(2); // Activate River-Boat

            Scene shellScene = new Scene(shell, 1380, 820);
            shellSceneRef.set(shellScene);

            Stage stage = stageRef.get();
            stage.setScene(shellScene);
            return null;
        });
        waitFrames(10);
        saveSnapshot(capture(shellSceneRef.get()), "milestone23_06_chapter2_multiexperiment_shell.png");
    }
}
