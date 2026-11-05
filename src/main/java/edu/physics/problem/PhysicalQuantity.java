package edu.physics.problem;

/** A typed numeric input with a declared unit; finiteness is checked by the solver. */
public record PhysicalQuantity(double value, PhysicalUnit unit) {
    public PhysicalQuantity {
        if (unit == null) throw new IllegalArgumentException("A quantity unit is required");
    }

    public double valueInSi() { return unit.toSi(value); }
}
