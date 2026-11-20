package edu.physics.ui;

import edu.physics.problem.MathExpression;
import edu.physics.problem.PhysicalUnit;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared mathematical typography utility for PHYSORA.
 *
 * <p>Transforms raw abstract syntax tree (AST) representations and programming-style
 * formulas (e.g. {@code ((x0 + (vx0 * t))...)}, {@code vx0}, {@code vy0}, {@code t ^ 2.0})
 * into standard, student-facing textbook typography featuring proper Unicode subscripts (x₀, y₀,
 * vₓ₀, vᵧ₀, vₓ, vᵧ, vᵣₓ, vᵣᵧ, vₘ), superscripts (t², sec⁴), Greek symbols (θ, φ, Δφ, κ, κ_down),
 * vector notation, fractions, and SI units (m/s²). Never outputs raw curly braces or LaTeX markup
 * into plain text labels.</p>
 */
public final class MathTypography {

    private static final Map<String, String> IDENTIFIER_MAP = Map.ofEntries(
            Map.entry("x0", "x₀"),
            Map.entry("y0", "y₀"),
            Map.entry("vx0", "vₓ₀"),
            Map.entry("vy0", "vᵧ₀"),
            Map.entry("vx", "vₓ"),
            Map.entry("vy", "vᵧ"),
            Map.entry("tApex", "t_apex"),
            Map.entry("timeToApex", "t_apex"),
            Map.entry("yMax", "y_max"),
            Map.entry("maximumHeight", "y_max"),
            Map.entry("tLanding", "t_land"),
            Map.entry("flightTime", "T (flight time)"),
            Map.entry("yLanding", "y_land"),
            Map.entry("speedApex", "v_apex"),
            Map.entry("speedAtApex", "v_apex"),
            Map.entry("vyApex", "vᵧ,apex"),
            Map.entry("v_rx", "vᵣₓ"),
            Map.entry("vrx", "vᵣₓ"),
            Map.entry("v_ry", "vᵣᵧ"),
            Map.entry("vry", "vᵣᵧ"),
            Map.entry("v_r", "vᵣ"),
            Map.entry("vr", "vᵣ"),
            Map.entry("v_m", "vₘ"),
            Map.entry("vm", "vₘ"),
            Map.entry("v_d", "v_d"),
            Map.entry("vd", "v_d"),
            Map.entry("v1", "v₁"),
            Map.entry("v2", "v₂"),
            Map.entry("theta", "θ"),
            Map.entry("phi", "φ"),
            Map.entry("phi_opt", "φ_opt"),
            Map.entry("phi_act", "φ_act"),
            Map.entry("phi1", "φ₁"),
            Map.entry("phi2", "φ₂"),
            Map.entry("delta_phi", "Δφ"),
            Map.entry("Delta_phi", "Δφ"),
            Map.entry("kappa", "κ"),
            Map.entry("kappa_down", "κ_down"),
            Map.entry("horizontalDisplacement", "vₓ₀t (horizontal run)"),
            Map.entry("verticalLinearChange", "vᵧ₀t (vertical rise)"),
            Map.entry("gravityDrop", "½gt² (gravity drop)"),
            Map.entry("gravityVelocityChange", "-gt (gravity speed drop)"),
            Map.entry("crossingTime1", "t₁"),
            Map.entry("crossingTime2", "t₂"),
            Map.entry("range", "R (horizontal range)"),
            Map.entry("initialSpeed", "v₀"),
            Map.entry("launchAngle", "θ"),
            Map.entry("gravity", "g"),
            Map.entry("initialX", "x₀"),
            Map.entry("initialY", "y₀"),
            Map.entry("landingY", "y_land"),
            Map.entry("time", "t")
    );

    private MathTypography() {
        // Utility class
    }

    /**
     * Formats an equation AST into polished textbook mathematical notation.
     * Special cases known textbook relationships to ensure canonical mathematical layouts.
     */
    public static String formatEquation(MathExpression.Equation eq) {
        if (eq == null) return "";

        String id = eq.id();
        if ("position-at-time".equals(id)) {
            return "(x, y) = (x₀ + vₓ₀t,  y₀ + vᵧ₀t - ½gt²)";
        } else if ("velocity-at-time".equals(id)) {
            return "(vₓ, vᵧ) = (vₓ₀,  vᵧ₀ - gt)";
        } else if ("landing-height-root".equals(id)) {
            return "y₀ + vᵧ₀t - ½gt² = y_land";
        } else if ("vertical-apex".equals(id) || "maximum-height".equals(id)) {
            return "y_max = y₀ + vᵧ₀² / (2g)";
        } else if ("time-to-apex".equals(id)) {
            return "t_apex = max(0, vᵧ₀ / g)";
        } else if ("range-from-landing-time".equals(id)) {
            return "range = vₓ₀ · t_land";
        } else if ("speed-at-apex".equals(id)) {
            return "v_apex = √(vₓ₀² + vᵧ,apex²)";
        }

        return formatExpression(eq.left()) + " = " + formatExpression(eq.right());
    }

    /**
     * Recursively formats an arbitrary {@link MathExpression} into clean mathematical typography.
     */
    public static String formatExpression(MathExpression expr) {
        if (expr == null) return "";

        if (expr instanceof MathExpression.Literal lit) {
            double val = lit.value();
            if (Math.abs(val - 0.5) < 1e-9) {
                return "½";
            }
            if (val == Math.floor(val) && !Double.isInfinite(val)) {
                return String.valueOf((long) val);
            }
            return String.valueOf(val);
        }

        if (expr instanceof MathExpression.Symbol sym) {
            return formatIdentifier(sym.name());
        }

        if (expr instanceof MathExpression.Tuple tuple) {
            return "(" + tuple.elements().stream()
                    .map(MathTypography::formatExpression)
                    .collect(Collectors.joining(", ")) + ")";
        }

        if (expr instanceof MathExpression.Function fn) {
            String name = fn.name().name();
            String args = fn.arguments().stream()
                    .map(MathTypography::formatExpression)
                    .collect(Collectors.joining(", "));
            if ("sqrt".equalsIgnoreCase(name)) {
                return "√(" + args + ")";
            }
            return name.toLowerCase(java.util.Locale.ROOT) + "(" + args + ")";
        }

        if (expr instanceof MathExpression.Binary bin) {
            MathExpression.Operator op = bin.operator();
            MathExpression left = bin.left();
            MathExpression right = bin.right();

            if (op == MathExpression.Operator.POWER) {
                String base = formatExpression(left);
                if (right instanceof MathExpression.Literal lit) {
                    if (Math.abs(lit.value() - 2.0) < 1e-9) return base + "²";
                    if (Math.abs(lit.value() - 3.0) < 1e-9) return base + "³";
                    if (Math.abs(lit.value() - 4.0) < 1e-9) return base + "⁴";
                }
                return base + "^" + formatExpression(right);
            }

            if (op == MathExpression.Operator.ADD) {
                return formatExpression(left) + " + " + formatExpression(right);
            }

            if (op == MathExpression.Operator.SUBTRACT) {
                return formatExpression(left) + " - " + formatExpression(right);
            }

            if (op == MathExpression.Operator.MULTIPLY) {
                if (isHalf(left)) {
                    return "½" + formatFactor(right);
                }
                if (isHalf(right)) {
                    return "½" + formatFactor(left);
                }
                return formatFactor(left) + " · " + formatFactor(right);
            }

            if (op == MathExpression.Operator.DIVIDE) {
                return formatFactor(left) + " / " + formatDenominator(right);
            }
        }

        if (expr instanceof MathExpression.Integral integral) {
            return "∫[" + formatExpression(integral.lower()) + " → " + formatExpression(integral.upper())
                    + "] " + formatExpression(integral.integrand()) + " d" + formatIdentifier(integral.variable().name());
        }

        return expr.render();
    }

    private static boolean isHalf(MathExpression expr) {
        return (expr instanceof MathExpression.Literal lit) && Math.abs(lit.value() - 0.5) < 1e-9;
    }

    private static String formatFactor(MathExpression expr) {
        if (expr instanceof MathExpression.Binary bin &&
                (bin.operator() == MathExpression.Operator.ADD || bin.operator() == MathExpression.Operator.SUBTRACT)) {
            return "(" + formatExpression(expr) + ")";
        }
        return formatExpression(expr);
    }

    private static String formatDenominator(MathExpression expr) {
        if (expr instanceof MathExpression.Binary bin && bin.operator() != MathExpression.Operator.POWER) {
            return "(" + formatExpression(expr) + ")";
        }
        return formatExpression(expr);
    }

    /**
     * Formats an identifier name to mathematical notation with clean Unicode subscripts or Greek symbols.
     * Never returns literal braces like {@code {x0}}.
     */
    public static String formatIdentifier(String raw) {
        if (raw == null || raw.isBlank()) return "";
        return IDENTIFIER_MAP.getOrDefault(raw, raw);
    }

    /**
     * Formats SI physical units cleanly, replacing caret notation with Unicode superscripts.
     */
    public static String formatUnit(PhysicalUnit unit) {
        if (unit == null) return "";
        return switch (unit) {
            case METRE_PER_SECOND_SQUARED -> "m/s²";
            case METRE_PER_SECOND -> "m/s";
            case KILOMETRE_PER_HOUR -> "km/h";
            case METRE -> "m";
            case KILOMETRE -> "km";
            case SECOND -> "s";
            case MILLISECOND -> "ms";
            case RADIAN -> "rad";
            case DEGREE -> "°";
            case ONE -> "";
            default -> unit.symbol();
        };
    }

    /**
     * Overhauls a textual description string containing programming notation into clean typography.
     * Eliminates literal braces, raw identifiers, carets, and pseudo-variable names.
     */
    public static String formatText(String text) {
        if (text == null || text.isBlank()) return "";

        String s = text;
        // Units
        s = s.replace("m/s^2", "m/s²").replace("m/s2", "m/s²");
        // Powers
        s = s.replace("t ^ 2.0", "t²").replace("t ^ 2", "t²").replace("t^2", "t²");
        s = s.replace("sec^4", "sec⁴");
        // Square root
        s = s.replace("sqrt(", "√(");

        // Vector arrow braces cleanup
        s = s.replace("v⃗_{B/A}", "v⃗_B/A").replace("v⃗_{b/g}", "v⃗_b/g")
             .replace("v⃗_{b/r}", "v⃗_b/r").replace("v⃗_{r/m}", "v⃗_r/m")
             .replace("v_{B/A}", "v_B/A").replace("a_{B/A}", "a_B/A")
             .replace("r_{B/A}", "r_B/A").replace("v_{A/B}", "v_A/B")
             .replace("a_{A/B}", "a_A/B").replace("r_{A/B}", "r_A/B");

        // Literal LaTeX curly brace cleaning
        s = s.replace("v_{x0}", "vₓ₀").replace("v_{y0}", "vᵧ₀")
             .replace("v_{rx}", "vᵣₓ").replace("v_{ry}", "vᵣᵧ")
             .replace("v_{r/m}", "v_r/m").replace("v_{apex}", "v_apex")
             .replace("y_{max}", "y_max").replace("t_{apex}", "t_apex")
             .replace("t_{land}", "t_land").replace("y_{land}", "y_land")
             .replace("φ_{opt}", "φ_opt").replace("φ_{act}", "φ_act")
             .replace("κ_{down}", "κ_down");

        // Raw tokens
        s = s.replaceAll("\\bvx0\\b", "vₓ₀");
        s = s.replaceAll("\\bvy0\\b", "vᵧ₀");
        s = s.replaceAll("\\bvx\\b", "vₓ");
        s = s.replaceAll("\\bvy\\b", "vᵧ");
        s = s.replaceAll("\\bx0\\b", "x₀");
        s = s.replaceAll("\\by0\\b", "y₀");
        s = s.replaceAll("\\bv_rx\\b", "vᵣₓ").replaceAll("\\bvrx\\b", "vᵣₓ");
        s = s.replaceAll("\\bv_ry\\b", "vᵣᵧ").replaceAll("\\bvry\\b", "vᵣᵧ");

        // Raw solver parameters
        s = s.replace("horizontalDisplacement", "vₓ₀t (horizontal run)");
        s = s.replace("verticalLinearChange", "vᵧ₀t (vertical rise)");
        s = s.replace("gravityDrop", "½gt² (gravity drop)");
        s = s.replace("gravityVelocityChange", "-gt (gravity speed drop)");

        // Greek letters and subscripts
        s = s.replaceAll("\\btheta\\b", "θ");
        s = s.replaceAll("\\bphi_opt\\b", "φ_opt");
        s = s.replaceAll("\\bphi_act\\b", "φ_act");
        s = s.replaceAll("\\bphi_1\\b", "φ₁").replaceAll("\\bphi1\\b", "φ₁");
        s = s.replaceAll("\\bphi_2\\b", "φ₂").replaceAll("\\bphi2\\b", "φ₂");
        s = s.replaceAll("\\bkappa_down\\b", "κ_down");
        s = s.replaceAll("\\bdelta_phi\\b", "Δφ").replaceAll("\\bDelta_phi\\b", "Δφ");

        return s;
    }
}
