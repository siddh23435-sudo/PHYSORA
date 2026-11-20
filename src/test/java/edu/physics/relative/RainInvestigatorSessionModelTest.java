package edu.physics.relative;

import edu.physics.simulation.Vector2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RainInvestigatorSessionModel - Phase 2B.1 Headless Session Architecture Tests")
class RainInvestigatorSessionModelTest {

    @Nested
    @DisplayName("1. Curated Pedagogical Presets")
    class PresetsTests {

        @Test
        @DisplayName("Preset 1: Standard JEE Case reconstructs (0, -10) with kappa = sqrt(5) and is VERIFIED")
        void testStandardJeePreset() {
            RainInvestigatorSessionModel model = new RainInvestigatorSessionModel(RainInvestigatorPreset.STANDARD_JEE);

            assertEquals(0.0, model.vPerson1(), 1e-9);
            assertEquals(0.0, model.angleDeg1(), 1e-9);
            assertEquals(10.0, model.vPerson2(), 1e-9);
            assertEquals(45.0, model.angleDeg2(), 1e-9);

            RainInvestigatorResult result = model.evaluatorResult();
            assertEquals(RainInvestigatorStatus.VALID, result.status());
            assertTrue(result.isSolvable());
            assertTrue(result.reconstructedRain().isPresent());

            Vector2D rain = result.reconstructedRainVelocity();
            assertEquals(0.0, rain.x(), 1e-9);
            assertEquals(-10.0, rain.y(), 1e-9);

            double expectedKappa = Math.sqrt(5.0);
            assertEquals(expectedKappa, result.downwardConditionNumber(), 1e-9);
            double expectedRelErr = expectedKappa * Math.toRadians(1.0);
            assertEquals(expectedRelErr, result.estimatedDownwardRelError(), 1e-9);

            RainVerificationSnapshot verification = model.verificationSnapshot();
            assertEquals(RainVerificationStatus.VERIFIED, verification.status());
            assertFalse(verification.isSensitive());
            assertEquals(0.0, verification.residualRad1(), 1e-9);
            assertEquals(0.0, verification.residualRad2(), 1e-9);
            assertTrue(verification.maxResidualDeg() <= RainVerificationSnapshot.DEFAULT_VERIFICATION_THRESHOLD_DEG);
            assertTrue(verification.summaryMessage().contains("Reconstruction Verified"));
        }

        @Test
        @DisplayName("Preset 2: Windy JEE Classic reconstructs (5, -5*sqrt(3)) with kappa = 2*sqrt(6)/3 and is VERIFIED")
        void testWindyJeePreset() {
            RainInvestigatorSessionModel model = new RainInvestigatorSessionModel(RainInvestigatorPreset.WINDY_JEE);

            assertEquals(0.0, model.vPerson1(), 1e-9);
            assertEquals(-30.0, model.angleDeg1(), 1e-9);
            assertEquals(10.0, model.vPerson2(), 1e-9);
            assertEquals(30.0, model.angleDeg2(), 1e-9);

            RainInvestigatorResult result = model.evaluatorResult();
            assertEquals(RainInvestigatorStatus.VALID, result.status());
            assertTrue(result.isSolvable());

            Vector2D rain = result.reconstructedRainVelocity();
            assertEquals(5.0, rain.x(), 1e-9);
            assertEquals(-5.0 * Math.sqrt(3.0), rain.y(), 1e-9);

            double expectedKappa = 2.0 * Math.sqrt(6.0) / 3.0;
            assertEquals(expectedKappa, result.downwardConditionNumber(), 1e-9);
            double expectedRelErr = expectedKappa * Math.toRadians(1.0);
            assertEquals(expectedRelErr, result.estimatedDownwardRelError(), 1e-9);

            RainVerificationSnapshot verification = model.verificationSnapshot();
            assertEquals(RainVerificationStatus.VERIFIED, verification.status());
            assertFalse(verification.isSensitive());
            assertEquals(0.0, verification.residualRad1(), 1e-9);
            assertEquals(0.0, verification.residualRad2(), 1e-9);
            assertTrue(verification.maxResidualDeg() <= RainVerificationSnapshot.DEFAULT_VERIFICATION_THRESHOLD_DEG);
        }

        @Test
        @DisplayName("Preset 3: Near-Parallel preset reconstructs (-1.66, -10.05) with kappa ~ 81.04 and sensitive VERIFIED status")
        void testNearParallelPreset() {
            RainInvestigatorSessionModel model = new RainInvestigatorSessionModel(RainInvestigatorPreset.NEAR_PARALLEL);

            RainInvestigatorResult result = model.evaluatorResult();
            assertEquals(RainInvestigatorStatus.ILL_CONDITIONED, result.status());
            assertTrue(result.isSolvable());

            Vector2D rain = result.reconstructedRainVelocity();
            // Reconstructed: vd ≈ 10.053383, vrx ≈ -1.659132, vry ≈ -10.053383
            assertEquals(-1.65913219, rain.x(), 1e-6);
            assertEquals(-10.05338308, rain.y(), 1e-6);

            // Condition number kappa ≈ 81.036033
            assertEquals(81.036033, result.downwardConditionNumber(), 1e-4);
            // Relative uncertainty ≈ 1.4143 (141.43%)
            assertEquals(1.4143, result.estimatedDownwardRelError(), 1e-3);

            RainVerificationSnapshot verification = model.verificationSnapshot();
            assertEquals(RainVerificationStatus.VERIFIED, verification.status());
            assertTrue(verification.isSensitive(), "Ill-conditioned result must expose isSensitive = true");
            assertTrue(verification.sensitivityNotice().contains("kappa_down = 81.04"));
            assertTrue(verification.maxResidualDeg() <= RainVerificationSnapshot.DEFAULT_VERIFICATION_THRESHOLD_DEG);
        }

        @Test
        @DisplayName("Preset 4: Identical Speeds returns DEGENERATE_IDENTICAL_SPEEDS and NOT_VERIFIABLE")
        void testIdenticalSpeedsPreset() {
            RainInvestigatorSessionModel model = new RainInvestigatorSessionModel(RainInvestigatorPreset.IDENTICAL_SPEEDS);

            RainInvestigatorResult result = model.evaluatorResult();
            assertEquals(RainInvestigatorStatus.DEGENERATE_IDENTICAL_SPEEDS, result.status());
            assertFalse(result.isSolvable());
            assertTrue(result.reconstructedRain().isEmpty());

            RainVerificationSnapshot verification = model.verificationSnapshot();
            assertEquals(RainVerificationStatus.NOT_VERIFIABLE, verification.status());
            assertTrue(Double.isNaN(verification.maxResidualDeg()));
            assertTrue(verification.summaryMessage().contains("verification unavailable"));
        }

        @Test
        @DisplayName("Preset 5: Inconsistent Direction returns INVALID_INCONSISTENT_DIRECTION and NOT_VERIFIABLE")
        void testInconsistentDirectionPreset() {
            RainInvestigatorSessionModel model = new RainInvestigatorSessionModel(RainInvestigatorPreset.INCONSISTENT_DIRECTION);

            RainInvestigatorResult result = model.evaluatorResult();
            assertEquals(RainInvestigatorStatus.INVALID_INCONSISTENT_DIRECTION, result.status());
            assertFalse(result.isSolvable());
            assertTrue(result.reconstructedRain().isEmpty());

            RainVerificationSnapshot verification = model.verificationSnapshot();
            assertEquals(RainVerificationStatus.NOT_VERIFIABLE, verification.status());
            assertTrue(Double.isNaN(verification.maxResidualDeg()));
        }
    }

    @Nested
    @DisplayName("2. Single-Observation Ambiguity & Candidate Velocities")
    class AmbiguityAndCandidateTests {

        @Test
        @DisplayName("Stage 2 hides Observation 2 and sets isUnderdetermined to true")
        void testStage2HidesObservation2() {
            RainInvestigatorSessionModel model = new RainInvestigatorSessionModel();

            assertEquals(RainInvestigatorStage.STAGE_1_EVIDENCE, model.stage());
            assertTrue(model.isObservation2Visible());
            assertFalse(model.isUnderdetermined());

            model.setStage(RainInvestigatorStage.STAGE_2_AMBIGUITY);
            assertEquals(RainInvestigatorStage.STAGE_2_AMBIGUITY, model.stage());
            assertFalse(model.isObservation2Visible());
            assertTrue(model.isUnderdetermined());

            model.setStage(RainInvestigatorStage.STAGE_3_GEOMETRY);
            assertEquals(RainInvestigatorStage.STAGE_3_GEOMETRY, model.stage());
            assertTrue(model.isObservation2Visible());
            assertFalse(model.isUnderdetermined());
        }

        @Test
        @DisplayName("setObservation2Visible cleanly transitions between Stage 2 and Stage 3")
        void testSetObservation2VisibleTransitions() {
            RainInvestigatorSessionModel model = new RainInvestigatorSessionModel();

            model.setObservation2Visible(false);
            assertEquals(RainInvestigatorStage.STAGE_2_AMBIGUITY, model.stage());
            assertFalse(model.isObservation2Visible());

            model.setObservation2Visible(true);
            assertEquals(RainInvestigatorStage.STAGE_3_GEOMETRY, model.stage());
            assertTrue(model.isObservation2Visible());
        }

        @Test
        @DisplayName("Candidate rain velocities along Line 1 satisfy equation and round-trip through RainManSolver")
        void testCandidateVelocitiesSatisfyLineAndForwardSolve() {
            // Observer 1: v1 = 4.0 m/s, phi1 = 30.0 deg
            double v1 = 4.0;
            double phi1 = Math.toRadians(30.0);
            RainInvestigatorSessionModel model = new RainInvestigatorSessionModel(v1, phi1, 8.0, Math.toRadians(45.0));

            double[] testDownSpeeds = { 2.5, 5.0, 10.0, 15.0, 20.0 };
            double tan1 = Math.tan(phi1);

            for (double vd : testDownSpeeds) {
                Vector2D candidate = model.getCandidateRainVelocityForDownwardSpeed(vd);

                // Check Line 1 equation: vx = v1 - vd * tan(phi1), vy = -vd
                double expectedVx = v1 - vd * tan1;
                double expectedVy = -vd;
                assertEquals(expectedVx, candidate.x(), 1e-9);
                assertEquals(expectedVy, candidate.y(), 1e-9);

                // Round-trip through forward solver for Observer 1
                RainManResult forward = RainManSolver.solve(candidate, new Vector2D(v1, 0.0));
                assertEquals(phi1, forward.umbrellaAngleRad(), 1e-9,
                        "Every candidate on Line 1 must reproduce observation angle phi1 under forward simulation");
            }
        }

        @Test
        @DisplayName("Candidate velocity methods reject non-physical vertical components (vd <= 0 or vy >= 0)")
        void testCandidateValidation() {
            RainInvestigatorSessionModel model = new RainInvestigatorSessionModel();

            assertThrows(IllegalArgumentException.class, () -> model.getCandidateRainVelocityForDownwardSpeed(0.0));
            assertThrows(IllegalArgumentException.class, () -> model.getCandidateRainVelocityForDownwardSpeed(-5.0));
            assertThrows(IllegalArgumentException.class, () -> model.getCandidateRainVelocityForDownwardSpeed(Double.NaN));

            assertThrows(IllegalArgumentException.class, () -> model.getCandidateRainVelocityForVy(0.0));
            assertThrows(IllegalArgumentException.class, () -> model.getCandidateRainVelocityForVy(5.0));
            assertThrows(IllegalArgumentException.class, () -> model.getCandidateRainVelocityForVy(Double.NaN));
        }
    }

    @Nested
    @DisplayName("3. Session Updates, Presets & Listeners")
    class SessionUpdatesAndListenersTests {

        @Test
        @DisplayName("setObservations re-evaluates result and notifies change listeners")
        void testSetObservationsReEvaluatesAndNotifies() {
            RainInvestigatorSessionModel model = new RainInvestigatorSessionModel();
            AtomicInteger changeCount = new AtomicInteger(0);

            RainInvestigatorChangeListener listener = m -> changeCount.incrementAndGet();
            model.addChangeListener(listener);

            // Change observations to Windy JEE values
            model.setObservations(0.0, Math.toRadians(-30.0), 10.0, Math.toRadians(30.0));

            assertEquals(1, changeCount.get());
            assertEquals(RainInvestigatorStatus.VALID, model.evaluatorResult().status());
            assertEquals(5.0, model.evaluatorResult().reconstructedRainVelocity().x(), 1e-9);
            assertTrue(model.activePreset().isEmpty(), "Custom observation updates clear active preset");

            // Remove listener and update again
            model.removeChangeListener(listener);
            model.loadPreset(RainInvestigatorPreset.STANDARD_JEE);

            assertEquals(1, changeCount.get(), "Removed listener must not be called");
            assertTrue(model.activePreset().isPresent());
            assertEquals(RainInvestigatorPreset.STANDARD_JEE, model.activePreset().get());
        }

        @Test
        @DisplayName("Non-finite inputs evaluate to INVALID_NON_FINITE and NOT_VERIFIABLE")
        void testNonFiniteInputs() {
            RainInvestigatorSessionModel model = new RainInvestigatorSessionModel(Double.NaN, 0.0, 5.0, 0.5);

            assertEquals(RainInvestigatorStatus.INVALID_NON_FINITE, model.evaluatorResult().status());
            assertFalse(model.evaluatorResult().isSolvable());
            assertEquals(RainVerificationStatus.NOT_VERIFIABLE, model.verificationSnapshot().status());
        }
    }

    @Nested
    @DisplayName("4. Scale-Aware & Verification Discrepancy Diagnostics")
    class ScaleAndDiscrepancyTests {

        @Test
        @DisplayName("Large velocity scale (1000 m/s) reconstructs (250, -1000) within scale-aware tolerance")
        void testLargeVelocityScaleReconstruction() {
            // True rain: vr = (250, -1000) m/s (vd = 1000 m/s)
            // Obs 1: v1 = 0, tan(phi1) = (0 - 250) / 1000 = -0.25 -> phi1 = atan(-0.25)
            // Obs 2: v2 = 1000, tan(phi2) = (1000 - 250) / 1000 = 0.75 -> phi2 = atan(0.75)
            double v1 = 0.0;
            double phi1 = Math.atan(-0.25);
            double v2 = 1000.0;
            double phi2 = Math.atan(0.75);

            RainInvestigatorSessionModel model = new RainInvestigatorSessionModel(v1, phi1, v2, phi2);

            RainInvestigatorResult result = model.evaluatorResult();
            assertEquals(RainInvestigatorStatus.VALID, result.status());
            assertTrue(result.isSolvable());

            Vector2D rain = result.reconstructedRainVelocity();
            RelativeTolerancePolicy policy = RelativeTolerancePolicy.DEFAULT;
            assertTrue(policy.isZeroVelocity(rain.x() - 250.0, 1000.0),
                    "Reconstructed vx must match 250 m/s within scale tolerance");
            assertTrue(policy.isZeroVelocity(rain.y() - (-1000.0), 1000.0),
                    "Reconstructed vy must match -1000 m/s within scale tolerance");

            RainVerificationSnapshot verification = model.verificationSnapshot();
            assertEquals(RainVerificationStatus.VERIFIED, verification.status());
            assertTrue(verification.maxResidualDeg() <= RainVerificationSnapshot.DEFAULT_VERIFICATION_THRESHOLD_DEG);
        }

        @Test
        @DisplayName("Verification discrepancy is detected and triggers NUMERICAL_WARNING when residuals exceed threshold")
        void testVerificationDiscrepancyDetection() {
            // Intentionally evaluate verification for a perturbed rain vector that deviates by 0.5 m/s
            // Obs 1 expects true rain (0, -10) with v1 = 0 (phi1 = 0) and v2 = 10 (phi2 = 45 deg)
            Vector2D perturbedRain = new Vector2D(0.5, -9.5); // Perturbed

            RainVerificationSnapshot snapshot = RainVerificationSnapshot.compute(
                    perturbedRain,
                    0.0, 0.0,
                    10.0, Math.PI / 4.0,
                    RainInvestigatorStatus.VALID,
                    Math.sqrt(5.0),
                    RainVerificationSnapshot.DEFAULT_VERIFICATION_THRESHOLD_DEG
            );

            assertEquals(RainVerificationStatus.NUMERICAL_WARNING, snapshot.status());
            assertTrue(snapshot.maxResidualDeg() > RainVerificationSnapshot.DEFAULT_VERIFICATION_THRESHOLD_DEG,
                    "Perturbed vector should produce residual > 1e-4 deg");
            assertTrue(snapshot.summaryMessage().contains("Verification Discrepancy"));
        }
    }
}

