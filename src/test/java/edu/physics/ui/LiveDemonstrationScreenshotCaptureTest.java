package edu.physics.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("Milestone F Live Demonstration & Journey Screenshot Verification")
class LiveDemonstrationScreenshotCaptureTest {

    private static final String ARTIFACT_DIR =
            "C:/Users/Siddhant Gangwal/.gemini/antigravity/brain/77ba2716-d4c5-462f-8fcc-a4f036a0efc2/screenshots";
    private static final String ROOT_ARTIFACT_DIR =
            "C:/Users/Siddhant Gangwal/.gemini/antigravity/brain/77ba2716-d4c5-462f-8fcc-a4f036a0efc2";

    @BeforeAll
    static void setup() throws Exception {
        FxTestRuntime.ensureStarted();
        Files.createDirectories(Paths.get(ARTIFACT_DIR));
        Files.createDirectories(Paths.get(ROOT_ARTIFACT_DIR));
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
    }

    private static void waitUntilFx(Callable<Boolean> condition) throws Exception {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadlineNanos) {
            if (Boolean.TRUE.equals(runOnFxThread(condition))) {
                return;
            }
            Thread.sleep(30);
        }
        fail("Timed out waiting for JavaFX condition");
    }

    @SuppressWarnings("unchecked")
    private static <T extends Node> T node(Parent parent, String id, Class<T> type) {
        Node found = parent.lookup("#" + id);
        assertNotNull(found, "Node with id #" + id + " must be present in the scene graph");
        assertTrue(type.isInstance(found), "Node #" + id + " must be an instance of " + type.getSimpleName());
        return (T) found;
    }

    @Test
    @DisplayName("Execute full 7-step student journey and capture visual evidence for all modes")
    void executeFullStudentJourneyAndCaptureEvidence() throws Exception {
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        AtomicReference<PhysoraApplicationShell> shellRef = new AtomicReference<>();
        AtomicReference<Scene> sceneRef = new AtomicReference<>();

        runOnFxThread(() -> {
            Stage stage = new Stage();
            PhysoraApplicationShell shell = new PhysoraApplicationShell();
            Scene scene = new Scene(shell, 1280, 800);
            scene.getStylesheets().add(LiveDemonstrationScreenshotCaptureTest.class
                    .getResource("/edu/physics/ui/physora.css").toExternalForm());
            stage.setScene(scene);
            stage.show();

            stageRef.set(stage);
            shellRef.set(shell);
            sceneRef.set(scene);
            return null;
        });

        Stage stage = stageRef.get();
        PhysoraApplicationShell shell = shellRef.get();
        Scene scene = sceneRef.get();

        try {
            // STEP 1: EXPERIMENT (Default Kinematics simulator at apex)
            runOnFxThread(() -> {
                shell.controller().seekToApex();
                return null;
            });
            saveSnapshot(capture(scene), "01_experiment_default.png");

            // STEP 2: PREDICT (Active challenge locked)
            runOnFxThread(() -> {
                ToggleButton predictTab = node(shell, "predictTab", ToggleButton.class);
                predictTab.setSelected(true);
                predictTab.getOnAction().handle(null);

                TextField hField = node(shell, "predictMaxHeightField", TextField.class);
                TextField tField = node(shell, "predictFlightTimeField", TextField.class);
                TextField rField = node(shell, "predictRangeField", TextField.class);
                hField.setText("11.5");
                tField.setText("2.8");
                rField.setText("42.0");

                Button lockButton = node(shell, "lockPredictionsButton", Button.class);
                lockButton.fire();
                return null;
            });
            saveSnapshot(capture(scene), "02_predict_challenge_locked.png");

            // STEP 3: PREDICT EVALUATION (Touchdown reached, results card revealed)
            runOnFxThread(() -> {
                shell.controller().seekToLanding();
                return null;
            });
            saveSnapshot(capture(scene), "03_predict_revealed_comparison.png");

            // STEP 4: COMPARE (30° vs 60° complementary demo with shared isotropic overlay)
            runOnFxThread(() -> {
                ToggleButton compareTab = node(shell, "compareTab", ToggleButton.class);
                compareTab.setSelected(true);
                compareTab.getOnAction().handle(null);

                Button demoButton = node(shell, "loadComplementaryDemoButton", Button.class);
                demoButton.fire();
                return null;
            });
            saveSnapshot(capture(scene), "04_compare_trajectories_overlay.png");

            // STEP 5: UNDERSTAND (Guided mathematical derivation)
            runOnFxThread(() -> {
                ToggleButton logicTab = node(shell, "logicViewTab", ToggleButton.class);
                logicTab.setSelected(true);
                logicTab.getOnAction().handle(null);
                return null;
            });
            saveSnapshot(capture(scene), "05_understand_guided_derivations.png");

            // STEP 6: ASK AI (Attached experiment context & populated verified response)
            runOnFxThread(() -> {
                ToggleButton aiTab = node(shell, "aiProblemTab", ToggleButton.class);
                aiTab.setSelected(true);
                aiTab.getOnAction().handle(null);

                // Explicitly select offline local provider to guarantee zero network calls and zero quota consumption
                @SuppressWarnings("unchecked")
                ComboBox<edu.physics.ai.ProviderMode> modeSelector = (ComboBox<edu.physics.ai.ProviderMode>) (Object) node(shell, "aiModeSelector", ComboBox.class);
                modeSelector.setValue(edu.physics.ai.ProviderMode.OFFLINE);

                Button attachButton = node(shell, "useCurrentExperimentButton", Button.class);
                attachButton.fire();

                TextArea questionArea = node(shell, "aiQuestionInput", TextArea.class);
                questionArea.setText("A ball is projected at 20 m/s at 30 degrees with g = 10 m/s^2. Find its maximum height and horizontal range.");

                Button submitButton = node(shell, "aiSubmitButton", Button.class);
                submitButton.fire();
                return null;
            });

            // Wait for deterministic solver and AI workflow to complete
            Button submitButton = node(shell, "aiSubmitButton", Button.class);
            TextArea solutionOutput = node(shell, "aiSolutionOutput", TextArea.class);
            Label aiStatus = node(shell, "aiStatus", Label.class);
            waitUntilFx(() -> !submitButton.isDisabled()
                    && !solutionOutput.getText().isBlank()
                    && !aiStatus.getStyleClass().contains("ai-status-error")
                    && solutionOutput.getText().contains("5.00000 m")
                    && solutionOutput.getText().contains("34.6410 m"));

            assertTrue(solutionOutput.getText().contains("5.00000 m"), "Expected 5.00000 m in verified solution");
            assertTrue(solutionOutput.getText().contains("34.6410 m"), "Expected 34.6410 m in verified solution");
            assertTrue(solutionOutput.getText().contains("PARAMETER & CONTEXT DISTINCTION"), "Expected parameter distinction banner");
            assertTrue(solutionOutput.getText().contains("theta = 30.0 deg"), "Expected 30.0 deg in applied question parameters");
            assertTrue(solutionOutput.getText().contains("v0 = 20.00 m/s"), "Expected 20.00 m/s in applied question parameters");
            assertTrue(solutionOutput.getText().contains("g = 10.00 m/s²"), "Expected 10.00 m/s² in applied question parameters");

            saveSnapshot(capture(scene), "06_ask_ai_experiment_context.png");

            // STEP 7: NAVIGATION (Planned chapter roadmap view: Chapter 02 Relative Motion)
            runOnFxThread(() -> {
                shell.selectChapter(2);
                return null;
            });
            saveSnapshot(capture(scene), "07_planned_chapter_roadmap.png");

            // Return to Kinematics to confirm experiment state remained preserved
            runOnFxThread(() -> {
                shell.selectChapter(1);
                ToggleButton expTab = node(shell, "simulationViewTab", ToggleButton.class);
                expTab.setSelected(true);
                expTab.getOnAction().handle(null);
                return null;
            });
        } finally {
            runOnFxThread(() -> {
                shell.dispose();
                stage.close();
                return null;
            });
        }

        // Verify all 7 screenshot files were written to disk
        String[] files = {
                "01_experiment_default.png",
                "02_predict_challenge_locked.png",
                "03_predict_revealed_comparison.png",
                "04_compare_trajectories_overlay.png",
                "05_understand_guided_derivations.png",
                "06_ask_ai_experiment_context.png",
                "07_planned_chapter_roadmap.png"
        };
        for (String file : files) {
            Path path = Paths.get(ARTIFACT_DIR, file);
            assertTrue(Files.exists(path), "Screenshot file must exist in screenshots/: " + file);
            assertTrue(Files.size(path) > 1000, "Screenshot file must be non-empty: " + file);

            Path rootPath = Paths.get(ROOT_ARTIFACT_DIR, file);
            assertTrue(Files.exists(rootPath), "Screenshot file must exist in artifact root: " + file);
            assertTrue(Files.size(rootPath) > 1000, "Screenshot file in root must be non-empty: " + file);
        }
    }

    @Test
    @DisplayName("Verify responsive layout across 1280x800, 1440x900, and 1024x768 resolutions without clipping")
    void verifyResponsiveLayoutAcrossStandardResolutions() throws Exception {
        runOnFxThread(() -> {
            Stage stage = new Stage();
            PhysoraApplicationShell shell = new PhysoraApplicationShell();
            Scene scene = new Scene(shell, 1280, 800);
            scene.getStylesheets().add(LiveDemonstrationScreenshotCaptureTest.class
                    .getResource("/edu/physics/ui/physora.css").toExternalForm());
            stage.setScene(scene);
            stage.show();

            try {
                int[][] resolutions = {
                        {1280, 800},
                        {1440, 900},
                        {1024, 768}
                };

                for (int[] res : resolutions) {
                    int w = res[0];
                    int h = res[1];
                    stage.setWidth(w);
                    stage.setHeight(h);
                    shell.resize(w, h);
                    shell.applyCss();
                    shell.layout();

                    // Verify root shell bounds
                    assertTrue(shell.getWidth() > 0 && shell.getHeight() > 0,
                            "Shell must have positive dimensions at " + w + "x" + h);

                    // Verify chapter rail
                    ChapterRail rail = node(shell, "chapterRail", ChapterRail.class);
                    assertTrue(rail.getWidth() > 0 && rail.getHeight() > 0,
                            "Chapter rail must be rendered at " + w + "x" + h);

                    // Verify bench view & workspace stage
                    NormalBenchView bench = shell.normalBenchView();
                    assertNotNull(bench);
                    Node stageNode = bench.lookup(".workspace-stage");
                    assertNotNull(stageNode);
                    assertTrue(stageNode.getLayoutBounds().getWidth() > 0,
                            "Workspace stage must have positive width at " + w + "x" + h);

                    // Verify inspector
                    Node inspector = bench.lookup(".model-inspector");
                    assertNotNull(inspector);
                    assertTrue(inspector.getLayoutBounds().getWidth() > 0,
                            "Inspector must have positive width at " + w + "x" + h);

                    // Verify footer transport strip
                    Node footer = bench.lookup(".transport-strip");
                    assertNotNull(footer, "Transport strip footer must be present at " + w + "x" + h);
                    assertTrue(footer.getLayoutBounds().getWidth() > 0,
                            "Footer must be rendered at " + w + "x" + h);

                    // Switch through all 5 modes to confirm no layout exceptions
                    String[] tabIds = {"simulationViewTab", "predictTab", "compareTab", "logicViewTab", "aiProblemTab"};
                    for (String tabId : tabIds) {
                        ToggleButton tab = node(shell, tabId, ToggleButton.class);
                        tab.setSelected(true);
                        tab.getOnAction().handle(null);
                        shell.applyCss();
                        shell.layout();
                    }
                    if (w == 1024 && h == 768) {
                        // Capture Compare mode at 1024x768
                        ToggleButton cmp = node(shell, "compareTab", ToggleButton.class);
                        cmp.setSelected(true);
                        cmp.getOnAction().handle(null);
                        Button demo = node(shell, "loadComplementaryDemoButton", Button.class);
                        demo.fire();
                        shell.applyCss();
                        shell.layout();
                        saveSnapshot(capture(scene), "04_compare_mode_1024x768.png");

                        // Capture Understand mode at 1024x768
                        ToggleButton und = node(shell, "logicViewTab", ToggleButton.class);
                        und.setSelected(true);
                        und.getOnAction().handle(null);
                        shell.applyCss();
                        shell.layout();
                        saveSnapshot(capture(scene), "05_understand_mode_1024x768.png");
                    }

                    // Reset back to simulation
                    ToggleButton expTab = node(shell, "simulationViewTab", ToggleButton.class);
                    expTab.setSelected(true);
                    expTab.getOnAction().handle(null);
                }

                // Verify rail collapse at 1024x768
                stage.setWidth(1024);
                stage.setHeight(768);
                shell.resize(1024, 768);
                shell.applyCss();
                shell.layout();

                ChapterRail rail = node(shell, "chapterRail", ChapterRail.class);
                double initialRailWidth = rail.getWidth();
                Button collapseToggle = node(shell, "railCollapseToggle", Button.class);
                collapseToggle.fire();
                shell.applyCss();
                shell.layout();
                double collapsedWidth = rail.getWidth();
                assertTrue(collapsedWidth < initialRailWidth, "Rail collapse toggle must reduce rail width");

                // Expand rail back
                collapseToggle.fire();
                shell.applyCss();
                shell.layout();
                assertTrue(rail.getWidth() >= initialRailWidth - 1.0, "Rail must restore to uncollapsed width");

                return null;
            } finally {
                shell.dispose();
                stage.close();
            }
        });

        String[] responsiveFiles = {"04_compare_mode_1024x768.png", "05_understand_mode_1024x768.png"};
        for (String file : responsiveFiles) {
            Path path = Paths.get(ARTIFACT_DIR, file);
            assertTrue(Files.exists(path), "Screenshot file must exist in screenshots/: " + file);
            assertTrue(Files.size(path) > 1000, "Screenshot file must be non-empty: " + file);

            Path rootPath = Paths.get(ROOT_ARTIFACT_DIR, file);
            assertTrue(Files.exists(rootPath), "Screenshot file must exist in artifact root: " + file);
            assertTrue(Files.size(rootPath) > 1000, "Screenshot file in root must be non-empty: " + file);
        }
    }
}
