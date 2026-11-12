package edu.physics.problem;

import edu.physics.projectile.LandingSurface;
import edu.physics.projectile.ProjectileMotion;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static edu.physics.problem.ProjectileProblem.Input;
import static edu.physics.problem.ProjectileProblem.Target;
import static edu.physics.problem.ProjectileSolution.Status;

/** Deterministic, JavaFX-independent solver for supported projectile questions. */
public final class ProjectileProblemSolver {
    private static final List<String> MODEL_ASSUMPTIONS = List.of(
            "point-particle", "two-dimensional-motion", "constant-downward-gravity", "air-resistance-ignored");

    public ProjectileSolution solve(ProjectileProblem problem) {
        if (problem == null) return response(Status.INVALID_INPUT, null, List.of(), List.of(), List.of(), List.of(), List.of("problem-required"));
        EnumMap<Input, Double> values = new EnumMap<>(Input.class);
        List<String> invalid = new ArrayList<>();
        problem.givens().forEach((input, quantity) -> {
            if (input == null || quantity == null) {
                invalid.add("null-input-quantity");
            } else if (input.dimension() != quantity.unit().dimension()) {
                invalid.add("incompatible-unit:" + input.name());
            } else if (!Double.isFinite(quantity.value())) {
                invalid.add("non-finite-input:" + input.name());
            } else if ((input == Input.INITIAL_SPEED || input == Input.GRAVITY || input == Input.TIME || input == Input.TARGET_SPEED)
                    && quantity.value() < 0.0) {
                invalid.add("negative-nonnegative-quantity:" + input.name());
            } else {
                double si = quantity.valueInSi();
                if (!Double.isFinite(si)) invalid.add("unit-conversion-overflow:" + input.name());
                else values.put(input, si);
            }
        });
        if (!invalid.isEmpty()) return response(Status.INVALID_INPUT, problem.target(), List.of(), List.of(), List.of(), List.of(), invalid).withProblem(problem);

        List<Input> missing = missingInputs(problem, values);
        if (!missing.isEmpty()) return response(Status.UNDERDETERMINED, problem.target(), List.of(), List.of(), missing, assumptions(problem), List.of("additional-inputs-required")).withProblem(problem);

        try {
            return solveValidated(problem, values).withProblem(problem);
        } catch (IllegalArgumentException ex) {
            return response(Status.INVALID_INPUT, problem.target(), List.of(), List.of(), List.of(), assumptions(problem), List.of("invalid-physical-input")).withProblem(problem);
        } catch (ArithmeticException ex) {
            return response(Status.INVALID_INPUT, problem.target(), List.of(), List.of(), List.of(), assumptions(problem), List.of("numeric-range-exceeded")).withProblem(problem);
        }
    }

    /** Returns required inputs absent from a request without evaluating its physics. */
    public static List<Input> missingInputsFor(ProjectileProblem problem) {
        if (problem == null) throw new IllegalArgumentException("Problem is required");
        EnumMap<Input, Double> present = new EnumMap<>(Input.class);
        problem.givens().forEach((input, quantity) -> present.put(input, quantity.value()));
        return missingInputs(problem, present);
    }

    /** Solves multipart questions in order and requires each dependency to resolve uniquely. */
    public ProjectileSequenceSolution solveSequence(ProjectileProblemSequence sequence) {
        if (sequence == null) {
            return new ProjectileSequenceSolution(Status.INVALID_INPUT, List.of(), 0, List.of("sequence-required"));
        }
        List<ProjectileSequenceSolution.PartResult> completed = new ArrayList<>();
        for (int index = 0; index < sequence.parts().size(); index++) {
            ProjectileProblemSequence.Part part = sequence.parts().get(index);
            EnumMap<Input, PhysicalQuantity> givens = new EnumMap<>(Input.class);
            givens.putAll(sequence.sharedGivens());
            givens.putAll(part.givens());
            List<String> dependencyErrors = new ArrayList<>();
            for (Map.Entry<Input, ProjectileProblemSequence.ResultReference> entry : part.references().entrySet()) {
                ProjectileProblemSequence.ResultReference reference = entry.getValue();
                if (reference.partIndex() >= index) {
                    dependencyErrors.add("reference-must-point-to-an-earlier-part:" + entry.getKey());
                    continue;
                }
                ProjectileSolution prior = completed.get(reference.partIndex()).solution();
                if (prior.status() != Status.SOLVED || prior.candidates().size() != 1) {
                    dependencyErrors.add("referenced-part-is-not-uniquely-solved:" + reference.partIndex());
                    continue;
                }
                PhysicalQuantity quantity = prior.candidates().get(0).get(reference.quantityName());
                if (quantity == null) {
                    dependencyErrors.add("referenced-result-not-found:" + reference.quantityName());
                    continue;
                }
                givens.put(entry.getKey(), quantity);
            }
            if (!dependencyErrors.isEmpty()) {
                return new ProjectileSequenceSolution(Status.AMBIGUOUS, completed, index, dependencyErrors);
            }

            ProjectileSolution solution = solve(new ProjectileProblem(
                    sequence.statement() + " (part " + (index + 1) + ")", part.target(), givens));
            completed.add(new ProjectileSequenceSolution.PartResult(index, solution));
            if (solution.status() != Status.SOLVED) {
                return new ProjectileSequenceSolution(solution.status(), completed, index, solution.warnings());
            }
        }
        return new ProjectileSequenceSolution(Status.SOLVED, completed, -1, List.of());
    }

    private ProjectileSolution solveValidated(ProjectileProblem problem, Map<Input, Double> v) {
        Target target = problem.target();
        if (target == Target.FIND_INITIAL_SPEED) return solveInitialSpeed(problem, v);
        if (target == Target.FIND_LAUNCH_ANGLE) return solveLaunchAngle(problem, v);

        double speed = v.get(Input.INITIAL_SPEED);
        double angle = v.get(Input.LAUNCH_ANGLE);
        double gravity = v.get(Input.GRAVITY);
        double x0 = v.getOrDefault(Input.INITIAL_X, 0.0);
        double y0 = v.getOrDefault(Input.INITIAL_Y, 0.0);
        double landingY = v.getOrDefault(Input.LANDING_Y, y0);
        ProjectileMotion model = new ProjectileMotion(speed, angle, gravity, x0, y0, landingY);
        List<String> assumptions = assumptions(problem);
        if (gravity == 0.0 && model.initialVy() > 0.0
                && (target == Target.MAXIMUM_HEIGHT || target == Target.TIME_TO_APEX || target == Target.SPEED_AT_APEX)) {
            return response(Status.UNBOUNDED, target, List.of(), List.of(), List.of(), assumptions,
                    List.of("positive-vertical-velocity-with-zero-gravity-has-no-finite-apex"));
        }

        return switch (target) {
            case HORIZONTAL_RANGE -> optionalScalar(problem, assumptions, model.horizontalRange(), "range", PhysicalUnit.METRE, "range-from-landing-time", Map.of("vx0", model.initialVx()));
            case MAXIMUM_HEIGHT -> optionalScalar(problem, assumptions, model.maximumHeight(), "maximumHeight", PhysicalUnit.METRE, "maximum-height", Map.of("y0", y0, "vy0", model.initialVy(), "g", gravity));
            case TIME_TO_APEX -> optionalScalar(problem, assumptions, model.timeToApex(), "timeToApex", PhysicalUnit.SECOND, "time-to-apex", Map.of("vy0", model.initialVy(), "g", gravity));
            case FLIGHT_TIME -> solveFlightTime(problem, assumptions, model, y0, landingY);
            case SPEED_AT_APEX -> {
                Optional<ProjectileMotion.State> apex = model.apexState();
                yield apex.isEmpty()
                        ? response(Status.NO_SOLUTION, target, List.of(), List.of(), List.of(), assumptions, List.of("no-finite-apex"))
                        : scalar(problem, assumptions, apex.get().speed(), "speedAtApex", PhysicalUnit.METRE_PER_SECOND, "speed-at-apex", Map.of("vx0", apex.get().vx(), "vyApex", apex.get().vy()));
            }
            case VELOCITY_AT_TIME, SPEED_AT_TIME, POSITION_AT_TIME, DISTANCE_TRAVELLED_AT_TIME -> solveAtTime(problem, assumptions, model, v);
            case POSITION_AT_HORIZONTAL_DISPLACEMENT, VELOCITY_AT_HORIZONTAL_DISPLACEMENT, SPEED_AT_HORIZONTAL_DISPLACEMENT,
                    TIME_AT_HORIZONTAL_DISPLACEMENT -> solveAtHorizontalDisplacement(problem, assumptions, model, v);
            case POSITION_AT_HEIGHT, VELOCITY_AT_HEIGHT, SPEED_AT_HEIGHT, TIMES_AT_HEIGHT -> solveAtHeight(problem, assumptions, model, v);
            case TIMES_AT_POSITION -> solveAtPosition(problem, assumptions, model, v);
            case FIND_INITIAL_SPEED, FIND_LAUNCH_ANGLE -> throw new AssertionError("Inverse solve dispatch error");
        };
    }

    private ProjectileSolution solveAtTime(ProjectileProblem problem, List<String> assumptions,
                                           ProjectileMotion model, Map<Input, Double> v) {
        double time = v.get(Input.TIME);
        ProjectileMotion.State state = model.stateAt(time);
        return switch (problem.target()) {
            case POSITION_AT_TIME -> candidate(problem, Status.SOLVED, assumptions,
                    "position-at-time",
                    Map.of("x0", model.initialX(), "y0", model.initialY(),
                            "vx0", model.initialVx(), "vy0", model.initialVy(), "t", time, "g", model.gravity()),
                    Map.of("horizontalDisplacement", q(model.initialVx() * time, PhysicalUnit.METRE),
                            "verticalLinearChange", q(model.initialVy() * time, PhysicalUnit.METRE),
                            "gravityDrop", q(0.5 * model.gravity() * time * time, PhysicalUnit.METRE)),
                    Map.of("x", q(state.x(), PhysicalUnit.METRE), "y", q(state.y(), PhysicalUnit.METRE)),
                    Map.of("x", q(state.x(), PhysicalUnit.METRE), "y", q(state.y(), PhysicalUnit.METRE)), List.of());
            case VELOCITY_AT_TIME -> candidate(problem, Status.SOLVED, assumptions,
                    "velocity-at-time", Map.of("vx0", model.initialVx(), "vy0", model.initialVy(),
                            "g", model.gravity(), "t", time),
                    Map.of("gravityVelocityChange", q(-model.gravity() * time, PhysicalUnit.METRE_PER_SECOND)),
                    Map.of("vx", q(state.vx(), PhysicalUnit.METRE_PER_SECOND), "vy", q(state.vy(), PhysicalUnit.METRE_PER_SECOND)),
                    Map.of("vx", q(state.vx(), PhysicalUnit.METRE_PER_SECOND), "vy", q(state.vy(), PhysicalUnit.METRE_PER_SECOND)), List.of());
            case SPEED_AT_TIME -> scalar(problem, assumptions, state.speed(), "speed", PhysicalUnit.METRE_PER_SECOND,
                    "speed-from-velocity", Map.of("vx", state.vx(), "vy", state.vy()));
            case DISTANCE_TRAVELLED_AT_TIME -> scalar(problem, assumptions, model.distanceTravelledAt(time), "distanceTravelled", PhysicalUnit.METRE,
                    "projectile-arc-length", Map.of("t", time, "vx0", model.initialVx(), "vy0", model.initialVy(), "g", model.gravity()));
            default -> throw new AssertionError("Not a time target");
        };
    }

    private ProjectileSolution solveAtHorizontalDisplacement(ProjectileProblem problem, List<String> assumptions,
                                                             ProjectileMotion model, Map<Input, Double> v) {
        double targetX = model.initialX() + v.get(Input.HORIZONTAL_DISPLACEMENT);
        ProjectileMotion.TimeSolutions roots = model.timesAtHorizontalPosition(targetX);
        if (roots.infinitelyMany()) return response(Status.AMBIGUOUS, problem.target(), List.of(), List.of(), List.of(), assumptions, List.of("projectile-remains-at-requested-x-for-all-times"));
        if (roots.times().isEmpty()) return response(Status.NO_SOLUTION, problem.target(), List.of(), List.of(), List.of(), assumptions, List.of("horizontal-displacement-not-reached-in-forward-time"));
        List<Map<String, PhysicalQuantity>> candidates = new ArrayList<>();
        for (double time : roots.times()) {
            ProjectileMotion.State state = model.stateAt(time);
            if (problem.target() == Target.TIME_AT_HORIZONTAL_DISPLACEMENT) {
                candidates.add(Map.of("time", q(time, PhysicalUnit.SECOND)));
            } else if (problem.target() == Target.POSITION_AT_HORIZONTAL_DISPLACEMENT) {
                candidates.add(Map.of("x", q(state.x(), PhysicalUnit.METRE), "y", q(state.y(), PhysicalUnit.METRE), "time", q(time, PhysicalUnit.SECOND)));
            } else if (problem.target() == Target.SPEED_AT_HORIZONTAL_DISPLACEMENT) {
                candidates.add(Map.of("speed", q(state.speed(), PhysicalUnit.METRE_PER_SECOND), "time", q(time, PhysicalUnit.SECOND)));
            } else {
                candidates.add(Map.of("vx", q(state.vx(), PhysicalUnit.METRE_PER_SECOND), "vy", q(state.vy(), PhysicalUnit.METRE_PER_SECOND), "time", q(time, PhysicalUnit.SECOND)));
            }
        }
        Status status = candidates.size() > 1 ? Status.MULTIPLE_SOLUTIONS : Status.SOLVED;
        ProjectileSolution.Step step = step("horizontal-position-query",
                Map.of("dx", v.get(Input.HORIZONTAL_DISPLACEMENT), "vx0", model.initialVx()),
                timeRootIntermediates(roots), candidates.get(0));
        return response(status, problem.target(), candidates, List.of(step), List.of(), assumptions, List.of());
    }

    private ProjectileSolution solveAtHeight(ProjectileProblem problem, List<String> assumptions,
                                             ProjectileMotion model, Map<Input, Double> v) {
        double height = v.get(Input.TARGET_Y);
        ProjectileMotion.TimeSolutions roots = model.timesAtHeight(height);
        if (roots.infinitelyMany()) return response(Status.AMBIGUOUS, problem.target(), List.of(), List.of(), List.of(), assumptions, List.of("height-is-constant-for-all-times"));
        if (roots.times().isEmpty()) return response(Status.NO_SOLUTION, problem.target(), List.of(), List.of(), List.of(), assumptions, List.of("height-is-not-reached"));
        List<Map<String, PhysicalQuantity>> candidates = new ArrayList<>();
        for (double time : roots.times()) {
            ProjectileMotion.State state = model.stateAt(time);
            if (problem.target() == Target.TIMES_AT_HEIGHT) {
                candidates.add(Map.of("time", q(time, PhysicalUnit.SECOND), "vx", q(state.vx(), PhysicalUnit.METRE_PER_SECOND), "vy", q(state.vy(), PhysicalUnit.METRE_PER_SECOND)));
            } else if (problem.target() == Target.POSITION_AT_HEIGHT) {
                candidates.add(Map.of("time", q(time, PhysicalUnit.SECOND), "x", q(state.x(), PhysicalUnit.METRE), "y", q(state.y(), PhysicalUnit.METRE)));
            } else if (problem.target() == Target.VELOCITY_AT_HEIGHT) {
                candidates.add(Map.of("time", q(time, PhysicalUnit.SECOND), "vx", q(state.vx(), PhysicalUnit.METRE_PER_SECOND), "vy", q(state.vy(), PhysicalUnit.METRE_PER_SECOND)));
            } else {
                candidates.add(Map.of("speed", q(state.speed(), PhysicalUnit.METRE_PER_SECOND), "time", q(time, PhysicalUnit.SECOND)));
            }
        }
        Status status = candidates.size() > 1 ? Status.MULTIPLE_SOLUTIONS : Status.SOLVED;
        ProjectileSolution.Step step = step("vertical-position-roots",
                Map.of("targetY", height, "vy0", model.initialVy(), "g", model.gravity()),
                timeRootIntermediates(roots), candidates.get(0));
        return response(status, problem.target(), candidates, List.of(step), List.of(), assumptions, List.of());
    }

    private ProjectileSolution solveAtPosition(ProjectileProblem problem, List<String> assumptions,
                                              ProjectileMotion model, Map<Input, Double> values) {
        double targetX = values.get(Input.TARGET_X);
        double targetY = values.get(Input.TARGET_Y);
        ProjectileMotion.TimeSolutions roots = model.timesAtPosition(targetX, targetY);
        if (roots.infinitelyMany()) return response(Status.AMBIGUOUS, problem.target(), List.of(), List.of(), List.of(), assumptions, List.of("projectile-remains-at-requested-position-for-all-times"));
        if (roots.times().isEmpty()) return response(Status.NO_SOLUTION, problem.target(), List.of(), List.of(), List.of(), assumptions, List.of("position-is-not-reached-in-forward-time"));
        List<Map<String, PhysicalQuantity>> candidates = roots.times().stream()
                .map(time -> Map.of("time", q(time, PhysicalUnit.SECOND))).toList();
        Status status = candidates.size() > 1 ? Status.MULTIPLE_SOLUTIONS : Status.SOLVED;
        ProjectileSolution.Step step = step("position-query", Map.of("targetX", targetX, "targetY", targetY),
                timeRootIntermediates(roots), candidates.get(0));
        return response(status, problem.target(), candidates, List.of(step), List.of(), assumptions, List.of());
    }

    private ProjectileSolution solveFlightTime(ProjectileProblem problem, List<String> assumptions,
                                                ProjectileMotion model, double initialY, double landingY) {
        OptionalDouble landingTime = model.timeOfFlight();
        if (landingTime.isEmpty()) {
            return response(Status.NO_SOLUTION, problem.target(), List.of(), List.of(), List.of(), assumptions,
                    List.of("requested-event-is-not-finite-or-not-reached"));
        }
        Map<String, PhysicalQuantity> results = Map.of("flightTime", q(landingTime.getAsDouble(), PhysicalUnit.SECOND));
        return candidate(problem, Status.SOLVED, assumptions, "landing-height-root",
                Map.of("y0", initialY, "vy0", model.initialVy(), "g", model.gravity(), "yLanding", landingY),
                timeRootIntermediates(model.timesAtHeight(landingY)), results, results, List.of());
    }

    private static Map<String, PhysicalQuantity> timeRootIntermediates(ProjectileMotion.TimeSolutions roots) {
        Map<String, PhysicalQuantity> values = new LinkedHashMap<>();
        for (int i = 0; i < roots.times().size(); i++) {
            values.put("crossingTime" + (i + 1), q(roots.times().get(i), PhysicalUnit.SECOND));
        }
        return Map.copyOf(values);
    }

    private ProjectileSolution solveInitialSpeed(ProjectileProblem problem, Map<Input, Double> v) {
        double angleDegrees = v.get(Input.LAUNCH_ANGLE);
        double gravity = v.get(Input.GRAVITY);
        double x0 = v.getOrDefault(Input.INITIAL_X, 0.0);
        double y0 = v.getOrDefault(Input.INITIAL_Y, 0.0);
        if (v.containsKey(Input.TIME) && v.containsKey(Input.TARGET_SPEED)) {
            double time = v.get(Input.TIME);
            double targetSpeed = v.get(Input.TARGET_SPEED);
            double gt = gravity * time;
            if (!Double.isFinite(gt)) return response(Status.INVALID_INPUT, problem.target(), List.of(), List.of(), List.of(), assumptions(problem), List.of("numeric-range-exceeded"));
            if (time == 0.0 || gravity == 0.0) {
                return scalar(problem, assumptions(problem), targetSpeed, "initialSpeed", PhysicalUnit.METRE_PER_SECOND,
                        "initial-speed-from-speed", Map.of("targetSpeed", targetSpeed, "time", time));
            }
            double cosineTerm = gt * cosDegrees(angleDegrees);
            double scale = Math.max(targetSpeed, Math.abs(cosineTerm));
            double normalizedDiscriminant = scale == 0.0 ? 0.0
                    : Math.fma(targetSpeed / scale, targetSpeed / scale,
                    -(cosineTerm / scale) * (cosineTerm / scale));
            double tolerance = 8.0 * Math.ulp(1.0);
            if (normalizedDiscriminant < 0.0 && -normalizedDiscriminant <= tolerance) normalizedDiscriminant = 0.0;
            if (normalizedDiscriminant < 0.0) return response(Status.NO_SOLUTION, problem.target(), List.of(), List.of(), List.of(), assumptions(problem), List.of("target-speed-is-below-the-minimum-horizontal-speed"));
            double root = scale * Math.sqrt(normalizedDiscriminant);
            double center = gt * sinDegrees(angleDegrees);
            List<Double> speeds = new ArrayList<>(2);
            double lowerSpeed = center - root;
            double upperSpeed = center + root;
            if (!Double.isFinite(lowerSpeed) || !Double.isFinite(upperSpeed)) return response(Status.INVALID_INPUT, problem.target(), List.of(), List.of(), List.of(), assumptions(problem), List.of("numeric-range-exceeded"));
            if (lowerSpeed >= 0.0) speeds.add(lowerSpeed);
            if (upperSpeed >= 0.0 && (root != 0.0 || speeds.isEmpty())) speeds.add(upperSpeed);
            if (speeds.isEmpty()) return response(Status.NO_SOLUTION, problem.target(), List.of(), List.of(), List.of(), assumptions(problem), List.of("no-nonnegative-initial-speed-satisfies-condition"));
            List<Map<String, PhysicalQuantity>> candidates = speeds.stream().distinct()
                    .map(value -> Map.of("initialSpeed", q(value, PhysicalUnit.METRE_PER_SECOND))).toList();
            Status status = candidates.size() > 1 ? Status.MULTIPLE_SOLUTIONS : Status.SOLVED;
            ProjectileSolution.Step step = step("inverse-initial-speed-from-speed",
                    Map.of("speedAtTime", targetSpeed, "g", gravity, "t", time, "theta", angleDegrees),
                    Map.of("gravityTimeProduct", q(gt, PhysicalUnit.METRE_PER_SECOND),
                            "horizontalGravityTerm", q(cosineTerm, PhysicalUnit.METRE_PER_SECOND),
                            "normalizedDiscriminant", q(normalizedDiscriminant, PhysicalUnit.ONE),
                            "speedRoot", q(root, PhysicalUnit.METRE_PER_SECOND),
                            "verticalCenterTerm", q(center, PhysicalUnit.METRE_PER_SECOND)), candidates.get(0));
            return response(status, problem.target(), candidates, List.of(step), List.of(), assumptions(problem), List.of());
        }
        if (v.containsKey(Input.TARGET_VX)) {
            double cosine = cosDegrees(angleDegrees);
            double targetVx = v.get(Input.TARGET_VX);
            if (cosine == 0.0) {
                if (targetVx != 0.0) return response(Status.NO_SOLUTION, problem.target(), List.of(), List.of(), List.of(), assumptions(problem), List.of("vertical-launch-has-zero-horizontal-velocity"));
            } else {
                double speed = targetVx / cosine;
                if (speed < 0.0) return response(Status.NO_SOLUTION, problem.target(), List.of(), List.of(), List.of(), assumptions(problem), List.of("horizontal-component-implies-negative-speed"));
                return scalar(problem, assumptions(problem), speed, "initialSpeed", PhysicalUnit.METRE_PER_SECOND,
                        "initial-speed-from-horizontal-velocity", Map.of("vx0", targetVx, "theta", angleDegrees),
                        Map.of("cosTheta", q(cosine, PhysicalUnit.ONE)));
            }
        }
        if (v.containsKey(Input.TIME) && v.containsKey(Input.TARGET_VY)) {
            double time = v.get(Input.TIME);
            double denominator = sinDegrees(angleDegrees);
            double gravityContribution = gravity * time;
            double numerator = v.get(Input.TARGET_VY) + gravityContribution;
            if (denominator == 0.0) return response(Status.UNDERDETERMINED, problem.target(), List.of(), List.of(), List.of(Input.TARGET_VY), assumptions(problem), List.of("vertical-component-condition-does-not-determine-speed"));
            double speed = numerator / denominator;
            if (!Double.isFinite(speed)) return response(Status.INVALID_INPUT, problem.target(), List.of(), List.of(), List.of(), assumptions(problem), List.of("numeric-range-exceeded"));
            if (speed < 0.0) return response(Status.NO_SOLUTION, problem.target(), List.of(), List.of(), List.of(), assumptions(problem), List.of("condition-implies-negative-speed"));
            return scalar(problem, assumptions(problem), speed, "initialSpeed", PhysicalUnit.METRE_PER_SECOND,
                    "initial-speed-from-vertical-velocity",
                    Map.of("vy", v.get(Input.TARGET_VY), "g", gravity, "t", time, "theta", angleDegrees),
                    Map.of("gravityVelocityContribution", q(gravityContribution, PhysicalUnit.METRE_PER_SECOND),
                            "launchVerticalComponent", q(numerator, PhysicalUnit.METRE_PER_SECOND),
                            "sinTheta", q(denominator, PhysicalUnit.ONE)));
        }
        if (v.containsKey(Input.HORIZONTAL_DISPLACEMENT) && v.containsKey(Input.VERTICAL_DISPLACEMENT)) {
            double dx = v.get(Input.HORIZONTAL_DISPLACEMENT);
            double dy = v.get(Input.VERTICAL_DISPLACEMENT);
            double cosine = cosDegrees(angleDegrees);
            if (dx == 0.0 || cosine == 0.0) return response(Status.UNDERDETERMINED, problem.target(), List.of(), List.of(), List.of(Input.TIME), assumptions(problem), List.of("zero-horizontal-component-needs-time-condition"));
            double tangent = sinDegrees(angleDegrees) / cosine;
            double horizontalTrajectoryTerm = dx * tangent;
            double requiredHeight = horizontalTrajectoryTerm - dy;
            if (gravity == 0.0) {
                if (requiredHeight == 0.0) return response(Status.UNDERDETERMINED, problem.target(), List.of(), List.of(), List.of(Input.TIME), assumptions(problem), List.of("zero-gravity-path-does-not-determine-speed"));
                return response(Status.NO_SOLUTION, problem.target(), List.of(), List.of(), List.of(), assumptions(problem), List.of("conditions-contradict-zero-gravity-motion"));
            }
            if (requiredHeight <= 0.0 || Math.signum(dx) != Math.signum(cosine)) return response(Status.NO_SOLUTION, problem.target(), List.of(), List.of(), List.of(), assumptions(problem), List.of("conditions-have-no-forward-trajectory"));
            double numerator = 0.5 * gravity * dx * dx;
            double denominator = cosine * cosine * requiredHeight;
            double speedSquared = numerator / denominator;
            if (!(speedSquared >= 0.0) || !Double.isFinite(speedSquared)) return response(Status.INVALID_INPUT, problem.target(), List.of(), List.of(), List.of(), assumptions(problem), List.of("numeric-range-exceeded"));
            return scalar(problem, assumptions(problem), Math.sqrt(speedSquared), "initialSpeed", PhysicalUnit.METRE_PER_SECOND,
                    "initial-speed-from-displacement",
                    Map.of("dx", dx, "dy", dy, "theta", angleDegrees, "g", gravity),
                    Map.of("cosTheta", q(cosine, PhysicalUnit.ONE), "tanTheta", q(tangent, PhysicalUnit.ONE),
                            "horizontalTrajectoryTerm", q(horizontalTrajectoryTerm, PhysicalUnit.METRE),
                            "requiredHeight", q(requiredHeight, PhysicalUnit.METRE)));
        }
        return response(Status.UNDERDETERMINED, problem.target(), List.of(), List.of(),
                List.of(Input.TARGET_VX, Input.TIME, Input.TARGET_VY, Input.TARGET_SPEED, Input.HORIZONTAL_DISPLACEMENT, Input.VERTICAL_DISPLACEMENT), assumptions(problem), List.of("provide-a-velocity-component-or-time-with-speed-or-vertical-velocity-or-both-displacement-components"));
    }

    private ProjectileSolution solveLaunchAngle(ProjectileProblem problem, Map<Input, Double> v) {
        double speed = v.get(Input.INITIAL_SPEED);
        double gravity = v.get(Input.GRAVITY);
        double dx = v.get(Input.HORIZONTAL_DISPLACEMENT);
        double dy = v.get(Input.VERTICAL_DISPLACEMENT);
        if (speed == 0.0) {
            Status status = dx == 0.0 && dy == 0.0 ? Status.AMBIGUOUS : Status.NO_SOLUTION;
            return response(status, problem.target(), List.of(), List.of(), List.of(), assumptions(problem),
                    List.of(dx == 0.0 && dy == 0.0 ? "zero-speed-destination-does-not-select-a-launch-angle" : "zero-speed-cannot-reach-nonzero-displacement"));
        }
        if (dx == 0.0) return response(Status.AMBIGUOUS, problem.target(), List.of(), List.of(), List.of(), assumptions(problem), List.of("purely-vertical-target-needs-direction-or-time-constraint"));
        double a = gravity * dx * dx / (2.0 * speed * speed);
        if (!Double.isFinite(a)) return response(Status.INVALID_INPUT, problem.target(), List.of(), List.of(), List.of(), assumptions(problem), List.of("numeric-range-exceeded"));
        if (a == 0.0) {
            if (dx == 0.0) return response(Status.UNDERDETERMINED, problem.target(), List.of(), List.of(), List.of(Input.TIME), assumptions(problem), List.of("zero-gravity-inverse-needs-time"));
            double angle = Math.toDegrees(Math.atan(dy / dx));
            if (dx < 0.0) angle += 180.0;
            return scalar(problem, assumptions(problem), angle, "launchAngle", PhysicalUnit.DEGREE,
                    "launch-angle-from-displacement", Map.of("dx", dx, "dy", dy),
                    Map.of("displacementSlope", q(dy / dx, PhysicalUnit.ONE)));
        }
        double constant = a + dy;
        double discriminant = Math.fma(-4.0 * a, constant, dx * dx);
        double tolerance = 8.0 * Math.ulp(Math.abs(dx * dx) + Math.abs(4.0 * a * constant));
        if (!Double.isFinite(discriminant)) return response(Status.INVALID_INPUT, problem.target(), List.of(), List.of(), List.of(), assumptions(problem), List.of("numeric-range-exceeded"));
        if (discriminant < 0.0 && -discriminant <= tolerance) discriminant = 0.0;
        if (discriminant < 0.0) return response(Status.NO_SOLUTION, problem.target(), List.of(), List.of(), List.of(), assumptions(problem), List.of("no-real-launch-angle-satisfies-displacement"));

        double sqrt = Math.sqrt(discriminant);
        double q = 0.5 * (dx + Math.copySign(sqrt, dx));
        double u1 = q / a;
        double u2 = q == 0.0 ? u1 : constant / q;
        List<Double> angles = new ArrayList<>();
        addForwardAngle(angles, u1, dx);
        addForwardAngle(angles, u2, dx);
        if (angles.isEmpty()) return response(Status.NO_SOLUTION, problem.target(), List.of(), List.of(), List.of(), assumptions(problem), List.of("no-forward-time-launch-angle"));
        angles.sort(Double::compare);
        List<Map<String, PhysicalQuantity>> candidates = angles.stream()
                .distinct().map(value -> Map.of("launchAngle", q(value, PhysicalUnit.DEGREE))).toList();
        Status status = candidates.size() > 1 ? Status.MULTIPLE_SOLUTIONS : Status.SOLVED;
        ProjectileSolution.Step step = step("inverse-launch-angle",
                Map.of("A", a, "dx", dx, "dy", dy, "v0", speed, "g", gravity),
                Map.of("quadraticConstant", q(constant, PhysicalUnit.METRE),
                        "discriminantRoot", q(sqrt, PhysicalUnit.METRE),
                        "stableNumerator", q(q, PhysicalUnit.METRE),
                        "tangentCandidate1", q(u1, PhysicalUnit.ONE),
                        "tangentCandidate2", q(u2, PhysicalUnit.ONE)), candidates.get(0));
        return response(status, problem.target(), candidates, List.of(step), List.of(), assumptions(problem), List.of());
    }

    private static void addForwardAngle(List<Double> angles, double tangent, double dx) {
        double angle = Math.toDegrees(Math.atan(tangent));
        if (dx < 0.0) angle += 180.0;
        if (Math.cos(Math.toRadians(angle)) * dx <= 0.0) return;
        for (double existing : angles) if (Math.abs(existing - angle) <= 8.0 * Math.ulp(Math.max(1.0, Math.abs(angle)))) return;
        angles.add(angle);
    }

    private static double sinDegrees(double degrees) {
        double reduced = degrees % 360.0;
        if (reduced < 0.0) reduced += 360.0;
        if (reduced % 90.0 == 0.0) {
            return switch ((int) (reduced / 90.0)) { case 1 -> 1.0; case 3 -> -1.0; default -> 0.0; };
        }
        return Math.sin(Math.toRadians(reduced));
    }

    private static double cosDegrees(double degrees) {
        double reduced = degrees % 360.0;
        if (reduced < 0.0) reduced += 360.0;
        if (reduced % 90.0 == 0.0) {
            return switch ((int) (reduced / 90.0)) { case 0 -> 1.0; case 2 -> -1.0; default -> 0.0; };
        }
        return Math.cos(Math.toRadians(reduced));
    }

    private static List<Input> missingInputs(ProjectileProblem problem, Map<Input, Double> values) {
        Target target = problem.target();
        List<Input> required = switch (target) {
            case HORIZONTAL_RANGE, MAXIMUM_HEIGHT, TIME_TO_APEX, FLIGHT_TIME, SPEED_AT_APEX -> List.of(Input.INITIAL_SPEED, Input.LAUNCH_ANGLE, Input.GRAVITY);
            case VELOCITY_AT_TIME, SPEED_AT_TIME, POSITION_AT_TIME, DISTANCE_TRAVELLED_AT_TIME -> List.of(Input.INITIAL_SPEED, Input.LAUNCH_ANGLE, Input.GRAVITY, Input.TIME);
            case POSITION_AT_HORIZONTAL_DISPLACEMENT, VELOCITY_AT_HORIZONTAL_DISPLACEMENT, SPEED_AT_HORIZONTAL_DISPLACEMENT, TIME_AT_HORIZONTAL_DISPLACEMENT -> List.of(Input.INITIAL_SPEED, Input.LAUNCH_ANGLE, Input.GRAVITY, Input.HORIZONTAL_DISPLACEMENT);
            case POSITION_AT_HEIGHT, VELOCITY_AT_HEIGHT, SPEED_AT_HEIGHT, TIMES_AT_HEIGHT -> List.of(Input.INITIAL_SPEED, Input.LAUNCH_ANGLE, Input.GRAVITY, Input.TARGET_Y);
            case TIMES_AT_POSITION -> List.of(Input.INITIAL_SPEED, Input.LAUNCH_ANGLE, Input.GRAVITY, Input.TARGET_X, Input.TARGET_Y);
            case FIND_INITIAL_SPEED -> List.of(Input.LAUNCH_ANGLE, Input.GRAVITY);
            case FIND_LAUNCH_ANGLE -> List.of(Input.INITIAL_SPEED, Input.GRAVITY, Input.HORIZONTAL_DISPLACEMENT, Input.VERTICAL_DISPLACEMENT);
        };
        List<Input> missing = required.stream().filter(input -> !values.containsKey(input)).toList();
        boolean hasInverseSpeedCondition = values.containsKey(Input.TARGET_VX)
                || (values.containsKey(Input.TIME) && values.containsKey(Input.TARGET_VY))
                || (values.containsKey(Input.TIME) && values.containsKey(Input.TARGET_SPEED))
                || (values.containsKey(Input.HORIZONTAL_DISPLACEMENT) && values.containsKey(Input.VERTICAL_DISPLACEMENT));
        if (target == Target.FIND_INITIAL_SPEED && missing.isEmpty() && !hasInverseSpeedCondition) {
            return List.of(Input.TIME, Input.TARGET_VY, Input.HORIZONTAL_DISPLACEMENT, Input.VERTICAL_DISPLACEMENT);
        }
        return missing;
    }

    private static ProjectileSolution optionalScalar(ProjectileProblem p, List<String> assumptions,
                                                     OptionalDouble value, String name, PhysicalUnit unit,
                                                     String equationId, Map<String, Double> substitutions) {
        return value.isPresent()
                ? scalar(p, assumptions, value.getAsDouble(), name, unit, equationId, substitutions)
                : response(Status.NO_SOLUTION, p.target(), List.of(), List.of(), List.of(), assumptions, List.of("requested-event-is-not-finite-or-not-reached"));
    }

    private static ProjectileSolution scalar(ProjectileProblem p, List<String> assumptions, double value,
                                            String name, PhysicalUnit unit, String equationId,
                                            Map<String, Double> substitutions) {
        return scalar(p, assumptions, value, name, unit, equationId, substitutions, Map.of());
    }

    private static ProjectileSolution scalar(ProjectileProblem p, List<String> assumptions, double value,
                                            String name, PhysicalUnit unit, String equationId,
                                            Map<String, Double> substitutions,
                                            Map<String, PhysicalQuantity> intermediateResults) {
        Map<String, PhysicalQuantity> candidate = Map.of(name, q(value, unit));
        return candidate(p, Status.SOLVED, assumptions, equationId, substitutions,
                intermediateResults, candidate, candidate, List.of());
    }

    private static ProjectileSolution candidate(ProjectileProblem p, Status status, List<String> assumptions,
                                                String equationId,
                                                Map<String, Double> substitutions,
                                                Map<String, PhysicalQuantity> results,
                                                Map<String, PhysicalQuantity> candidate,
                                                List<String> warnings) {
        return candidate(p, status, assumptions, equationId, substitutions, Map.of(), results, candidate, warnings);
    }

    private static ProjectileSolution candidate(ProjectileProblem p, Status status, List<String> assumptions,
                                                String equationId,
                                                Map<String, Double> substitutions,
                                                Map<String, PhysicalQuantity> intermediateResults,
                                                Map<String, PhysicalQuantity> results,
                                                Map<String, PhysicalQuantity> candidate,
                                                List<String> warnings) {
        return response(status, p.target(), List.of(candidate),
                List.of(step(equationId, substitutions, intermediateResults, results)), List.of(), assumptions, warnings);
    }

    private static ProjectileSolution.Step step(String id, Map<String, Double> substitutions,
                                                Map<String, PhysicalQuantity> results) {
        return step(id, substitutions, Map.of(), results);
    }

    private static ProjectileSolution.Step step(String id, Map<String, Double> substitutions,
                                                Map<String, PhysicalQuantity> intermediateResults,
                                                Map<String, PhysicalQuantity> results) {
        MathExpression.Equation equation = ProjectileEquations.byId(id);
        Map<String, PhysicalUnit> units = equation.symbolsByName().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().unit()));
        Map<String, PhysicalQuantity> typedSubstitutions = substitutions.entrySet().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        entry -> new PhysicalQuantity(entry.getValue(), requireSymbolUnit(units, entry.getKey(), id))));
        return new ProjectileSolution.Step(equation, typedSubstitutions, intermediateResults, results,
                ProjectileSolution.PrecisionPolicy.standardDisplay(), equation.principleId());
    }

    private static PhysicalUnit requireSymbolUnit(Map<String, PhysicalUnit> units, String name, String equationId) {
        PhysicalUnit unit = units.get(name);
        if (unit == null) throw new IllegalStateException("Equation " + equationId + " does not define substitution symbol " + name);
        return unit;
    }

    private static ProjectileSolution response(Status status, Target target,
                                               List<Map<String, PhysicalQuantity>> candidates,
                                               List<ProjectileSolution.Step> steps, List<Input> missing,
                                               List<String> assumptions, List<String> warnings) {
        return new ProjectileSolution(status, target, "", Map.of(), List.of(),
                "Cartesian coordinates: +x right, +y up; launch angle counterclockwise from +x; SI units; landing uses the first future downward crossing.",
                candidates, steps, missing, assumptions, warnings);
    }

    private static PhysicalQuantity q(double value, PhysicalUnit unit) {
        return new PhysicalQuantity(value, unit);
    }

    private static List<String> assumptions(ProjectileProblem p) {
        List<String> assumptions = new ArrayList<>(MODEL_ASSUMPTIONS);
        if (!p.givens().containsKey(Input.INITIAL_X)) assumptions.add("initial-x-defaulted-to-zero-metres");
        if (!p.givens().containsKey(Input.INITIAL_Y)) assumptions.add("initial-y-defaulted-to-zero-metres");
        if (!p.givens().containsKey(Input.LANDING_Y)) assumptions.add("landing-y-defaulted-to-initial-y");
        return List.copyOf(assumptions);
    }

    /**
     * Deterministically solves a {@link CanonicalPhysicsProblem} by delegating all kinematics
     * to {@link ProjectileMotion}, keeping per-target statuses independent so valid target answers
     * are preserved even when sibling targets are unbounded, underdetermined, or have no landing.
     */
    public CanonicalSolution solveCanonical(CanonicalPhysicsProblem canonical) {
        if (canonical == null) {
            return new CanonicalSolution(
                    CanonicalSolution.OverallStatus.INVALID_INPUT,
                    "",
                    canonicalConvention(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of("canonical-problem-required"));
        }

        List<String> baseAssumptions = canonicalAssumptions(canonical);

        if (!canonical.unsupportedConstraints().isEmpty()) {
            List<CanonicalSolution.PartResult> unsupportedParts = canonical.parts().stream()
                    .map(part -> new CanonicalSolution.PartResult(
                            part.index(),
                            part.target(),
                            CanonicalSolution.PartStatus.UNSUPPORTED,
                            CanonicalSolution.ContactClassification.NOT_APPLICABLE,
                            requiredQuantitiesFor(part.target()),
                            List.of(),
                            List.of(),
                            List.of(),
                            baseAssumptions,
                            canonical.unsupportedConstraints()))
                    .toList();
            return new CanonicalSolution(
                    CanonicalSolution.OverallStatus.UNSUPPORTED,
                    canonical.statement(),
                    canonicalConvention(),
                    unsupportedParts,
                    canonical.unsupportedConstraints(),
                    baseAssumptions,
                    List.of("unsupported-physics-constraints"));
        }

        if (!canonical.validationErrors().isEmpty()) {
            List<CanonicalSolution.PartResult> invalidParts = canonical.parts().stream()
                    .map(part -> new CanonicalSolution.PartResult(
                            part.index(),
                            part.target(),
                            CanonicalSolution.PartStatus.INVALID_INPUT,
                            CanonicalSolution.ContactClassification.NOT_APPLICABLE,
                            requiredQuantitiesFor(part.target()),
                            List.of(),
                            List.of(),
                            List.of(),
                            baseAssumptions,
                            canonical.validationErrors()))
                    .toList();
            return new CanonicalSolution(
                    CanonicalSolution.OverallStatus.INVALID_INPUT,
                    canonical.statement(),
                    canonicalConvention(),
                    invalidParts,
                    List.of(),
                    baseAssumptions,
                    canonical.validationErrors());
        }

        ProjectileMotion sharedMotion = (canonical.hasVelocity() && canonical.hasGravity())
                ? canonical.toMotion()
                : null;

        List<CanonicalSolution.PartResult> results = new ArrayList<>(canonical.parts().size());
        for (CanonicalPhysicsProblem.PartSpec part : canonical.parts()) {
            results.add(solveCanonicalPart(canonical, part, sharedMotion, baseAssumptions));
        }

        CanonicalSolution.OverallStatus overall = CanonicalSolution.deriveOverallStatus(results, false, false);
        return new CanonicalSolution(
                overall,
                canonical.statement(),
                canonicalConvention(),
                results,
                List.of(),
                baseAssumptions,
                List.of());
    }

    private CanonicalSolution.PartResult solveCanonicalPart(
            CanonicalPhysicsProblem canonical,
            CanonicalPhysicsProblem.PartSpec part,
            ProjectileMotion motion,
            List<String> baseAssumptions) {

        CanonicalPhysicsProblem.CanonicalTarget target = part.target();
        EnumMap<Input, Double> partValues = new EnumMap<>(Input.class);
        List<String> partErrors = validatePartParameters(part.parameters(), partValues);
        if (!partErrors.isEmpty()) {
            return canonicalPartFailure(part, CanonicalSolution.PartStatus.INVALID_INPUT,
                    CanonicalSolution.ContactClassification.NOT_APPLICABLE,
                    List.of(), baseAssumptions, partErrors);
        }

        if (target instanceof CanonicalPhysicsProblem.StandardTarget standardTarget
                && (standardTarget.target() == Target.FIND_INITIAL_SPEED
                || standardTarget.target() == Target.FIND_LAUNCH_ANGLE)) {
            return solveCanonicalInversePart(canonical, part, standardTarget.target(), baseAssumptions);
        }

        List<String> missing = new ArrayList<>();
        if (!canonical.hasVelocity()) {
            canonical.missingSharedInputs().stream()
                    .filter(input -> input != Input.GRAVITY)
                    .map(Enum::name)
                    .forEach(missing::add);
        }
        if (!canonical.hasGravity()) {
            missing.add(Input.GRAVITY.name());
        }
        if (target instanceof CanonicalPhysicsProblem.StandardTarget standardTarget) {
            for (Input req : requiredPartInputsFor(standardTarget.target())) {
                if (!partValues.containsKey(req)) {
                    missing.add(req.name());
                }
            }
        }
        if (!missing.isEmpty()) {
            return canonicalPartFailure(part, CanonicalSolution.PartStatus.UNDERDETERMINED,
                    CanonicalSolution.ContactClassification.NOT_APPLICABLE,
                    missing.stream().distinct().toList(),
                    baseAssumptions,
                    List.of("additional-inputs-required"));
        }

        if (target instanceof CanonicalPhysicsProblem.SurfaceTarget surfaceTarget) {
            return solveCanonicalSurfacePart(part, surfaceTarget, motion, baseAssumptions);
        }

        Target stdTarget = ((CanonicalPhysicsProblem.StandardTarget) target).target();
        return solveCanonicalStandardForwardPart(canonical, part, stdTarget, partValues, motion, baseAssumptions);
    }

    private CanonicalSolution.PartResult solveCanonicalInversePart(
            CanonicalPhysicsProblem canonical,
            CanonicalPhysicsProblem.PartSpec part,
            Target inverseTarget,
            List<String> baseAssumptions) {
        if (!canonical.landingSurface().isHorizontal()
                && part.parameters().containsKey(Input.HORIZONTAL_DISPLACEMENT)
                && !part.parameters().containsKey(Input.VERTICAL_DISPLACEMENT)) {
            return canonicalPartFailure(part, CanonicalSolution.PartStatus.UNSUPPORTED,
                    CanonicalSolution.ContactClassification.NOT_APPLICABLE,
                    List.of(), baseAssumptions,
                    List.of("inverse-target-requires-inclined-surface-intersection"));
        }

        EnumMap<Input, PhysicalQuantity> givens = new EnumMap<>(Input.class);
        givens.put(Input.INITIAL_X, q(canonical.initialX(), PhysicalUnit.METRE));
        givens.put(Input.INITIAL_Y, q(canonical.initialY(), PhysicalUnit.METRE));
        canonical.gravity().ifPresent(g -> givens.put(Input.GRAVITY, q(g, PhysicalUnit.METRE_PER_SECOND_SQUARED)));
        if (canonical.landingSurface().isHorizontal()) {
            givens.put(Input.LANDING_Y, q(canonical.landingSurface().anchorY(), PhysicalUnit.METRE));
        }

        CanonicalPhysicsProblem.VelocityInputMetadata meta = canonical.velocityMetadata();
        if (inverseTarget == Target.FIND_INITIAL_SPEED) {
            if (canonical.derivedLaunchAngleDegrees().isPresent()) {
                givens.put(Input.LAUNCH_ANGLE, q(canonical.derivedLaunchAngleDegrees().getAsDouble(), PhysicalUnit.DEGREE));
            } else if (meta.submittedAngle() != null
                    && meta.submittedAngle().unit().dimension() == PhysicalUnit.Dimension.ANGLE
                    && Double.isFinite(meta.submittedAngle().valueInSi())) {
                double normalizedAngle = meta.angleReference() == CanonicalPhysicsProblem.AngleReference.INCLINE_SURFACE
                        ? meta.submittedAngle().valueInSi() + canonical.landingSurface().angleDegrees()
                        : meta.submittedAngle().valueInSi();
                givens.put(Input.LAUNCH_ANGLE, q(normalizedAngle, PhysicalUnit.DEGREE));
            }
        } else {
            if (canonical.derivedInitialSpeed().isPresent()) {
                givens.put(Input.INITIAL_SPEED, q(canonical.derivedInitialSpeed().getAsDouble(), PhysicalUnit.METRE_PER_SECOND));
            } else if (meta.submittedSpeed() != null
                    && meta.submittedSpeed().unit().dimension() == PhysicalUnit.Dimension.SPEED
                    && Double.isFinite(meta.submittedSpeed().valueInSi())) {
                givens.put(Input.INITIAL_SPEED, q(meta.submittedSpeed().valueInSi(), PhysicalUnit.METRE_PER_SECOND));
            }
        }

        givens.putAll(part.parameters());
        ProjectileSolution legacy = solve(new ProjectileProblem(canonical.statement(), inverseTarget, givens));
        return fromLegacyInverseSolution(part, legacy, baseAssumptions);
    }

    private CanonicalSolution.PartResult fromLegacyInverseSolution(
            CanonicalPhysicsProblem.PartSpec part,
            ProjectileSolution legacy,
            List<String> canonicalBaseAssumptions) {
        CanonicalSolution.PartResult mapped = fromLegacySolution(
                part, legacy, CanonicalSolution.ContactClassification.NOT_APPLICABLE, canonicalBaseAssumptions);
        List<String> filteredAssumptions = new ArrayList<>(canonicalBaseAssumptions);
        for (String item : legacy.assumptions()) {
            if (!"landing-y-defaulted-to-initial-y".equals(item) && !filteredAssumptions.contains(item)) {
                filteredAssumptions.add(item);
            }
        }
        return new CanonicalSolution.PartResult(
                mapped.partIndex(),
                mapped.target(),
                mapped.status(),
                mapped.contactClassification(),
                mapped.requiredQuantities(),
                mapped.candidates(),
                mapped.steps(),
                mapped.missingInputs(),
                filteredAssumptions,
                mapped.diagnostics());
    }

    private CanonicalSolution.PartResult solveCanonicalStandardForwardPart(
            CanonicalPhysicsProblem canonical,
            CanonicalPhysicsProblem.PartSpec part,
            Target target,
            Map<Input, Double> partValues,
            ProjectileMotion motion,
            List<String> baseAssumptions) {

        if (motion.gravity() == 0.0 && motion.initialVy() > 0.0
                && (target == Target.MAXIMUM_HEIGHT || target == Target.TIME_TO_APEX || target == Target.SPEED_AT_APEX)) {
            return canonicalPartFailure(part, CanonicalSolution.PartStatus.UNBOUNDED,
                    classifyContact(motion), List.of(), baseAssumptions,
                    List.of("positive-vertical-velocity-with-zero-gravity-has-no-finite-apex"));
        }

        ProjectileProblem synthetic = new ProjectileProblem(canonical.statement(), target, Map.of());
        return switch (target) {
            case HORIZONTAL_RANGE -> solveCanonicalHorizontalRange(part, motion, baseAssumptions);
            case FLIGHT_TIME -> solveCanonicalFlightTime(part, motion, baseAssumptions);
            case MAXIMUM_HEIGHT -> {
                ProjectileSolution legacy = optionalScalar(synthetic, baseAssumptions, motion.maximumHeight(),
                        "maximumHeight", PhysicalUnit.METRE, "maximum-height",
                        Map.of("y0", motion.initialY(), "vy0", motion.initialVy(), "g", motion.gravity()));
                yield fromLegacySolution(part, legacy, CanonicalSolution.ContactClassification.NOT_APPLICABLE, baseAssumptions);
            }
            case TIME_TO_APEX -> {
                ProjectileSolution legacy = optionalScalar(synthetic, baseAssumptions, motion.timeToApex(),
                        "timeToApex", PhysicalUnit.SECOND, "time-to-apex",
                        Map.of("vy0", motion.initialVy(), "g", motion.gravity()));
                yield fromLegacySolution(part, legacy, CanonicalSolution.ContactClassification.NOT_APPLICABLE, baseAssumptions);
            }
            case SPEED_AT_APEX -> {
                Optional<ProjectileMotion.State> apex = motion.apexState();
                if (apex.isEmpty()) {
                    yield canonicalPartFailure(part, CanonicalSolution.PartStatus.NO_SOLUTION,
                            CanonicalSolution.ContactClassification.NOT_APPLICABLE,
                            List.of(), baseAssumptions, List.of("no-finite-apex"));
                }
                ProjectileSolution legacy = scalar(synthetic, baseAssumptions, apex.get().speed(),
                        "speedAtApex", PhysicalUnit.METRE_PER_SECOND, "speed-at-apex",
                        Map.of("vx0", apex.get().vx(), "vyApex", apex.get().vy()));
                yield fromLegacySolution(part, legacy, CanonicalSolution.ContactClassification.NOT_APPLICABLE, baseAssumptions);
            }
            case VELOCITY_AT_TIME, SPEED_AT_TIME, POSITION_AT_TIME, DISTANCE_TRAVELLED_AT_TIME ->
                    fromLegacySolution(part, solveAtTime(synthetic, baseAssumptions, motion, partValues),
                            CanonicalSolution.ContactClassification.NOT_APPLICABLE, baseAssumptions);
            case POSITION_AT_HORIZONTAL_DISPLACEMENT, VELOCITY_AT_HORIZONTAL_DISPLACEMENT,
                    SPEED_AT_HORIZONTAL_DISPLACEMENT, TIME_AT_HORIZONTAL_DISPLACEMENT ->
                    fromLegacySolution(part, solveAtHorizontalDisplacement(synthetic, baseAssumptions, motion, partValues),
                            CanonicalSolution.ContactClassification.NOT_APPLICABLE, baseAssumptions);
            case POSITION_AT_HEIGHT, VELOCITY_AT_HEIGHT, SPEED_AT_HEIGHT, TIMES_AT_HEIGHT ->
                    fromLegacySolution(part, solveAtHeight(synthetic, baseAssumptions, motion, partValues),
                            CanonicalSolution.ContactClassification.NOT_APPLICABLE, baseAssumptions);
            case TIMES_AT_POSITION ->
                    fromLegacySolution(part, solveAtPosition(synthetic, baseAssumptions, motion, partValues),
                            CanonicalSolution.ContactClassification.NOT_APPLICABLE, baseAssumptions);
            case FIND_INITIAL_SPEED, FIND_LAUNCH_ANGLE ->
                    throw new AssertionError("Inverse target dispatched as forward target");
        };
    }

    private CanonicalSolution.PartResult solveCanonicalSurfacePart(
            CanonicalPhysicsProblem.PartSpec part,
            CanonicalPhysicsProblem.SurfaceTarget target,
            ProjectileMotion motion,
            List<String> baseAssumptions) {

        return switch (target) {
            case RANGE_ALONG_SURFACE -> solveCanonicalRangeAlongSurface(part, motion, baseAssumptions);
            case SIGNED_DISPLACEMENT_ALONG_SURFACE -> solveCanonicalSignedSurfaceDisplacement(part, motion, baseAssumptions);
            case COORDINATE_ALONG_SURFACE_FROM_ANCHOR -> solveCanonicalAnchorCoordinate(part, motion, baseAssumptions);
            case LANDING_POSITION -> solveCanonicalLandingPosition(part, motion, baseAssumptions);
            case MAXIMUM_HEIGHT_IN_FLIGHT -> solveCanonicalMaxHeightInFlight(part, motion, baseAssumptions);
            case MAX_PERPENDICULAR_DISTANCE, MAX_SIGNED_PERPENDICULAR_DISTANCE ->
                    solveCanonicalPerpendicularClearance(part, target, motion, baseAssumptions);
        };
    }

    private CanonicalSolution.PartResult solveCanonicalFlightTime(
            CanonicalPhysicsProblem.PartSpec part,
            ProjectileMotion motion,
            List<String> assumptions) {
        OptionalDouble flight = motion.timeOfFlight();
        if (flight.isEmpty()) {
            return noLandingResult(part, assumptions);
        }
        double duration = flight.getAsDouble();
        CanonicalSolution.ContactClassification contact = duration == 0.0
                ? CanonicalSolution.ContactClassification.IMMEDIATE_CONTACT
                : CanonicalSolution.ContactClassification.FINITE_FLIGHT;
        Map<String, PhysicalQuantity> results = Map.of("flightTime", q(duration, PhysicalUnit.SECOND));
        ProjectileSolution.Step step = surfaceLandingStep(motion, duration, Map.of(), results);
        return canonicalSolved(part, contact, List.of(results), List.of(step), assumptions,
                contactDiagnostics(contact));
    }

    private CanonicalSolution.PartResult solveCanonicalHorizontalRange(
            CanonicalPhysicsProblem.PartSpec part,
            ProjectileMotion motion,
            List<String> assumptions) {
        OptionalDouble flight = motion.timeOfFlight();
        OptionalDouble range = motion.horizontalRange();
        if (flight.isEmpty() || range.isEmpty()) {
            return noLandingResult(part, assumptions);
        }
        double duration = flight.getAsDouble();
        CanonicalSolution.ContactClassification contact = duration == 0.0
                ? CanonicalSolution.ContactClassification.IMMEDIATE_CONTACT
                : CanonicalSolution.ContactClassification.FINITE_FLIGHT;
        Map<String, PhysicalQuantity> results = Map.of("range", q(range.getAsDouble(), PhysicalUnit.METRE));
        ProjectileSolution.Step step = step("range-from-landing-time",
                Map.of("vx0", motion.initialVx(), "tLanding", duration),
                Map.of(),
                results);
        return canonicalSolved(part, contact, List.of(results), List.of(step), assumptions,
                contactDiagnostics(contact));
    }

    private CanonicalSolution.PartResult solveCanonicalRangeAlongSurface(
            CanonicalPhysicsProblem.PartSpec part,
            ProjectileMotion motion,
            List<String> assumptions) {
        OptionalDouble flight = motion.timeOfFlight();
        OptionalDouble range = motion.rangeAlongSurface();
        if (flight.isEmpty() || range.isEmpty()) {
            return noLandingResult(part, assumptions);
        }
        double duration = flight.getAsDouble();
        CanonicalSolution.ContactClassification contact = duration == 0.0
                ? CanonicalSolution.ContactClassification.IMMEDIATE_CONTACT
                : CanonicalSolution.ContactClassification.FINITE_FLIGHT;
        Map<String, PhysicalQuantity> results = Map.of("rangeAlongSurface", q(range.getAsDouble(), PhysicalUnit.METRE));
        ProjectileSolution.Step step = step("range-along-surface",
                Map.of("vx0", motion.initialVx(), "tLanding", duration,
                        "beta", motion.landingSurface().angleDegrees()),
                Map.of("flightTime", q(duration, PhysicalUnit.SECOND)),
                results);
        return canonicalSolved(part, contact, List.of(results), List.of(step), assumptions,
                contactDiagnostics(contact));
    }

    private CanonicalSolution.PartResult solveCanonicalSignedSurfaceDisplacement(
            CanonicalPhysicsProblem.PartSpec part,
            ProjectileMotion motion,
            List<String> assumptions) {
        OptionalDouble flight = motion.timeOfFlight();
        OptionalDouble signed = motion.signedDisplacementAlongSurface();
        if (flight.isEmpty() || signed.isEmpty()) {
            return noLandingResult(part, assumptions);
        }
        double duration = flight.getAsDouble();
        CanonicalSolution.ContactClassification contact = duration == 0.0
                ? CanonicalSolution.ContactClassification.IMMEDIATE_CONTACT
                : CanonicalSolution.ContactClassification.FINITE_FLIGHT;
        Map<String, PhysicalQuantity> results = Map.of(
                "signedSurfaceDisplacement", q(signed.getAsDouble(), PhysicalUnit.METRE));
        ProjectileSolution.Step step = step("signed-surface-displacement",
                Map.of("vx0", motion.initialVx(), "tLanding", duration,
                        "beta", motion.landingSurface().angleDegrees()),
                Map.of("flightTime", q(duration, PhysicalUnit.SECOND)),
                results);
        return canonicalSolved(part, contact, List.of(results), List.of(step), assumptions,
                contactDiagnostics(contact));
    }

    private CanonicalSolution.PartResult solveCanonicalAnchorCoordinate(
            CanonicalPhysicsProblem.PartSpec part,
            ProjectileMotion motion,
            List<String> assumptions) {
        OptionalDouble flight = motion.timeOfFlight();
        Optional<ProjectileMotion.State> landing = motion.landingState();
        OptionalDouble anchorCoord = motion.signedCoordinateAlongSurfaceFromAnchor();
        if (flight.isEmpty() || landing.isEmpty() || anchorCoord.isEmpty()) {
            return noLandingResult(part, assumptions);
        }
        double duration = flight.getAsDouble();
        CanonicalSolution.ContactClassification contact = duration == 0.0
                ? CanonicalSolution.ContactClassification.IMMEDIATE_CONTACT
                : CanonicalSolution.ContactClassification.FINITE_FLIGHT;
        Map<String, PhysicalQuantity> results = Map.of(
                "anchorSurfaceCoordinate", q(anchorCoord.getAsDouble(), PhysicalUnit.METRE));
        ProjectileSolution.Step step = step("coordinate-along-surface-from-anchor",
                Map.of("xLanding", landing.get().x(),
                        "anchorX", motion.landingSurface().anchorX(),
                        "beta", motion.landingSurface().angleDegrees()),
                Map.of("flightTime", q(duration, PhysicalUnit.SECOND)),
                results);
        return canonicalSolved(part, contact, List.of(results), List.of(step), assumptions,
                contactDiagnostics(contact));
    }

    private CanonicalSolution.PartResult solveCanonicalLandingPosition(
            CanonicalPhysicsProblem.PartSpec part,
            ProjectileMotion motion,
            List<String> assumptions) {
        OptionalDouble flight = motion.timeOfFlight();
        Optional<ProjectileMotion.State> landing = motion.landingState();
        if (flight.isEmpty() || landing.isEmpty()) {
            return noLandingResult(part, assumptions);
        }
        double duration = flight.getAsDouble();
        ProjectileMotion.State state = landing.get();
        CanonicalSolution.ContactClassification contact = duration == 0.0
                ? CanonicalSolution.ContactClassification.IMMEDIATE_CONTACT
                : CanonicalSolution.ContactClassification.FINITE_FLIGHT;
        Map<String, PhysicalQuantity> results = Map.of(
                "xLanding", q(state.x(), PhysicalUnit.METRE),
                "yLanding", q(state.y(), PhysicalUnit.METRE),
                "flightTime", q(duration, PhysicalUnit.SECOND));
        ProjectileSolution.Step landingTimeStep = surfaceLandingStep(
                motion, duration, Map.of(), Map.of("tLanding", q(duration, PhysicalUnit.SECOND)));
        ProjectileSolution.Step posStep = step("landing-position-on-surface",
                Map.of("x0", motion.initialX(), "y0", motion.initialY(),
                        "vx0", motion.initialVx(), "vy0", motion.initialVy(),
                        "g", motion.gravity(), "tLanding", duration),
                Map.of(),
                Map.of("xLanding", q(state.x(), PhysicalUnit.METRE),
                        "yLanding", q(state.y(), PhysicalUnit.METRE)));
        return canonicalSolved(part, contact, List.of(results), List.of(landingTimeStep, posStep),
                assumptions, contactDiagnostics(contact));
    }

    private CanonicalSolution.PartResult solveCanonicalMaxHeightInFlight(
            CanonicalPhysicsProblem.PartSpec part,
            ProjectileMotion motion,
            List<String> assumptions) {
        OptionalDouble height = motion.maximumHeightInFlight();
        CanonicalSolution.ContactClassification contact = classifyContact(motion);
        if (height.isEmpty()) {
            return canonicalPartFailure(part, CanonicalSolution.PartStatus.UNBOUNDED,
                    contact, List.of(), assumptions,
                    List.of("positive-vertical-velocity-with-zero-gravity-has-no-finite-apex"));
        }
        double tPeak;
        OptionalDouble flight = motion.timeOfFlight();
        if (flight.isPresent()) {
            double duration = flight.getAsDouble();
            if (duration == 0.0) {
                tPeak = 0.0;
            } else if (motion.gravity() > 0.0 && motion.initialVy() > 0.0
                    && (motion.initialVy() / motion.gravity()) < duration) {
                tPeak = motion.initialVy() / motion.gravity();
            } else {
                tPeak = motion.stateAt(duration).y() >= motion.initialY() ? duration : 0.0;
            }
        } else {
            tPeak = motion.gravity() > 0.0 && motion.initialVy() > 0.0
                    ? motion.initialVy() / motion.gravity()
                    : 0.0;
        }
        Map<String, PhysicalQuantity> results = Map.of(
                "maximumHeightInFlight", q(height.getAsDouble(), PhysicalUnit.METRE));
        ProjectileSolution.Step step = step("maximum-height-in-flight",
                Map.of("y0", motion.initialY(), "vy0", motion.initialVy(),
                        "tPeak", tPeak, "g", motion.gravity()),
                flight.isPresent() ? Map.of("flightTime", q(flight.getAsDouble(), PhysicalUnit.SECOND)) : Map.of(),
                results);
        return canonicalSolved(part, contact, List.of(results), List.of(step), assumptions,
                flight.isPresent() && motion.gravity() > 0.0 && motion.initialVy() > 0.0
                        && (motion.initialVy() / motion.gravity()) >= flight.getAsDouble()
                        ? List.of("impact-precedes-unconstrained-vertical-apex")
                        : List.of());
    }

    private CanonicalSolution.PartResult solveCanonicalPerpendicularClearance(
            CanonicalPhysicsProblem.PartSpec part,
            CanonicalPhysicsProblem.SurfaceTarget target,
            ProjectileMotion motion,
            List<String> assumptions) {
        CanonicalSolution.ContactClassification contact = classifyContact(motion);
        OptionalDouble maxSigned = motion.maxSignedPerpendicularDistance();
        if (maxSigned.isEmpty()) {
            return canonicalPartFailure(part, CanonicalSolution.PartStatus.UNBOUNDED,
                    contact, List.of(), assumptions,
                    List.of("positive-normal-velocity-with-zero-gravity-is-unbounded"));
        }

        if (target == CanonicalPhysicsProblem.SurfaceTarget.MAX_PERPENDICULAR_DISTANCE
                && motion.maxPerpendicularDistance().isEmpty()) {
            return canonicalPartFailure(part, CanonicalSolution.PartStatus.NO_SOLUTION,
                    contact, List.of(), assumptions,
                    List.of("trajectory-remains-strictly-below-surface"));
        }

        double value = target == CanonicalPhysicsProblem.SurfaceTarget.MAX_PERPENDICULAR_DISTANCE
                ? motion.maxPerpendicularDistance().orElseThrow()
                : maxSigned.getAsDouble();
        String resultKey = target == CanonicalPhysicsProblem.SurfaceTarget.MAX_PERPENDICULAR_DISTANCE
                ? "maxPerpendicularDistance"
                : "maxSignedPerpendicularDistance";
        Map<String, PhysicalQuantity> results = Map.of(resultKey, q(value, PhysicalUnit.METRE));

        LandingSurface surface = motion.landingSurface();
        ProjectileSolution.Step step;
        if (motion.gravity() > 0.0) {
            double dPerp0 = motion.signedPerpendicularDistanceAt(0.0);
            double vPerp0 = Math.fma(motion.initialVy(), surface.cosine(),
                    -motion.initialVx() * surface.sine());
            step = step("max-perpendicular-distance",
                    Map.of("dPerp0", dPerp0, "vPerp0", vPerp0,
                            "g", motion.gravity(), "beta", surface.angleDegrees()),
                    Map.of(),
                    results);
        } else {
            step = step("surface-perpendicular-clearance",
                    Map.of("x", motion.initialX(), "y", motion.initialY(),
                            "anchorX", surface.anchorX(), "anchorY", surface.anchorY(),
                            "beta", surface.angleDegrees()),
                    Map.of(),
                    results);
        }
        return canonicalSolved(part, contact, List.of(results), List.of(step), assumptions,
                contactDiagnostics(contact));
    }

    private ProjectileSolution.Step surfaceLandingStep(ProjectileMotion motion,
                                                       double landingTime,
                                                       Map<String, PhysicalQuantity> intermediates,
                                                       Map<String, PhysicalQuantity> results) {
        LandingSurface surface = motion.landingSurface();
        return step("surface-landing-time-root",
                Map.of("x0", motion.initialX(), "y0", motion.initialY(),
                        "vx0", motion.initialVx(), "vy0", motion.initialVy(),
                        "g", motion.gravity(), "t", landingTime,
                        "anchorX", surface.anchorX(), "anchorY", surface.anchorY(),
                        "beta", surface.angleDegrees()),
                intermediates,
                results);
    }

    private CanonicalSolution.PartResult noLandingResult(CanonicalPhysicsProblem.PartSpec part,
                                                         List<String> assumptions) {
        return canonicalPartFailure(part, CanonicalSolution.PartStatus.NO_SOLUTION,
                CanonicalSolution.ContactClassification.NO_LANDING,
                List.of(), assumptions, List.of("requested-event-is-not-finite-or-not-reached"));
    }

    private CanonicalSolution.PartResult fromLegacySolution(
            CanonicalPhysicsProblem.PartSpec part,
            ProjectileSolution legacy,
            CanonicalSolution.ContactClassification contact,
            List<String> baseAssumptions) {
        CanonicalSolution.PartStatus mapped = switch (legacy.status()) {
            case SOLVED -> CanonicalSolution.PartStatus.SOLVED;
            case MULTIPLE_SOLUTIONS -> CanonicalSolution.PartStatus.MULTIPLE_SOLUTIONS;
            case NO_SOLUTION -> CanonicalSolution.PartStatus.NO_SOLUTION;
            case UNDERDETERMINED -> CanonicalSolution.PartStatus.UNDERDETERMINED;
            case AMBIGUOUS -> CanonicalSolution.PartStatus.AMBIGUOUS;
            case UNBOUNDED -> CanonicalSolution.PartStatus.UNBOUNDED;
            case INVALID_INPUT -> CanonicalSolution.PartStatus.INVALID_INPUT;
        };
        List<String> missingNames = legacy.missingInputs().stream().map(Enum::name).toList();
        List<String> mergedAssumptions = legacy.assumptions().isEmpty() ? baseAssumptions : legacy.assumptions();
        return new CanonicalSolution.PartResult(
                part.index(),
                part.target(),
                mapped,
                contact,
                requiredQuantitiesFor(part.target()),
                legacy.candidates(),
                legacy.steps(),
                missingNames,
                mergedAssumptions,
                legacy.warnings());
    }

    private CanonicalSolution.PartResult canonicalSolved(
            CanonicalPhysicsProblem.PartSpec part,
            CanonicalSolution.ContactClassification contact,
            List<Map<String, PhysicalQuantity>> candidates,
            List<ProjectileSolution.Step> steps,
            List<String> assumptions,
            List<String> diagnostics) {
        return new CanonicalSolution.PartResult(
                part.index(),
                part.target(),
                CanonicalSolution.PartStatus.SOLVED,
                contact,
                requiredQuantitiesFor(part.target()),
                candidates,
                steps,
                List.of(),
                assumptions,
                diagnostics);
    }

    private CanonicalSolution.PartResult canonicalPartFailure(
            CanonicalPhysicsProblem.PartSpec part,
            CanonicalSolution.PartStatus status,
            CanonicalSolution.ContactClassification contact,
            List<String> missingInputs,
            List<String> assumptions,
            List<String> diagnostics) {
        return new CanonicalSolution.PartResult(
                part.index(),
                part.target(),
                status,
                contact,
                requiredQuantitiesFor(part.target()),
                List.of(),
                List.of(),
                missingInputs,
                assumptions,
                diagnostics);
    }

    private static CanonicalSolution.ContactClassification classifyContact(ProjectileMotion motion) {
        OptionalDouble flight = motion.timeOfFlight();
        if (flight.isEmpty()) {
            return CanonicalSolution.ContactClassification.NO_LANDING;
        }
        return flight.getAsDouble() == 0.0
                ? CanonicalSolution.ContactClassification.IMMEDIATE_CONTACT
                : CanonicalSolution.ContactClassification.FINITE_FLIGHT;
    }

    private static List<String> contactDiagnostics(CanonicalSolution.ContactClassification contact) {
        return contact == CanonicalSolution.ContactClassification.IMMEDIATE_CONTACT
                ? List.of("immediate-contact-at-launch")
                : List.of();
    }

    private static List<String> validatePartParameters(Map<Input, PhysicalQuantity> parameters,
                                                       Map<Input, Double> values) {
        List<String> errors = new ArrayList<>();
        parameters.forEach((input, quantity) -> {
            if (input == null || quantity == null) {
                errors.add("null-input-quantity");
            } else if (input.dimension() != quantity.unit().dimension()) {
                errors.add("incompatible-unit:" + input.name());
            } else if (!Double.isFinite(quantity.value())) {
                errors.add("non-finite-input:" + input.name());
            } else if ((input == Input.TIME || input == Input.TARGET_SPEED) && quantity.value() < 0.0) {
                errors.add("negative-nonnegative-quantity:" + input.name());
            } else {
                double si = quantity.valueInSi();
                if (!Double.isFinite(si)) {
                    errors.add("unit-conversion-overflow:" + input.name());
                } else {
                    values.put(input, si);
                }
            }
        });
        return errors;
    }

    private static List<Input> requiredPartInputsFor(Target target) {
        return switch (target) {
            case VELOCITY_AT_TIME, SPEED_AT_TIME, POSITION_AT_TIME, DISTANCE_TRAVELLED_AT_TIME -> List.of(Input.TIME);
            case POSITION_AT_HORIZONTAL_DISPLACEMENT, VELOCITY_AT_HORIZONTAL_DISPLACEMENT,
                    SPEED_AT_HORIZONTAL_DISPLACEMENT, TIME_AT_HORIZONTAL_DISPLACEMENT -> List.of(Input.HORIZONTAL_DISPLACEMENT);
            case POSITION_AT_HEIGHT, VELOCITY_AT_HEIGHT, SPEED_AT_HEIGHT, TIMES_AT_HEIGHT -> List.of(Input.TARGET_Y);
            case TIMES_AT_POSITION -> List.of(Input.TARGET_X, Input.TARGET_Y);
            default -> List.of();
        };
    }

    private static List<String> requiredQuantitiesFor(CanonicalPhysicsProblem.CanonicalTarget target) {
        if (target instanceof CanonicalPhysicsProblem.StandardTarget standardTarget) {
            return switch (standardTarget.target()) {
                case HORIZONTAL_RANGE -> List.of("range");
                case MAXIMUM_HEIGHT -> List.of("maximumHeight");
                case TIME_TO_APEX -> List.of("timeToApex");
                case FLIGHT_TIME -> List.of("flightTime");
                case SPEED_AT_APEX -> List.of("speedAtApex");
                case VELOCITY_AT_TIME, VELOCITY_AT_HORIZONTAL_DISPLACEMENT, VELOCITY_AT_HEIGHT -> List.of("vx", "vy");
                case SPEED_AT_TIME, SPEED_AT_HORIZONTAL_DISPLACEMENT, SPEED_AT_HEIGHT -> List.of("speed");
                case POSITION_AT_TIME, POSITION_AT_HORIZONTAL_DISPLACEMENT, POSITION_AT_HEIGHT -> List.of("x", "y");
                case TIMES_AT_HEIGHT, TIMES_AT_POSITION, TIME_AT_HORIZONTAL_DISPLACEMENT -> List.of("time");
                case DISTANCE_TRAVELLED_AT_TIME -> List.of("distanceTravelled");
                case FIND_INITIAL_SPEED -> List.of("initialSpeed");
                case FIND_LAUNCH_ANGLE -> List.of("launchAngle");
            };
        }
        return switch ((CanonicalPhysicsProblem.SurfaceTarget) target) {
            case RANGE_ALONG_SURFACE -> List.of("rangeAlongSurface");
            case SIGNED_DISPLACEMENT_ALONG_SURFACE -> List.of("signedSurfaceDisplacement");
            case COORDINATE_ALONG_SURFACE_FROM_ANCHOR -> List.of("anchorSurfaceCoordinate");
            case MAX_PERPENDICULAR_DISTANCE -> List.of("maxPerpendicularDistance");
            case MAX_SIGNED_PERPENDICULAR_DISTANCE -> List.of("maxSignedPerpendicularDistance");
            case LANDING_POSITION -> List.of("xLanding", "yLanding", "flightTime");
            case MAXIMUM_HEIGHT_IN_FLIGHT -> List.of("maximumHeightInFlight");
        };
    }

    private static List<String> canonicalAssumptions(CanonicalPhysicsProblem problem) {
        List<String> list = new ArrayList<>(MODEL_ASSUMPTIONS);
        list.add(problem.landingSurface().isHorizontal()
                ? "landing-surface-horizontal"
                : "landing-surface-inclined");
        for (String assumption : problem.assumptions()) {
            if (!list.contains(assumption)) {
                list.add(assumption);
            }
        }
        return List.copyOf(list);
    }

    private static String canonicalConvention() {
        return "Cartesian coordinates: +x right, +y up; canonical velocity vector (vx0, vy0); SI units; landing uses the first future downward surface crossing.";
    }
}
