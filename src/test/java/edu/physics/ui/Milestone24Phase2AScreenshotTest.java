package edu.physics.ui;

import javafx.scene.Scene;
import javafx.scene.control.Button;
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


@DisplayName("Milestone 2.4 Phase 2A - Rain-Man Forward Experiment Visual Acceptance & Screenshot Capture")
class Milestone24Phase2AScreenshotTest {

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
        System.out.println("Captured visual artifact: " + rootTarget.getAbsolutePath());
    }

    @Test
    @DisplayName("Capture full visual verification suite across viewports and physics scenarios")
    void captureMilestone24Screenshots() throws Exception {
        // 1. Initial 1600x900 Setup
        AtomicReference<RainManSimulationController> ctrlRef = new AtomicReference<>();
        AtomicReference<RainManWorkbenchView> viewRef = new AtomicReference<>();
        AtomicReference<Scene> sceneRef = new AtomicReference<>();

        runOnFxThread(() -> {
            RainManSimulationController controller = new RainManSimulationController();
            ctrlRef.set(controller);

            RainManWorkbenchView view = new RainManWorkbenchView(controller);
            viewRef.set(view);

            Scene scene = new Scene(view, 1600, 900);
            sceneRef.set(scene);

            Stage stage = new Stage();
            stage.setTitle("PHYSORA - Milestone 2.4 Phase 2A Rain-Man Forward Experiment");
            stage.setScene(scene);
            stage.show();
            stageRef.set(stage);
            return null;
        });

        Thread.sleep(300);

        // Snapshot 1: Default Scenario at 1600x900 (misaligned vertical umbrella, walking forward at 3 m/s)
        BufferedImage img01 = capture(sceneRef.get());
        saveSnapshot(img01, "m24_01_rainman_default_1600x900.png");

        // Snapshot 2: Aligned Scenario at 1600x900 (snapped to optimal angle +20.6°)
        runOnFxThread(() -> {
            Button snapBtn = (Button) viewRef.get().lookup("#snapOptimalButton");
            snapBtn.fire();
            return null;
        });
        Thread.sleep(200);
        BufferedImage img02 = capture(sceneRef.get());
        saveSnapshot(img02, "m24_02_rainman_aligned_1600x900.png");

        // Snapshot 3: Tailwind Equal Speed Preset (vrx = vm = 4 m/s, apparent rain vertical)
        runOnFxThread(() -> {
            @SuppressWarnings("unchecked")
            ComboBox<String> combo = (ComboBox<String>) viewRef.get().lookup("#scenarioPresetCombo");
            combo.setValue("Tailwind Equal Speed (vrx = vm, Rain appears vertical)");
            return null;
        });
        Thread.sleep(200);
        BufferedImage img03 = capture(sceneRef.get());
        saveSnapshot(img03, "m24_03_rainman_tailwind_zero_relative_1600x900.png");

        // Snapshot 4: Headwind Running Preset (vrx = -3 m/s, vm = 5 m/s)
        runOnFxThread(() -> {
            @SuppressWarnings("unchecked")
            ComboBox<String> combo = (ComboBox<String>) viewRef.get().lookup("#scenarioPresetCombo");
            combo.setValue("Headwind Walking (Wind opposes motion)");
            Button snapBtn = (Button) viewRef.get().lookup("#snapOptimalButton");
            snapBtn.fire();
            return null;
        });
        Thread.sleep(200);
        BufferedImage img04 = capture(sceneRef.get());
        saveSnapshot(img04, "m24_04_rainman_headwind_running_1600x900.png");

        // Snapshot 5: Backward Walking Preset (vm = -4 m/s, negative tilt)
        runOnFxThread(() -> {
            @SuppressWarnings("unchecked")
            ComboBox<String> combo = (ComboBox<String>) viewRef.get().lookup("#scenarioPresetCombo");
            combo.setValue("Backward Walking (vm < 0)");
            Button snapBtn = (Button) viewRef.get().lookup("#snapOptimalButton");
            snapBtn.fire();
            return null;
        });
        Thread.sleep(200);
        BufferedImage img05 = capture(sceneRef.get());
        saveSnapshot(img05, "m24_05_rainman_backward_walking_1600x900.png");

        // Snapshot 6: Responsive Viewport 1440x900
        runOnFxThread(() -> {
            Stage stage = stageRef.get();
            stage.setWidth(1440);
            stage.setHeight(900);
            return null;
        });
        Thread.sleep(300);
        BufferedImage img06 = capture(sceneRef.get());
        saveSnapshot(img06, "m24_06_rainman_responsive_1440x900.png");

        // Snapshot 7: Responsive Viewport 1366x768
        runOnFxThread(() -> {
            Stage stage = stageRef.get();
            stage.setWidth(1366);
            stage.setHeight(768);
            return null;
        });
        Thread.sleep(300);
        BufferedImage img07 = capture(sceneRef.get());
        saveSnapshot(img07, "m24_07_rainman_responsive_1366x768.png");

        // Snapshot 8: Responsive Viewport 1280x720 (720p / 1080p @ 150%)
        runOnFxThread(() -> {
            Stage stage = stageRef.get();
            stage.setWidth(1280);
            stage.setHeight(720);
            return null;
        });
        Thread.sleep(300);
        BufferedImage img08 = capture(sceneRef.get());
        saveSnapshot(img08, "m24_08_rainman_responsive_1280x720.png");

        // Snapshot 9: Complete PhysoraApplicationShell with Chapter 02 and all 3 Experiments Visible
        AtomicReference<Scene> shellSceneRef = new AtomicReference<>();
        runOnFxThread(() -> {
            PhysoraApplicationShell shell = new PhysoraApplicationShell();
            shell.selectChapter(2); // Chapter 2
            shell.plannedChapterView().selectChapter2Experiment(3); // Exp 2.3 Rain-Man

            Scene shellScene = new Scene(shell, 1600, 900);
            shellSceneRef.set(shellScene);

            Stage stage = stageRef.get();
            stage.setScene(shellScene);
            stage.setWidth(1600);
            stage.setHeight(900);
            return null;
        });
        Thread.sleep(400);
        BufferedImage img09 = capture(shellSceneRef.get());
        saveSnapshot(img09, "m24_09_chapter2_three_experiments_shell.png");

        // Clean up
        runOnFxThread(() -> {
            ctrlRef.get().dispose();
            viewRef.get().dispose();
            return null;
        });
    }
}
