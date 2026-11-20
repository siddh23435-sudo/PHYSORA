package edu.physics.problem;

import edu.physics.projectile.ProjectileMotion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Pure, deterministic educational derivation generator for 2D Kinematics.
 * Structures derivations into the approved six-part sequence:
 * GIVEN → PHYSICAL PRINCIPLE → FORMULA → SUBSTITUTION → CALCULATION → FINAL ANSWER & INTERPRETATION.
 */
public final class GuidedDerivations {

    public enum Topic {
        VELOCITY_COMPONENTS("Initial Velocity Components", "v₀x and v₀y"),
        TIME_TO_APEX("Time to Peak Altitude", "t_apex"),
        MAX_HEIGHT("Maximum Height", "H_max"),
        TIME_OF_FLIGHT("Total Flight Time", "T_flight"),
        HORIZONTAL_RANGE("Horizontal Range", "R"),
        LANDING_VELOCITY("Landing Velocity", "v_land");

        private final String title;
        private final String shortName;

        Topic(String title, String shortName) {
            this.title = title;
            this.shortName = shortName;
        }

        public String title() { return title; }
        public String shortName() { return shortName; }
    }

    public enum StepKind {
        GIVEN("GIVEN PARAMETERS", "Active experiment conditions"),
        PRINCIPLE("PHYSICAL PRINCIPLE", "Governing physical law"),
        FORMULA("ALGEBRAIC FORMULA", "Symbolic equation"),
        SUBSTITUTION("VALUE SUBSTITUTION", "Inserting experiment values"),
        CALCULATION("STEP-BY-STEP CALCULATION", "Intermediate algebraic steps"),
        FINAL_ANSWER("FINAL ANSWER & INTERPRETATION", "Result and physical meaning");

        private final String heading;
        private final String subtitle;

        StepKind(String heading, String subtitle) {
            this.heading = heading;
            this.subtitle = subtitle;
        }

        public String heading() { return heading; }
        public String subtitle() { return subtitle; }
    }

    public record DerivationStep(
            StepKind kind,
            String title,
            String content,
            List<String> bulletDetails) {
        public DerivationStep {
            bulletDetails = List.copyOf(bulletDetails);
        }
    }

    public record Derivation(
            Topic topic,
            String title,
            String targetSymbol,
            String finalResultFormatted,
            List<DerivationStep> steps) {
        public Derivation {
            steps = List.copyOf(steps);
        }
    }

    private GuidedDerivations() {}

    /**
     * Builds all six structured derivations for the given projectile model.
     */
    public static List<Derivation> buildAll(ProjectileMotion model) {
        return List.of(
                deriveVelocityComponents(model),
                deriveTimeToApex(model),
                deriveMaxHeight(model),
                deriveTimeOfFlight(model),
                deriveHorizontalRange(model),
                deriveLandingVelocity(model)
        );
    }

    public static Derivation deriveVelocityComponents(ProjectileMotion m) {
        double v0 = m.initialSpeed();
        double deg = m.launchAngleDegrees();
        double rad = Math.toRadians(deg);
        double vx = m.initialVx();
        double vy = m.initialVy();

        List<DerivationStep> steps = new ArrayList<>();

        // 1. Given
        steps.add(new DerivationStep(StepKind.GIVEN, "Launch Parameters",
                "The projectile is projected with magnitude v0 at an inclination θ to the horizontal axis.",
                List.of(
                        String.format(Locale.ROOT, "Launch speed (v0) = %.2f m/s", v0),
                        String.format(Locale.ROOT, "Launch angle (θ) = %.2f° (%.4f radians)", deg, rad)
                )));

        // 2. Principle
        steps.add(new DerivationStep(StepKind.PRINCIPLE, "Orthogonal Vector Decomposition",
                "In Cartesian coordinates, a 2D velocity vector can be resolved into independent perpendicular components along the horizontal x-axis and vertical y-axis using right-triangle trigonometry.",
                List.of(
                        "Horizontal velocity is adjacent to the angle θ: cos θ = vx0 / v0",
                        "Vertical velocity is opposite to the angle θ: sin θ = vy0 / v0",
                        "Because orthogonal directions are independent, horizontal motion has zero impact on vertical acceleration."
                )));

        // 3. Formula
        steps.add(new DerivationStep(StepKind.FORMULA, "Trigonometric Components",
                "vx0 = v0 · cos(θ)\nvy0 = v0 · sin(θ)",
                List.of(
                        "v0 = initial launch speed magnitude (m/s)",
                        "θ = launch angle above horizontal positive x-axis (degrees or radians)",
                        "vx0 = initial horizontal velocity component (m/s)",
                        "vy0 = initial vertical velocity component (m/s)"
                )));

        // 4. Substitution
        steps.add(new DerivationStep(StepKind.SUBSTITUTION, "Insert Active Values",
                String.format(Locale.ROOT, "vx0 = %.2f · cos(%.2f°)\nvy0 = %.2f · sin(%.2f°)", v0, deg, v0, deg),
                List.of(
                        String.format(Locale.ROOT, "cos(%.2f°) = %.5f", deg, Math.cos(rad)),
                        String.format(Locale.ROOT, "sin(%.2f°) = %.5f", deg, Math.sin(rad))
                )));

        // 5. Calculation
        steps.add(new DerivationStep(StepKind.CALCULATION, "Evaluate Products",
                String.format(Locale.ROOT, "vx0 = %.2f · %.5f = %.2f m/s\nvy0 = %.2f · %.5f = %.2f m/s",
                        v0, Math.cos(rad), vx, v0, Math.sin(rad), vy),
                List.of(
                        String.format(Locale.ROOT, "Speed check: √(vx0² + vy0²) = √(%.2f² + %.2f²) = %.2f m/s", vx, vy, Math.hypot(vx, vy))
                )));

        // 6. Final Answer
        String result = String.format(Locale.ROOT, "vx0 = %.2f m/s,  vy0 = %.2f m/s", vx, vy);
        steps.add(new DerivationStep(StepKind.FINAL_ANSWER, "Component Vectors",
                result,
                List.of(
                        "The projectile moves forward at a constant rate of " + String.format(Locale.ROOT, "%.2f m/s", vx) + " because ax = 0.",
                        "The vertical component of " + String.format(Locale.ROOT, "%.2f m/s", vy) + " will be uniformly reduced by gravity at " + String.format(Locale.ROOT, "%.2f m/s²", m.gravity()) + "."
                )));

        return new Derivation(Topic.VELOCITY_COMPONENTS, "Initial Velocity Components", "v₀x, v₀y", result, steps);
    }

    public static Derivation deriveTimeToApex(ProjectileMotion m) {
        double vy = m.initialVy();
        double g = m.gravity();
        OptionalDouble tApexOpt = m.timeToApex();

        List<DerivationStep> steps = new ArrayList<>();

        // 1. Given
        steps.add(new DerivationStep(StepKind.GIVEN, "Vertical Motion Parameters",
                "Upward motion is governed solely by the initial vertical velocity and downward gravitational acceleration.",
                List.of(
                        String.format(Locale.ROOT, "Initial vertical velocity (vy0) = %.2f m/s", vy),
                        String.format(Locale.ROOT, "Downward acceleration (g) = %.2f m/s²", g)
                )));

        // 2. Principle
        steps.add(new DerivationStep(StepKind.PRINCIPLE, "Apex Velocity Invariant",
                "At the peak (apex) of a projectile's flight, the particle instantaneously ceases to rise before it begins descending. Therefore, its vertical velocity is strictly zero: vy(t_apex) = 0.",
                List.of(
                        "Uniform acceleration equation: vy(t) = vy0 - g · t",
                        "Setting vy = 0 yields: 0 = vy0 - g · t_apex",
                        "Solving for time gives: t_apex = vy0 / g"
                )));

        // 3. Formula
        steps.add(new DerivationStep(StepKind.FORMULA, "Time to Peak Equation",
                "t_apex = vy0 / g = (v0 · sin θ) / g",
                List.of(
                        "t_apex = time taken from launch to reach maximum height (s)",
                        "vy0 = initial vertical velocity (m/s)",
                        "g = local acceleration due to gravity (m/s²)"
                )));

        if (g <= 0.0) {
            if (vy > 0.0) {
                steps.add(new DerivationStep(StepKind.SUBSTITUTION, "Zero Gravity & Positive Vertical Velocity",
                        String.format(Locale.ROOT, "g = 0.00 m/s², vy0 = %.2f m/s > 0", vy),
                        List.of("Division by zero: no gravitational deceleration exists to halt upward ascent.")));
                steps.add(new DerivationStep(StepKind.CALCULATION, "Mathematically Unbounded Ascent",
                        "t_apex → ∞",
                        List.of("The projectile continues upward indefinitely at constant vertical speed vy = vy0.",
                                "Because vy(t) remains strictly positive, no turning point exists.")));
                steps.add(new DerivationStep(StepKind.FINAL_ANSWER, "Apex Unbounded", "Unbounded (∞)",
                        List.of("Apex is mathematically unbounded under zero-gravity assumptions (g = 0, vy0 > 0).",
                                "Without downward gravity, the particle never turns around.")));
                return new Derivation(Topic.TIME_TO_APEX, "Time to Peak Altitude", "t_apex", "Unbounded (∞)", steps);
            } else if (vy == 0.0) {
                String ans = "0.00 s (Constant height at launch)";
                steps.add(new DerivationStep(StepKind.SUBSTITUTION, "Zero Gravity & Zero Vertical Velocity",
                        "g = 0.00 m/s², vy0 = 0.00 m/s",
                        List.of("Vertical velocity is identically zero for all time.")));
                steps.add(new DerivationStep(StepKind.CALCULATION, "Constant Elevation",
                        "t_apex = 0.00 s (Starts at peak)",
                        List.of("The trajectory remains at constant height y(t) = y0 indefinitely.",
                                "No future apex exists; projectile starts at its peak elevation.")));
                steps.add(new DerivationStep(StepKind.FINAL_ANSWER, "Peak at Launch", ans,
                        List.of("Projectile starts at its apex at t = 0; vertical height never changes.")));
                return new Derivation(Topic.TIME_TO_APEX, "Time to Peak Altitude", "t_apex", ans, steps);
            } else {
                String ans = "0.00 s (Downward motion; no future apex)";
                steps.add(new DerivationStep(StepKind.SUBSTITUTION, "Zero Gravity & Downward Launch",
                        String.format(Locale.ROOT, "g = 0.00 m/s², vy0 = %.2f m/s < 0", vy),
                        List.of("The particle is projected downward and maintains constant negative vertical speed.")));
                steps.add(new DerivationStep(StepKind.CALCULATION, "Strictly Decreasing Height",
                        "t_apex = 0.00 s (Peak at launch)",
                        List.of("Elevation strictly decreases: y(t) = y0 - |vy0|·t.",
                                "The highest point occurred at the initial launch instant t = 0. No future apex exists.")));
                steps.add(new DerivationStep(StepKind.FINAL_ANSWER, "No Future Apex", ans,
                        List.of("Maximum height was at launch (t = 0); no future apex exists.")));
                return new Derivation(Topic.TIME_TO_APEX, "Time to Peak Altitude", "t_apex", ans, steps);
            }
        }

        if (vy <= 0.0) {
            String ans = (vy == 0.0)
                    ? "0.00 s (Horizontal launch; peak at t=0)"
                    : "0.00 s (Downward launch; no future apex)";
            steps.add(new DerivationStep(StepKind.SUBSTITUTION, "Non-Ascending Launch Under Gravity",
                    String.format(Locale.ROOT, "vy0 = %.2f m/s <= 0, g = %.2f m/s²", vy, g),
                    List.of(vy == 0.0
                            ? "Launch is horizontal; downward gravity accelerates vertical speed downward immediately."
                            : "Launch is directed downward; gravity further accelerates descent.")));
            steps.add(new DerivationStep(StepKind.CALCULATION, "Immediate Apex",
                    "t_apex = 0.00 s",
                    List.of("The launch elevation y0 is the highest point on the trajectory.",
                            "Height strictly decreases for all t > 0.")));
            steps.add(new DerivationStep(StepKind.FINAL_ANSWER, "Peak at Launch", ans,
                    List.of("Maximum elevation occurs at t = 0; no future apex exists.")));
            return new Derivation(Topic.TIME_TO_APEX, "Time to Peak Altitude", "t_apex", ans, steps);
        }

        double tApex = tApexOpt.orElse(vy / g);

        // 4. Substitution
        steps.add(new DerivationStep(StepKind.SUBSTITUTION, "Insert Active Values",
                String.format(Locale.ROOT, "t_apex = %.2f / %.2f", vy, g),
                List.of(
                        String.format(Locale.ROOT, "vy0 = %.2f m/s", vy),
                        String.format(Locale.ROOT, "g = %.2f m/s²", g)
                )));

        // 5. Calculation
        steps.add(new DerivationStep(StepKind.CALCULATION, "Compute Quotient",
                String.format(Locale.ROOT, "t_apex = %.4f s ≈ %.2f s", tApex, tApex),
                List.of(
                        String.format(Locale.ROOT, "Every second, gravity removes %.2f m/s of vertical speed.", g),
                        String.format(Locale.ROOT, "To extinguish %.2f m/s requires exactly %.2f s.", vy, tApex)
                )));

        // 6. Final Answer
        String result = String.format(Locale.ROOT, "%.2f s", tApex);
        steps.add(new DerivationStep(StepKind.FINAL_ANSWER, "Future Apex Reached",
                result,
                List.of(
                        "A future apex exists: the projectile ascends for exactly " + result + " before reaching its maximum elevation.",
                        "For a level landing surface, the descent time equals the ascent time (T_flight = 2 · t_apex)."
                )));

        return new Derivation(Topic.TIME_TO_APEX, "Time to Peak Altitude", "t_apex", result, steps);
    }

    public static Derivation deriveMaxHeight(ProjectileMotion m) {
        double y0 = m.initialY();
        double vy = m.initialVy();
        double g = m.gravity();
        OptionalDouble hMaxOpt = m.maximumHeight();

        List<DerivationStep> steps = new ArrayList<>();

        // 1. Given
        steps.add(new DerivationStep(StepKind.GIVEN, "Vertical Boundary Conditions",
                "The launch platform and upward velocity determine the maximum height reached above the ground.",
                List.of(
                        String.format(Locale.ROOT, "Launch elevation (y0) = %.2f m", y0),
                        String.format(Locale.ROOT, "Vertical velocity (vy0) = %.2f m/s", vy),
                        String.format(Locale.ROOT, "Gravity (g) = %.2f m/s²", g)
                )));

        // 2. Principle
        steps.add(new DerivationStep(StepKind.PRINCIPLE, "Torricelli's Third Kinematic Equation",
                "Using the work-energy relation or third equation of uniform acceleration vy² = vy0² - 2g·(y - y0):\nAt the apex, vy = 0. Thus:\n0 = vy0² - 2g·Δy_apex ⟹ Δy_apex = vy0² / (2g).\nTotal maximum height: H_max = y0 + Δy_apex.",
                List.of(
                        "Δy_apex = maximum vertical rise ABOVE the launch point.",
                        "Total maximum height H_max = y0 + Δy_apex (ABOVE GROUND y = 0).",
                        "JEE Main & Advanced strictly distinguish height above platform (Δy) from coordinate height above ground (y0 + Δy)."
                )));

        // 3. Formula
        steps.add(new DerivationStep(StepKind.FORMULA, "Maximum Height Formulas",
                "Δy_apex = vy0² / (2g) = (v0² · sin²θ) / (2g)\nH_max = y0 + Δy_apex",
                List.of(
                        "y0 = initial platform elevation (m)",
                        "Δy_apex = vertical rise above launch elevation (m)",
                        "H_max = absolute maximum height above datum y = 0 (m)"
                )));

        if (g <= 0.0) {
            if (vy > 0.0) {
                steps.add(new DerivationStep(StepKind.SUBSTITUTION, "Zero Gravity & Positive Vertical Velocity",
                        String.format(Locale.ROOT, "g = 0.00 m/s², vy0 = %.2f m/s > 0, y0 = %.2f m", vy, y0),
                        List.of("Without downward gravity, upward motion continues without deceleration.")));
                steps.add(new DerivationStep(StepKind.CALCULATION, "Mathematically Unbounded Height",
                        "H_max → ∞",
                        List.of("Height grows linearly without bound: y(t) = y0 + vy0·t.",
                                "No finite maximum height exists under g = 0 with vy0 > 0.")));
                steps.add(new DerivationStep(StepKind.FINAL_ANSWER, "Unbounded Height", "Unbounded (∞)",
                        List.of("Apex height is mathematically unbounded under zero-gravity assumptions.",
                                "Vertical position increases indefinitely.")));
                return new Derivation(Topic.MAX_HEIGHT, "Maximum Height", "H_max", "Unbounded (∞)", steps);
            } else if (vy == 0.0) {
                String ans = String.format(Locale.ROOT, "%.2f m (Constant elevation)", y0);
                steps.add(new DerivationStep(StepKind.SUBSTITUTION, "Zero Gravity & Zero Vertical Velocity",
                        String.format(Locale.ROOT, "g = 0.00 m/s², vy0 = 0.00 m/s, y0 = %.2f m", y0),
                        List.of("Vertical motion is zero; position remains y(t) = y0.")));
                steps.add(new DerivationStep(StepKind.CALCULATION, "Constant Height",
                        String.format(Locale.ROOT, "H_max = y0 = %.2f m", y0),
                        List.of("The projectile stays at constant height y0 for all time.",
                                "Maximum height is simply the launch elevation.")));
                steps.add(new DerivationStep(StepKind.FINAL_ANSWER, "Constant Elevation", ans,
                        List.of(String.format(Locale.ROOT, "Height remains constant at %.2f m.", y0))));
                return new Derivation(Topic.MAX_HEIGHT, "Maximum Height", "H_max", ans, steps);
            } else {
                String ans = String.format(Locale.ROOT, "%.2f m (Peak at launch)", y0);
                steps.add(new DerivationStep(StepKind.SUBSTITUTION, "Zero Gravity & Downward Launch",
                        String.format(Locale.ROOT, "g = 0.00 m/s², vy0 = %.2f m/s < 0, y0 = %.2f m", vy, y0),
                        List.of("Projectile is launched downward in zero gravity; elevation strictly decreases.")));
                steps.add(new DerivationStep(StepKind.CALCULATION, "Launch is Highest Elevation",
                        String.format(Locale.ROOT, "H_max = y0 = %.2f m (No ascent occurs)", y0),
                        List.of("Height y(t) = y0 - |vy0|·t < y0 for all t > 0.",
                                "Maximum elevation was attained at the instant of launch.")));
                steps.add(new DerivationStep(StepKind.FINAL_ANSWER, "Peak at Launch", ans,
                        List.of(String.format(Locale.ROOT, "Maximum elevation was %.2f m at t = 0.", y0),
                                "No future apex exists; height decreases for all subsequent time.")));
                return new Derivation(Topic.MAX_HEIGHT, "Maximum Height", "H_max", ans, steps);
            }
        }

        if (vy <= 0.0) {
            String ans = String.format(Locale.ROOT, "%.2f m (Peak at launch)", y0);
            steps.add(new DerivationStep(StepKind.SUBSTITUTION, "Non-Ascending Launch Under Gravity",
                    String.format(Locale.ROOT, "vy0 = %.2f m/s <= 0, y0 = %.2f m, g = %.2f m/s²", vy, y0, g),
                    List.of(vy == 0.0
                            ? "Launch is horizontal; vertical rise Δy_apex = 0."
                            : "Launch is directed downward; trajectory immediately descends below y0.")));
            steps.add(new DerivationStep(StepKind.CALCULATION, "No Vertical Rise",
                    String.format(Locale.ROOT, "Δy_apex = 0.00 m ⟹ H_max = y0 = %.2f m", y0),
                    List.of("Trajectory never ascends above launch elevation.",
                            "Peak altitude occurred at the launch coordinate.")));
            steps.add(new DerivationStep(StepKind.FINAL_ANSWER, "Peak at Launch", ans,
                    List.of(String.format(Locale.ROOT, "Maximum height above ground is launch elevation %.2f m.", y0),
                            "Net rise above platform is 0.00 m.")));
            return new Derivation(Topic.MAX_HEIGHT, "Maximum Height", "H_max", ans, steps);
        }

        double deltaY = (vy > 0.0) ? (vy * vy) / (2.0 * g) : 0.0;
        double hMax = y0 + deltaY;

        // 4. Substitution
        steps.add(new DerivationStep(StepKind.SUBSTITUTION, "Insert Active Values",
                String.format(Locale.ROOT, "Δy_apex = (%.2f)² / (2 · %.2f)\nH_max = %.2f + Δy_apex", vy, g, y0),
                List.of(
                        String.format(Locale.ROOT, "vy0² = (%.2f)² = %.4f m²/s²", vy, vy * vy),
                        String.format(Locale.ROOT, "2g = 2 · %.2f = %.4f m/s²", g, 2.0 * g)
                )));

        // 5. Calculation
        steps.add(new DerivationStep(StepKind.CALCULATION, "Evaluate Terms",
                String.format(Locale.ROOT, "Δy_apex = %.4f / %.4f = %.2f m\nH_max = %.2f + %.2f = %.2f m",
                        vy * vy, 2.0 * g, deltaY, y0, deltaY, hMax),
                List.of(
                        String.format(Locale.ROOT, "Rise above launch: Δy_apex = %.2f m", deltaY),
                        String.format(Locale.ROOT, "Launch elevation: y0 = %.2f m", y0)
                )));

        // 6. Final Answer
        String result = String.format(Locale.ROOT, "%.2f m", hMax);
        steps.add(new DerivationStep(StepKind.FINAL_ANSWER, "Peak Altitude",
                result,
                List.of(
                        "Absolute maximum height above ground: " + result + ".",
                        "Net vertical rise above launch position: " + String.format(Locale.ROOT, "%.2f m", deltaY) + "."
                )));

        return new Derivation(Topic.MAX_HEIGHT, "Maximum Height", "H_max", result, steps);
    }

    public static Derivation deriveTimeOfFlight(ProjectileMotion m) {
        double y0 = m.initialY();
        double vy = m.initialVy();
        double g = m.gravity();
        double yLand = m.landingY();
        OptionalDouble tofOpt = m.timeOfFlight();

        List<DerivationStep> steps = new ArrayList<>();

        // 1. Given
        steps.add(new DerivationStep(StepKind.GIVEN, "Boundary Heights",
                "Flight time terminates when the projectile intersects the landing surface y = y_land.",
                List.of(
                        String.format(Locale.ROOT, "Launch elevation (y0) = %.2f m", y0),
                        String.format(Locale.ROOT, "Landing surface (y_land) = %.2f m", yLand),
                        String.format(Locale.ROOT, "Initial vertical velocity (vy0) = %.2f m/s", vy),
                        String.format(Locale.ROOT, "Downward acceleration (g) = %.2f m/s²", g)
                )));

        // 2. Principle
        steps.add(new DerivationStep(StepKind.PRINCIPLE, "Second Kinematic Equation at Landing",
                "The vertical position as a function of time is: y(t) = y0 + vy0·t - ½g·t².\nSetting y(T) = y_land yields a quadratic equation in flight time T:\n½g·T² - vy0·T + (y_land - y0) = 0.\nThe positive real quadratic root gives the physical landing time.",
                List.of(
                        "For a level launch and landing (y0 = y_land), the equation simplifies to T = 2·vy0 / g.",
                        "For elevated platforms (cliff launches), the positive real quadratic root gives the physical landing time."
                )));

        // 3. Formula
        steps.add(new DerivationStep(StepKind.FORMULA, "Quadratic Flight Time Equation",
                "½g·T² - vy0·T + (y_land - y0) = 0\nT = [vy0 + √(vy0² + 2g·(y0 - y_land))] / g",
                List.of(
                        "Discriminant D = vy0² + 2g(y0 - y_land)",
                        "If D < 0, the trajectory apex is below the elevated landing plane and no intersection occurs."
                )));

        if (g <= 0.0) {
            if (vy == 0.0) {
                if (Math.abs(y0 - yLand) < 1e-4) {
                    steps.add(new DerivationStep(StepKind.SUBSTITUTION, "Zero Gravity on Landing Surface",
                            String.format(Locale.ROOT, "g = 0.00 m/s², vy0 = 0.00 m/s, y0 = y_land = %.2f m", y0),
                            List.of("The projectile is launched directly on the landing plane and remains on it.")));
                    steps.add(new DerivationStep(StepKind.CALCULATION, "Immediate Surface Contact", "T = 0.00 s", List.of()));
                    steps.add(new DerivationStep(StepKind.FINAL_ANSWER, "On Surface", "0.00 s (On surface)",
                            List.of("Projectile starts and remains on the landing surface.")));
                    return new Derivation(Topic.TIME_OF_FLIGHT, "Total Flight Time", "T", "0.00 s", steps);
                } else {
                    steps.add(new DerivationStep(StepKind.SUBSTITUTION, "Zero Gravity Parallel Motion",
                            String.format(Locale.ROOT, "g = 0.00 m/s², vy0 = 0.00 m/s, y0 = %.2f m, y_land = %.2f m", y0, yLand),
                            List.of("Trajectory is horizontal and strictly parallel to landing plane with zero vertical speed.")));
                    steps.add(new DerivationStep(StepKind.CALCULATION, "Parallel Motion (No Crossing)", "T = No landing", List.of()));
                    steps.add(new DerivationStep(StepKind.FINAL_ANSWER, "No Landing", "No landing",
                            List.of("Parallel trajectory never intersects landing surface under zero gravity.")));
                    return new Derivation(Topic.TIME_OF_FLIGHT, "Total Flight Time", "T", "No landing", steps);
                }
            } else {
                double tLinear = (yLand - y0) / vy;
                if (tLinear > 0.0) {
                    String ans = String.format(Locale.ROOT, "%.2f s", tLinear);
                    steps.add(new DerivationStep(StepKind.SUBSTITUTION, "Linear Motion in Zero Gravity",
                            String.format(Locale.ROOT, "y(t) = %.2f + (%.2f)·t = %.2f", y0, vy, yLand),
                            List.of("Without gravity, vertical motion is uniform: T = (y_land - y0) / vy0.")));
                    steps.add(new DerivationStep(StepKind.CALCULATION, "Evaluate Linear Crossing",
                            String.format(Locale.ROOT, "T = (%.2f - %.2f) / %.2f = %.2f s", yLand, y0, vy, tLinear),
                            List.of("Forward linear intersection with the landing plane.")));
                    steps.add(new DerivationStep(StepKind.FINAL_ANSWER, "Linear Crossing", ans,
                            List.of("Particle reaches landing plane in " + ans + " in uniform motion.")));
                    return new Derivation(Topic.TIME_OF_FLIGHT, "Total Flight Time", "T", ans, steps);
                } else {
                    steps.add(new DerivationStep(StepKind.SUBSTITUTION, "Moving Away Under Zero Gravity",
                            String.format(Locale.ROOT, "y0 = %.2f m, y_land = %.2f m, vy0 = %.2f m/s", y0, yLand, vy),
                            List.of("Particle velocity points away from the landing plane.")));
                    steps.add(new DerivationStep(StepKind.CALCULATION, "No Forward Crossing", "T = No landing (t < 0)", List.of()));
                    steps.add(new DerivationStep(StepKind.FINAL_ANSWER, "No Landing", "No landing",
                            List.of("Particle moves away from landing plane; no future intersection exists.")));
                    return new Derivation(Topic.TIME_OF_FLIGHT, "Total Flight Time", "T", "No landing", steps);
                }
            }
        }

        double deltaH = y0 - yLand;
        double discriminant = vy * vy + 2.0 * g * deltaH;

        if (discriminant < 0.0 || !tofOpt.isPresent() || tofOpt.getAsDouble() <= 0.0) {
            String note = (discriminant < 0.0)
                    ? String.format(Locale.ROOT, "Discriminant D = %.4f < 0. Trajectory apex is below landing plane (y_land = %.2f m).", discriminant, yLand)
                    : "No landing occurs under current boundary conditions.";
            steps.add(new DerivationStep(StepKind.SUBSTITUTION, "Boundary Intersection Check",
                    String.format(Locale.ROOT, "D = (%.2f)² + 2(%.2f)(%.2f - %.2f) = %.4f", vy, g, y0, yLand, discriminant),
                    List.of(note)));
            steps.add(new DerivationStep(StepKind.CALCULATION, "No Real Solution", "T = No landing", List.of()));
            steps.add(new DerivationStep(StepKind.FINAL_ANSWER, "No Landing", "No landing", List.of(note)));
            return new Derivation(Topic.TIME_OF_FLIGHT, "Total Flight Time", "T", "No landing", steps);
        }

        double T = tofOpt.getAsDouble();
        double sqrtD = Math.sqrt(Math.max(0.0, discriminant));

        // 4. Substitution
        steps.add(new DerivationStep(StepKind.SUBSTITUTION, "Insert Active Values",
                String.format(Locale.ROOT, "T = [%.2f + √((%.2f)² + 2(%.2f)(%.2f))] / %.2f",
                        vy, vy, g, deltaH, g),
                List.of(
                        String.format(Locale.ROOT, "vy0² = %.4f", vy * vy),
                        String.format(Locale.ROOT, "2g(y0 - y_land) = 2 · %.2f · %.2f = %.4f", g, deltaH, 2.0 * g * deltaH),
                        String.format(Locale.ROOT, "Discriminant D = %.4f + %.4f = %.4f", vy * vy, 2.0 * g * deltaH, discriminant)
                )));

        // 5. Calculation
        steps.add(new DerivationStep(StepKind.CALCULATION, "Evaluate Quadratic Root",
                String.format(Locale.ROOT, "√D = √%.4f = %.4f\nT = (%.2f + %.4f) / %.2f = %.4f / %.2f = %.2f s",
                        discriminant, sqrtD, vy, sqrtD, g, vy + sqrtD, g, T),
                List.of(
                        "We select the positive root because time advances forward from t = 0.",
                        String.format(Locale.ROOT, "Verified time: T = %.2f s", T)
                )));

        // 6. Final Answer
        String result = String.format(Locale.ROOT, "%.2f s", T);
        steps.add(new DerivationStep(StepKind.FINAL_ANSWER, "Total Flight Duration",
                result,
                List.of(
                        "The particle remains airborne for exactly " + result + " before impacting the landing plane.",
                        Math.abs(deltaH) < 1e-4
                                ? "Because launch and landing are level, ascent time = descent time = " + String.format(Locale.ROOT, "%.2f s", T / 2.0) + "."
                                : String.format(Locale.ROOT, "Elevation difference of %.2f m modifies flight time relative to level flight.", deltaH)
                )));

        return new Derivation(Topic.TIME_OF_FLIGHT, "Total Flight Time", "T", result, steps);
    }

    public static Derivation deriveHorizontalRange(ProjectileMotion m) {
        double x0 = m.initialX();
        double vx = m.initialVx();
        OptionalDouble tofOpt = m.timeOfFlight();
        OptionalDouble rangeOpt = m.horizontalRange();

        List<DerivationStep> steps = new ArrayList<>();

        // 1. Given
        steps.add(new DerivationStep(StepKind.GIVEN, "Horizontal Motion Inputs",
                "Horizontal motion is uniform (constant velocity) because air resistance and wind are neglected.",
                List.of(
                        String.format(Locale.ROOT, "Initial coordinate (x0) = %.2f m", x0),
                        String.format(Locale.ROOT, "Constant horizontal velocity (vx0) = %.2f m/s", vx),
                        String.format(Locale.ROOT, "Flight time (T) = %s",
                                tofOpt.isPresent() ? String.format(Locale.ROOT, "%.2f s", tofOpt.getAsDouble()) : "No landing")
                )));

        // 2. Principle
        steps.add(new DerivationStep(StepKind.PRINCIPLE, "Zero Horizontal Acceleration",
                "According to Newton's First Law, in the absence of horizontal forces (ax = 0), horizontal velocity remains constant throughout the entire trajectory:\nvx(t) = vx0 = v0 · cos θ.\nThe horizontal range is therefore the linear distance traveled during total flight time T.",
                List.of(
                        "x(t) = x0 + vx0 · t",
                        "Net horizontal range R = x(T) - x0 = vx0 · T",
                        "Total coordinate at landing x_land = x0 + R"
                )));

        // 3. Formula
        steps.add(new DerivationStep(StepKind.FORMULA, "Range Equation",
                "R = vx0 · T = (v0 · cos θ) · T\nx_land = x0 + R",
                List.of(
                        "R = net horizontal displacement between launch and landing (m)",
                        "x_land = absolute x-coordinate where particle lands (m)",
                        "T = total flight duration (s)"
                )));

        if (!rangeOpt.isPresent() || !tofOpt.isPresent()) {
            steps.add(new DerivationStep(StepKind.SUBSTITUTION, "No Landing Event", "T is undefined or unbounded.", List.of()));
            steps.add(new DerivationStep(StepKind.CALCULATION, "No Solution", "R = No landing", List.of()));
            steps.add(new DerivationStep(StepKind.FINAL_ANSWER, "No Landing", "No landing", List.of("Range requires a landing surface intersection.")));
            return new Derivation(Topic.HORIZONTAL_RANGE, "Horizontal Range", "R", "No landing", steps);
        }

        double T = tofOpt.getAsDouble();
        double R = rangeOpt.getAsDouble();
        double xLand = x0 + R;

        // 4. Substitution
        steps.add(new DerivationStep(StepKind.SUBSTITUTION, "Insert Active Values",
                String.format(Locale.ROOT, "R = %.2f · %.2f\nx_land = %.2f + R", vx, T, x0),
                List.of(
                        String.format(Locale.ROOT, "vx0 = %.2f m/s", vx),
                        String.format(Locale.ROOT, "T = %.2f s", T)
                )));

        // 5. Calculation
        steps.add(new DerivationStep(StepKind.CALCULATION, "Evaluate Product",
                String.format(Locale.ROOT, "R = %.2f · %.2f = %.2f m\nx_land = %.2f + %.2f = %.2f m",
                        vx, T, R, x0, R, xLand),
                List.of(
                        String.format(Locale.ROOT, "Net horizontal displacement: %.2f m", R),
                        String.format(Locale.ROOT, "Landing x-coordinate: %.2f m", xLand)
                )));

        // 6. Final Answer
        String result = String.format(Locale.ROOT, "%.2f m", R);
        steps.add(new DerivationStep(StepKind.FINAL_ANSWER, "Horizontal Displacement",
                result,
                List.of(
                        "Net horizontal distance traveled: " + result + ".",
                        String.format(Locale.ROOT, "Impact point on surface: (%.2f, %.2f) m.", xLand, m.landingY())
                )));

        return new Derivation(Topic.HORIZONTAL_RANGE, "Horizontal Range", "R", result, steps);
    }

    public static Derivation deriveLandingVelocity(ProjectileMotion m) {
        double vx = m.initialVx();
        double vy = m.initialVy();
        double g = m.gravity();
        OptionalDouble tofOpt = m.timeOfFlight();

        List<DerivationStep> steps = new ArrayList<>();

        // 1. Given
        steps.add(new DerivationStep(StepKind.GIVEN, "Impact Conditions",
                "At the instant of contact with the landing surface, the particle possesses both invariant horizontal velocity and accelerated vertical velocity.",
                List.of(
                        String.format(Locale.ROOT, "Horizontal velocity (vx) = %.2f m/s (constant)", vx),
                        String.format(Locale.ROOT, "Initial vertical velocity (vy0) = %.2f m/s", vy),
                        String.format(Locale.ROOT, "Downward gravity (g) = %.2f m/s²", g),
                        String.format(Locale.ROOT, "Flight duration (T) = %s",
                                tofOpt.isPresent() ? String.format(Locale.ROOT, "%.2f s", tofOpt.getAsDouble()) : "No landing")
                )));

        // 2. Principle
        steps.add(new DerivationStep(StepKind.PRINCIPLE, "Superposition of Impact Components",
                "At landing time T:\n1. vx_land = vx0 (unaffected by gravity)\n2. vy_land = vy0 - g·T (uniformly decelerated/accelerated downward)\nTotal impact speed is the Euclidean vector magnitude v_land = √(vx_land² + vy_land²).\nAngle conventions: θ_vector = atan2(vy_land, vx_land) counterclockwise from +x; θ_impact = atan2(|vy_land|, |vx_land|) angle below horizontal.",
                List.of(
                        "Cartesian direction angle: θ_vector = atan2(vy_land, vx_land) in [-180°, 180°].",
                        "Impact pitch angle: θ_impact = atan2(|vy_land|, |vx_land|) in [0°, 90°] below horizontal.",
                        "For purely vertical descent (vx = 0), θ_vector is exactly -90.0° and θ_impact is 90.0°.",
                        "Conservation of energy: v_land = √(v0² + 2g(y0 - y_land)), independent of launch angle!"
                )));

        // 3. Formula
        steps.add(new DerivationStep(StepKind.FORMULA, "Impact Vector & Angle Equations",
                "vx_land = vx0\nvy_land = vy0 - g · T\nv_land = √(vx_land² + vy_land²)\nθ_vector = atan2(vy_land, vx_land)\nθ_impact = atan2(|vy_land|, |vx_land|)",
                List.of(
                        "vx_land = horizontal velocity component at impact (m/s)",
                        "vy_land = vertical velocity component at impact (m/s)",
                        "v_land = magnitude of landing velocity (m/s)",
                        "θ_vector = standard Cartesian direction angle (degrees)",
                        "θ_impact = surface incidence angle below horizontal (degrees)"
                )));

        if (!tofOpt.isPresent() || tofOpt.getAsDouble() <= 0.0) {
            steps.add(new DerivationStep(StepKind.SUBSTITUTION, "No Landing", "T is undefined or no landing occurs.", List.of()));
            steps.add(new DerivationStep(StepKind.CALCULATION, "No Solution", "v_land = No landing", List.of()));
            steps.add(new DerivationStep(StepKind.FINAL_ANSWER, "No Landing", "No landing", List.of("Impact velocity requires landing contact.")));
            return new Derivation(Topic.LANDING_VELOCITY, "Landing Velocity", "v_land", "No landing", steps);
        }

        double T = tofOpt.getAsDouble();
        double vyLand = vy - g * T;
        double speedLand = Math.hypot(vx, vyLand);
        double vectorAngle = Math.toDegrees(Math.atan2(vyLand, vx));
        double impactPitch = Math.toDegrees(Math.atan2(Math.abs(vyLand), Math.abs(vx)));

        // 4. Substitution
        steps.add(new DerivationStep(StepKind.SUBSTITUTION, "Insert Active Values",
                String.format(Locale.ROOT, "vx_land = %.2f m/s\nvy_land = %.2f - (%.2f · %.2f)\nv_land = √((%.2f)² + (vy_land)²)",
                        vx, vy, g, T, vx),
                List.of(
                        String.format(Locale.ROOT, "g · T = %.2f · %.2f = %.2f m/s", g, T, g * T),
                        String.format(Locale.ROOT, "Landing vertical component: vy_land = %.2f m/s", vyLand)
                )));

        // 5. Calculation
        steps.add(new DerivationStep(StepKind.CALCULATION, "Evaluate Velocity Vector & Direction",
                String.format(Locale.ROOT, "vx_land = %.2f m/s\nvy_land = %.2f m/s\nv_land = √(%.2f² + (%.2f)²) = %.2f m/s\nθ_vector = atan2(%.2f, %.2f) = %.1f°\nθ_impact = atan2(|%.2f|, |%.2f|) = %.1f°",
                        vx, vyLand, vx, vyLand, speedLand, vyLand, vx, vectorAngle, vyLand, vx, impactPitch),
                List.of(
                        vx == 0.0
                                ? "Vertical launch (vx = 0): particle strikes vertically downward (θ_vector = -90.0°)."
                                : String.format(Locale.ROOT, "Horizontal velocity vx remains invariant at %.2f m/s.", vx),
                        String.format(Locale.ROOT, "Energy check: √(%.2f² + 2·%.2f·%.2f) = %.2f m/s",
                                m.initialSpeed(), g, m.initialY() - m.landingY(),
                                Math.sqrt(Math.max(0.0, m.initialSpeed() * m.initialSpeed() + 2 * g * (m.initialY() - m.landingY()))))
                )));

        // 6. Final Answer
        String result = String.format(Locale.ROOT, "%.2f m/s (θ_vector = %.1f°, %.1f° below horizontal)", speedLand, vectorAngle, impactPitch);
        steps.add(new DerivationStep(StepKind.FINAL_ANSWER, "Impact Vector & Direction",
                result,
                List.of(
                        String.format(Locale.ROOT, "Landing velocity vector: (%.2f, %.2f) m/s.", vx, vyLand),
                        String.format(Locale.ROOT, "Impact speed: %.2f m/s.", speedLand),
                        String.format(Locale.ROOT, "Cartesian vector angle: %.1f° counterclockwise from +x.", vectorAngle),
                        String.format(Locale.ROOT, "Impact pitch angle: %.1f° below horizontal.", impactPitch)
                )));

        return new Derivation(Topic.LANDING_VELOCITY, "Landing Velocity", "v_land", result, steps);
    }
}
