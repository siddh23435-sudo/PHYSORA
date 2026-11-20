package edu.physics.ui;

import edu.physics.problem.MathExpression;
import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.PhysicalUnit;
import edu.physics.problem.ProjectileSolution;
import javafx.scene.Node;
import javafx.scene.text.TextFlow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MathTypography & MathView - Comprehensive Mathematical Presentation Tests")
class MathTypographyTest {

    @BeforeAll
    static void initFx() throws Exception {
        FxTestRuntime.ensureStarted();
    }

    @Test
    @DisplayName("formatIdentifier converts programming tokens to textbook mathematical symbols and clean Unicode subscripts without literal braces")
    void testFormatIdentifier() {
        assertEquals("x₀", MathTypography.formatIdentifier("x0"));
        assertEquals("y₀", MathTypography.formatIdentifier("y0"));
        assertEquals("vₓ₀", MathTypography.formatIdentifier("vx0"));
        assertEquals("vᵧ₀", MathTypography.formatIdentifier("vy0"));
        assertEquals("vₓ", MathTypography.formatIdentifier("vx"));
        assertEquals("vᵧ", MathTypography.formatIdentifier("vy"));
        assertEquals("t_apex", MathTypography.formatIdentifier("tApex"));
        assertEquals("y_max", MathTypography.formatIdentifier("yMax"));
        assertEquals("t_land", MathTypography.formatIdentifier("tLanding"));
        assertEquals("y_land", MathTypography.formatIdentifier("yLanding"));
        assertEquals("v_apex", MathTypography.formatIdentifier("speedApex"));
        assertEquals("vᵧ,apex", MathTypography.formatIdentifier("vyApex"));
        assertEquals("vᵣₓ", MathTypography.formatIdentifier("v_rx"));
        assertEquals("vᵣᵧ", MathTypography.formatIdentifier("v_ry"));
        assertEquals("vₘ", MathTypography.formatIdentifier("v_m"));
        assertEquals("v_d", MathTypography.formatIdentifier("v_d"));
        assertEquals("v₁", MathTypography.formatIdentifier("v1"));
        assertEquals("v₂", MathTypography.formatIdentifier("v2"));
        assertEquals("θ", MathTypography.formatIdentifier("theta"));
        assertEquals("φ", MathTypography.formatIdentifier("phi"));
        assertEquals("φ_opt", MathTypography.formatIdentifier("phi_opt"));
        assertEquals("φ_act", MathTypography.formatIdentifier("phi_act"));
        assertEquals("φ₁", MathTypography.formatIdentifier("phi1"));
        assertEquals("φ₂", MathTypography.formatIdentifier("phi2"));
        assertEquals("Δφ", MathTypography.formatIdentifier("delta_phi"));
        assertEquals("Δφ", MathTypography.formatIdentifier("Delta_phi"));
        assertEquals("κ", MathTypography.formatIdentifier("kappa"));
        assertEquals("κ_down", MathTypography.formatIdentifier("kappa_down"));

        // Solver intermediate mappings
        assertEquals("vₓ₀t (horizontal run)", MathTypography.formatIdentifier("horizontalDisplacement"));
        assertEquals("vᵧ₀t (vertical rise)", MathTypography.formatIdentifier("verticalLinearChange"));
        assertEquals("½gt² (gravity drop)", MathTypography.formatIdentifier("gravityDrop"));
        assertEquals("-gt (gravity speed drop)", MathTypography.formatIdentifier("gravityVelocityChange"));
        assertEquals("t₁", MathTypography.formatIdentifier("crossingTime1"));
        assertEquals("t₂", MathTypography.formatIdentifier("crossingTime2"));
    }

    @Test
    @DisplayName("formatUnit converts units to standard SI notation with superscripts")
    void testFormatUnit() {
        assertEquals("m/s²", MathTypography.formatUnit(PhysicalUnit.METRE_PER_SECOND_SQUARED));
        assertEquals("m/s", MathTypography.formatUnit(PhysicalUnit.METRE_PER_SECOND));
        assertEquals("km/h", MathTypography.formatUnit(PhysicalUnit.KILOMETRE_PER_HOUR));
        assertEquals("m", MathTypography.formatUnit(PhysicalUnit.METRE));
        assertEquals("km", MathTypography.formatUnit(PhysicalUnit.KILOMETRE));
        assertEquals("s", MathTypography.formatUnit(PhysicalUnit.SECOND));
        assertEquals("ms", MathTypography.formatUnit(PhysicalUnit.MILLISECOND));
        assertEquals("rad", MathTypography.formatUnit(PhysicalUnit.RADIAN));
        assertEquals("°", MathTypography.formatUnit(PhysicalUnit.DEGREE));
        assertEquals("", MathTypography.formatUnit(PhysicalUnit.ONE));
    }

    @Test
    @DisplayName("formatEquation converts standard equations to clean textbook representations without curly braces")
    void testFormatEquation() {
        var posEq = new MathExpression.Equation("position-at-time", "kinematics",
                new MathExpression.Symbol("r", "position", PhysicalUnit.METRE),
                new MathExpression.Symbol("r", "position", PhysicalUnit.METRE),
                List.of());
        assertEquals("(x, y) = (x₀ + vₓ₀t,  y₀ + vᵧ₀t - ½gt²)", MathTypography.formatEquation(posEq));

        var velEq = new MathExpression.Equation("velocity-at-time", "kinematics",
                new MathExpression.Symbol("v", "velocity", PhysicalUnit.METRE_PER_SECOND),
                new MathExpression.Symbol("v", "velocity", PhysicalUnit.METRE_PER_SECOND),
                List.of());
        assertEquals("(vₓ, vᵧ) = (vₓ₀,  vᵧ₀ - gt)", MathTypography.formatEquation(velEq));

        var landEq = new MathExpression.Equation("landing-height-root", "kinematics",
                new MathExpression.Symbol("y", "landing height", PhysicalUnit.METRE),
                new MathExpression.Symbol("y", "landing height", PhysicalUnit.METRE),
                List.of());
        assertEquals("y₀ + vᵧ₀t - ½gt² = y_land", MathTypography.formatEquation(landEq));

        var apexEq = new MathExpression.Equation("vertical-apex", "kinematics",
                new MathExpression.Symbol("y", "apex height", PhysicalUnit.METRE),
                new MathExpression.Symbol("y", "apex height", PhysicalUnit.METRE),
                List.of());
        assertEquals("y_max = y₀ + vᵧ₀² / (2g)", MathTypography.formatEquation(apexEq));
    }

    @Test
    @DisplayName("formatText cleans prose strings of programming notation and curly braces")
    void testFormatText() {
        String input = "v_rx - v_ry * tan(theta) with acceleration in m/s^2 and t ^ 2.0, sqrt(vx0^2 + vy0^2) for kappa_down and v_{x0}";
        String formatted = MathTypography.formatText(input);

        assertTrue(formatted.contains("m/s²"), "m/s^2 should be converted to m/s²");
        assertTrue(formatted.contains("t²"), "t ^ 2.0 should be converted to t²");
        assertTrue(formatted.contains("√("), "sqrt( should be converted to √(");
        assertTrue(formatted.contains("vₓ₀"), "vx0 should be converted to vₓ₀");
        assertTrue(formatted.contains("vᵧ₀"), "vy0 should be converted to vᵧ₀");
        assertTrue(formatted.contains("θ"), "theta should be converted to θ");
        assertTrue(formatted.contains("κ_down"), "kappa_down should be converted to κ_down");
        assertFalse(formatted.contains("{"), "Should not contain literal curly braces");
        assertFalse(formatted.contains("}"), "Should not contain literal curly braces");
    }

    @Test
    @DisplayName("MathView builds rich TextFlow nodes with visual baseline styling")
    void testMathViewRendering() throws Exception {
        FxTestRuntime.runOnFxThread(() -> {
            TextFlow flow = MathView.flow("x(t) = x₀ + vₓ₀t - ½gt²");
            assertNotNull(flow);
            assertFalse(flow.getChildren().isEmpty());

            var posEq = new MathExpression.Equation("position-at-time", "kinematics",
                    new MathExpression.Symbol("r", "position", PhysicalUnit.METRE),
                    new MathExpression.Symbol("r", "position", PhysicalUnit.METRE),
                    List.of());
            Node eqNode = MathView.renderEquation(posEq);
            assertNotNull(eqNode);

            Map<String, PhysicalQuantity> givens = Map.of(
                    "g", new PhysicalQuantity(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                    "vx0", new PhysicalQuantity(14.14, PhysicalUnit.METRE_PER_SECOND)
            );
            Map<String, PhysicalQuantity> inter = Map.of(
                    "horizontalDisplacement", new PhysicalQuantity(28.28, PhysicalUnit.METRE)
            );
            Node substNode = MathView.renderSubstitutionsAndIntermediates(
                    givens, inter, new ProjectileSolution.PrecisionPolicy(2, java.math.RoundingMode.HALF_UP));
            assertNotNull(substNode);
            return null;
        });
    }
}
