package edu.physics.problem;

import org.junit.jupiter.api.Test;

import java.math.RoundingMode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MathExpressionTest {
    @Test
    void expressionsRenderWithStableStructureAndEquationSymbols() {
        MathExpression.Symbol time = new MathExpression.Symbol("t", "elapsed time", PhysicalUnit.SECOND);
        MathExpression.Symbol initial = new MathExpression.Symbol("y0", "initial height", PhysicalUnit.METRE);
        MathExpression.Binary expression = new MathExpression.Binary(MathExpression.Operator.ADD,
                initial, new MathExpression.Binary(MathExpression.Operator.MULTIPLY,
                new MathExpression.Literal(2), time));
        assertEquals("(y0 + (2.0 * t))", expression.render());

        MathExpression.Function sine = new MathExpression.Function(MathExpression.FunctionName.SIN,
                List.of(new MathExpression.Symbol("theta", "angle in degrees", PhysicalUnit.DEGREE)));
        assertEquals("sin(theta)", sine.render());
        MathExpression.Integral integral = new MathExpression.Integral(sine, time,
                new MathExpression.Literal(0), time);
        assertEquals("integral[0.0, t] sin(theta) dt", integral.render());
        assertEquals("(y0, t)", new MathExpression.Tuple(List.of(initial, time)).render());

        MathExpression.Equation equation = new MathExpression.Equation("example", "kinematics",
                initial, expression, List.of(initial, time));
        assertEquals("y0 = (y0 + (2.0 * t))", equation.render());
        assertEquals(PhysicalUnit.SECOND, equation.symbolsByName().get("t").unit());
    }

    @Test
    void invalidExpressionNodesAndUnregisteredEquationsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MathExpression.Symbol(" ", "x", PhysicalUnit.METRE));
        assertThrows(IllegalArgumentException.class, () -> new MathExpression.Literal(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new MathExpression.Tuple(List.of(new MathExpression.Literal(1))));
        assertThrows(IllegalArgumentException.class, () -> new MathExpression.Function(MathExpression.FunctionName.MAX,
                List.of(new MathExpression.Literal(1))));
        assertThrows(IllegalArgumentException.class, () -> new MathExpression.Equation("id", "p",
                new MathExpression.Literal(0), new MathExpression.Literal(1), List.of(
                new MathExpression.Symbol("x", "one", PhysicalUnit.ONE),
                new MathExpression.Symbol("x", "duplicate", PhysicalUnit.ONE))));
        assertThrows(IllegalArgumentException.class, () -> ProjectileEquations.byId("unknown-equation"));
        assertThrows(IllegalArgumentException.class, () -> new ProjectileSolution.PrecisionPolicy(-1, RoundingMode.HALF_UP));
    }
}
