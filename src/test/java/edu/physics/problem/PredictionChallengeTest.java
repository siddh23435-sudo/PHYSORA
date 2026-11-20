package edu.physics.problem;

import edu.physics.projectile.ProjectileMotion;
import edu.physics.ui.ExperimentSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PredictionChallenge Domain Logic Tests")
class PredictionChallengeTest {

    private ExperimentSnapshot sampleSnapshot(double v0, double theta, double g, double y0, double yLand) {
        ProjectileMotion m = new ProjectileMotion(v0, theta, g, 0.0, y0, yLand);
        return new ExperimentSnapshot(
                "kinematics", "01 · KINEMATICS", "Ideal Projectile",
                v0, theta, g, 0.0, y0, yLand, 0.0, "READY",
                m.maximumHeight(), m.horizontalRange(), m.timeOfFlight(), m.timeToApex(),
                0.0, 0.0, m.initialVx(), m.initialVy());
    }

    @Test
    @DisplayName("Initial state is DRAFT and holds baseline snapshot")
    void initialStateIsDraftAndHoldsBaseline() {
        ExperimentSnapshot snapshot = sampleSnapshot(20.0, 45.0, 9.81, 0.0, 0.0);
        PredictionChallenge challenge = new PredictionChallenge(snapshot);

        assertEquals(PredictionChallenge.State.DRAFT, challenge.state());
        assertSame(snapshot, challenge.baselineSnapshot());
        assertTrue(challenge.predictedValues().isEmpty());
        assertFalse(challenge.statusMessage().isBlank());
        assertTrue(challenge.evaluations().isEmpty());
    }

    @Test
    @DisplayName("Set and remove predictions during DRAFT state")
    void setAndRemovePredictionsInDraft() {
        ExperimentSnapshot snapshot = sampleSnapshot(20.0, 45.0, 9.81, 0.0, 0.0);
        PredictionChallenge challenge = new PredictionChallenge(snapshot);

        challenge.setPrediction(PredictionChallenge.Quantity.MAX_HEIGHT, 10.2);
        challenge.setPrediction(PredictionChallenge.Quantity.FLIGHT_TIME, 2.88);
        challenge.setPrediction(PredictionChallenge.Quantity.HORIZONTAL_RANGE, 40.7);

        assertEquals(3, challenge.predictedValues().size());
        assertEquals(10.2, challenge.predictedValues().get(PredictionChallenge.Quantity.MAX_HEIGHT));
        assertEquals(2.88, challenge.predictedValues().get(PredictionChallenge.Quantity.FLIGHT_TIME));
        assertEquals(40.7, challenge.predictedValues().get(PredictionChallenge.Quantity.HORIZONTAL_RANGE));

        challenge.removePrediction(PredictionChallenge.Quantity.FLIGHT_TIME);
        assertEquals(2, challenge.predictedValues().size());
        assertFalse(challenge.predictedValues().containsKey(PredictionChallenge.Quantity.FLIGHT_TIME));
    }

    @Test
    @DisplayName("Rejects negative, NaN, and Infinite prediction values")
    void rejectsInvalidPredictionValues() {
        ExperimentSnapshot snapshot = sampleSnapshot(20.0, 45.0, 9.81, 0.0, 0.0);
        PredictionChallenge challenge = new PredictionChallenge(snapshot);

        assertThrows(IllegalArgumentException.class, () ->
                challenge.setPrediction(PredictionChallenge.Quantity.MAX_HEIGHT, -1.0));
        assertThrows(IllegalArgumentException.class, () ->
                challenge.setPrediction(PredictionChallenge.Quantity.FLIGHT_TIME, Double.NaN));
        assertThrows(IllegalArgumentException.class, () ->
                challenge.setPrediction(PredictionChallenge.Quantity.HORIZONTAL_RANGE, Double.POSITIVE_INFINITY));
    }

    @Test
    @DisplayName("Locking requires at least one prediction")
    void lockingRequiresAtLeastOnePrediction() {
        ExperimentSnapshot snapshot = sampleSnapshot(20.0, 45.0, 9.81, 0.0, 0.0);
        PredictionChallenge challenge = new PredictionChallenge(snapshot);

        assertThrows(IllegalStateException.class, challenge::lock);

        challenge.setPrediction(PredictionChallenge.Quantity.MAX_HEIGHT, 10.0);
        challenge.lock();
        assertEquals(PredictionChallenge.State.LOCKED, challenge.state());
    }

    @Test
    @DisplayName("Mutations are strictly prohibited once LOCKED or EVALUATED")
    void mutationsRejectedOnceLocked() {
        ExperimentSnapshot snapshot = sampleSnapshot(20.0, 45.0, 9.81, 0.0, 0.0);
        PredictionChallenge challenge = new PredictionChallenge(snapshot);
        challenge.setPrediction(PredictionChallenge.Quantity.MAX_HEIGHT, 10.0);
        challenge.lock();

        assertThrows(IllegalStateException.class, () ->
                challenge.setPrediction(PredictionChallenge.Quantity.FLIGHT_TIME, 2.5));
        assertThrows(IllegalStateException.class, () ->
                challenge.removePrediction(PredictionChallenge.Quantity.MAX_HEIGHT));
    }

    @Test
    @DisplayName("Modifying simulation parameters invalidates locked prediction")
    void modifyingParametersInvalidatesLockedPrediction() {
        ExperimentSnapshot snapshot = sampleSnapshot(20.0, 45.0, 9.81, 0.0, 0.0);
        PredictionChallenge challenge = new PredictionChallenge(snapshot);
        challenge.setPrediction(PredictionChallenge.Quantity.MAX_HEIGHT, 10.0);
        challenge.lock();

        // Identical snapshot passes validity
        ExperimentSnapshot identical = sampleSnapshot(20.0, 45.0, 9.81, 0.0, 0.0);
        assertTrue(challenge.checkValidity(identical));
        assertEquals(PredictionChallenge.State.LOCKED, challenge.state());

        // Altered launch speed fails validity and transitions to INVALIDATED
        ExperimentSnapshot modified = sampleSnapshot(25.0, 45.0, 9.81, 0.0, 0.0);
        assertFalse(challenge.checkValidity(modified));
        assertEquals(PredictionChallenge.State.INVALIDATED, challenge.state());
        assertTrue(challenge.statusMessage().contains("modified"));
    }

    @Test
    @DisplayName("Evaluate computes absolute error, zero-safe percentage error, and constructive explanation")
    void evaluateComputesErrorsAndExplanations() {
        double v0 = 20.0;
        double theta = 45.0;
        double g = 9.81;
        ExperimentSnapshot snapshot = sampleSnapshot(v0, theta, g, 0.0, 0.0);
        ProjectileMotion model = new ProjectileMotion(v0, theta, g, 0.0, 0.0, 0.0);

        PredictionChallenge challenge = new PredictionChallenge(snapshot);
        challenge.setPrediction(PredictionChallenge.Quantity.MAX_HEIGHT, 10.0);
        challenge.setPrediction(PredictionChallenge.Quantity.FLIGHT_TIME, 3.0);
        challenge.setPrediction(PredictionChallenge.Quantity.HORIZONTAL_RANGE, 40.0);
        challenge.lock();

        challenge.evaluate(model);
        assertEquals(PredictionChallenge.State.EVALUATED, challenge.state());
        assertEquals(3, challenge.evaluations().size());

        for (PredictionChallenge.PredictionEvaluation eval : challenge.evaluations()) {
            assertTrue(eval.actualValue().isPresent());
            assertTrue(eval.percentageError().isPresent());
            assertTrue(eval.absoluteError() >= 0.0);
            assertFalse(eval.explanation().isBlank());

            switch (eval.quantity()) {
                case MAX_HEIGHT -> {
                    assertEquals(10.0, eval.predictedValue());
                    assertEquals(model.maximumHeight().getAsDouble(), eval.actualValue().getAsDouble(), 1e-4);
                    assertTrue(eval.explanation().contains("Torricelli"));
                }
                case FLIGHT_TIME -> {
                    assertEquals(3.0, eval.predictedValue());
                    assertEquals(model.timeOfFlight().getAsDouble(), eval.actualValue().getAsDouble(), 1e-4);
                    assertTrue(eval.explanation().contains("Flight time"));
                }
                case HORIZONTAL_RANGE -> {
                    assertEquals(40.0, eval.predictedValue());
                    assertEquals(model.horizontalRange().getAsDouble(), eval.actualValue().getAsDouble(), 1e-4);
                    assertTrue(eval.explanation().contains("Horizontal displacement"));
                }
            }
        }
    }

    @Test
    @DisplayName("Zero-safe percentage error handles zero or unbounded actual outcomes safely")
    void zeroSafeHandlingForZeroOrUnboundedActual() {
        // Zero gravity case: flight time / range may be empty, maximum height unbounded
        double v0 = 20.0;
        double theta = 45.0;
        double g = 0.0;
        ExperimentSnapshot snapshot = sampleSnapshot(v0, theta, g, 0.0, 0.0);
        ProjectileMotion model = new ProjectileMotion(v0, theta, g, 0.0, 0.0, 0.0);

        PredictionChallenge challenge = new PredictionChallenge(snapshot);
        challenge.setPrediction(PredictionChallenge.Quantity.MAX_HEIGHT, 15.0);
        challenge.setPrediction(PredictionChallenge.Quantity.FLIGHT_TIME, 4.0);
        challenge.lock();

        challenge.evaluate(model);
        assertEquals(PredictionChallenge.State.EVALUATED, challenge.state());

        for (PredictionChallenge.PredictionEvaluation eval : challenge.evaluations()) {
            // Actual is empty in zero gravity for both unbounded height and infinite/no landing flight time
            assertTrue(eval.actualValue().isEmpty());
            assertTrue(eval.percentageError().isEmpty());
            assertTrue(eval.explanation().contains("unbounded") || eval.explanation().contains("landing"));
        }
    }
}

