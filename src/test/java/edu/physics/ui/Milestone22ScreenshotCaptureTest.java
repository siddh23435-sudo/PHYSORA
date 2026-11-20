package edu.physics.ui;

import edu.physics.relative.ReferenceFrame;
import edu.physics.relative.TwoProjectilePursuitModel;
import javafx.scene.Node;
import javafx.scene.Scene;
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

@DisplayName("Milestone 2.2 Live Demonstration & Visual Screenshot Capture")
class Milestone22ScreenshotCaptureTest {

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
    @DisplayName("Capture all reference frames, educational overlays, presets, and velocity addition triangle")
    @SuppressWarnings("unchecked")
    void captureReferenceFramesAndDemonstrationEvidence() throws Exception {
        RelativeMotionSimulationController controller = new RelativeMotionSimulationController();
        controller.setModel(TwoProjectilePursuitModel.flagshipCollisionPreset());

        AtomicReference<RelativeMotionWorkbenchView> workbenchRef = new AtomicReference<>();
        AtomicReference<Scene> sceneRef = new AtomicReference<>();

        runOnFxThread(() -> {
            RelativeMotionWorkbenchView workbench = new RelativeMotionWorkbenchView(controller);
            workbenchRef.set(workbench);

            Scene scene = new Scene(workbench, 1420, 900);
            sceneRef.set(scene);

            Stage stage = new Stage();
            stageRef.set(stage);
            stage.setTitle("PHYSORA - Milestone 2.2 Relative Motion Reference Frames");
            stage.setScene(scene);
            stage.show();
            return null;
        });

        waitFrames(15);

        // 1. Ground Frame S at mid-flight t = 1.25 s
        runOnFxThread(() -> {
            controller.setModel(TwoProjectilePursuitModel.flagshipCollisionPreset());
            controller.setReferenceFrame(ReferenceFrame.GROUND);
            controller.seek(1.25);
            return null;
        });
        waitFrames(10);
        BufferedImage imgGroundMid = capture(sceneRef.get());
        saveSnapshot(imgGroundMid, "milestone22_01_ground_frame_midflight.png");

        // 2. Ground Frame S at exact mid-air collision t = 2.50 s
        runOnFxThread(() -> {
            controller.setReferenceFrame(ReferenceFrame.GROUND);
            controller.seek(2.50);
            return null;
        });
        waitFrames(10);
        BufferedImage imgGroundCollision = capture(sceneRef.get());
        saveSnapshot(imgGroundCollision, "milestone22_02_ground_frame_collision.png");

        // 3. Observer A Frame S_A at t = 1.25 s (Showing stationary A at Origin, r_{B/A}, v_{B/A}, ticks, HUD)
        runOnFxThread(() -> {
            controller.setReferenceFrame(ReferenceFrame.OBSERVER_A);
            controller.seek(1.25);
            return null;
        });
        waitFrames(10);
        BufferedImage imgObserverA = capture(sceneRef.get());
        saveSnapshot(imgObserverA, "milestone22_03_observer_a_frame.png");

        // 4. Observer B Frame S_B at t = 1.25 s (Showing stationary B at Origin, r_{A/B}, v_{A/B}, HUD)
        runOnFxThread(() -> {
            controller.setReferenceFrame(ReferenceFrame.OBSERVER_B);
            controller.seek(1.25);
            return null;
        });
        waitFrames(10);
        BufferedImage imgObserverB = capture(sceneRef.get());
        saveSnapshot(imgObserverB, "milestone22_04_observer_b_frame.png");

        // 5. Observer A Frame S_A at exact collision t = 2.50 s (Collision intercept at origin)
        runOnFxThread(() -> {
            controller.setReferenceFrame(ReferenceFrame.OBSERVER_A);
            controller.seek(2.50);
            return null;
        });
        waitFrames(10);
        BufferedImage imgObserverACollision = capture(sceneRef.get());
        saveSnapshot(imgObserverACollision, "milestone22_05_observer_frame_collision.png");

        // 6. Near-Miss Preset in Observer A Frame at closest approach t = 2.50 s
        runOnFxThread(() -> {
            controller.setModel(TwoProjectilePursuitModel.nearMissPreset());
            controller.setReferenceFrame(ReferenceFrame.OBSERVER_A);
            controller.seek(2.50);
            return null;
        });
        waitFrames(10);
        BufferedImage imgNearMiss = capture(sceneRef.get());
        saveSnapshot(imgNearMiss, "milestone22_06_near_miss_preset.png");

        // 7. Angled Intercept Preset in Ground Frame at t = 1.50 s
        runOnFxThread(() -> {
            controller.setModel(TwoProjectilePursuitModel.angledCollisionPreset());
            controller.setReferenceFrame(ReferenceFrame.GROUND);
            controller.seek(1.50);
            return null;
        });
        waitFrames(10);
        BufferedImage imgAngled = capture(sceneRef.get());
        saveSnapshot(imgAngled, "milestone22_07_angled_intercept_preset.png");

        // 8. High-resolution crop of the Live Velocity Triangle Widget
        Node trianglePane = runOnFxThread(() -> {
            return sceneRef.get().lookup("#liveVelocityTrianglePane");
        });
        assertNotNull(trianglePane, "liveVelocityTrianglePane must exist");
        BufferedImage imgTriangle = captureNode(trianglePane);
        saveSnapshot(imgTriangle, "milestone22_08_live_velocity_triangle.png");

        // 9. Full PhysoraApplicationShell integration showing Chapter 02 selected in sidebar
        runOnFxThread(() -> {
            controller.dispose();

            PhysoraApplicationShell shell = new PhysoraApplicationShell();
            Scene shellScene = new Scene(shell, 1420, 900);
            stageRef.get().setScene(shellScene);

            // Navigate to Chapter 2
            shell.selectChapter(2);
            sceneRef.set(shellScene);
            return null;
        });
        waitFrames(15);
        BufferedImage imgShellChapter2 = capture(sceneRef.get());
        saveSnapshot(imgShellChapter2, "milestone22_09_shell_chapter2_integrated.png");
    }
}
