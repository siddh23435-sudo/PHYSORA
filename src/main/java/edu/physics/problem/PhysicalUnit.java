package edu.physics.problem;

/** Supported input units. Values are converted to SI before solving. */
public enum PhysicalUnit {
    METRE("m", Dimension.LENGTH, 1.0),
    KILOMETRE("km", Dimension.LENGTH, 1_000.0),
    SECOND("s", Dimension.TIME, 1.0),
    MILLISECOND("ms", Dimension.TIME, 0.001),
    METRE_PER_SECOND("m/s", Dimension.SPEED, 1.0),
    KILOMETRE_PER_HOUR("km/h", Dimension.SPEED, 1.0 / 3.6),
    METRE_PER_SECOND_SQUARED("m/s^2", Dimension.ACCELERATION, 1.0),
    DEGREE("deg", Dimension.ANGLE, 1.0),
    RADIAN("rad", Dimension.ANGLE, 180.0 / Math.PI);

    public enum Dimension { LENGTH, TIME, SPEED, ACCELERATION, ANGLE }

    private final String symbol;
    private final Dimension dimension;
    private final double toSiFactor;

    PhysicalUnit(String symbol, Dimension dimension, double toSiFactor) {
        this.symbol = symbol;
        this.dimension = dimension;
        this.toSiFactor = toSiFactor;
    }

    public String symbol() { return symbol; }
    public Dimension dimension() { return dimension; }
    public double toSi(double value) { return value * toSiFactor; }
}
