package edu.physics.problem;

import java.util.Arrays;
import java.util.List;

import static edu.physics.problem.MathExpression.FunctionName.*;
import static edu.physics.problem.MathExpression.Operator.*;

/** Catalogue of symbolic relationships used by the deterministic projectile solver. */
final class ProjectileEquations {
    private static final PhysicalUnit M = PhysicalUnit.METRE;
    private static final PhysicalUnit S = PhysicalUnit.SECOND;
    private static final PhysicalUnit V = PhysicalUnit.METRE_PER_SECOND;
    private static final PhysicalUnit A = PhysicalUnit.METRE_PER_SECOND_SQUARED;
    private static final PhysicalUnit DEG = PhysicalUnit.DEGREE;
    private static final PhysicalUnit ONE = PhysicalUnit.ONE;

    private ProjectileEquations() {}

    static MathExpression.Equation byId(String id) {
        return switch (id) {
            case "range-from-landing-time" -> {
                var range = s("range", "horizontal displacement to landing", M);
                var vx = s("vx0", "initial horizontal velocity", V);
                var landingTime = s("tLanding", "first landing time", S);
                yield e(id, "constant-horizontal-velocity", range, mul(vx, landingTime), range, vx, landingTime);
            }
            case "vertical-apex", "maximum-height" -> {
                var yMax = s("yMax", "maximum vertical position", M);
                var y0 = s("y0", "initial vertical position", M);
                var vy0 = s("vy0", "initial vertical velocity", V);
                var gravity = s("g", "gravitational acceleration magnitude", A);
                var rhs = add(y0, div(pow(vy0, 2), mul(lit(2), gravity)));
                yield e("maximum-height", "vertical-kinematics", yMax, rhs, yMax, y0, vy0, gravity);
            }
            case "time-to-apex" -> {
                var apexTime = s("tApex", "time at maximum height", S);
                var vy0 = s("vy0", "initial vertical velocity", V);
                var gravity = s("g", "gravitational acceleration magnitude", A);
                yield e(id, "vertical-velocity-zero-at-apex", apexTime,
                        fn(MAX, lit(0), div(vy0, gravity)), apexTime, vy0, gravity);
            }
            case "landing-height-root" -> {
                var y0 = s("y0", "initial vertical position", M);
                var vy0 = s("vy0", "initial vertical velocity", V);
                var time = s("t", "elapsed time", S);
                var gravity = s("g", "gravitational acceleration magnitude", A);
                var landingY = s("yLanding", "landing surface vertical position", M);
                var rhs = sub(add(y0, mul(vy0, time)), div(mul(lit(0.5), mul(gravity, pow(time, 2))), lit(1)));
                yield e(id, "vertical-position-kinematics", rhs, landingY, y0, vy0, time, gravity, landingY);
            }
            case "speed-at-apex" -> {
                var speed = s("speedApex", "speed at maximum height", V);
                var vx = s("vx0", "horizontal velocity at apex", V);
                var vy = s("vyApex", "vertical velocity at apex", V);
                yield e(id, "speed-magnitude", speed, sqrt(add(pow(vx, 2), pow(vy, 2))), speed, vx, vy);
            }
            case "position-at-time" -> {
                var x = s("x", "horizontal position", M);
                var y = s("y", "vertical position", M);
                var x0 = s("x0", "initial horizontal position", M);
                var y0 = s("y0", "initial vertical position", M);
                var vx0 = s("vx0", "initial horizontal velocity", V);
                var vy0 = s("vy0", "initial vertical velocity", V);
                var time = s("t", "elapsed time", S);
                var gravity = s("g", "gravitational acceleration magnitude", A);
                yield e(id, "constant-acceleration-kinematics", new MathExpression.Tuple(List.of(x, y)),
                        new MathExpression.Tuple(List.of(add(x0, mul(vx0, time)),
                                sub(add(y0, mul(vy0, time)), mul(lit(0.5), mul(gravity, pow(time, 2)))))),
                        x, y, x0, y0, vx0, vy0, time, gravity);
            }
            case "velocity-at-time" -> {
                var vx = s("vx", "horizontal velocity", V);
                var vy = s("vy", "vertical velocity", V);
                var vx0 = s("vx0", "initial horizontal velocity", V);
                var vy0 = s("vy0", "initial vertical velocity", V);
                var gravity = s("g", "gravitational acceleration magnitude", A);
                var time = s("t", "elapsed time", S);
                yield e(id, "constant-acceleration-velocity", new MathExpression.Tuple(List.of(vx, vy)),
                        new MathExpression.Tuple(List.of(vx0, sub(vy0, mul(gravity, time)))), vx, vy, vx0, vy0, gravity, time);
            }
            case "speed-from-velocity" -> {
                var speed = s("speed", "resultant speed", V);
                var vx = s("vx", "horizontal velocity", V);
                var vy = s("vy", "vertical velocity", V);
                yield e(id, "velocity-magnitude", speed, sqrt(add(pow(vx, 2), pow(vy, 2))), speed, vx, vy);
            }
            case "projectile-arc-length" -> {
                var distance = s("s", "distance travelled along trajectory", M);
                var time = s("t", "elapsed time", S);
                var vx = s("vx0", "constant horizontal velocity", V);
                var vy0 = s("vy0", "initial vertical velocity", V);
                var gravity = s("g", "gravitational acceleration magnitude", A);
                var tau = s("tau", "integration time", S);
                var integrand = sqrt(add(pow(vx, 2), pow(sub(vy0, mul(gravity, tau)), 2)));
                yield e(id, "trajectory-arc-length", distance,
                        new MathExpression.Integral(integrand, tau, lit(0), time), distance, time, vx, vy0, gravity, tau);
            }
            case "horizontal-position-query" -> {
                var time = s("t", "time to horizontal displacement", S);
                var dx = s("dx", "horizontal displacement from launch", M);
                var vx0 = s("vx0", "initial horizontal velocity", V);
                yield e(id, "constant-horizontal-velocity", time, div(dx, vx0), time, dx, vx0);
            }
            case "vertical-position-roots" -> {
                var y = s("y", "vertical position", M);
                var y0 = s("y0", "initial vertical position", M);
                var vy0 = s("vy0", "initial vertical velocity", V);
                var time = s("t", "time at requested height", S);
                var gravity = s("g", "gravitational acceleration magnitude", A);
                var targetY = s("targetY", "requested vertical position", M);
                yield e(id, "vertical-position-quadratic", y, sub(add(y0, mul(vy0, time)), mul(lit(0.5), mul(gravity, pow(time, 2)))),
                        y, y0, vy0, time, gravity, targetY);
            }
            case "position-query" -> {
                var x = s("x(t)", "horizontal position at time t", M);
                var y = s("y(t)", "vertical position at time t", M);
                var targetX = s("targetX", "requested horizontal position", M);
                var targetY = s("targetY", "requested vertical position", M);
                yield e(id, "two-dimensional-position-intersection", new MathExpression.Tuple(List.of(x, y)),
                        new MathExpression.Tuple(List.of(targetX, targetY)), x, y, targetX, targetY);
            }
            case "initial-speed-from-speed" -> {
                var speed = s("initialSpeed", "initial speed", V);
                var target = s("targetSpeed", "speed at time zero", V);
                var time = s("time", "elapsed time for the degenerate speed condition", S);
                yield e(id, "initial-speed-at-launch", speed, target, speed, target, time);
            }
            case "inverse-initial-speed-from-speed" -> {
                var speedAtTime = s("speedAtTime", "speed at requested time", V);
                var initialSpeed = s("v0", "initial speed", V);
                var gravity = s("g", "gravitational acceleration magnitude", A);
                var time = s("t", "elapsed time", S);
                var angle = s("theta", "launch angle counterclockwise from +x", DEG);
                var rhs = add(sub(pow(initialSpeed, 2), mul(lit(2), mul(gravity, mul(time, mul(initialSpeed, fn(SIN, angle)))))),
                        pow(mul(gravity, time), 2));
                yield e(id, "speed-from-velocity-components", pow(speedAtTime, 2), rhs, speedAtTime, initialSpeed, gravity, time, angle);
            }
            case "initial-speed-from-horizontal-velocity" -> {
                var vx0 = s("vx0", "initial horizontal velocity", V);
                var initialSpeed = s("v0", "initial speed", V);
                var angle = s("theta", "launch angle counterclockwise from +x", DEG);
                var cosTheta = s("cosTheta", "cosine of the launch angle", ONE);
                yield e(id, "initial-velocity-resolution", vx0, mul(initialSpeed, fn(COS, angle)), vx0, initialSpeed, angle, cosTheta);
            }
            case "initial-speed-from-vertical-velocity" -> {
                var vy = s("vy", "vertical velocity at requested time", V);
                var initialSpeed = s("v0", "initial speed", V);
                var angle = s("theta", "launch angle counterclockwise from +x", DEG);
                var gravity = s("g", "gravitational acceleration magnitude", A);
                var time = s("t", "elapsed time", S);
                var sinTheta = s("sinTheta", "sine of the launch angle", ONE);
                yield e(id, "vertical-velocity-kinematics", vy, sub(mul(initialSpeed, fn(SIN, angle)), mul(gravity, time)),
                        vy, initialSpeed, angle, gravity, time, sinTheta);
            }
            case "initial-speed-from-displacement" -> {
                var dy = s("dy", "vertical displacement", M);
                var dx = s("dx", "horizontal displacement", M);
                var angle = s("theta", "launch angle counterclockwise from +x", DEG);
                var gravity = s("g", "gravitational acceleration magnitude", A);
                var initialSpeed = s("v0", "initial speed", V);
                var rhs = sub(mul(dx, fn(TAN, angle)),
                        div(mul(gravity, pow(dx, 2)), mul(lit(2), mul(pow(initialSpeed, 2), pow(fn(COS, angle), 2)))));
                yield e(id, "projectile-trajectory-equation", dy, rhs, dy, dx, angle, gravity, initialSpeed);
            }
            case "launch-angle-from-displacement" -> {
                var angle = s("theta", "launch angle counterclockwise from +x", DEG);
                var dy = s("dy", "vertical displacement", M);
                var dx = s("dx", "horizontal displacement", M);
                yield e(id, "zero-gravity-linear-motion", fn(TAN, angle), div(dy, dx), angle, dy, dx);
            }
            case "inverse-launch-angle" -> {
                var parameter = s("A", "g*dx^2/(2*v0^2)", M);
                var angle = s("theta", "launch angle counterclockwise from +x", DEG);
                var dx = s("dx", "horizontal displacement", M);
                var dy = s("dy", "vertical displacement", M);
                var speed = s("v0", "initial speed", V);
                var gravity = s("g", "gravitational acceleration magnitude", A);
                var tangent = fn(TAN, angle);
                var lhs = add(sub(mul(parameter, pow(tangent, 2)), mul(dx, tangent)), add(parameter, dy));
                yield e(id, "projectile-trajectory-quadratic", lhs, lit(0), parameter, angle, dx, dy, speed, gravity);
            }
            case "surface-landing-time-root" -> {
                var x0 = s("x0", "initial horizontal position", M);
                var y0 = s("y0", "initial vertical position", M);
                var vx0 = s("vx0", "initial horizontal velocity", V);
                var vy0 = s("vy0", "initial vertical velocity", V);
                var gravity = s("g", "gravitational acceleration magnitude", A);
                var time = s("t", "elapsed time", S);
                var anchorX = s("anchorX", "surface anchor horizontal position", M);
                var anchorY = s("anchorY", "surface anchor vertical position", M);
                var beta = s("beta", "landing surface inclination angle", DEG);
                var yTrajectory = sub(add(y0, mul(vy0, time)), mul(lit(0.5), mul(gravity, pow(time, 2))));
                var xTrajectory = add(x0, mul(vx0, time));
                var ySurface = add(anchorY, mul(sub(xTrajectory, anchorX), fn(TAN, beta)));
                yield e(id, "surface-intersection-kinematics", yTrajectory, ySurface,
                        x0, y0, vx0, vy0, gravity, time, anchorX, anchorY, beta);
            }
            case "range-along-surface" -> {
                var range = s("rangeAlongSurface", "absolute distance along landing surface", M);
                var vx0 = s("vx0", "initial horizontal velocity", V);
                var tLanding = s("tLanding", "first landing time", S);
                var beta = s("beta", "landing surface inclination angle", DEG);
                var signedDisp = div(mul(vx0, tLanding), fn(COS, beta));
                yield e(id, "surface-tangential-range", range, sqrt(pow(signedDisp, 2)),
                        range, vx0, tLanding, beta);
            }
            case "signed-surface-displacement" -> {
                var displacement = s("signedSurfaceDisplacement", "signed displacement along surface tangent", M);
                var vx0 = s("vx0", "initial horizontal velocity", V);
                var tLanding = s("tLanding", "first landing time", S);
                var beta = s("beta", "landing surface inclination angle", DEG);
                yield e(id, "surface-tangential-displacement", displacement, div(mul(vx0, tLanding), fn(COS, beta)),
                        displacement, vx0, tLanding, beta);
            }
            case "coordinate-along-surface-from-anchor" -> {
                var coordinate = s("anchorSurfaceCoordinate", "signed surface coordinate from anchor", M);
                var xLanding = s("xLanding", "landing horizontal coordinate", M);
                var anchorX = s("anchorX", "surface anchor horizontal coordinate", M);
                var beta = s("beta", "landing surface inclination angle", DEG);
                yield e(id, "surface-anchor-coordinate", coordinate, div(sub(xLanding, anchorX), fn(COS, beta)),
                        coordinate, xLanding, anchorX, beta);
            }
            case "landing-position-on-surface" -> {
                var xLanding = s("xLanding", "landing horizontal coordinate", M);
                var yLanding = s("yLanding", "landing vertical coordinate", M);
                var x0 = s("x0", "initial horizontal position", M);
                var y0 = s("y0", "initial vertical position", M);
                var vx0 = s("vx0", "initial horizontal velocity", V);
                var vy0 = s("vy0", "initial vertical velocity", V);
                var gravity = s("g", "gravitational acceleration magnitude", A);
                var tLanding = s("tLanding", "first landing time", S);
                yield e(id, "surface-landing-coordinates",
                        new MathExpression.Tuple(List.of(xLanding, yLanding)),
                        new MathExpression.Tuple(List.of(
                                add(x0, mul(vx0, tLanding)),
                                sub(add(y0, mul(vy0, tLanding)), mul(lit(0.5), mul(gravity, pow(tLanding, 2)))))),
                        xLanding, yLanding, x0, y0, vx0, vy0, gravity, tLanding);
            }
            case "max-perpendicular-distance" -> {
                var maxPerp = s("maxPerpDistance", "maximum signed perpendicular clearance from surface (non-negative when above or on surface)", M);
                var dPerp0 = s("dPerp0", "initial perpendicular clearance", M);
                var vPerp0 = s("vPerp0", "initial perpendicular velocity component", V);
                var gravity = s("g", "gravitational acceleration magnitude", A);
                var beta = s("beta", "landing surface inclination angle", DEG);
                var rise = div(pow(fn(MAX, lit(0), vPerp0), 2), mul(lit(2), mul(gravity, fn(COS, beta))));
                yield e(id, "surface-normal-kinematics", maxPerp, add(dPerp0, rise),
                        maxPerp, dPerp0, vPerp0, gravity, beta);
            }
            case "surface-perpendicular-clearance" -> {
                var perp = s("perpDistance", "signed perpendicular distance from surface", M);
                var x = s("x", "horizontal position", M);
                var y = s("y", "vertical position", M);
                var anchorX = s("anchorX", "surface anchor horizontal coordinate", M);
                var anchorY = s("anchorY", "surface anchor vertical coordinate", M);
                var beta = s("beta", "landing surface inclination angle", DEG);
                var rhs = sub(mul(sub(y, anchorY), fn(COS, beta)), mul(sub(x, anchorX), fn(SIN, beta)));
                yield e(id, "surface-normal-projection", perp, rhs, perp, x, y, anchorX, anchorY, beta);
            }
            case "maximum-height-in-flight" -> {
                var yMaxFlight = s("yMaxFlight", "maximum vertical height attained during flight", M);
                var y0 = s("y0", "initial vertical position", M);
                var vy0 = s("vy0", "initial vertical velocity", V);
                var tPeak = s("tPeak", "flight instant attaining maximum height", S);
                var gravity = s("g", "gravitational acceleration magnitude", A);
                yield e(id, "flight-bounded-vertical-peak", yMaxFlight,
                        sub(add(y0, mul(vy0, tPeak)), mul(lit(0.5), mul(gravity, pow(tPeak, 2)))),
                        yMaxFlight, y0, vy0, tPeak, gravity);
            }
            default -> throw new IllegalArgumentException("No structured equation registered for " + id);
        };
    }

    private static MathExpression.Equation e(String id, String principle, MathExpression left,
                                              MathExpression right, MathExpression.Symbol... symbols) {
        return new MathExpression.Equation(id, principle, left, right, Arrays.asList(symbols));
    }
    private static MathExpression.Symbol s(String name, String definition, PhysicalUnit unit) {
        return new MathExpression.Symbol(name, definition, unit);
    }
    private static MathExpression.Literal lit(double value) { return new MathExpression.Literal(value); }
    private static MathExpression.Binary add(MathExpression x, MathExpression y) { return new MathExpression.Binary(ADD, x, y); }
    private static MathExpression.Binary sub(MathExpression x, MathExpression y) { return new MathExpression.Binary(SUBTRACT, x, y); }
    private static MathExpression.Binary mul(MathExpression x, MathExpression y) { return new MathExpression.Binary(MULTIPLY, x, y); }
    private static MathExpression.Binary div(MathExpression x, MathExpression y) { return new MathExpression.Binary(DIVIDE, x, y); }
    private static MathExpression.Binary pow(MathExpression x, double exponent) { return new MathExpression.Binary(POWER, x, lit(exponent)); }
    private static MathExpression.Function fn(MathExpression.FunctionName name, MathExpression... args) {
        return new MathExpression.Function(name, Arrays.asList(args));
    }
    private static MathExpression.Function sqrt(MathExpression value) { return fn(SQRT, value); }
}
