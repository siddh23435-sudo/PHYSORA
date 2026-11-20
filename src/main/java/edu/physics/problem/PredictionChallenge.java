package edu.physics.problem;

import edu.physics.projectile.ProjectileMotion;
import edu.physics.ui.ExperimentSnapshot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Pure, deterministic domain logic for the "Predict Before You Simulate" challenge.
 *
 * <p>Enforces prediction integrity:
 * 1. Predictions must be recorded and locked before results are evaluated.
 * 2. Predictions cannot be prefilled with solver ground-truth.
 * 3. Parameter changes after locking mark the prediction as invalidated.
 * 4. Percentage error is suppressed when actual values are zero or near-zero.
 */
public final class PredictionChallenge {

    public enum Quantity {
        MAX_HEIGHT("Maximum Height", "H_max", "m"),
        FLIGHT_TIME("Flight Time", "T", "s"),
        HORIZONTAL_RANGE("Horizontal Range", "R", "m");

        private final String title;
        private final String symbol;
        private final String unit;

        Quantity(String title, String symbol, String unit) {
            this.title = title;
            this.symbol = symbol;
            this.unit = unit;
        }

        public String title() { return title; }
        public String symbol() { return symbol; }
        public String unit() { return unit; }
    }

    public enum State {
        DRAFT,
        LOCKED,
        EVALUATED,
        INVALIDATED
    }

    public record PredictionEvaluation(
            Quantity quantity,
            double predictedValue,
            OptionalDouble actualValue,
            double absoluteError,
            OptionalDouble percentageError,
            String explanation) {
    }

    private final ExperimentSnapshot baselineSnapshot;
    private final Map<Quantity, Double> predictedValues = new EnumMap<>(Quantity.class);
    private State state = State.DRAFT;
    private final List<PredictionEvaluation> evaluations = new ArrayList<>();
    private String statusMessage = "Select one or more quantities, enter your estimates, and click LOCK PREDICTIONS.";

    public PredictionChallenge(ExperimentSnapshot snapshot) {
        this.baselineSnapshot = Objects.requireNonNull(snapshot, "baseline snapshot is required");
    }

    public ExperimentSnapshot baselineSnapshot() {
        return baselineSnapshot;
    }

    public Map<Quantity, Double> predictedValues() {
        return Map.copyOf(predictedValues);
    }

    public State state() {
        return state;
    }

    public List<PredictionEvaluation> evaluations() {
        return List.copyOf(evaluations);
    }

    public String statusMessage() {
        return statusMessage;
    }

    public void setPrediction(Quantity quantity, double value) {
        if (state == State.LOCKED || state == State.EVALUATED) {
            throw new IllegalStateException("Cannot mutate predictions once locked or evaluated");
        }
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0) {
            throw new IllegalArgumentException("Predictions must be non-negative finite numbers");
        }
        predictedValues.put(quantity, value);
    }

    public void removePrediction(Quantity quantity) {
        if (state == State.LOCKED || state == State.EVALUATED) {
            throw new IllegalStateException("Cannot mutate predictions once locked or evaluated");
        }
        predictedValues.remove(quantity);
    }

    public void lock() {
        if (predictedValues.isEmpty()) {
            throw new IllegalStateException("Select and enter at least one prediction before locking");
        }
        this.state = State.LOCKED;
        this.statusMessage = "Predictions locked! Run the experiment to reveal solver results and physical comparison.";
    }

    public boolean checkValidity(ExperimentSnapshot current) {
        if (state != State.LOCKED) {
            return true;
        }
        boolean matches = Math.abs(current.initialSpeed() - baselineSnapshot.initialSpeed()) < 1e-4
                && Math.abs(current.launchAngleDegrees() - baselineSnapshot.launchAngleDegrees()) < 1e-4
                && Math.abs(current.gravity() - baselineSnapshot.gravity()) < 1e-4
                && Math.abs(current.initialX() - baselineSnapshot.initialX()) < 1e-4
                && Math.abs(current.initialY() - baselineSnapshot.initialY()) < 1e-4
                && Math.abs(current.landingY() - baselineSnapshot.landingY()) < 1e-4;
        if (!matches) {
            this.state = State.INVALIDATED;
            this.statusMessage = "⚠ Simulation parameters were modified after locking. Your locked prediction was for the previous configuration.";
            return false;
        }
        return true;
    }

    public void evaluate(ProjectileMotion model) {
        if (state != State.LOCKED) {
            throw new IllegalStateException("Challenge must be locked before evaluation");
        }
        evaluations.clear();
        for (Map.Entry<Quantity, Double> entry : predictedValues.entrySet()) {
            Quantity q = entry.getKey();
            double predicted = entry.getValue();
            OptionalDouble actual = switch (q) {
                case MAX_HEIGHT -> model.maximumHeight();
                case FLIGHT_TIME -> model.timeOfFlight();
                case HORIZONTAL_RANGE -> model.horizontalRange();
            };

            double absError = actual.isPresent() ? Math.abs(predicted - actual.getAsDouble()) : Double.NaN;
            OptionalDouble pctError;
            if (actual.isPresent()) {
                double actVal = actual.getAsDouble();
                if (Math.abs(actVal) > 1e-4) {
                    pctError = OptionalDouble.of((absError / actVal) * 100.0);
                } else {
                    pctError = OptionalDouble.empty(); // Zero-safe
                }
            } else {
                pctError = OptionalDouble.empty();
            }

            String explanation = buildExplanation(q, predicted, actual, absError, pctError, model);
            evaluations.add(new PredictionEvaluation(q, predicted, actual, absError, pctError, explanation));
        }
        this.state = State.EVALUATED;
        this.statusMessage = "Simulation complete. Results and constructive explanations revealed.";
    }

    private static String buildExplanation(Quantity q, double predicted, OptionalDouble actual,
                                           double absError, OptionalDouble pctError, ProjectileMotion m) {
        if (actual.isEmpty()) {
            return switch (q) {
                case MAX_HEIGHT -> "Actual height is unbounded (∞) because downward gravity is zero.";
                case FLIGHT_TIME -> "No landing surface intersection occurs under active boundary conditions.";
                case HORIZONTAL_RANGE -> "Range is undefined when the trajectory does not intersect the landing plane.";
            };
        }
        double act = actual.getAsDouble();
        String pctStr = pctError.isPresent()
                ? String.format(Locale.ROOT, " (%.1f%% difference)", pctError.getAsDouble())
                : " (actual is near zero; absolute difference reported)";

        return switch (q) {
            case MAX_HEIGHT -> String.format(Locale.ROOT,
                    "Predicted %.2f m vs actual %.2f m (Δ = %.2f m%s). Governed by Torricelli's equation H_max = y0 + vy0²/(2g). Remember that launch elevation y0 = %.2f m adds directly to maximum altitude.",
                    predicted, act, absError, pctStr, m.initialY());
            case FLIGHT_TIME -> String.format(Locale.ROOT,
                    "Predicted %.2f s vs actual %.2f s (Δ = %.2f s%s). Flight time satisfies ½g·T² - vy0·T + (y_land - y0) = 0. For level ground, T = 2·vy0/g.",
                    predicted, act, absError, pctStr);
            case HORIZONTAL_RANGE -> String.format(Locale.ROOT,
                    "Predicted %.2f m vs actual %.2f m (Δ = %.2f m%s). Horizontal displacement is strictly R = vx0 · T because horizontal acceleration ax = 0.",
                    predicted, act, absError, pctStr);
        };
    }
}

