package edu.physics.ui;

import edu.physics.problem.MathExpression;
import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.ProjectileSolution;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Reusable mathematical presentation component for PHYSORA.
 *
 * <p>Builds rich JavaFX {@link TextFlow} and layout nodes that typeset mathematical equations,
 * symbols, subscripts, superscripts, vectors, and SI units with textbook quality.
 * Eliminates raw programming syntax, curly braces, and ad-hoc string formatting.</p>
 */
public final class MathView {

    private static final String FONT_MATH = "Georgia";
    private static final String FONT_MONO = "Consolas";

    private MathView() {
        // Utility / component class
    }

    /**
     * Creates a {@link TextFlow} parsing the given mathematical expression string.
     * Applies italic serif styling to mathematical variables, baseline translation to subscripts
     * and superscripts, and standard upright styling to numbers, units, and operators.
     */
    public static TextFlow flow(String mathText) {
        return flow(mathText, 13.5, Color.web("#142e4b"));
    }

    /**
     * Creates a {@link TextFlow} with custom font size and base text color.
     */
    public static TextFlow flow(String mathText, double fontSize, Color color) {
        TextFlow textFlow = new TextFlow();
        if (mathText == null || mathText.isBlank()) {
            return textFlow;
        }

        double subSupSize = Math.max(9.0, fontSize * 0.78);
        Font mathFont = Font.font(FONT_MATH, FontPosture.ITALIC, fontSize);
        Font normalFont = Font.font(FONT_MATH, FontPosture.REGULAR, fontSize);
        Font subFont = Font.font(FONT_MATH, FontPosture.REGULAR, subSupSize);
        Font superFont = Font.font(FONT_MATH, FontPosture.REGULAR, subSupSize);
        Font monoFont = Font.font(FONT_MONO, FontWeight.BOLD, fontSize);

        int len = mathText.length();
        int i = 0;
        while (i < len) {
            char c = mathText.charAt(i);

            // 1. Unicode subscripts: ₀ ₁ ₂ ₃ ₄ ₅ ₆ ₇ ₈ ₉ ₓ ᵧ ᵣ ₘ ₐ ₑ ₒ ₚ ₜ ₛ
            if (isUnicodeSubscript(c)) {
                Text t = new Text(String.valueOf(c));
                t.setFont(subFont);
                t.setFill(color);
                t.setTranslateY(fontSize * 0.22);
                textFlow.getChildren().add(t);
                i++;
                continue;
            }

            // 2. Unicode superscripts: ² ³ ⁴ ⁵
            if (isUnicodeSuperscript(c)) {
                Text t = new Text(String.valueOf(c));
                t.setFont(superFont);
                t.setFill(color);
                t.setTranslateY(-fontSize * 0.32);
                textFlow.getChildren().add(t);
                i++;
                continue;
            }

            // 3. LaTeX-style caret superscript: e.g. ^2 or ^{apex}
            if (c == '^') {
                i++;
                if (i < len && mathText.charAt(i) == '{') {
                    int close = mathText.indexOf('}', i);
                    if (close > i) {
                        String sup = mathText.substring(i + 1, close);
                        Text t = new Text(sup);
                        t.setFont(superFont);
                        t.setFill(color);
                        t.setTranslateY(-fontSize * 0.32);
                        textFlow.getChildren().add(t);
                        i = close + 1;
                        continue;
                    }
                } else if (i < len) {
                    Text t = new Text(String.valueOf(mathText.charAt(i)));
                    t.setFont(superFont);
                    t.setFill(color);
                    t.setTranslateY(-fontSize * 0.32);
                    textFlow.getChildren().add(t);
                    i++;
                    continue;
                }
            }

            // 4. LaTeX-style underscore subscript: e.g. _0 or _{max}
            if (c == '_') {
                i++;
                if (i < len && mathText.charAt(i) == '{') {
                    int close = mathText.indexOf('}', i);
                    if (close > i) {
                        String sub = mathText.substring(i + 1, close);
                        Text t = new Text(sub);
                        t.setFont(subFont);
                        t.setFill(color);
                        t.setTranslateY(fontSize * 0.22);
                        textFlow.getChildren().add(t);
                        i = close + 1;
                        continue;
                    }
                } else if (i < len) {
                    Text t = new Text(String.valueOf(mathText.charAt(i)));
                    t.setFont(subFont);
                    t.setFill(color);
                    t.setTranslateY(fontSize * 0.22);
                    textFlow.getChildren().add(t);
                    i++;
                    continue;
                }
            }

            // 5. Greek characters and special math symbols
            if (isGreekOrMathSymbol(c)) {
                Text t = new Text(String.valueOf(c));
                t.setFont(normalFont);
                t.setFill(color);
                textFlow.getChildren().add(t);
                i++;
                continue;
            }

            // 6. Vector symbols with combining arrows (e.g. v⃗ or u⃗)
            if (i + 1 < len && mathText.charAt(i + 1) == '\u20D7') {
                Text t = new Text(mathText.substring(i, i + 2));
                t.setFont(Font.font(FONT_MATH, FontWeight.BOLD, FontPosture.ITALIC, fontSize));
                t.setFill(color);
                textFlow.getChildren().add(t);
                i += 2;
                continue;
            }

            // 7. Mathematical variables (single Latin letters followed by non-word or punctuation)
            if (Character.isLetter(c)) {
                int start = i;
                while (i < len && Character.isLetter(mathText.charAt(i))) {
                    i++;
                }
                String word = mathText.substring(start, i);
                Text t = new Text(word);
                if (word.length() == 1 && !"d".equals(word)) {
                    t.setFont(mathFont);
                } else if ("max".equals(word) || "apex".equals(word) || "land".equals(word)
                        || "sin".equals(word) || "cos".equals(word) || "tan".equals(word)
                        || "sqrt".equals(word) || "atan2".equals(word)) {
                    t.setFont(normalFont);
                } else {
                    t.setFont(normalFont);
                }
                t.setFill(color);
                textFlow.getChildren().add(t);
                continue;
            }

            // 8. Numbers, operators, fractions, spaces, and punctuation
            int start = i;
            while (i < len && !Character.isLetter(mathText.charAt(i))
                    && !isUnicodeSubscript(mathText.charAt(i))
                    && !isUnicodeSuperscript(mathText.charAt(i))
                    && !isGreekOrMathSymbol(mathText.charAt(i))
                    && mathText.charAt(i) != '^'
                    && mathText.charAt(i) != '_'
                    && (i + 1 >= len || mathText.charAt(i + 1) != '\u20D7')) {
                i++;
            }
            Text t = new Text(mathText.substring(start, i));
            t.setFont(normalFont);
            t.setFill(color);
            textFlow.getChildren().add(t);
        }

        return textFlow;
    }

    private static boolean isUnicodeSubscript(char c) {
        return (c >= '\u2080' && c <= '\u2089') // ₀ - ₉
                || c == '\u1D68' // ₓ
                || c == '\u1D67' // ᵧ
                || c == '\u1D63' // ᵣ
                || c == '\u2098' // ₘ
                || c == '\u2090' // ₐ
                || c == '\u2091' // ₑ
                || c == '\u2092' // ₒ
                || c == '\u2099' // ₙ
                || c == '\u209A' // ₚ
                || c == '\u209B' // ₛ
                || c == '\u209C'; // ₜ
    }

    private static boolean isUnicodeSuperscript(char c) {
        return c == '²' || c == '³' || c == '⁴' || c == '⁵' || c == '¹' || c == '⁰';
    }

    private static boolean isGreekOrMathSymbol(char c) {
        return c == 'θ' || c == 'φ' || c == 'Δ' || c == 'κ' || c == '√' || c == '½' || c == '·' || c == '∫' || c == '±';
    }

    /**
     * Renders an equation as a polished mathematical card.
     * Special-cases known vector / kinematic systems to display authentic stacked two-line systems.
     */
    public static Node renderEquation(MathExpression.Equation eq) {
        if (eq == null) {
            return new Label();
        }

        String id = eq.id();
        VBox card = new VBox(5);
        card.setStyle("-fx-background-color: #f0f4fe; -fx-padding: 8 14 8 14; " +
                "-fx-border-color: #c7d7fc; -fx-border-radius: 4px; -fx-background-radius: 4px;");

        if ("position-at-time".equals(id)) {
            TextFlow row1 = flow("x(t) = x₀ + vₓ₀t", 13.5, Color.web("#142e4b"));
            TextFlow row2 = flow("y(t) = y₀ + vᵧ₀t - ½gt²", 13.5, Color.web("#142e4b"));
            card.getChildren().addAll(row1, row2);
            return card;
        }

        if ("velocity-at-time".equals(id)) {
            TextFlow row1 = flow("vₓ(t) = vₓ₀", 13.5, Color.web("#142e4b"));
            TextFlow row2 = flow("vᵧ(t) = vᵧ₀ - gt", 13.5, Color.web("#142e4b"));
            card.getChildren().addAll(row1, row2);
            return card;
        }

        TextFlow flow = flow(MathTypography.formatEquation(eq), 13.5, Color.web("#142e4b"));
        card.getChildren().add(flow);
        return card;
    }

    /**
     * Renders substitutions and intermediate results in a clean, two-column aligned mathematical format.
     */
    public static Node renderSubstitutionsAndIntermediates(
            Map<String, PhysicalQuantity> substitutions,
            Map<String, PhysicalQuantity> intermediates,
            ProjectileSolution.PrecisionPolicy precision) {

        VBox container = new VBox(8);
        container.setStyle("-fx-background-color: #faf8f3; -fx-padding: 10 14 10 14; " +
                "-fx-border-color: #e2ded4; -fx-border-radius: 4px; -fx-background-radius: 4px;");

        // Section 1: Substitutions (Givens)
        if (substitutions != null && !substitutions.isEmpty()) {
            Label header = new Label("SUBSTITUTIONS (ACTIVE EXPERIMENT GIVENS)");
            header.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.0));
            header.setStyle("-fx-text-fill: #2453d4;");

            GridPane grid = new GridPane();
            grid.setHgap(20);
            grid.setVgap(4);

            List<Map.Entry<String, PhysicalQuantity>> sorted = substitutions.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .toList();

            for (int idx = 0; idx < sorted.size(); idx++) {
                Map.Entry<String, PhysicalQuantity> entry = sorted.get(idx);
                String formattedKey = MathTypography.formatIdentifier(entry.getKey());
                String formattedVal = formatQuantity(entry.getValue(), precision);

                TextFlow item = flow(formattedKey + " = " + formattedVal, 11.5, Color.web("#1e293b"));
                int col = idx % 2;
                int row = idx / 2;
                grid.add(item, col, row);
            }

            container.getChildren().addAll(header, grid);
        }

        // Section 2: Intermediate Results
        if (intermediates != null && !intermediates.isEmpty()) {
            if (!container.getChildren().isEmpty()) {
                Region div = new Region();
                div.setPrefHeight(1);
                div.setStyle("-fx-background-color: #e2ded4;");
                container.getChildren().add(div);
            }

            Label header = new Label("INTERMEDIATE CALCULATIONS");
            header.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.0));
            header.setStyle("-fx-text-fill: #059669;");

            GridPane grid = new GridPane();
            grid.setHgap(20);
            grid.setVgap(4);

            List<Map.Entry<String, PhysicalQuantity>> sorted = intermediates.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .toList();

            for (int idx = 0; idx < sorted.size(); idx++) {
                Map.Entry<String, PhysicalQuantity> entry = sorted.get(idx);
                String formattedKey = MathTypography.formatIdentifier(entry.getKey());
                String formattedVal = formatQuantity(entry.getValue(), precision);

                TextFlow item = flow(formattedKey + " = " + formattedVal, 11.5, Color.web("#1e293b"));
                grid.add(item, 0, idx);
            }

            container.getChildren().addAll(header, grid);
        }

        return container;
    }

    private static String formatQuantity(PhysicalQuantity quantity, ProjectileSolution.PrecisionPolicy precision) {
        if (quantity == null) return "";
        String value = BigDecimal.valueOf(quantity.value())
                .setScale(precision.displayedDecimalPlaces(), precision.roundingMode()).toPlainString();
        String unit = MathTypography.formatUnit(quantity.unit());
        return unit.isEmpty() ? value : value + " " + unit;
    }
}

