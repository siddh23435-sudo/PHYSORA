package edu.physics.problem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Presentation-independent expression nodes used to describe solver equations. */
public sealed interface MathExpression
        permits MathExpression.Symbol, MathExpression.Literal, MathExpression.Binary,
        MathExpression.Function, MathExpression.Integral, MathExpression.Tuple {

    String render();

    record Symbol(String name, String definition, PhysicalUnit unit) implements MathExpression {
        public Symbol {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Symbol name is required");
            if (definition == null || definition.isBlank()) throw new IllegalArgumentException("Symbol definition is required");
            Objects.requireNonNull(unit, "Symbol unit is required");
        }
        @Override public String render() { return name; }
    }

    record Literal(double value) implements MathExpression {
        public Literal {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Expression literal must be finite");
        }
        @Override public String render() { return Double.toString(value); }
    }

    enum Operator { ADD("+"), SUBTRACT("-"), MULTIPLY("*"), DIVIDE("/"), POWER("^");
        private final String token;
        Operator(String token) { this.token = token; }
    }

    record Binary(Operator operator, MathExpression left, MathExpression right) implements MathExpression {
        public Binary {
            Objects.requireNonNull(operator);
            Objects.requireNonNull(left);
            Objects.requireNonNull(right);
        }
        @Override public String render() { return "(" + left.render() + " " + operator.token + " " + right.render() + ")"; }
    }

    enum FunctionName { SIN("sin"), COS("cos"), TAN("tan"), ATAN("atan"), SQRT("sqrt"), MAX("max");
        private final String token;
        FunctionName(String token) { this.token = token; }
    }

    record Function(FunctionName name, List<MathExpression> arguments) implements MathExpression {
        public Function {
            Objects.requireNonNull(name);
            arguments = List.copyOf(arguments);
            if (arguments.isEmpty()) throw new IllegalArgumentException("A function needs an argument");
            if (name == FunctionName.MAX && arguments.size() < 2) throw new IllegalArgumentException("max needs at least two arguments");
            if (name != FunctionName.MAX && arguments.size() != 1) throw new IllegalArgumentException("Function takes one argument");
        }
        @Override public String render() {
            return name.token + "(" + arguments.stream().map(MathExpression::render).collect(Collectors.joining(", ")) + ")";
        }
    }

    record Integral(MathExpression integrand, Symbol variable, MathExpression lower,
                    MathExpression upper) implements MathExpression {
        public Integral {
            Objects.requireNonNull(integrand);
            Objects.requireNonNull(variable);
            Objects.requireNonNull(lower);
            Objects.requireNonNull(upper);
        }
        @Override public String render() {
            return "integral[" + lower.render() + ", " + upper.render() + "] "
                    + integrand.render() + " d" + variable.name();
        }
    }

    record Tuple(List<MathExpression> elements) implements MathExpression {
        public Tuple {
            elements = List.copyOf(elements);
            if (elements.size() < 2) throw new IllegalArgumentException("A tuple needs at least two elements");
        }
        @Override public String render() {
            return "(" + elements.stream().map(MathExpression::render).collect(Collectors.joining(", ")) + ")";
        }
    }

    record Equation(String id, String principleId, MathExpression left, MathExpression right,
                    List<Symbol> symbols) {
        public Equation {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Equation identity is required");
            if (principleId == null || principleId.isBlank()) throw new IllegalArgumentException("Principle identity is required");
            Objects.requireNonNull(left);
            Objects.requireNonNull(right);
            symbols = List.copyOf(symbols);
            Map<String, Symbol> index = new LinkedHashMap<>();
            for (Symbol symbol : symbols) {
                if (index.putIfAbsent(symbol.name(), symbol) != null) throw new IllegalArgumentException("Duplicate symbol: " + symbol.name());
            }
        }

        public Map<String, Symbol> symbolsByName() {
            return symbols.stream().collect(Collectors.toUnmodifiableMap(Symbol::name, symbol -> symbol));
        }

        public String render() { return left.render() + " = " + right.render(); }
    }
}
