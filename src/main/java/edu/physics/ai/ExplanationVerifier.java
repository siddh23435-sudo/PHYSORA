package edu.physics.ai;

import edu.physics.problem.ProjectileSequenceSolution;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Rejects unsupported numerical prose and result references absent from the verified solution. */
public final class ExplanationVerifier {
    private static final Pattern NUMERIC_LITERAL = Pattern.compile("(?<![\\p{L}])[-+]?\\d+(?:[.,]\\d+)?(?:[eE][-+]?\\d+)?");

    public Verification verify(ExplanationDraft explanation, ProjectileSequenceSolution solution) {
        if (explanation == null || solution == null) return new Verification(false, List.of("explanation-and-verified-solution-required"));
        Set<String> resultKeys = new HashSet<>();
        solution.completedParts().forEach(part -> {
            part.solution().candidates().forEach(candidate -> resultKeys.addAll(candidate.keySet()));
            part.solution().steps().forEach(step -> {
                resultKeys.addAll(step.results().keySet());
                resultKeys.addAll(step.intermediateResults().keySet());
                resultKeys.addAll(step.substitutions().keySet());
            });
        });
        java.util.ArrayList<String> issues = new java.util.ArrayList<>();
        if (explanation.sections().isEmpty()) issues.add("explanation-has-no-sections");
        for (int sectionIndex = 0; sectionIndex < explanation.sections().size(); sectionIndex++) {
            ExplanationDraft.Section section = explanation.sections().get(sectionIndex);
            for (int claimIndex = 0; claimIndex < section.claims().size(); claimIndex++) {
                ExplanationDraft.Claim claim = section.claims().get(claimIndex);
                String location = sectionIndex + ":" + claimIndex;
                if (NUMERIC_LITERAL.matcher(claim.text()).find()) issues.add("numeric-literal-not-bound-to-result:" + location);
                if (claim.resultKey() != null && !resultKeys.contains(claim.resultKey())) {
                    issues.add("unknown-result-reference:" + location + ":" + claim.resultKey());
                }
            }
        }
        return new Verification(issues.isEmpty(), issues);
    }

    public record Verification(boolean verified, List<String> issues) {
        public Verification {
            issues = List.copyOf(issues);
            if (verified && !issues.isEmpty()) throw new IllegalArgumentException("Verified explanation cannot contain issues");
        }
    }
}
