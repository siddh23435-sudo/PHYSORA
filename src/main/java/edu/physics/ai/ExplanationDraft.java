package edu.physics.ai;

import java.util.List;

/** Structured explanatory prose; numerical claims must point to solver result keys. */
public record ExplanationDraft(List<Section> sections) {
    public ExplanationDraft {
        sections = List.copyOf(sections);
    }

    public record Section(String heading, List<Claim> claims) {
        public Section {
            if (heading == null || heading.isBlank()) throw new IllegalArgumentException("Section heading is required");
            claims = List.copyOf(claims);
        }
    }

    public record Claim(String text, String resultKey) {
        public Claim {
            if (text == null || text.isBlank()) throw new IllegalArgumentException("Claim text is required");
            if (resultKey != null && resultKey.isBlank()) throw new IllegalArgumentException("Result key cannot be blank");
        }
    }
}
