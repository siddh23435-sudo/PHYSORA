package edu.physics.ui;

import edu.physics.ai.ProviderMode;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("Ask AI Reproduction Visual Capture Test")
class AskAiVisualCaptureTest {

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

    @Test
    @DisplayName("Capture working Ask AI solution showing 5.00 m maximum height")
    @SuppressWarnings("unchecked")
    void captureAskAiReproductionSuccess() throws Exception {
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
            stage.setTitle("PHYSORA — Ask AI Verification");
            stage.show();
            stageRef.set(stage);
            return null;
        });

        // 1. Switch to Kinematics Ask AI mode
        runOnFxThread(() -> {
            PhysoraApplicationShell shell = shellRef.get();
            shell.selectChapter(1);
            ToggleButton aiTab = (ToggleButton) shell.lookup("#aiProblemTab");
            assertNotNull(aiTab, "aiProblemTab must exist");
            aiTab.fire();
            return null;
        });

        Thread.sleep(150);

        // 2. Configure OFFLINE mode for deterministic instant execution and submit the question
        String question = "A ball is kicked from level ground with an initial speed of 20 m/s at an angle of 30\u00b0 above the horizontal. Taking gravity (g) as 10 m/s\u00b2, what is the maximum height the ball reaches?";

        runOnFxThread(() -> {
            PhysoraApplicationShell shell = shellRef.get();
            ComboBox<ProviderMode> modeSelector = (ComboBox<ProviderMode>) shell.lookup("#aiModeSelector");
            assertNotNull(modeSelector, "aiModeSelector must exist");
            modeSelector.setValue(ProviderMode.OFFLINE);

            TextArea questionInput = (TextArea) shell.lookup("#aiQuestionInput");
            assertNotNull(questionInput, "aiQuestionInput must exist");
            questionInput.setText(question);

            Button submitButton = (Button) shell.lookup("#aiSubmitButton");
            assertNotNull(submitButton, "aiSubmitButton must exist");
            submitButton.fire();
            return null;
        });

        // 3. Wait for execution to complete
        waitUntilFx(() -> {
            PhysoraApplicationShell shell = shellRef.get();
            Button submitButton = (Button) shell.lookup("#aiSubmitButton");
            Label status = (Label) shell.lookup("#aiStatus");
            return submitButton != null && !submitButton.isDisabled()
                    && status != null && "Interpretation, deterministic solution, and explanation passed validation.".equals(status.getText());
        });

        // 4. Verify output content
        runOnFxThread(() -> {
            PhysoraApplicationShell shell = shellRef.get();
            Label status = (Label) shell.lookup("#aiStatus");
            TextArea output = (TextArea) shell.lookup("#aiSolutionOutput");

            assertFalse(status.getStyleClass().contains("ai-status-error"), "Status should not have error styling");
            String outputText = output.getText();
            assertNotNull(outputText);
            assertTrue(outputText.contains("MAXIMUM_HEIGHT"), "Must contain target MAXIMUM_HEIGHT");
            assertTrue(outputText.contains("maximumHeight = 5.0") || outputText.contains("5.00"),
                    "Output must contain calculated 5.00 m, got:\n" + outputText);
            return null;
        });

        Thread.sleep(200);

        // 5. Capture screenshot
        BufferedImage image = capture(sceneRef.get());
        saveSnapshot(image, "ask_ai_reproduction_success.png");

        System.out.println("Screenshot captured successfully: ask_ai_reproduction_success.png");
    }
}
