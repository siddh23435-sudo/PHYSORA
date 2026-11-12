package edu.physics.ai;

import edu.physics.problem.ProjectileProblem;

import java.util.EnumSet;
import java.util.Set;

/** Provider-neutral classification metadata for the supported projectile-problem targets. */
public final class ProblemClassifier {
    private static final Set<ProjectileProblem.Target> SUPPORTED = EnumSet.allOf(ProjectileProblem.Target.class);

    public Classification classify(ProjectileProblem.Target target) {
        if (target == null || !SUPPORTED.contains(target)) {
            throw new IllegalArgumentException("A supported projectile target is required");
        }
        Category category = switch (target) {
            case HORIZONTAL_RANGE, MAXIMUM_HEIGHT, TIME_TO_APEX, FLIGHT_TIME, SPEED_AT_APEX -> Category.EVENT;
            case POSITION_AT_TIME, VELOCITY_AT_TIME, SPEED_AT_TIME, DISTANCE_TRAVELLED_AT_TIME -> Category.AT_TIME;
            case POSITION_AT_HORIZONTAL_DISPLACEMENT, VELOCITY_AT_HORIZONTAL_DISPLACEMENT,
                    SPEED_AT_HORIZONTAL_DISPLACEMENT, TIME_AT_HORIZONTAL_DISPLACEMENT -> Category.AT_HORIZONTAL_POSITION;
            case POSITION_AT_HEIGHT, VELOCITY_AT_HEIGHT, SPEED_AT_HEIGHT, TIMES_AT_HEIGHT -> Category.AT_HEIGHT;
            case TIMES_AT_POSITION -> Category.AT_POSITION;
            case FIND_INITIAL_SPEED -> Category.INVERSE_SPEED;
            case FIND_LAUNCH_ANGLE -> Category.INVERSE_ANGLE;
        };
        return new Classification(target, category);
    }

    public enum Category { EVENT, AT_TIME, AT_HORIZONTAL_POSITION, AT_HEIGHT, AT_POSITION, INVERSE_SPEED, INVERSE_ANGLE }

    public record Classification(ProjectileProblem.Target target, Category category) { }
}
