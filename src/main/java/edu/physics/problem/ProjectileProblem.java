package edu.physics.problem;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Presentation-independent definition of one projectile-motion question. */
public record ProjectileProblem(String statement, Target target, Map<Input, PhysicalQuantity> givens) {
    public enum Input {
        INITIAL_SPEED(PhysicalUnit.Dimension.SPEED),
        LAUNCH_ANGLE(PhysicalUnit.Dimension.ANGLE),
        GRAVITY(PhysicalUnit.Dimension.ACCELERATION),
        INITIAL_X(PhysicalUnit.Dimension.LENGTH),
        INITIAL_Y(PhysicalUnit.Dimension.LENGTH),
        LANDING_Y(PhysicalUnit.Dimension.LENGTH),
        TIME(PhysicalUnit.Dimension.TIME),
        HORIZONTAL_DISPLACEMENT(PhysicalUnit.Dimension.LENGTH),
        VERTICAL_DISPLACEMENT(PhysicalUnit.Dimension.LENGTH),
        TARGET_X(PhysicalUnit.Dimension.LENGTH),
        TARGET_Y(PhysicalUnit.Dimension.LENGTH),
        TARGET_SPEED(PhysicalUnit.Dimension.SPEED),
        TARGET_VX(PhysicalUnit.Dimension.SPEED),
        TARGET_VY(PhysicalUnit.Dimension.SPEED);

        private final PhysicalUnit.Dimension dimension;
        Input(PhysicalUnit.Dimension dimension) { this.dimension = dimension; }
        public PhysicalUnit.Dimension dimension() { return dimension; }
    }

    public enum Target {
        HORIZONTAL_RANGE,
        MAXIMUM_HEIGHT,
        TIME_TO_APEX,
        FLIGHT_TIME,
        SPEED_AT_APEX,
        VELOCITY_AT_TIME,
        SPEED_AT_TIME,
        POSITION_AT_HORIZONTAL_DISPLACEMENT,
        VELOCITY_AT_HORIZONTAL_DISPLACEMENT,
        SPEED_AT_HORIZONTAL_DISPLACEMENT,
        POSITION_AT_HEIGHT,
        VELOCITY_AT_HEIGHT,
        SPEED_AT_HEIGHT,
        POSITION_AT_TIME,
        TIMES_AT_HEIGHT,
        TIMES_AT_POSITION,
        TIME_AT_HORIZONTAL_DISPLACEMENT,
        DISTANCE_TRAVELLED_AT_TIME,
        FIND_INITIAL_SPEED,
        FIND_LAUNCH_ANGLE
    }

    public ProjectileProblem {
        if (statement == null) throw new IllegalArgumentException("Problem statement is required");
        if (target == null) throw new IllegalArgumentException("Problem target is required");
        if (givens == null) throw new IllegalArgumentException("Problem givens are required");
        EnumMap<Input, PhysicalQuantity> copy = new EnumMap<>(Input.class);
        copy.putAll(givens);
        if (copy.containsValue(null)) throw new IllegalArgumentException("Problem quantities cannot be null");
        givens = Collections.unmodifiableMap(copy);
    }
}
