package edu.physics.problem;

import edu.physics.projectile.ProjectileMotion;

import java.util.ArrayList;
import java.util.EnumMap;
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
        if (!invalid.isEmpty()) return response(Status.INVALID_INPUT, problem.target(), List.of(), List.of(), List.of(), List.of(), invalid);

        List<Input> missing = missingInputs(problem, values);
        if (!missing.isEmpty()) return response(Status.UNDERDETERMINED, problem.target(), List.of(), List.of(), missing, assumptions(problem), List.of("additional-inputs-required"));

        try {
            return solveValidated(problem, values);
        } catch (IllegalArgumentException ex) {
            return response(Status.INVALID_INPUT, problem.target(), List.of(), List.of(), List.of(), assumptions(problem), List.of("invalid-physical-input"));
        } catch (ArithmeticException ex) {
            return response(Status.INVALID_INPUT, problem.target(), List.of(), List.of(), List.of(), assumptions(problem), List.of("numeric-range-exceeded"));
        }
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
            case HORIZONTAL_RANGE -> optionalScalar(problem, assumptions, model.horizontalRange(), "range", PhysicalUnit.METRE, "range-from-landing-time", "R = vx0 * tLanding", Map.of("vx0", model.initialVx()));
            case MAXIMUM_HEIGHT -> optionalScalar(problem, assumptions, model.maximumHeight(), "maximumHeight", PhysicalUnit.METRE, "vertical-apex", "yMax = y0 + vy0^2 / (2g)", Map.of("y0", y0, "vy0", model.initialVy(), "g", gravity));
            case TIME_TO_APEX -> optionalScalar(problem, assumptions, model.timeToApex(), "timeToApex", PhysicalUnit.SECOND, "vertical-apex", "tApex = max(0, vy0 / g)", Map.of("vy0", model.initialVy(), "g", gravity));
            case FLIGHT_TIME -> optionalScalar(problem, assumptions, model.timeOfFlight(), "flightTime", PhysicalUnit.SECOND, "landing-height-root", "y0 + vy0*t - 0.5*g*t^2 = yLanding", Map.of("y0", y0, "vy0", model.initialVy(), "g", gravity, "yLanding", landingY));
            case SPEED_AT_APEX -> {
                Optional<ProjectileMotion.State> apex = model.apexState();
                yield apex.isEmpty()
                        ? response(Status.NO_SOLUTION, target, List.of(), List.of(), List.of(), assumptions, List.of("no-finite-apex"))
                        : scalar(problem, assumptions, apex.get().speed(), "speedAtApex", PhysicalUnit.METRE_PER_SECOND, "speed-at-apex", "speedApex = sqrt(vx0^2 + vyApex^2)", Map.of("vx0", apex.get().vx(), "vyApex", apex.get().vy()));
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
                    "position-at-time", "x=x0+vx0*t; y=y0+vy0*t-0.5*g*t^2",
                    Map.of("t", time), Map.of("x", q(state.x(), PhysicalUnit.METRE), "y", q(state.y(), PhysicalUnit.METRE)),
                    Map.of("x", q(state.x(), PhysicalUnit.METRE), "y", q(state.y(), PhysicalUnit.METRE)), List.of());
            case VELOCITY_AT_TIME -> candidate(problem, Status.SOLVED, assumptions,
                    "velocity-at-time", "vx=vx0; vy=vy0-g*t", Map.of("t", time),
                    Map.of("vx", q(state.vx(), PhysicalUnit.METRE_PER_SECOND), "vy", q(state.vy(), PhysicalUnit.METRE_PER_SECOND)),
                    Map.of("vx", q(state.vx(), PhysicalUnit.METRE_PER_SECOND), "vy", q(state.vy(), PhysicalUnit.METRE_PER_SECOND)), List.of());
            case SPEED_AT_TIME -> scalar(problem, assumptions, state.speed(), "speed", PhysicalUnit.METRE_PER_SECOND,
                    "speed-from-velocity", "speed = sqrt(vx^2 + vy^2)", Map.of("vx", state.vx(), "vy", state.vy()));
            case DISTANCE_TRAVELLED_AT_TIME -> scalar(problem, assumptions, model.distanceTravelledAt(time), "distanceTravelled", PhysicalUnit.METRE,
                    "projectile-arc-length", "s(t) = integral from 0 to t of sqrt(vx^2 + vy(t)^2) dt", Map.of("t", time, "vx", state.vx(), "vy", state.vy(), "g", model.gravity()));
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
        ProjectileSolution.Step step = step("horizontal-position-query", "t = dx / vx0", Map.of("dx", v.get(Input.HORIZONTAL_DISPLACEMENT), "vx0", model.initialVx()), candidates.get(0));
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
        ProjectileSolution.Step step = step("vertical-position-roots", "y = y0 + vy0*t - 0.5*g*t^2", Map.of("targetY", height, "vy0", model.initialVy(), "g", model.gravity()), candidates.get(0));
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
        ProjectileSolution.Step step = step("position-query", "x(t)=targetX and y(t)=targetY", Map.of("targetX", targetX, "targetY", targetY), candidates.get(0));
        return response(status, problem.target(), candidates, List.of(step), List.of(), assumptions, List.of());
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
                        "initial-speed-from-speed", "speed(0)=v0", Map.of("targetSpeed", targetSpeed, "time", time));
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
            ProjectileSolution.Step step = step("inverse-initial-speed-from-speed", "speed(t)^2 = v0^2 - 2*g*t*v0*sin(theta) + (g*t)^2", Map.of("speedAtTime", targetSpeed, "g", gravity, "t", time, "theta", v.get(Input.LAUNCH_ANGLE)), candidates.get(0));
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
                        "initial-speed-from-horizontal-velocity", "vx0 = v0*cos(theta)", Map.of("vx0", targetVx, "cosTheta", cosine));
            }
        }
        if (v.containsKey(Input.TIME) && v.containsKey(Input.TARGET_VY)) {
            double time = v.get(Input.TIME);
            double denominator = sinDegrees(angleDegrees);
            double numerator = v.get(Input.TARGET_VY) + gravity * time;
            if (denominator == 0.0) return response(Status.UNDERDETERMINED, problem.target(), List.of(), List.of(), List.of(Input.TARGET_VY), assumptions(problem), List.of("vertical-component-condition-does-not-determine-speed"));
            double speed = numerator / denominator;
            if (!Double.isFinite(speed)) return response(Status.INVALID_INPUT, problem.target(), List.of(), List.of(), List.of(), assumptions(problem), List.of("numeric-range-exceeded"));
            if (speed < 0.0) return response(Status.NO_SOLUTION, problem.target(), List.of(), List.of(), List.of(), assumptions(problem), List.of("condition-implies-negative-speed"));
            return scalar(problem, assumptions(problem), speed, "initialSpeed", PhysicalUnit.METRE_PER_SECOND,
                    "initial-speed-from-vertical-velocity", "vy(t)=v0*sin(theta)-g*t",
                    Map.of("vy", v.get(Input.TARGET_VY), "g", gravity, "t", time, "sinTheta", denominator));
        }
        if (v.containsKey(Input.HORIZONTAL_DISPLACEMENT) && v.containsKey(Input.VERTICAL_DISPLACEMENT)) {
            double dx = v.get(Input.HORIZONTAL_DISPLACEMENT);
            double dy = v.get(Input.VERTICAL_DISPLACEMENT);
            double cosine = cosDegrees(angleDegrees);
            if (dx == 0.0 || cosine == 0.0) return response(Status.UNDERDETERMINED, problem.target(), List.of(), List.of(), List.of(Input.TIME), assumptions(problem), List.of("zero-horizontal-component-needs-time-condition"));
            double requiredHeight = dx * (sinDegrees(angleDegrees) / cosine) - dy;
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
                    "initial-speed-from-displacement", "dy=dx*tan(theta)-g*dx^2/(2*v0^2*cos(theta)^2)",
                    Map.of("dx", dx, "dy", dy, "theta", v.get(Input.LAUNCH_ANGLE), "g", gravity));
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
                    "launch-angle-from-displacement", "tan(theta)=dy/dx", Map.of("dx", dx, "dy", dy));
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
        ProjectileSolution.Step step = step("inverse-launch-angle", "A*tan(theta)^2 - dx*tan(theta) + (A+dy)=0", Map.of("A", a, "dx", dx, "dy", dy, "v0", speed, "g", gravity), candidates.get(0));
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
                                                     String equationId, String equation, Map<String, Double> substitutions) {
        return value.isPresent()
                ? scalar(p, assumptions, value.getAsDouble(), name, unit, equationId, equation, substitutions)
                : response(Status.NO_SOLUTION, p.target(), List.of(), List.of(), List.of(), assumptions, List.of("requested-event-is-not-finite-or-not-reached"));
    }

    private static ProjectileSolution scalar(ProjectileProblem p, List<String> assumptions, double value,
                                            String name, PhysicalUnit unit, String equationId,
                                            String equation, Map<String, Double> substitutions) {
        Map<String, PhysicalQuantity> candidate = Map.of(name, q(value, unit));
        return candidate(p, Status.SOLVED, assumptions, equationId, equation, substitutions,
                candidate, candidate, List.of());
    }

    private static ProjectileSolution candidate(ProjectileProblem p, Status status, List<String> assumptions,
                                                String equationId, String equation,
                                                Map<String, Double> substitutions,
                                                Map<String, PhysicalQuantity> results,
                                                Map<String, PhysicalQuantity> candidate,
                                                List<String> warnings) {
        return response(status, p.target(), List.of(candidate), List.of(step(equationId, equation, substitutions, results)), List.of(), assumptions, warnings);
    }

    private static ProjectileSolution.Step step(String id, String equation, Map<String, Double> substitutions,
                                                Map<String, PhysicalQuantity> results) {
        return new ProjectileSolution.Step(id, equation, substitutions, results, "projectile-motion");
    }

    private static ProjectileSolution response(Status status, Target target,
                                               List<Map<String, PhysicalQuantity>> candidates,
                                               List<ProjectileSolution.Step> steps, List<Input> missing,
                                               List<String> assumptions, List<String> warnings) {
        return new ProjectileSolution(status, target, candidates, steps, missing, assumptions, warnings);
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
}
