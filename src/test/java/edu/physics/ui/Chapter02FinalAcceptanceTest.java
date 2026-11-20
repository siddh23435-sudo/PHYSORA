package edu.physics.ui;

import edu.physics.relative.ReferenceFrame;
import edu.physics.relative.TwoProjectilePursuitModel;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
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

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Chapter 02 UI Final Acceptance Audit: Live Navigation Protocol & Responsive Scaling")
class Chapter02FinalAcceptanceTest {

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

    private static void waitFrames(int count) throws Exception {
        for (int i = 0; i < count; i++) {
            Thread.sleep(25);
            runOnFxThread(() -> null);
        }
    }

    @Test
    @DisplayName("Protocol 1: Reproduce user 9-step navigation sequence on actual running application shell")
    void testExactNineStepNavigationSequenceOnRunningApp() throws Exception {
        AtomicReference<PhysoraApplicationShell> shellRef = new AtomicReference<>();
        AtomicReference<Scene> sceneRef = new AtomicReference<>();

        runOnFxThread(() -> {
            Stage stage = new Stage();
            stageRef.set(stage);

            PhysoraApplicationShell shell = new PhysoraApplicationShell();
            shellRef.set(shell);

            Scene scene = new Scene(shell, 1440, 810);
            scene.getStylesheets().add(getClass().getResource("/edu/physics/ui/physora.css").toExternalForm());
            stage.setScene(scene);
            stage.show();
            sceneRef.set(scene);
            return null;
        });
        waitFrames(5);

        PhysoraApplicationShell shell = shellRef.get();

        // STEP 1: Open PHYSORA
        runOnFxThread(() -> {
            int chNum = shell.chapterRail().selectedChapter().number();
            Node center = shell.getCenter();
            System.out.println("[AUDIT STEP 1] Open PHYSORA -> Selected Chapter: " + chNum +
                    ", Center: " + center.getClass().getSimpleName());
            assertEquals(1, chNum, "Step 1: Expected Chapter 01 Kinematics selected");
            assertSame(shell.normalBenchView(), center, "Step 1: Expected NormalBenchView active");
            return null;
        });

        // STEP 2: Select Chapter 02 — Relative Motion
        runOnFxThread(() -> {
            shell.selectChapter(2);
            int chNum = shell.chapterRail().selectedChapter().number();
            Node center = shell.getCenter();
            System.out.println("[AUDIT STEP 2] Select Chapter 02 -> Selected Chapter: " + chNum +
                    ", Center: " + center.getClass().getSimpleName() + " (id=" + center.getId() + ")");
            assertEquals(2, chNum, "Step 2: Expected Chapter 02 Relative Motion selected");
            assertSame(shell.plannedChapterView(), center, "Step 2: Expected PlannedChapterView active");
            // Default experiment in Chapter 2 must be Experiment 2.1
            PlannedChapterView pv = shell.plannedChapterView();
            assertNotNull(pv.lookup("#relativeMotionWorkbenchView"), "Step 2: Expected RelativeMotionWorkbenchView present");
            return null;
        });
        waitFrames(3);

        // STEP 3: Select Experiment 2.1 — Two-Projectile
        runOnFxThread(() -> {
            PlannedChapterView pv = shell.plannedChapterView();
            Button btn21 = (Button) pv.lookup("#btnExpTwoProjectile");
            assertNotNull(btn21);
            btn21.fire();
            System.out.println("[AUDIT STEP 3] Click Experiment 2.1 -> Active: RelativeMotionWorkbenchView");
            assertNotNull(pv.lookup("#relativeMotionWorkbenchView"), "Step 3: RelativeMotionWorkbenchView must be active");
            return null;
        });
        waitFrames(3);

        // STEP 4: Select Experiment 2.2 — River-Boat
        runOnFxThread(() -> {
            PlannedChapterView pv = shell.plannedChapterView();
            Button btn22 = (Button) pv.lookup("#btnExpRiverBoat");
            assertNotNull(btn22);
            btn22.fire();

            int chNum = shell.chapterRail().selectedChapter().number();
            Node center = shell.getCenter();
            System.out.println("[AUDIT STEP 4] Click Experiment 2.2 -> Selected Chapter: " + chNum +
                    ", Shell Center: " + center.getClass().getSimpleName());
            assertEquals(2, chNum, "Step 4: Must remain in Chapter 02");
            assertSame(shell.plannedChapterView(), center, "Step 4: Must remain in PlannedChapterView, NOT Kinematics!");
            assertNotSame(shell.normalBenchView(), center, "Step 4: Must NOT navigate to Kinematics!");
            return null;
        });
        waitFrames(3);

        // STEP 5: Confirm River-Boat workbench is visible
        runOnFxThread(() -> {
            PlannedChapterView pv = shell.plannedChapterView();
            Node rbView = pv.lookup("#riverBoatWorkbenchView");
            System.out.println("[AUDIT STEP 5] Confirm River-Boat workbench visible -> rbView=" +
                    (rbView != null ? rbView.getClass().getSimpleName() : "NULL"));
            assertNotNull(rbView, "Step 5: RiverBoatWorkbenchView must be visible in the scene graph");
            assertTrue(rbView.isVisible(), "Step 5: RiverBoatWorkbenchView must be marked visible");
            return null;
        });

        // STEP 6: Switch back to Experiment 2.1
        runOnFxThread(() -> {
            PlannedChapterView pv = shell.plannedChapterView();
            Button btn21 = (Button) pv.lookup("#btnExpTwoProjectile");
            btn21.fire();
            System.out.println("[AUDIT STEP 6] Switch back to Experiment 2.1 -> Active: RelativeMotionWorkbenchView");
            assertNotNull(pv.lookup("#relativeMotionWorkbenchView"), "Step 6: RelativeMotionWorkbenchView must be active");
            return null;
        });
        waitFrames(3);

        // STEP 7: Switch again to River-Boat
        runOnFxThread(() -> {
            PlannedChapterView pv = shell.plannedChapterView();
            Button btn22 = (Button) pv.lookup("#btnExpRiverBoat");
            btn22.fire();
            System.out.println("[AUDIT STEP 7] Switch again to River-Boat -> Active: RiverBoatWorkbenchView");
            assertNotNull(pv.lookup("#riverBoatWorkbenchView"), "Step 7: RiverBoatWorkbenchView must be active");
            assertSame(shell.plannedChapterView(), shell.getCenter(), "Step 7: Shell center must be PlannedChapterView");
            return null;
        });
        waitFrames(3);

        // STEP 8: Return explicitly to Chapter 01 — Kinematics
        runOnFxThread(() -> {
            PlannedChapterView pv = shell.plannedChapterView();
            Button returnBtn = (Button) pv.lookup("#returnToKinematicsButton");
            assertNotNull(returnBtn);
            returnBtn.fire();

            int chNum = shell.chapterRail().selectedChapter().number();
            Node center = shell.getCenter();
            System.out.println("[AUDIT STEP 8] Click Return to Kinematics -> Selected Chapter: " + chNum +
                    ", Shell Center: " + center.getClass().getSimpleName());
            assertEquals(1, chNum, "Step 8: ChapterRail must update to Chapter 1 Kinematics");
            assertSame(shell.normalBenchView(), center, "Step 8: Shell center must return to NormalBenchView");
            return null;
        });
        waitFrames(3);

        // STEP 9: Re-enter Chapter 02 and select River-Boat again
        runOnFxThread(() -> {
            shell.selectChapter(2);
            PlannedChapterView pv = shell.plannedChapterView();
            Button btn22 = (Button) pv.lookup("#btnExpRiverBoat");
            btn22.fire();

            int chNum = shell.chapterRail().selectedChapter().number();
            Node center = shell.getCenter();
            System.out.println("[AUDIT STEP 9] Re-enter Ch 2 & select River-Boat -> Selected Chapter: " + chNum +
                    ", Shell Center: " + center.getClass().getSimpleName() + ", Active: RiverBoatWorkbenchView");
            assertEquals(2, chNum, "Step 9: Chapter 02 must be selected");
            assertSame(shell.plannedChapterView(), center, "Step 9: Shell center must be PlannedChapterView");
            assertNotNull(pv.lookup("#riverBoatWorkbenchView"), "Step 9: RiverBoatWorkbenchView must be active");
            return null;
        });
    }

    @Test
    @DisplayName("Protocol 2: Responsive layout verification and screenshot evidence across 4 mandatory viewports")
    void testResponsiveLayoutAndCanvasResizabilityAcrossAllViewports() throws Exception {
        AtomicReference<PhysoraApplicationShell> shellRef = new AtomicReference<>();
        AtomicReference<Scene> sceneRef = new AtomicReference<>();
        AtomicReference<Stage> localStageRef = new AtomicReference<>();

        runOnFxThread(() -> {
            Stage stage = new Stage();
            localStageRef.set(stage);
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
        waitFrames(10);

        PhysoraApplicationShell shell = shellRef.get();
        Scene scene = sceneRef.get();
        Stage stage = localStageRef.get();

        // VIEWPORT 1: 1600 x 900
        runOnFxThread(() -> {
            stage.setWidth(1600);
            stage.setHeight(900);
            shell.selectChapter(2);
            shell.plannedChapterView().selectChapter2Experiment(1); // Two-Projectile
            RelativeMotionSimulationController c = shell.plannedChapterView().relativeMotionWorkbench().controller();
            c.setModel(TwoProjectilePursuitModel.flagshipCollisionPreset());
            c.setReferenceFrame(ReferenceFrame.GROUND);
            c.seek(1.25);
            shell.applyCss();
            shell.layout();
            return null;
        });
        waitFrames(8);

        // Assert resizability at 1600x900
        double[] canvasWidths = new double[4];
        runOnFxThread(() -> {
            Node canvas = shell.lookup("#relativeMotionWorkbenchView").lookup("Canvas");
            assertNotNull(canvas);
            canvasWidths[0] = canvas.getBoundsInLocal().getWidth();
            System.out.println("[VIEWPORT 1600x900] Canvas width: " + canvasWidths[0] + ", height: " + canvas.getBoundsInLocal().getHeight());
            assertTrue(canvasWidths[0] >= 700.0, "Canvas width at 1600x900 should be >= 700px, actual: " + canvasWidths[0]);
            return null;
        });
        saveSnapshot(capture(scene), "audit_01_ground_1600x900.png");

        // Observer A at 1600x900
        runOnFxThread(() -> {
            RelativeMotionSimulationController c = shell.plannedChapterView().relativeMotionWorkbench().controller();
            c.setReferenceFrame(ReferenceFrame.OBSERVER_A);
            shell.applyCss();
            shell.layout();
            return null;
        });
        waitFrames(5);
        saveSnapshot(capture(scene), "audit_02_observer_a_1600x900.png");

        // Observer B at 1600x900
        runOnFxThread(() -> {
            RelativeMotionSimulationController c = shell.plannedChapterView().relativeMotionWorkbench().controller();
            c.setReferenceFrame(ReferenceFrame.OBSERVER_B);
            shell.applyCss();
            shell.layout();
            return null;
        });
        waitFrames(5);
        saveSnapshot(capture(scene), "audit_03_observer_b_1600x900.png");

        // Collision moment at 1600x900
        runOnFxThread(() -> {
            RelativeMotionSimulationController c = shell.plannedChapterView().relativeMotionWorkbench().controller();
            c.seek(2.50);
            shell.applyCss();
            shell.layout();
            return null;
        });
        waitFrames(5);
        saveSnapshot(capture(scene), "audit_04_observer_collision_1600x900.png");

        // River-Boat at 1600x900
        runOnFxThread(() -> {
            shell.plannedChapterView().selectChapter2Experiment(2);
            shell.plannedChapterView().riverBoatWorkbench().controller().seek(12.5);
            shell.applyCss();
            shell.layout();
            return null;
        });
        waitFrames(6);
        saveSnapshot(capture(scene), "audit_05_riverboat_1600x900.png");

        // VIEWPORT 2: 1440 x 900
        runOnFxThread(() -> {
            stage.setWidth(1440);
            stage.setHeight(900);
            shell.applyCss();
            shell.layout();
            return null;
        });
        waitFrames(8);
        runOnFxThread(() -> {
            Node canvas = shell.lookup("#riverBoatWorkbenchView").lookup("Canvas");
            assertNotNull(canvas);
            canvasWidths[1] = canvas.getBoundsInLocal().getWidth();
            System.out.println("[VIEWPORT 1440x900] River-Boat Canvas width: " + canvasWidths[1]);
            assertTrue(canvasWidths[1] > 400.0);
            return null;
        });
        saveSnapshot(capture(scene), "audit_06_riverboat_1440x900.png");

        // VIEWPORT 3: 1366 x 768
        runOnFxThread(() -> {
            stage.setWidth(1366);
            stage.setHeight(768);
            shell.applyCss();
            shell.layout();
            return null;
        });
        waitFrames(8);
        runOnFxThread(() -> {
            Node canvas = shell.lookup("#riverBoatWorkbenchView").lookup("Canvas");
            assertNotNull(canvas);
            canvasWidths[2] = canvas.getBoundsInLocal().getWidth();
            System.out.println("[VIEWPORT 1366x768] River-Boat Canvas width: " + canvasWidths[2]);
            assertTrue(canvasWidths[2] > 400.0);
            return null;
        });
        saveSnapshot(capture(scene), "audit_07_riverboat_1366x768.png");

        // VIEWPORT 4: 1280 x 720 (Equivalent to 1080p at 150% Windows scaling)
        runOnFxThread(() -> {
            stage.setWidth(1280);
            stage.setHeight(720);
            shell.applyCss();
            shell.layout();
            return null;
        });
        waitFrames(8);

        // Verify Two-Projectile at 1280x720
        runOnFxThread(() -> {
            shell.plannedChapterView().selectChapter2Experiment(1);
            shell.plannedChapterView().relativeMotionWorkbench().controller().setReferenceFrame(ReferenceFrame.GROUND);
            shell.plannedChapterView().relativeMotionWorkbench().controller().seek(1.25);
            shell.applyCss();
            shell.layout();
            return null;
        });
        waitFrames(8);
        runOnFxThread(() -> {
            Node canvas = shell.lookup("#relativeMotionWorkbenchView").lookup("Canvas");
            assertNotNull(canvas);
            canvasWidths[3] = canvas.getBoundsInLocal().getWidth();
            System.out.println("[VIEWPORT 1280x720] Ground Canvas width: " + canvasWidths[3]);
            assertTrue(canvasWidths[3] > 400.0);
            // Verify that canvas dynamically adjusted between 1600 and 1280
            assertTrue(canvasWidths[0] > canvasWidths[3],
                    "Canvas must be genuinely resizable: 1600px width (" + canvasWidths[0] +
                            ") must be > 1280px width (" + canvasWidths[3] + ")");
            return null;
        });
        saveSnapshot(capture(scene), "audit_08_ground_1280x720.png");

        // Verify River-Boat at 1280x720 with scrolled sidebar
        runOnFxThread(() -> {
            shell.plannedChapterView().selectChapter2Experiment(2);
            RiverBoatWorkbenchView rbView = shell.plannedChapterView().riverBoatWorkbench();
            rbView.controller().seek(12.5);

            // Find ScrollPane and verify vertical scroll capability
            ScrollPane sp = (ScrollPane) rbView.lookup(".scroll-pane");
            assertNotNull(sp, "Sidebar must be enclosed in ScrollPane");
            sp.setVvalue(0.5); // Scroll halfway to prove independent vertical scrolling

            shell.applyCss();
            shell.layout();
            return null;
        });
        waitFrames(8);
        saveSnapshot(capture(scene), "audit_09_riverboat_1280x720_scrolled.png");
    }
}

