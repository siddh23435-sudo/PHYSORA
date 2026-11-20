package edu.physics.ui;

import edu.physics.ai.AIConversationService;
import edu.physics.ai.AIProblemWorkflow;
import edu.physics.ai.AIWorkflowResult;
import edu.physics.ai.ExplanationVerifier;
import edu.physics.ai.InterpretationDraft;
import edu.physics.ai.InterpretationRequest;
import edu.physics.ai.ProblemValidationResult;
import edu.physics.ai.ProblemValidator;
import edu.physics.ai.SolutionVerifier;
import edu.physics.ai.SolverOrchestrator;
import edu.physics.ai.local.LocalAIProvider;
import edu.physics.problem.GuidedDerivations;
import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.PhysicalUnit;
import edu.physics.problem.ProjectileProblem;
import edu.physics.problem.ProjectileProblemSolver;
import edu.physics.projectile.ProjectileMotion;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;

class GuidedLearningAndAiContextTest {

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
            scene.getStylesheets().add(GuidedLearningAndAiContextTest.class
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
    void allSixGuidedDerivationsBuiltWithSixStepSequence() {
        ProjectileMotion model = new ProjectileMotion(20.0, 45.0, 9.81, 0.0, 0.0, 0.0);
        List<GuidedDerivations.Derivation> derivations = GuidedDerivations.buildAll(model);

        assertEquals(6, derivations.size(), "Must generate exactly six guided derivations");

        GuidedDerivations.Topic[] expectedTopics = {
                GuidedDerivations.Topic.VELOCITY_COMPONENTS,
                GuidedDerivations.Topic.TIME_TO_APEX,
                GuidedDerivations.Topic.MAX_HEIGHT,
                GuidedDerivations.Topic.TIME_OF_FLIGHT,
                GuidedDerivations.Topic.HORIZONTAL_RANGE,
                GuidedDerivations.Topic.LANDING_VELOCITY
        };

        for (int i = 0; i < 6; i++) {
            GuidedDerivations.Derivation d = derivations.get(i);
            assertEquals(expectedTopics[i], d.topic());
            assertNotNull(d.title());
            assertNotNull(d.targetSymbol());
            assertNotNull(d.finalResultFormatted());

            List<GuidedDerivations.DerivationStep> steps = d.steps();
            assertEquals(6, steps.size(), "Each derivation must contain exactly 6 pedagogical steps");

            assertEquals(GuidedDerivations.StepKind.GIVEN, steps.get(0).kind());
            assertEquals(GuidedDerivations.StepKind.PRINCIPLE, steps.get(1).kind());
            assertEquals(GuidedDerivations.StepKind.FORMULA, steps.get(2).kind());
            assertEquals(GuidedDerivations.StepKind.SUBSTITUTION, steps.get(3).kind());
            assertEquals(GuidedDerivations.StepKind.CALCULATION, steps.get(4).kind());
            assertEquals(GuidedDerivations.StepKind.FINAL_ANSWER, steps.get(5).kind());

            for (GuidedDerivations.DerivationStep s : steps) {
                assertFalse(s.content().isBlank(), "Content in step " + s.kind() + " must not be blank");
                assertFalse(s.bulletDetails().isEmpty(), "Bullet details in step " + s.kind() + " must be provided");
            }
        }
    }

    @Test
    void maxHeightDerivationDistinguishesRiseAboveLaunchFromHeightAboveGround() {
        // Elevated launch from y0 = 15m with vy0 = 10 m/s, g = 9.81 m/s^2
        // deltaY = 10^2 / (2 * 9.81) = 100 / 19.62 ≈ 5.0968 m
        // H_max = 15 + 5.0968 ≈ 20.10 m
        ProjectileMotion cliff = new ProjectileMotion(20.0, 30.0, 9.81, 0.0, 15.0, 0.0);
        GuidedDerivations.Derivation d = GuidedDerivations.deriveMaxHeight(cliff);

        assertEquals(GuidedDerivations.Topic.MAX_HEIGHT, d.topic());
        assertTrue(d.finalResultFormatted().contains("20.10 m"));

        // Step 1: Given shows y0 = 15.00 m
        assertTrue(d.steps().get(0).bulletDetails().stream().anyMatch(b -> b.contains("15.00 m")));

        // Step 2: Principle explains distinction between rise above platform and height above ground
        assertTrue(d.steps().get(1).content().contains("H_max = y0 + Δy_apex"));

        // Step 4: Substitution shows y0 = 15.00
        assertTrue(d.steps().get(3).content().contains("15.00"));

        // Step 6: Final Answer shows both values
        assertTrue(d.steps().get(5).bulletDetails().stream().anyMatch(b -> b.contains("20.10 m")));
        assertTrue(d.steps().get(5).bulletDetails().stream().anyMatch(b -> b.contains("5.10 m")));
    }

    @Test
    void zeroGravityAndEdgeCasesHandledInDerivations() {
        // 1. g = 0, vy0 > 0: Unbounded upward ascent
        ProjectileMotion zeroGUp = new ProjectileMotion(20.0, 45.0, 0.0, 0.0, 5.0, 0.0);
        GuidedDerivations.Derivation apexZeroGUp = GuidedDerivations.deriveTimeToApex(zeroGUp);
        assertTrue(apexZeroGUp.finalResultFormatted().contains("Unbounded"));
        assertTrue(apexZeroGUp.steps().get(4).bulletDetails().stream().anyMatch(b -> b.contains("strictly positive")));
        GuidedDerivations.Derivation heightZeroGUp = GuidedDerivations.deriveMaxHeight(zeroGUp);
        assertTrue(heightZeroGUp.finalResultFormatted().contains("Unbounded"));

        // 2. g = 0, vy0 = 0: Constant elevation at launch
        ProjectileMotion zeroGFlat = new ProjectileMotion(20.0, 0.0, 0.0, 0.0, 8.0, 0.0);
        GuidedDerivations.Derivation apexZeroGFlat = GuidedDerivations.deriveTimeToApex(zeroGFlat);
        assertTrue(apexZeroGFlat.finalResultFormatted().contains("0.00 s"));
        assertTrue(apexZeroGFlat.steps().get(4).bulletDetails().stream().anyMatch(b -> b.contains("constant height")));
        GuidedDerivations.Derivation heightZeroGFlat = GuidedDerivations.deriveMaxHeight(zeroGFlat);
        assertTrue(heightZeroGFlat.finalResultFormatted().contains("8.00 m"));
        assertTrue(heightZeroGFlat.steps().get(4).bulletDetails().stream().anyMatch(b -> b.contains("constant height")));

        // 3. g = 0, vy0 < 0: Downward motion in zero-g, highest at launch
        ProjectileMotion zeroGDown = new ProjectileMotion(20.0, -30.0, 0.0, 0.0, 10.0, 0.0);
        GuidedDerivations.Derivation apexZeroGDown = GuidedDerivations.deriveTimeToApex(zeroGDown);
        assertTrue(apexZeroGDown.finalResultFormatted().contains("0.00 s"));
        assertTrue(apexZeroGDown.steps().get(4).bulletDetails().stream().anyMatch(b -> b.contains("highest point occurred at the initial launch")));
        GuidedDerivations.Derivation heightZeroGDown = GuidedDerivations.deriveMaxHeight(zeroGDown);
        assertTrue(heightZeroGDown.finalResultFormatted().contains("10.00 m"));
        assertTrue(heightZeroGDown.steps().get(4).bulletDetails().stream().anyMatch(b -> b.contains("attained at the instant of launch")));

        // 4. g > 0, vy0 > 0: Normal ascent to future apex
        ProjectileMotion normalUp = new ProjectileMotion(20.0, 30.0, 9.81, 0.0, 0.0, 0.0);
        GuidedDerivations.Derivation apexNormal = GuidedDerivations.deriveTimeToApex(normalUp);
        assertTrue(apexNormal.finalResultFormatted().contains("1.02 s"));
        assertTrue(apexNormal.steps().get(5).title().contains("Future Apex"));
        GuidedDerivations.Derivation heightNormal = GuidedDerivations.deriveMaxHeight(normalUp);
        assertTrue(heightNormal.finalResultFormatted().contains("5.10 m"));

        // 5. g > 0, vy0 = 0: Horizontal launch under gravity, peak at t = 0
        ProjectileMotion horiz = new ProjectileMotion(20.0, 0.0, 9.81, 0.0, 12.0, 0.0);
        GuidedDerivations.Derivation apexHoriz = GuidedDerivations.deriveTimeToApex(horiz);
        assertTrue(apexHoriz.finalResultFormatted().contains("0.00 s"));
        assertTrue(apexHoriz.steps().get(3).bulletDetails().stream().anyMatch(b -> b.contains("Launch is horizontal")));
        GuidedDerivations.Derivation heightHoriz = GuidedDerivations.deriveMaxHeight(horiz);
        assertTrue(heightHoriz.finalResultFormatted().contains("12.00 m"));
        assertTrue(heightHoriz.steps().get(4).bulletDetails().stream().anyMatch(b -> b.contains("Peak altitude occurred at the launch")));

        // 6. g > 0, vy0 < 0: Downward launch under gravity, strictly decreasing
        ProjectileMotion down = new ProjectileMotion(20.0, -45.0, 9.81, 0.0, 15.0, 0.0);
        GuidedDerivations.Derivation apexDown = GuidedDerivations.deriveTimeToApex(down);
        assertTrue(apexDown.finalResultFormatted().contains("0.00 s"));
        assertTrue(apexDown.steps().get(3).bulletDetails().stream().anyMatch(b -> b.contains("Launch is directed downward")));
        GuidedDerivations.Derivation heightDown = GuidedDerivations.deriveMaxHeight(down);
        assertTrue(heightDown.finalResultFormatted().contains("15.00 m"));
        assertTrue(heightDown.steps().get(4).bulletDetails().stream().anyMatch(b -> b.contains("never ascends above launch")));

        // Cliff landing below launch point (y0 = 20, landing = -10)
        ProjectileMotion deepDrop = new ProjectileMotion(20.0, 45.0, 9.81, 0.0, 20.0, -10.0);
        GuidedDerivations.Derivation tofDrop = GuidedDerivations.deriveTimeOfFlight(deepDrop);
        assertFalse(tofDrop.finalResultFormatted().contains("No landing"));
        assertTrue(tofDrop.steps().get(1).content().contains("positive real quadratic root"));

        // Elevated landing higher than apex: negative discriminant
        ProjectileMotion unreachableLanding = new ProjectileMotion(10.0, 30.0, 9.81, 0.0, 0.0, 10.0);
        GuidedDerivations.Derivation tofUnreachable = GuidedDerivations.deriveTimeOfFlight(unreachableLanding);
        assertTrue(tofUnreachable.finalResultFormatted().contains("No landing"));
        assertTrue(tofUnreachable.steps().get(3).content().contains("D ="));

        // Zero gravity linear flight time
        ProjectileMotion zeroGLinear = new ProjectileMotion(20.0, -30.0, 0.0, 0.0, 20.0, 0.0);
        GuidedDerivations.Derivation tofZ = GuidedDerivations.deriveTimeOfFlight(zeroGLinear);
        assertFalse(tofZ.finalResultFormatted().contains("No landing"));
        assertTrue(tofZ.finalResultFormatted().contains("2.00 s"));
    }

    @Test
    void landingVelocityQuadrantAndAngleConventions() {
        // 1. Normal forward descending landing (vx > 0, vy_land < 0)
        ProjectileMotion forward = new ProjectileMotion(20.0, 45.0, 9.81, 0.0, 0.0, 0.0);
        GuidedDerivations.Derivation landFwd = GuidedDerivations.deriveLandingVelocity(forward);
        assertTrue(landFwd.finalResultFormatted().contains("20.00 m/s"));
        assertTrue(landFwd.finalResultFormatted().contains("-45.0°"));
        assertTrue(landFwd.finalResultFormatted().contains("45.0° below horizontal"));

        // 2. Pure vertical launch (vx = 0, angle = 90°)
        ProjectileMotion vertical = new ProjectileMotion(20.0, 90.0, 9.81, 0.0, 0.0, 0.0);
        GuidedDerivations.Derivation landVert = GuidedDerivations.deriveLandingVelocity(vertical);
        assertTrue(landVert.finalResultFormatted().contains("20.00 m/s"));
        assertTrue(landVert.finalResultFormatted().contains("-90.0°"));
        assertTrue(landVert.finalResultFormatted().contains("90.0° below horizontal"));
        assertTrue(landVert.steps().get(4).bulletDetails().stream().anyMatch(b -> b.contains("Vertical launch")));

        // 3. Backward launch (vx < 0, angle = 135°)
        ProjectileMotion backward = new ProjectileMotion(20.0, 135.0, 9.81, 0.0, 0.0, 0.0);
        GuidedDerivations.Derivation landBack = GuidedDerivations.deriveLandingVelocity(backward);
        assertTrue(landBack.finalResultFormatted().contains("20.00 m/s"));
        assertTrue(landBack.finalResultFormatted().contains("-135.0°"));
    }

    @Test
    void experimentSnapshotCapturesActiveParametersAndDeterministicSolverTruth() {
        ProjectileSimulationController controller = new ProjectileSimulationController();
        controller.launchSpeedProperty().set(25.0);
        controller.launchAngleProperty().set(30.0);
        controller.gravityProperty().set(9.81);
        controller.initialXProperty().set(2.0);
        controller.initialYProperty().set(8.0);
        controller.landingYProperty().set(3.0);
        controller.seek(0.5);

        ExperimentSnapshot snapshot = ExperimentSnapshot.fromController(controller, "01 · KINEMATICS", "Ideal Projectile Motion");

        assertEquals("kinematics", snapshot.chapterId());
        assertEquals("01 · KINEMATICS", snapshot.chapterTitle());
        assertEquals("Ideal Projectile Motion", snapshot.modelTitle());
        assertEquals(25.0, snapshot.initialSpeed(), 1e-9);
        assertEquals(30.0, snapshot.launchAngleDegrees(), 1e-9);
        assertEquals(9.81, snapshot.gravity(), 1e-9);
        assertEquals(2.0, snapshot.initialX(), 1e-9);
        assertEquals(8.0, snapshot.initialY(), 1e-9);
        assertEquals(3.0, snapshot.landingY(), 1e-9);
        assertEquals(0.5, snapshot.simulationTime(), 1e-9);
        assertEquals("PAUSED", snapshot.simulationStatus());

        assertTrue(snapshot.maximumHeight().isPresent());
        assertTrue(snapshot.horizontalRange().isPresent());
        assertTrue(snapshot.timeOfFlight().isPresent());

        // Format tests
        String summary = snapshot.formatSummary();
        assertTrue(summary.contains("v0 = 25.00 m/s"));
        assertTrue(summary.contains("θ = 30.0°"));
        assertTrue(summary.contains("launch (2.0, 8.0) m"));

        String solverStr = snapshot.formatSolverResults();
        assertTrue(solverStr.contains("H_max ="));
        assertTrue(solverStr.contains("Range ="));
        assertTrue(solverStr.contains("Flight Time ="));

        String promptContext = snapshot.toPromptContext();
        assertTrue(promptContext.contains("[ACTIVE EXPERIMENT CONTEXT]"));
        assertTrue(promptContext.contains("Deterministic Physics Solver Ground Truth:"));
        assertTrue(promptContext.contains("Do not fabricate different numerical values"));
    }

    @Test
    void projectileLogicViewInteractiveTopicAndStepNavigation() throws Exception {
        withApplication((stage, shell, root) -> {
            NormalBenchView bench = shell.normalBenchView();
            ToggleButton logicTab = node(root, "logicViewTab", ToggleButton.class);
            logicTab.fire();

            // Backward-compatible node assertions
            Label currentState = node(root, "logicCurrentState", Label.class);
            assertTrue(currentState.getText().contains("x 0.00 m"));
            assertTrue(currentState.getText().contains("y 0.00 m"));

            Label stepDetails = node(root, "logicStepDetails0", Label.class);
            assertTrue(stepDetails.getText().contains("Substitutions: "));
            assertTrue(stepDetails.getText().contains("Intermediate results: "));

            // Topic switching
            ToggleButton maxHBtn = node(root, "derivationTopic_MAX_HEIGHT", ToggleButton.class);
            maxHBtn.fire();
            assertTrue(maxHBtn.isSelected());

            // Step navigation
            Button nextBtn = node(root, "derivationNextStep", Button.class);
            Button prevBtn = node(root, "derivationPrevStep", Button.class);
            assertTrue(prevBtn.isDisabled(), "Previous step should be disabled on step 1");

            nextBtn.fire(); // Advance to Step 2
            assertFalse(prevBtn.isDisabled());

            // Toggle show all steps
            ToggleButton showAll = node(root, "derivationShowAllToggle", ToggleButton.class);
            showAll.fire();
            assertTrue(showAll.isSelected());
            assertTrue(prevBtn.isDisabled(), "Previous should be disabled when showing all steps");
            assertTrue(nextBtn.isDisabled(), "Next should be disabled when showing all steps");

            // Change parameter in model and verify derivation automatically updates
            shell.controller().launchSpeedProperty().set(30.0);
            return null;
        });
    }

    @Test
    void aiProblemViewUseCurrentExperimentAttachesSnapshotAndTracksFreshness() throws Exception {
        withApplication((stage, shell, root) -> {
            ToggleButton aiTab = node(root, "aiProblemTab", ToggleButton.class);
            aiTab.fire();

            AIProblemView aiView = shell.normalBenchView().aiProblemView();
            Node contextCard = node(root, "experimentContextCard", Node.class);
            Button useExperimentBtn = node(root, "useCurrentExperimentButton", Button.class);
            Node staleWarning = node(root, "experimentContextStaleWarning", Node.class);

            assertFalse(contextCard.isVisible(), "Context card should initially be hidden");

            // Attach experiment
            useExperimentBtn.fire();
            assertTrue(contextCard.isVisible(), "Context card should appear after clicking use experiment");
            assertNotNull(aiView.activeExperimentSnapshot());
            assertEquals(20.0, aiView.activeExperimentSnapshot().initialSpeed(), 1e-9);
            assertFalse(aiView.isContextStale(), "Context must be current upon initial attachment");
            assertFalse(staleWarning.isVisible(), "Stale warning must be hidden when context is fresh");

            // Change controller speed in bench -> context becomes STALE
            shell.controller().launchSpeedProperty().set(32.0);
            assertTrue(aiView.isContextStale(), "Modifying parameters must mark attached context as stale");
            assertTrue(staleWarning.isVisible(), "Stale warning must be displayed when parameters differ");
            assertEquals(20.0, aiView.activeExperimentSnapshot().initialSpeed(), 1e-9, "Attached snapshot is immutable");

            // Refresh context -> restored to CURRENT
            Button refreshBtn = node(root, "refreshExperimentContextButton", Button.class);
            refreshBtn.fire();
            assertFalse(aiView.isContextStale(), "Refreshing context must restore fresh/current status");
            assertFalse(staleWarning.isVisible(), "Stale warning must be hidden after refresh");
            assertEquals(32.0, aiView.activeExperimentSnapshot().initialSpeed(), 1e-9, "Snapshot updated to current value");

            // Click prompt chip and verify text area is populated
            TextArea questionArea = node(root, "aiQuestionInput", TextArea.class);
            List<Node> promptChips = root.lookupAll(".prompt-chip-btn").stream().toList();
            assertFalse(promptChips.isEmpty(), "Prompt chips must be present in experiment context card");
            ((Button) promptChips.get(0)).fire();
            assertFalse(questionArea.getText().isBlank(), "Clicking prompt chip must populate question input");

            // Clear context
            Button clearBtn = node(root, "clearExperimentContextButton", Button.class);
            clearBtn.fire();
            assertFalse(contextCard.isVisible(), "Context card should hide after clicking clear");
            assertNull(aiView.activeExperimentSnapshot());
            assertFalse(aiView.isContextStale());
            return null;
        });
    }

    @Test
    void exactIndependentQuestionSolvesDeterministically() {
        LocalAIProvider provider = new LocalAIProvider();
        ProblemValidator validator = new ProblemValidator();
        SolverOrchestrator orchestrator = new SolverOrchestrator(
                new ProjectileProblemSolver(), new SolutionVerifier());
        ExplanationVerifier explanationVerifier = new ExplanationVerifier();
        AIProblemWorkflow workflow = new AIProblemWorkflow(provider, validator, orchestrator, explanationVerifier);

        String question = "A ball is projected at 20 m/s at 45 degrees with g = 10 m/s^2. Find its maximum height and horizontal range.";
        AIWorkflowResult result = workflow.submit(new InterpretationRequest(question)).toCompletableFuture().join();

        assertEquals(AIWorkflowResult.Status.COMPLETE, result.status(), "Workflow must complete successfully");
        assertNotNull(result.orchestration());
        assertEquals(SolverOrchestrator.Status.VERIFIED, result.orchestration().status());

        // Analytical truth: H_max = 20^2 * sin^2(45) / (2 * 10) = 400 * 0.5 / 20 = 10.0 m
        // R = 20^2 * sin(90) / 10 = 400 * 1 / 10 = 40.0 m
        var parts = result.orchestration().canonicalSolution().partResults();
        assertEquals(2, parts.size());
        assertEquals(10.0, parts.get(0).steps().get(0).results().get("maximumHeight").value(), 1e-4);
        assertEquals(40.0, parts.get(1).steps().get(0).results().get("range").value(), 1e-4);
    }

    @Test
    void independentQuestionGravityIsolatedFromAttachedExperimentContext() {
        // Experiment bench snapshot uses standard g = 9.81 m/s^2
        ExperimentSnapshot snapshot = new ExperimentSnapshot(
                "kinematics", "01 · KINEMATICS", "Ideal Projectile Motion",
                20.0, 45.0, 9.81, 0.0, 0.0, 0.0, 2.88, "LANDED",
                OptionalDouble.of(10.19), OptionalDouble.of(40.77),
                OptionalDouble.of(2.88), OptionalDouble.of(1.44),
                40.77, 0.0, 14.14, -14.14);

        // Student's independent inquiry specifies g = 10 m/s^2
        String rawQuestion = "A ball is projected at 20 m/s at 45 degrees with g = 10 m/s^2. Find its maximum height and horizontal range.";
        String combinedPrompt = snapshot.toPromptContext() + "\n\n[STUDENT INQUIRY]\n" + rawQuestion;

        LocalAIProvider provider = new LocalAIProvider();
        ProblemValidator validator = new ProblemValidator();
        SolverOrchestrator orchestrator = new SolverOrchestrator(
                new ProjectileProblemSolver(), new SolutionVerifier());
        ExplanationVerifier explanationVerifier = new ExplanationVerifier();
        AIProblemWorkflow workflow = new AIProblemWorkflow(provider, validator, orchestrator, explanationVerifier);

        AIWorkflowResult result = workflow.submit(new InterpretationRequest(combinedPrompt)).toCompletableFuture().join();

        assertEquals(AIWorkflowResult.Status.COMPLETE, result.status());
        assertNotNull(result.orchestration());

        // Must solve using question parameter g = 10 m/s^2 (H_max = 10.00 m, Range = 40.00 m), NOT experiment g = 9.81 m/s^2
        var parts = result.orchestration().canonicalSolution().partResults();
        assertEquals(10.0, parts.get(0).steps().get(0).results().get("maximumHeight").value(), 1e-4);
        assertEquals(40.0, parts.get(1).steps().get(0).results().get("range").value(), 1e-4);

        // Verify attached experiment snapshot remains unmutated at g = 9.81 m/s^2
        assertEquals(9.81, snapshot.gravity(), 1e-9);
        assertEquals(10.19, snapshot.maximumHeight().getAsDouble(), 1e-2);
        assertEquals(40.77, snapshot.horizontalRange().getAsDouble(), 1e-2);
    }

    @Test
    void independentQuestion30DegreesYieldsCorrectHeightAndRange() {
        // Experiment bench snapshot uses standard v0 = 25 m/s, theta = 45 deg, g = 9.81 m/s^2
        ExperimentSnapshot snapshot = new ExperimentSnapshot(
                "kinematics", "01 · KINEMATICS", "Ideal Projectile Motion",
                25.0, 45.0, 9.81, 0.0, 0.0, 0.0, 3.60, "LANDED",
                OptionalDouble.of(15.93), OptionalDouble.of(63.71),
                OptionalDouble.of(3.60), OptionalDouble.of(1.80),
                63.71, 0.0, 17.68, -17.68);

        // Student's independent inquiry specifies v0 = 20 m/s, theta = 30 deg, g = 10 m/s^2
        String rawQuestion = "A ball is projected at 20 m/s at 30 degrees with g = 10 m/s^2. Find its maximum height and horizontal range.";
        String combinedPrompt = snapshot.toPromptContext() + "\n\n[STUDENT INQUIRY]\n" + rawQuestion;

        LocalAIProvider provider = new LocalAIProvider();
        ProblemValidator validator = new ProblemValidator();
        SolverOrchestrator orchestrator = new SolverOrchestrator(
                new ProjectileProblemSolver(), new SolutionVerifier());
        ExplanationVerifier explanationVerifier = new ExplanationVerifier();
        AIProblemWorkflow workflow = new AIProblemWorkflow(provider, validator, orchestrator, explanationVerifier);

        AIWorkflowResult result = workflow.submit(new InterpretationRequest(combinedPrompt)).toCompletableFuture().join();

        assertEquals(AIWorkflowResult.Status.COMPLETE, result.status());
        assertNotNull(result.orchestration());

        // For v0 = 20 m/s, theta = 30 deg, g = 10 m/s^2:
        // H_max = (v0^2 * sin^2(30)) / (2g) = 400 * 0.25 / 20 = 5.0 m
        // R = (v0^2 * sin(60)) / g = 400 * sqrt(3)/2 / 10 = 20 * sqrt(3) ~= 34.641016 m
        var parts = result.orchestration().canonicalSolution().partResults();
        assertEquals(5.0, parts.get(0).steps().get(0).results().get("maximumHeight").value(), 1e-4);
        assertEquals(20.0 * Math.sqrt(3.0), parts.get(1).steps().get(0).results().get("range").value(), 1e-4);

        // Verify attached experiment snapshot remains completely unmutated
        assertEquals(25.0, snapshot.initialSpeed(), 1e-9);
        assertEquals(45.0, snapshot.launchAngleDegrees(), 1e-9);
        assertEquals(9.81, snapshot.gravity(), 1e-9);
        assertEquals(15.93, snapshot.maximumHeight().getAsDouble(), 1e-2);
        assertEquals(63.71, snapshot.horizontalRange().getAsDouble(), 1e-2);
    }

    @Test
    void repeatedSubmissionsWithVaryingAnglesAndGravityValues() {
        LocalAIProvider provider = new LocalAIProvider();
        ProblemValidator validator = new ProblemValidator();
        SolverOrchestrator orchestrator = new SolverOrchestrator(
                new ProjectileProblemSolver(), new SolutionVerifier());
        AIProblemWorkflow workflow = new AIProblemWorkflow(provider, validator, orchestrator, new ExplanationVerifier());
        AIConversationService conversationService = new AIConversationService(workflow);

        // Submission 1: 45 deg, 20 m/s, g = 10 m/s^2 -> H = 10 m, R = 40 m
        var sub1 = conversationService.submit("A ball is projected at 20 m/s at 45 degrees with g = 10 m/s^2. Find its maximum height and horizontal range.");
        var snap1 = sub1.completion().join();
        assertTrue(snap1.applied());
        assertEquals(10.0, snap1.latest().orchestration().canonicalSolution().partResults().get(0).steps().get(0).results().get("maximumHeight").value(), 1e-4);
        assertEquals(40.0, snap1.latest().orchestration().canonicalSolution().partResults().get(1).steps().get(0).results().get("range").value(), 1e-4);

        // Submission 2: Revise to 30 deg, 20 m/s, g = 10 m/s^2 -> H = 5 m, R = 34.6410 m
        var snap2 = conversationService.revise(sub1.sessionId(), "A ball is projected at 20 m/s at 30 degrees with g = 10 m/s^2. Find its maximum height and horizontal range.").join();
        assertTrue(snap2.applied());
        assertEquals(5.0, snap2.latest().orchestration().canonicalSolution().partResults().get(0).steps().get(0).results().get("maximumHeight").value(), 1e-4);
        assertEquals(20.0 * Math.sqrt(3.0), snap2.latest().orchestration().canonicalSolution().partResults().get(1).steps().get(0).results().get("range").value(), 1e-4);

        // Submission 3: Revise to 60 deg, 20 m/s, g = 9.8 m/s^2 ->
        // H = 400 * 0.75 / 19.6 = 15.3061 m, R = 400 * sin(120) / 9.8 = 35.3480 m
        var snap3 = conversationService.revise(sub1.sessionId(), "A ball is projected at 20 m/s at 60 degrees with g = 9.8 m/s^2. Find its maximum height and horizontal range.").join();
        assertTrue(snap3.applied());
        double expectedH3 = (400.0 * Math.pow(Math.sin(Math.toRadians(60.0)), 2.0)) / (2.0 * 9.8);
        double expectedR3 = (400.0 * Math.sin(Math.toRadians(120.0))) / 9.8;
        assertEquals(expectedH3, snap3.latest().orchestration().canonicalSolution().partResults().get(0).steps().get(0).results().get("maximumHeight").value(), 1e-4);
        assertEquals(expectedR3, snap3.latest().orchestration().canonicalSolution().partResults().get(1).steps().get(0).results().get("range").value(), 1e-4);
    }

    @Test
    void missingLandingElevationAppliesDocumentedSameLevelAssumption() {
        LocalAIProvider provider = new LocalAIProvider();
        ProblemValidator validator = new ProblemValidator();

        String question = "A projectile is launched at 25 m/s at 30 degrees with g = 9.8 m/s^2. Find its horizontal range.";
        var draft = provider.interpret(new InterpretationRequest(question)).toCompletableFuture().join().content();
        ProblemValidationResult validation = validator.validate(question, draft);

        assertEquals(ProblemValidationResult.Status.READY, validation.status());
        assertTrue(validation.assumptions().stream().anyMatch(a -> a.contains("initial_y = landing_y = 0") || a.contains("horizontal ground")),
                "Must document standard flat-ground same-level assumption when landing elevation omitted");
        assertEquals(0.0, validation.canonicalProblem().initialY(), 1e-9);
        assertTrue(validation.canonicalProblem().landingSurface().isHorizontal());
    }

    @Test
    void incompleteProblemTriggersClarificationAndSolvesUponAnswer() {
        LocalAIProvider provider = new LocalAIProvider();

        // 1. Missing gravity triggers clarification
        String question = "A stone is thrown at 15 m/s at 30 degrees. Find range.";
        var initialDraft = provider.interpret(new InterpretationRequest(question)).toCompletableFuture().join().content();
        assertEquals(1, initialDraft.clarifications().size(), "Missing gravity must request clarification");
        assertTrue(initialDraft.clarifications().get(0).prompt().contains("gravitational acceleration"));

        // 2. Supplying clarification binds gravity and allows solving
        var turn = new InterpretationRequest.ClarificationTurn(
                initialDraft.clarifications().get(0).prompt(), "10 m/s^2");
        var followup = new InterpretationRequest(question, List.of(turn));
        var resolvedDraft = provider.interpret(followup).toCompletableFuture().join().content();
        assertTrue(resolvedDraft.clarifications().isEmpty(), "Clarification turn must resolve missing gravity");
        assertEquals(10.0, resolvedDraft.parts().get(0).givens().get(ProjectileProblem.Input.GRAVITY).value(), 1e-9);

        ProblemValidator validator = new ProblemValidator();
        ProblemValidationResult validation = validator.validate(followup, resolvedDraft);
        assertEquals(ProblemValidationResult.Status.READY, validation.status());
    }

    @Test
    void numericalSolverAuthorityAndAttributionDisclosed() {
        LocalAIProvider provider = new LocalAIProvider();
        ProblemValidator validator = new ProblemValidator();
        SolverOrchestrator orchestrator = new SolverOrchestrator(
                new ProjectileProblemSolver(), new SolutionVerifier());
        AIProblemWorkflow workflow = new AIProblemWorkflow(provider, validator, orchestrator, new ExplanationVerifier());

        String question = "A ball is projected at 20 m/s at 45 degrees with g = 10 m/s^2. Find its maximum height and horizontal range.";
        AIWorkflowResult result = workflow.submit(new InterpretationRequest(question)).toCompletableFuture().join();

        assertEquals(AIWorkflowResult.Status.COMPLETE, result.status());
        assertNotNull(result.interpretationAttribution());
        assertNotNull(result.explanationAttribution());

        // Numerical authority is exclusively with the deterministic solver
        assertNotNull(result.orchestration());
        assertEquals(SolverOrchestrator.Status.VERIFIED, result.orchestration().status());
        assertNotNull(result.orchestration().canonicalSolution());
        assertNotNull(result.orchestration().verification());
        assertTrue(result.orchestration().verification().verified());
    }
}
