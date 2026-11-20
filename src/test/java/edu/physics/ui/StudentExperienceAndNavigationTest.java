package edu.physics.ui;

import edu.physics.problem.PhysicsCapabilityRegistry;
import edu.physics.problem.PhysicsCapabilityRegistry.ChapterCapability;
import edu.physics.problem.PhysicsCapabilityRegistry.ChapterStatus;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;

class StudentExperienceAndNavigationTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxTestRuntime.ensureStarted();
    }

    private static <T> T runOnFxThread(Callable<T> work) throws Exception {
        return FxTestRuntime.runOnFxThread(work);
    }

    @FunctionalInterface
    private interface ApplicationAction<T> {
        T run(Stage stage, PhysoraApplicationShell shell, Parent root) throws Exception;
    }

    private static <T> T withApplication(ApplicationAction<T> action) throws Exception {
        return runOnFxThread(() -> {
            Stage stage = new Stage();
            PhysoraApplicationShell shell = new PhysoraApplicationShell();
            Scene scene = new Scene(shell, 1200, 780);
            scene.getStylesheets().add(StudentExperienceAndNavigationTest.class
                    .getResource("/edu/physics/ui/physora.css").toExternalForm());
            stage.setScene(scene);
            stage.show();
            try {
                return action.run(stage, shell, shell);
            } finally {
                shell.dispose();
                stage.close();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T extends Node> T node(Parent parent, String id, Class<T> type) {
        Node found = parent.lookup("#" + id);
        assertNotNull(found, "Node with id #" + id + " must be present in the scene graph");
        assertTrue(type.isInstance(found), "Node #" + id + " must be an instance of " + type.getSimpleName());
        return (T) found;
    }

    @Test
    void eightChaptersFrozenInCurriculumAndCapabilityRegistry() {
        List<ChapterCapability> chapters = PhysicsCapabilityRegistry.curriculumChapters();

        // Exactly eight chapters
        assertEquals(8, chapters.size(), "Curriculum must contain exactly eight chapters");

        // Chapter 1: Kinematics (AVAILABLE)
        ChapterCapability ch1 = chapters.get(0);
        assertEquals(1, ch1.number());
        assertEquals("kinematics", ch1.id());
        assertEquals("Kinematics", ch1.title());
        assertEquals(ChapterStatus.AVAILABLE, ch1.status());
        assertTrue(ch1.isAvailable());

        // Chapter 2: Relative Motion is now LIVE
        ChapterCapability ch2 = chapters.get(1);
        assertEquals(2, ch2.number());
        assertEquals("relative-motion", ch2.id());
        assertEquals("Relative Motion", ch2.title());
        assertEquals(ChapterStatus.AVAILABLE, ch2.status(), "Chapter 2 must be marked AVAILABLE (LIVE)");
        assertTrue(ch2.isAvailable());

        // Chapters 3 through 8: PLANNED
        for (int i = 2; i < 8; i++) {
            ChapterCapability cap = chapters.get(i);
            assertEquals(i + 1, cap.number());
            assertEquals(ChapterStatus.PLANNED, cap.status(),
                    "Chapter " + cap.number() + " (" + cap.title() + ") must be marked PLANNED");
            assertFalse(cap.isAvailable());
        }

        // Expected titles in exact order
        assertEquals("Relative Motion", chapters.get(1).title());
        assertEquals("Laws of Motion", chapters.get(2).title());
        assertEquals("Work & Energy", chapters.get(3).title());
        assertEquals("Momentum & Collisions", chapters.get(4).title());
        assertEquals("Circular Motion", chapters.get(5).title());
        assertEquals("Rotation", chapters.get(6).title());
        assertEquals("Oscillations", chapters.get(7).title());

        // Explicitly excluded chapters must NOT be present
        for (ChapterCapability c : chapters) {
            assertFalse(c.title().toLowerCase().contains("solid"), "Solids must be excluded");
            assertFalse(c.title().toLowerCase().contains("fluid"), "Fluids must be excluded");
        }
    }

    @Test
    void chapterRailCollapseAndExpandToggle() throws Exception {
        runOnFxThread(() -> {
            ChapterRail rail = new ChapterRail();
            assertFalse(rail.isCollapsed(), "Rail should start expanded");
            assertEquals(232.0, rail.getPrefWidth(), 1.0);

            Button toggle = (Button) rail.lookup("#railCollapseToggle");
            assertNotNull(toggle);
            assertEquals("◀", toggle.getText());

            // Collapse the rail
            rail.setCollapsed(true);
            assertTrue(rail.isCollapsed());
            assertEquals(64.0, rail.getPrefWidth(), 1.0);
            assertEquals("▶", toggle.getText());

            // Expand the rail
            rail.setCollapsed(false);
            assertFalse(rail.isCollapsed());
            assertEquals(232.0, rail.getPrefWidth(), 1.0);
            assertEquals("◀", toggle.getText());
            return null;
        });
    }

    @Test
    void plannedChapterNavigationDoesNotLaunchBrokenSimulationAndPreservesKinematicsState() throws Exception {
        withApplication((stage, shell, root) -> {
            ChapterRail rail = shell.chapterRail();
            ProjectileSimulationController controller = shell.controller();

            // Set custom kinematics parameters in Chapter 1
            controller.launchSpeedProperty().set(35.0);
            controller.launchAngleProperty().set(60.0);
            controller.initialXProperty().set(5.0);
            controller.seek(0.4);

            assertEquals(35.0, controller.launchSpeedProperty().get());
            assertEquals(0.4, controller.simulationTimeProperty().get(), 1e-9);

            // Navigate to Chapter 2: Relative Motion (Planned)
            rail.selectChapter(2);
            assertEquals(2, rail.selectedChapter().number());

            // Center of shell must now be PlannedChapterView
            Node centerView = shell.getCenter();
            assertNotNull(centerView);
            assertEquals("plannedChapterView", centerView.getId());

            // Return button must be visible
            Button returnBtn = node(shell, "returnToKinematicsButton", Button.class);
            assertTrue(returnBtn.isVisible());

            // Crucially: simulation controller parameters and clock were NOT destroyed
            assertEquals(35.0, controller.launchSpeedProperty().get());
            assertEquals(60.0, controller.launchAngleProperty().get());
            assertEquals(5.0, controller.initialXProperty().get());
            assertEquals(0.4, controller.simulationTimeProperty().get(), 1e-9);

            // Return to Chapter 1: Kinematics via the return button
            returnBtn.fire();
            assertEquals(1, rail.selectedChapter().number());
            assertSame(shell.normalBenchView(), shell.getCenter());

            // Verify kinematics parameters remain 100% intact
            assertEquals(35.0, controller.launchSpeedProperty().get());
            assertEquals(60.0, controller.launchAngleProperty().get());
            assertEquals(5.0, controller.initialXProperty().get());
            assertEquals(0.4, controller.simulationTimeProperty().get(), 1e-9);
            return null;
        });
    }

    @Test
    void learningModeNavigationPreservesExperimentState() throws Exception {
        withApplication((stage, shell, root) -> {
            ProjectileSimulationController controller = shell.controller();
            controller.launchSpeedProperty().set(28.0);
            controller.initialYProperty().set(10.0);
            controller.seek(0.5);

            ToggleButton expTab = node(root, "simulationViewTab", ToggleButton.class);
            ToggleButton underTab = node(root, "logicViewTab", ToggleButton.class);
            ToggleButton aiTab = node(root, "aiProblemTab", ToggleButton.class);

            Node simView = node(root, "simulationView", Node.class);
            Node logicView = node(root, "logicView", Node.class);
            aiTab.fire();
            Node aiView = node(root, "aiProblemView", Node.class);

            // Switch to UNDERSTAND (Logic)
            underTab.fire();
            assertTrue(logicView.isVisible());
            assertFalse(simView.isManaged());

            // Parameters and time intact
            assertEquals(28.0, controller.launchSpeedProperty().get());
            assertEquals(10.0, controller.initialYProperty().get());
            assertEquals(0.5, controller.simulationTimeProperty().get(), 1e-9);

            // Switch to ASK AI
            aiTab.fire();
            assertTrue(aiView.isVisible());
            assertFalse(simView.isManaged());

            // Switch back to EXPERIMENT
            expTab.fire();
            assertTrue(simView.isVisible());
            assertTrue(simView.isManaged());
            assertFalse(logicView.isManaged());

            assertEquals(28.0, controller.launchSpeedProperty().get());
            assertEquals(10.0, controller.initialYProperty().get());
            assertEquals(0.5, controller.simulationTimeProperty().get(), 1e-9);
            return null;
        });
    }

    @Test
    void simulationStatusTransitionsReflectActualPhysicsState() throws Exception {
        runOnFxThread(() -> {
            ProjectileSimulationController controller = new ProjectileSimulationController();

            // 1. Initial status is READY
            assertEquals(ProjectileSimulationController.SimulationStatus.READY, controller.status());

            // 2. Play begins -> PLAYING
            controller.play();
            assertEquals(ProjectileSimulationController.SimulationStatus.PLAYING, controller.status());

            // 3. Pause mid-trajectory -> PAUSED
            controller.advanceByElapsedSeconds(0.2);
            controller.pause();
            assertEquals(ProjectileSimulationController.SimulationStatus.PAUSED, controller.status());

            // 4. Seek to Apex -> AT APEX
            controller.seekToApex();
            assertEquals(ProjectileSimulationController.SimulationStatus.AT_APEX, controller.status());

            // 5. Seek to Landing -> LANDED
            controller.seekToLanding();
            assertEquals(ProjectileSimulationController.SimulationStatus.LANDED, controller.status());

            // 6. Restart animation -> READY
            controller.restartAnimation();
            assertEquals(ProjectileSimulationController.SimulationStatus.READY, controller.status());
            assertEquals(0.0, controller.simulationTimeProperty().get());

            // 7. Modifying parameters resets clock -> READY
            controller.play();
            controller.advanceByElapsedSeconds(0.3);
            assertEquals(ProjectileSimulationController.SimulationStatus.PLAYING, controller.status());
            controller.launchSpeedProperty().set(40.0);
            assertEquals(ProjectileSimulationController.SimulationStatus.READY, controller.status());
            assertEquals(0.0, controller.simulationTimeProperty().get());

            controller.dispose();
            return null;
        });
    }

    @Test
    void statusBadgesInSimulationViewAndTransportReflectStatusChanges() throws Exception {
        withApplication((stage, shell, root) -> {
            ProjectileSimulationController controller = shell.controller();
            Label simStatusBadge = node(root, "simulationStatusBadge", Label.class);
            Label transportStatusBadge = node(root, "transportStatusBadge", Label.class);

            // Initial: READY
            assertEquals("READY", simStatusBadge.getText());
            assertEquals("READY", transportStatusBadge.getText());
            assertTrue(simStatusBadge.getStyleClass().contains("status-ready"));

            // Play: PLAYING
            controller.play();
            assertEquals("PLAYING", simStatusBadge.getText());
            assertEquals("PLAYING", transportStatusBadge.getText());
            assertTrue(simStatusBadge.getStyleClass().contains("status-playing"));

            // Pause: PAUSED
            controller.advanceByElapsedSeconds(0.2);
            controller.pause();
            assertEquals("PAUSED", simStatusBadge.getText());
            assertEquals("PAUSED", transportStatusBadge.getText());
            assertTrue(simStatusBadge.getStyleClass().contains("status-paused"));

            // Seek to Apex: AT APEX
            controller.seekToApex();
            assertEquals("AT APEX", simStatusBadge.getText());
            assertEquals("AT APEX", transportStatusBadge.getText());
            assertTrue(simStatusBadge.getStyleClass().contains("status-apex"));

            // Restart: READY
            controller.restartAnimation();
            assertEquals("READY", simStatusBadge.getText());
            assertEquals("READY", transportStatusBadge.getText());
            assertTrue(simStatusBadge.getStyleClass().contains("status-ready"));
            return null;
        });
    }
}

