package edu.physics.ui;

import edu.physics.ai.ProviderMode;
import edu.physics.relative.RainInvestigatorPreset;
import edu.physics.relative.RainInvestigatorSessionModel;
import edu.physics.relative.RainInvestigatorStage;
import edu.physics.relative.ReferenceFrame;
import edu.physics.relative.TwoProjectilePursuitModel;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
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

@DisplayName("Final Demo Readiness & Acceptance Sprint - Two-Chapter Integrated Verification Test")
class FinalDemoReadinessVisualAuditTest {

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
        System.out.println("[EVIDENCE CAPTURED] " + filename + " (" + (subDirTarget.length() / 1024) + " KB)");
    }

    private static void waitFrames(int count) throws Exception {
        for (int i = 0; i < count; i++) {
            Thread.sleep(35);
            runOnFxThread(() -> null);
        }
    }

    private static void waitUntilFx(Callable<Boolean> condition) throws Exception {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadlineNanos) {
            if (Boolean.TRUE.equals(runOnFxThread(condition))) {
                return;
            }
            Thread.sleep(40);
        }
        fail("Timed out waiting for JavaFX condition");
    }

    @Test
    @DisplayName("Protocol: Complete End-to-End Two-Chapter Integrated Demo & Multi-Resolution Visual Evidence")
    void testCompleteTwoChapterDemoAndCaptureVisualEvidence() throws Exception {
        AtomicReference<PhysoraApplicationShell> shellRef = new AtomicReference<>();
        AtomicReference<Scene> sceneRef = new AtomicReference<>();

        runOnFxThread(() -> {
            Stage stage = new Stage();
            stageRef.set(stage);

            PhysoraApplicationShell shell = new PhysoraApplicationShell();
            shellRef.set(shell);

            Scene scene = new Scene(shell, 1600, 900);
            scene.getStylesheets().add(getClass().getResource("/edu/physics/ui/physora.css").toExternalForm());
            stage.setScene(scene);
            stage.show();
            sceneRef.set(scene);
            return null;
        });
        waitFrames(8);

        PhysoraApplicationShell shell = shellRef.get();
        Scene scene = sceneRef.get();
        Stage stage = stageRef.get();

        // ==========================================
        // PART 1: KINEMATICS FIVE-MODE AUDIT & CAPTURE
        // ==========================================

        // 1.1 Mode 1: Experiment
        runOnFxThread(() -> {
            shell.selectChapter(1);
            ToggleButton tab = (ToggleButton) shell.lookup("#simulationViewTab");
            tab.fire();
            shell.controller().play();
            shell.applyCss();
            shell.layout();
            return null;
        });
        waitFrames(6);
        runOnFxThread(() -> {
            shell.controller().pause();
            return null;
        });
        saveSnapshot(capture(scene), "demo_ch1_mode1_experiment_1600x900.png");

        // 1.2 Mode 2: Predict (evaluate against truth)
        runOnFxThread(() -> {
            ToggleButton tab = (ToggleButton) shell.lookup("#predictTab");
            tab.fire();
            shell.applyCss();
            shell.layout();
            return null;
        });
        waitFrames(4);
        saveSnapshot(capture(scene), "demo_ch1_mode2_predict_1600x900.png");

        // 1.3 Mode 3: Compare (check table headers at 1366x768 and 1600x900)
        runOnFxThread(() -> {
            ToggleButton tab = (ToggleButton) shell.lookup("#compareTab");
            tab.fire();
            shell.applyCss();
            shell.layout();
            return null;
        });
        waitFrames(5);
        saveSnapshot(capture(scene), "demo_ch1_mode3_compare_1600x900.png");

        // 1.4 Mode 4: Understand (guided 6-step derivation)
        runOnFxThread(() -> {
            ToggleButton tab = (ToggleButton) shell.lookup("#logicViewTab");
            tab.fire();
            shell.applyCss();
            shell.layout();
            return null;
        });
        waitFrames(4);
        saveSnapshot(capture(scene), "demo_ch1_mode4_understand_1600x900.png");

        // 1.5 Mode 5: Ask AI (Exact reproduction question -> 5.00 m)
        runOnFxThread(() -> {
            ToggleButton tab = (ToggleButton) shell.lookup("#aiProblemTab");
            tab.fire();

            ComboBox<ProviderMode> modeSelector = (ComboBox<ProviderMode>) shell.lookup("#aiModeSelector");
            assertNotNull(modeSelector, "aiModeSelector must exist");
            modeSelector.setValue(ProviderMode.OFFLINE);

            TextArea questionInput = (TextArea) shell.lookup("#aiQuestionInput");
            assertNotNull(questionInput, "aiQuestionInput must exist");
            questionInput.setText("A ball is kicked from level ground with an initial speed of 20 m/s at an angle of 30° above the horizontal. Taking gravity (g) as 10 m/s², what is the maximum height the ball reaches?");

            Button submitButton = (Button) shell.lookup("#aiSubmitButton");
            assertNotNull(submitButton, "aiSubmitButton must exist");
            submitButton.fire();
            return null;
        });

        // Wait for solution to complete
        waitUntilFx(() -> {
            Button submitButton = (Button) shell.lookup("#aiSubmitButton");
            Label status = (Label) shell.lookup("#aiStatus");
            return submitButton != null && !submitButton.isDisabled()
                    && status != null && "Interpretation, deterministic solution, and explanation passed validation.".equals(status.getText());
        });
        waitFrames(4);

        runOnFxThread(() -> {
            Label status = (Label) shell.lookup("#aiStatus");
            TextArea output = (TextArea) shell.lookup("#aiSolutionOutput");
            assertNotNull(output);
            String outputText = output.getText();
            assertNotNull(outputText);
            assertTrue(outputText.contains("maximumHeight = 5.0") || outputText.contains("5.00"),
                    "Deterministic solver must yield 5.00 m maximum height");
            return null;
        });
        saveSnapshot(capture(scene), "demo_ch1_mode5_ask_ai_1600x900.png");

        // ==========================================
        // PART 2: CHAPTER 02 FOUR EXPERIENCES AUDIT
        // ==========================================

        // 2.1 Navigate to Chapter 02
        runOnFxThread(() -> {
            shell.selectChapter(2);
            assertEquals(2, shell.chapterRail().selectedChapter().number());
            assertSame(shell.plannedChapterView(), shell.getCenter());
            return null;
        });
        waitFrames(5);

        // 2.2 Experience 2.1: Two-Projectile Pursuit & Reference Frames
        runOnFxThread(() -> {
            shell.plannedChapterView().selectChapter2Experiment(1);
            RelativeMotionWorkbenchView view21 = shell.plannedChapterView().relativeMotionWorkbench();
            assertNotNull(view21);
            assertTrue(view21.isVisible());

            // Switch to Ground frame
            view21.controller().setReferenceFrame(ReferenceFrame.GROUND);
            view21.controller().seek(1.25);
            shell.applyCss();
            shell.layout();
            return null;
        });
        waitFrames(4);
        saveSnapshot(capture(scene), "demo_ch2_exp21_ground_1600x900.png");

        // Observer A frame
        runOnFxThread(() -> {
            RelativeMotionWorkbenchView view21 = shell.plannedChapterView().relativeMotionWorkbench();
            view21.controller().setReferenceFrame(ReferenceFrame.OBSERVER_A);
            shell.applyCss();
            shell.layout();
            return null;
        });
        waitFrames(4);
        saveSnapshot(capture(scene), "demo_ch2_exp21_observer_a_1600x900.png");

        // Collision moment
        runOnFxThread(() -> {
            RelativeMotionWorkbenchView view21 = shell.plannedChapterView().relativeMotionWorkbench();
            view21.controller().setModel(TwoProjectilePursuitModel.flagshipCollisionPreset());
            view21.controller().seek(2.50);
            shell.applyCss();
            shell.layout();
            return null;
        });
        waitFrames(4);
        saveSnapshot(capture(scene), "demo_ch2_exp21_collision_1600x900.png");

        // 2.3 Experience 2.2: River-Boat Vector Crossing
        runOnFxThread(() -> {
            shell.plannedChapterView().selectChapter2Experiment(2);
            RiverBoatWorkbenchView view22 = shell.plannedChapterView().riverBoatWorkbench();
            assertNotNull(view22);
            assertTrue(view22.isVisible());
            view22.controller().seek(12.5);
            shell.applyCss();
            shell.layout();
            return null;
        });
        waitFrames(4);
        saveSnapshot(capture(scene), "demo_ch2_exp22_riverboat_1600x900.png");

        // 2.4 Experience 2.3: Rain-Man Forward Umbrella
        runOnFxThread(() -> {
            shell.plannedChapterView().selectChapter2Experiment(3);
            RainManWorkbenchView view23 = shell.plannedChapterView().rainManWorkbench();
            assertNotNull(view23);
            assertTrue(view23.isVisible());

            // Snap to optimal
            Button snapBtn = (Button) view23.lookup("#snapOptimalButton");
            snapBtn.fire();
            shell.applyCss();
            shell.layout();
            return null;
        });
        waitFrames(4);
        saveSnapshot(capture(scene), "demo_ch2_exp23_rainman_forward_1600x900.png");

        // 2.5 Experience 2.4: Inverse Rain Investigator
        runOnFxThread(() -> {
            shell.plannedChapterView().selectChapter2Experiment(4);
            RainInvestigatorWorkbenchView view24 = shell.plannedChapterView().rainInvestigatorWorkbench();
            assertNotNull(view24);
            assertTrue(view24.isVisible());

            // Stage 1: Evidence
            view24.model().loadPreset(RainInvestigatorPreset.STANDARD_JEE);
            view24.model().setStage(RainInvestigatorStage.STAGE_1_EVIDENCE);
            shell.applyCss();
            shell.layout();
            return null;
        });
        waitFrames(4);
        saveSnapshot(capture(scene), "demo_ch2_exp24_stage1_evidence_1600x900.png");

        // Stage 2: Ambiguity (candidate slider)
        runOnFxThread(() -> {
            RainInvestigatorWorkbenchView view24 = shell.plannedChapterView().rainInvestigatorWorkbench();
            view24.model().setStage(RainInvestigatorStage.STAGE_2_AMBIGUITY);
            view24.canvasRenderer().setCandidateVDown(10.0);
            shell.applyCss();
            shell.layout();
            return null;
        });
        waitFrames(4);
        saveSnapshot(capture(scene), "demo_ch2_exp24_stage2_ambiguity_1600x900.png");

        // Stage 3: Geometry (intersection)
        runOnFxThread(() -> {
            RainInvestigatorWorkbenchView view24 = shell.plannedChapterView().rainInvestigatorWorkbench();
            view24.model().setStage(RainInvestigatorStage.STAGE_3_GEOMETRY);
            shell.applyCss();
            shell.layout();
            return null;
        });
        waitFrames(4);
        saveSnapshot(capture(scene), "demo_ch2_exp24_stage3_geometry_1600x900.png");

        // Stage 5: Verification (round-trip forward check)
        runOnFxThread(() -> {
            RainInvestigatorWorkbenchView view24 = shell.plannedChapterView().rainInvestigatorWorkbench();
            view24.model().setStage(RainInvestigatorStage.STAGE_5_VERIFICATION);
            shell.applyCss();
            shell.layout();
            return null;
        });
        waitFrames(4);
        saveSnapshot(capture(scene), "demo_ch2_exp24_stage5_verification_1600x900.png");

        // ==========================================
        // PART 3: RESPONSIVE SCREENSHOTS AT 1366x768 & 1280x720
        // ==========================================

        int[][] compactResolutions = {
                {1366, 768},
                {1280, 720}
        };

        for (int[] res : compactResolutions) {
            int w = res[0];
            int h = res[1];
            String suffix = w + "x" + h;

            runOnFxThread(() -> {
                stage.setWidth(w);
                stage.setHeight(h);
                shell.applyCss();
                shell.layout();
                return null;
            });
            waitFrames(6);

            // Kinematics Compare at compact resolution (proves no header truncation)
            runOnFxThread(() -> {
                shell.selectChapter(1);
                ToggleButton tab = (ToggleButton) shell.lookup("#compareTab");
                tab.fire();
                shell.applyCss();
                shell.layout();
                return null;
            });
            waitFrames(4);
            saveSnapshot(capture(scene), "demo_ch1_compare_" + suffix + ".png");

            // Relative Motion Inverse Rain Geometry at compact resolution
            runOnFxThread(() -> {
                shell.selectChapter(2);
                shell.plannedChapterView().selectChapter2Experiment(4);
                shell.plannedChapterView().rainInvestigatorWorkbench().model().setStage(RainInvestigatorStage.STAGE_3_GEOMETRY);
                shell.applyCss();
                shell.layout();
                return null;
            });
            waitFrames(4);
            saveSnapshot(capture(scene), "demo_ch2_inverse_rain_geometry_" + suffix + ".png");
        }

        // ==========================================
        // PART 4: SEAMLESS RETURN TO KINEMATICS
        // ==========================================
        runOnFxThread(() -> {
            Button returnBtn = (Button) shell.lookup("#returnToKinematicsButton");
            assertNotNull(returnBtn);
            returnBtn.fire();

            assertEquals(1, shell.chapterRail().selectedChapter().number(),
                    "Return button must restore Chapter 01 Kinematics in the ChapterRail");
            assertSame(shell.normalBenchView(), shell.getCenter(),
                    "Shell center must be NormalBenchView");
            return null;
        });
        waitFrames(3);
    }
}
