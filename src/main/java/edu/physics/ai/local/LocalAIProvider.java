package edu.physics.ai.local;

import edu.physics.ai.AIProvider;
import edu.physics.ai.ExplanationDraft;
import edu.physics.ai.ExplanationRequest;
import edu.physics.ai.InterpretationDraft;
import edu.physics.ai.InterpretationRequest;
import edu.physics.ai.ProviderResult;
import edu.physics.problem.CanonicalSolution;
import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.PhysicalUnit;
import edu.physics.problem.ProjectileProblem;
import edu.physics.problem.ProjectileSequenceSolution;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic local physics interpretation and explanation provider.
 * Runs instantly offline with zero cloud API credits, zero rate limits, and zero external dependencies.
 */
public final class LocalAIProvider implements AIProvider {
    private static final Pattern SPEED_PATTERN = Pattern.compile(
            "(?i)(?:speed|velocity|projected at|launched at|thrown at)\\s*(?:of)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:m/s|mps|m\\s*s\\^?-1)?|(\\d+(?:\\.\\d+)?)\\s*(?:m/s|mps)\\b");
    private static final Pattern ANGLE_PATTERN = Pattern.compile(
            "(?i)(?:angle(?: of)?|at)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:degrees|deg|°)");
    private static final Pattern HORIZONTAL_PATTERN = Pattern.compile(
            "(?i)\\b(?:horizontally|horizontal projection)\\b");
    private static final Pattern GRAVITY_PATTERN = Pattern.compile(
            "(?i)(?:g\\s*=\\s*|gravity\\s*(?:of|=|is)?\\s*|take\\s+g\\s*=\\s*)(\\d+(?:\\.\\d+)?)\\s*(?:m/s\\^?2|mps2)?");
    private static final Pattern TOWER_HEIGHT_PATTERN = Pattern.compile(
            "(?i)(?:from\\s+(?:a\\s+)?(?:tower|cliff|platform|height)\\s+(?:of\\s+)?|initial\\s+height\\s*(?:of|=|is)?\\s*)(\\d+(?:\\.\\d+)?)\\s*(?:m|meters|metres)\\b");
    private static final Pattern TIME_PARAM_PATTERN = Pattern.compile(
            "(?i)(?:after|at\\s+t\\s*=|at\\s+time)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:s|sec|seconds)\\b");
    private static final Pattern HORIZ_DISP_PATTERN = Pattern.compile(
            "(?i)(?:horizontal\\s+displacement|distance)\\s*(?:of|=|is)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:m|meters|metres)\\b");

    @Override
    public CompletionStage<ProviderResult<InterpretationDraft>> interpret(InterpretationRequest request) {
        String text = request.question();
        String parsingText = text;
        int inquiryMarker = text.indexOf("[STUDENT INQUIRY]");
        if (inquiryMarker >= 0) {
            String inquiryPart = text.substring(inquiryMarker + "[STUDENT INQUIRY]".length()).trim();
            if (!inquiryPart.isEmpty()) {
                parsingText = inquiryPart;
            }
        }

        EnumMap<ProjectileProblem.Input, PhysicalQuantity> givens = new EnumMap<>(ProjectileProblem.Input.class);
        List<String> assumptions = new ArrayList<>();
        List<InterpretationDraft.Clarification> clarifications = new ArrayList<>();

        // 1. Initial speed
        Matcher speedMatcher = SPEED_PATTERN.matcher(parsingText);
        if (speedMatcher.find()) {
            String valStr = speedMatcher.group(1) != null ? speedMatcher.group(1) : speedMatcher.group(2);
            givens.put(ProjectileProblem.Input.INITIAL_SPEED,
                    new PhysicalQuantity(Double.parseDouble(valStr), PhysicalUnit.METRE_PER_SECOND));
        }

        // 2. Launch angle
        Matcher angleMatcher = ANGLE_PATTERN.matcher(parsingText);
        if (angleMatcher.find()) {
            givens.put(ProjectileProblem.Input.LAUNCH_ANGLE,
                    new PhysicalQuantity(Double.parseDouble(angleMatcher.group(1)), PhysicalUnit.DEGREE));
        } else if (HORIZONTAL_PATTERN.matcher(parsingText).find()) {
            givens.put(ProjectileProblem.Input.LAUNCH_ANGLE,
                    new PhysicalQuantity(0.0, PhysicalUnit.DEGREE));
            assumptions.add("Horizontal launch angle is 0 degrees.");
        }

        // 3. Gravity
        Matcher gravityMatcher = GRAVITY_PATTERN.matcher(parsingText);
        if (gravityMatcher.find()) {
            givens.put(ProjectileProblem.Input.GRAVITY,
                    new PhysicalQuantity(Double.parseDouble(gravityMatcher.group(1)), PhysicalUnit.METRE_PER_SECOND_SQUARED));
        }

        // 4. Initial height
        Matcher towerMatcher = TOWER_HEIGHT_PATTERN.matcher(parsingText);
        if (towerMatcher.find()) {
            double h = Double.parseDouble(towerMatcher.group(1));
            givens.put(ProjectileProblem.Input.INITIAL_Y, new PhysicalQuantity(h, PhysicalUnit.METRE));
            givens.put(ProjectileProblem.Input.LANDING_Y, new PhysicalQuantity(0.0, PhysicalUnit.METRE));
            assumptions.add("Launch elevation is " + h + " m and landing level is ground (y = 0 m).");
        } else {
            givens.put(ProjectileProblem.Input.INITIAL_Y, new PhysicalQuantity(0.0, PhysicalUnit.METRE));
            givens.put(ProjectileProblem.Input.LANDING_Y, new PhysicalQuantity(0.0, PhysicalUnit.METRE));
            assumptions.add("The launch height and landing height are the same (initial_y = landing_y = 0).");
        }

        // 5. Time parameter if present
        Matcher timeMatcher = TIME_PARAM_PATTERN.matcher(parsingText);
        if (timeMatcher.find()) {
            givens.put(ProjectileProblem.Input.TIME,
                    new PhysicalQuantity(Double.parseDouble(timeMatcher.group(1)), PhysicalUnit.SECOND));
        }

        // 6. Horizontal displacement parameter if present
        Matcher dispMatcher = HORIZ_DISP_PATTERN.matcher(parsingText);
        if (dispMatcher.find()) {
            givens.put(ProjectileProblem.Input.HORIZONTAL_DISPLACEMENT,
                    new PhysicalQuantity(Double.parseDouble(dispMatcher.group(1)), PhysicalUnit.METRE));
        }

        // 7. Check clarification history from previous turn
        for (InterpretationRequest.ClarificationTurn turn : request.clarifications()) {
            String answer = turn.answer().trim();
            Matcher numMatcher = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(answer);
            if (turn.question().toLowerCase(Locale.ROOT).contains("gravit") && numMatcher.find()) {
                double g = Double.parseDouble(numMatcher.group(1));
                givens.put(ProjectileProblem.Input.GRAVITY,
                        new PhysicalQuantity(g, PhysicalUnit.METRE_PER_SECOND_SQUARED));
                assumptions.add("Acceleration due to gravity set to " + g + " m/s^2 based on student clarification.");
            } else if (turn.question().toLowerCase(Locale.ROOT).contains("height") && numMatcher.find()) {
                double h = Double.parseDouble(numMatcher.group(1));
                givens.put(ProjectileProblem.Input.INITIAL_Y, new PhysicalQuantity(h, PhysicalUnit.METRE));
                givens.put(ProjectileProblem.Input.LANDING_Y, new PhysicalQuantity(0.0, PhysicalUnit.METRE));
            }
        }

        // 8. If gravity is still missing, trigger clarification
        if (!givens.containsKey(ProjectileProblem.Input.GRAVITY)) {
            clarifications.add(new InterpretationDraft.Clarification(
                    "What value of gravitational acceleration (g) should be used?",
                    "Gravity was not specified in the problem statement.",
                    List.of("9.8 m/s^2", "9.81 m/s^2", "10 m/s^2")));
        }

        // 9. Identify targets requested
        List<InterpretationDraft.ProblemPart> parts = new ArrayList<>();
        String lower = parsingText.toLowerCase(Locale.ROOT);

        if (lower.contains("max") && (lower.contains("height") || lower.contains("peak"))) {
            parts.add(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.MAXIMUM_HEIGHT, givens, Map.of()));
        }
        if (lower.contains("range") || lower.contains("horizontal distance")) {
            parts.add(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, givens, Map.of()));
        }
        if (lower.contains("flight time") || lower.contains("time of flight") || lower.contains("total time")) {
            parts.add(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.FLIGHT_TIME, givens, Map.of()));
        }
        if (lower.contains("time to apex") || lower.contains("time to reach highest") || lower.contains("time to maximum height")) {
            parts.add(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.TIME_TO_APEX, givens, Map.of()));
        }
        if (lower.contains("speed at apex") || lower.contains("speed at highest") || lower.contains("velocity at apex")) {
            parts.add(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.SPEED_AT_APEX, givens, Map.of()));
        }
        if (lower.contains("speed after") || (lower.contains("speed") && givens.containsKey(ProjectileProblem.Input.TIME))) {
            parts.add(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.SPEED_AT_TIME, givens, Map.of()));
        }
        if (lower.contains("velocity after") || (lower.contains("velocity") && givens.containsKey(ProjectileProblem.Input.TIME))) {
            parts.add(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.VELOCITY_AT_TIME, givens, Map.of()));
        }
        if (lower.contains("speed at horizontal") || (lower.contains("speed") && givens.containsKey(ProjectileProblem.Input.HORIZONTAL_DISPLACEMENT))) {
            parts.add(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.SPEED_AT_HORIZONTAL_DISPLACEMENT, givens, Map.of()));
        }

        // Default to range if no specific target identified
        if (parts.isEmpty()) {
            parts.add(new InterpretationDraft.ProblemPart(ProjectileProblem.Target.HORIZONTAL_RANGE, givens, Map.of()));
        }

        assumptions.add("Motion treated as 2D point particle under uniform downward gravity.");
        assumptions.add("Air resistance is neglected.");
        assumptions.add("no-air-resistance");

        InterpretationDraft draft = new InterpretationDraft(text, parts, assumptions, clarifications, 0.98);
        ProviderResult<InterpretationDraft> result = new ProviderResult<>(
                draft, UUID.randomUUID().toString(), "local-physics-engine",
                new ProviderResult.Usage(0, 0));
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletionStage<ProviderResult<ExplanationDraft>> explain(ExplanationRequest request) {
        List<ExplanationDraft.Section> sections = new ArrayList<>();

        // Section 1: Physical Principles
        sections.add(new ExplanationDraft.Section("Physical Principles and Coordinate Setup", List.of(
                new ExplanationDraft.Claim(
                        "The trajectory follows standard two-dimensional kinematics under uniform gravitational acceleration.", null),
                new ExplanationDraft.Claim(
                        "Air resistance is neglected and coordinates are oriented with positive y vertical and positive x horizontal.", null)
        )));

        // Section 2: Step-by-Step Analytical Derivation
        List<ExplanationDraft.Claim> derivationClaims = new ArrayList<>();
        if (request.canonicalSolution() != null) {
            CanonicalSolution solution = request.canonicalSolution();
            for (CanonicalSolution.PartResult part : solution.partResults()) {
                for (var step : part.steps()) {
                    if (step.intermediateResults().containsKey("vx0")) {
                        derivationClaims.add(new ExplanationDraft.Claim(
                                "The constant horizontal velocity component is calculated as initial speed times cosine of the launch angle:", "vx0"));
                    }
                    if (step.intermediateResults().containsKey("vy0")) {
                        derivationClaims.add(new ExplanationDraft.Claim(
                                "The initial vertical velocity component is determined by initial speed times sine of the launch angle:", "vy0"));
                    }
                    if (step.intermediateResults().containsKey("tLanding")) {
                        derivationClaims.add(new ExplanationDraft.Claim(
                                "The total duration until the projectile returns to the landing surface is:", "tLanding"));
                    }
                    if (step.intermediateResults().containsKey("tApex")) {
                        derivationClaims.add(new ExplanationDraft.Claim(
                                "The elapsed time to reach the trajectory apex where vertical velocity vanishes is:", "tApex"));
                    }
                }

                for (Map<String, PhysicalQuantity> candidate : part.candidates()) {
                    for (String key : candidate.keySet()) {
                        derivationClaims.add(new ExplanationDraft.Claim(
                                "The analytical calculation yields the verified result for this projectile target:", key));
                    }
                }
            }
        } else if (request.verifiedSolution() != null) {
            ProjectileSequenceSolution solution = request.verifiedSolution();
            for (ProjectileSequenceSolution.PartResult part : solution.completedParts()) {
                for (var step : part.solution().steps()) {
                    if (step.intermediateResults().containsKey("vx0")) {
                        derivationClaims.add(new ExplanationDraft.Claim(
                                "The constant horizontal velocity component is calculated as initial speed times cosine of the launch angle:", "vx0"));
                    }
                    if (step.intermediateResults().containsKey("vy0")) {
                        derivationClaims.add(new ExplanationDraft.Claim(
                                "The initial vertical velocity component is determined by initial speed times sine of the launch angle:", "vy0"));
                    }
                    if (step.intermediateResults().containsKey("tLanding")) {
                        derivationClaims.add(new ExplanationDraft.Claim(
                                "The total duration until the projectile returns to the landing surface is:", "tLanding"));
                    }
                    if (step.intermediateResults().containsKey("tApex")) {
                        derivationClaims.add(new ExplanationDraft.Claim(
                                "The elapsed time to reach the trajectory apex where vertical velocity vanishes is:", "tApex"));
                    }
                }

                for (Map<String, PhysicalQuantity> candidate : part.solution().candidates()) {
                    for (String key : candidate.keySet()) {
                        derivationClaims.add(new ExplanationDraft.Claim(
                                "The analytical calculation yields the verified result for this projectile target:", key));
                    }
                }
            }
        }

        sections.add(new ExplanationDraft.Section("Analytical Kinematic Derivation", derivationClaims));

        ExplanationDraft draft = new ExplanationDraft(sections);
        ProviderResult<ExplanationDraft> result = new ProviderResult<>(
                draft, UUID.randomUUID().toString(), "local-physics-engine",
                new ProviderResult.Usage(0, 0));
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public void close() {
        // No resources to close for local provider
    }
}

