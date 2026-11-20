package edu.physics.ui;

import edu.physics.relative.RainInvestigatorPreset;
import edu.physics.relative.RainInvestigatorSessionModel;
import edu.physics.relative.RainInvestigatorStage;
import javafx.application.Platform;
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

@DisplayName("Inverse Rain Investigator - Five-Stage Visual Evidence Capture & Resolution Verification")
class RainInvestigatorVisualCaptureTest {

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
        System.out.println("Saved visual evidence screenshot: " + filename + " (" + (subDirTarget.length() / 1024) + " KB)");
    }

    private static void waitFrames(int count) throws Exception {
        for (int i = 0; i < count; i++) {
            Thread.sleep(30);
            runOnFxThread(() -> null);
        }
    }

    @Test
    @DisplayName("Capture all 5 stages across 1600x900 and 1366x768 plus all presets")
    void captureAllStagesAndPresets() throws Exception {
        AtomicReference<RainInvestigatorWorkbenchView> viewRef = new AtomicReference<>();
        AtomicReference<Scene> sceneRef = new AtomicReference<>();

        runOnFxThread(() -> {
            Stage stage = new Stage();
            stageRef.set(stage);

            RainInvestigatorSessionModel model = new RainInvestigatorSessionModel(RainInvestigatorPreset.STANDARD_JEE);
            RainInvestigatorWorkbenchView view = new RainInvestigatorWorkbenchView(model);
            viewRef.set(view);

            Scene scene = new Scene(view, 1600, 900);
            scene.getStylesheets().add(getClass().getResource("/edu/physics/ui/physora.css").toExternalForm());
            stage.setScene(scene);
            stage.show();
            sceneRef.set(scene);
            return null;
        });
        waitFrames(6);

        RainInvestigatorWorkbenchView view = viewRef.get();
        Scene scene = sceneRef.get();
        RainInvestigatorSessionModel model = view.model();

        int[][] resolutions = {
                {1600, 900},
                {1366, 768}
        };

        for (int[] res : resolutions) {
            int w = res[0];
            int h = res[1];
            String resSuffix = w + "x" + h;

            runOnFxThread(() -> {
                Stage stage = stageRef.get();
                stage.setWidth(w);
                stage.setHeight(h);
                return null;
            });
            waitFrames(5);

            // 1. Stage 1: Evidence
            runOnFxThread(() -> {
                model.loadPreset(RainInvestigatorPreset.STANDARD_JEE);
                model.setStage(RainInvestigatorStage.STAGE_1_EVIDENCE);
                return null;
            });
            waitFrames(4);
            saveSnapshot(capture(scene), "rain_investigator_stage1_evidence_" + resSuffix + ".png");

            // 2. Stage 2: Ambiguity
            runOnFxThread(() -> {
                model.setStage(RainInvestigatorStage.STAGE_2_AMBIGUITY);
                view.canvasRenderer().setCandidateVDown(10.0);
                return null;
            });
            waitFrames(4);
            saveSnapshot(capture(scene), "rain_investigator_stage2_ambiguity_" + resSuffix + ".png");

            // 3. Stage 3: Geometry
            runOnFxThread(() -> {
                model.setStage(RainInvestigatorStage.STAGE_3_GEOMETRY);
                return null;
            });
            waitFrames(4);
            saveSnapshot(capture(scene), "rain_investigator_stage3_geometry_" + resSuffix + ".png");

            // 4. Stage 4: Reconstruction
            runOnFxThread(() -> {
                model.setStage(RainInvestigatorStage.STAGE_4_RECONSTRUCTION);
                return null;
            });
            waitFrames(4);
            saveSnapshot(capture(scene), "rain_investigator_stage4_reconstruction_" + resSuffix + ".png");

            // 5. Stage 5: Verification
            runOnFxThread(() -> {
                model.setStage(RainInvestigatorStage.STAGE_5_VERIFICATION);
                return null;
            });
            waitFrames(4);
            saveSnapshot(capture(scene), "rain_investigator_stage5_verification_" + resSuffix + ".png");
        }

        // Additional Presets at 1600x900
        runOnFxThread(() -> {
            Stage stage = stageRef.get();
            stage.setWidth(1600);
            stage.setHeight(900);
            return null;
        });
        waitFrames(4);

        // Preset 2: Windy JEE Classic
        runOnFxThread(() -> {
            model.loadPreset(RainInvestigatorPreset.WINDY_JEE);
            model.setStage(RainInvestigatorStage.STAGE_3_GEOMETRY);
            return null;
        });
        waitFrames(4);
        saveSnapshot(capture(scene), "rain_investigator_preset2_windy_1600x900.png");

        // Preset 3: Near-Parallel (Ill-Conditioned)
        runOnFxThread(() -> {
            model.loadPreset(RainInvestigatorPreset.NEAR_PARALLEL);
            model.setStage(RainInvestigatorStage.STAGE_3_GEOMETRY);
            return null;
        });
        waitFrames(4);
        saveSnapshot(capture(scene), "rain_investigator_preset3_near_parallel_1600x900.png");

        // Preset 4: Identical Speeds (Degenerate)
        runOnFxThread(() -> {
            model.loadPreset(RainInvestigatorPreset.IDENTICAL_SPEEDS);
            model.setStage(RainInvestigatorStage.STAGE_3_GEOMETRY);
            return null;
        });
        waitFrames(4);
        saveSnapshot(capture(scene), "rain_investigator_preset4_identical_speeds_1600x900.png");

        // Preset 5: Inconsistent Direction (Upward Rain)
        runOnFxThread(() -> {
            model.loadPreset(RainInvestigatorPreset.INCONSISTENT_DIRECTION);
            model.setStage(RainInvestigatorStage.STAGE_3_GEOMETRY);
            return null;
        });
        waitFrames(4);
        saveSnapshot(capture(scene), "rain_investigator_preset5_inconsistent_1600x900.png");
    }
}

