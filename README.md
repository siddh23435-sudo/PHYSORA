# PHYSORA

A Java-based interactive physics learning, modeling, and simulation platform for Class 11–12 students preparing for JEE Main and JEE Advanced.

## What PHYSORA Does

PHYSORA helps students construct physical models, understand governing equations, execute simulations, inspect results, and learn physics through experimentation. It combines an interactive projectile motion simulator with an AI-assisted problem-solving workflow and a foundational multi-body dynamics engine.

## Current Capabilities

### Interactive Projectile Motion Normal Bench (Student-Facing)

The desktop application presents a **Normal Bench** workspace with three tabs:

- **Simulation** — Animated projectile trajectory visualization with adjustable launch speed, angle, gravity, initial position, and landing height. Play/pause/reset controls, time scrubbing, playback rate, apex and landing markers, and velocity-component vectors. Physics uses positive-up Cartesian coordinates mapped to JavaFX screen-down rendering.
- **Logic** — Structured equation derivation showing `MathExpression` trees, typed substitutions, intermediate values, and final analytical results from the deterministic `ProjectileProblemSolver`. Supports direct and inverse projectile targets, multipart dependent sequences, and candidate root reporting.
- **AI Problem** — Submit natural-language projectile questions. An AI provider interprets the problem; PHYSORA validates the interpretation, solves it deterministically, and verifies the explanation. Attribution cards show which provider handled interpretation, explanation, and numerical verification independently. The AI never supplies accepted numerical answers.

### AI Provider Architecture

PHYSORA uses a multi-tier provider system with four operating modes:

| Mode | Behavior |
|---|---|
| `AUTO` (default) | Gemini → optional Ollama → LocalAIProvider |
| `CLOUD_ONLY` | Gemini only; no silent fallback |
| `LOCAL_AI` | Optional Ollama → LocalAIProvider; no Gemini |
| `OFFLINE` | Built-in rule-based LocalAIProvider only; zero network calls |

Providers are initialized lazily on question submission. No network call or Ollama probe occurs at startup. The built-in `LocalAIProvider` always works offline without any external service.

### Multi-Body Dynamics Engine (Backend Foundation)

The `edu.physics.simulation` package contains a verified headless multi-body constraint engine. This is **engineering infrastructure not yet connected to the interactive application**. It includes:

- 2D rigid body mechanics with symplectic integration
- Declarative scene composition (`LaboratoryScene`) with bodies, anchors, cables, distance links, revolute joints, and springs
- Bilateral constraint solving (distance links, revolute joints)
- Direct cable geometry with endpoint normalization and kinematic evaluation
- Mass-whitened Delassus RRQR solver with tier classification and redundant-row compatibility
- Unilateral active-set solver for cable systems with complementarity, velocity gating, and cycle detection

This infrastructure is fully tested but awaits dynamic simulation integration (Stage 3C-2A.4) and UI connection before it becomes a student-facing feature.

## Technology Stack

| Component | Technology |
|---|---|
| Language | Java 17 (mandatory) |
| Desktop UI | JavaFX 21 |
| Build system | Maven (use the checked-in Maven Wrapper) |
| Testing | JUnit 5 |
| Coverage | JaCoCo |
| AI (cloud) | Google Gemini via official GenAI Java SDK (optional) |
| AI (local) | Ollama (optional, disabled by default) |

## Getting Started

### Prerequisites

- Java 17 JDK
- No other dependencies required for basic usage

### Run the application

```powershell
.\mvnw.cmd javafx:run
```

On macOS/Linux:
```bash
./mvnw javafx:run
```

### Run the tests

```powershell
.\mvnw.cmd test
```

Run tests with coverage report:
```powershell
.\mvnw.cmd clean verify
```

Coverage report: `target/site/jacoco/index.html`

### AI Provider Configuration (Optional)

PHYSORA does **not** require Ollama or a Gemini API key to launch or solve problems offline.

**Environment variables:**

- `PHYSORA_AI_MODE` — `AUTO` (default), `CLOUD_ONLY`, `LOCAL_AI`, `OFFLINE`
- `GOOGLE_API_KEY` — Required for Gemini; set in local environment (never commit)
- `PHYSORA_GEMINI_MODEL` — Default `gemini-3.1-flash-lite`
- `PHYSORA_OLLAMA_ENABLED` — `true` or `false` (default `false`)
- `PHYSORA_OLLAMA_URL` — Default `http://127.0.0.1:11434`
- `PHYSORA_OLLAMA_MODEL` — Default `qwen-physics-test`
- `PHYSORA_AI_TIMEOUT_SECONDS` — Default `45`
- `PHYSORA_AI_MAX_ATTEMPTS` — Default `3` (range 1–5)
- `PHYSORA_AI_MAX_QUESTION_CHARS` — Default `12000`
- `PHYSORA_AI_MAX_OUTPUT_TOKENS` — Default `3000` (range 128–16384)

## Test Baseline

**367 automated tests**, all passing. Verified on `feature/physora-48hr-demo` at commit `f112909` (2026-11-15).

| Phase | Scope | Approximate Tests |
|---|---|---|
| Phase 1 | Projectile model, problem solver, UI controls | ~76 |
| Phase 2 | AI workflow, providers, validation, UI tab | ~124 |
| Phase 3 | Multi-body simulation, constraints, geometry | ~127 |
| **Total** | | **367** |

Note: Some tests cross-cut phase boundaries. The authoritative total is from `.\mvnw.cmd test`.

## Development Principles

- Correct physics before visual polish
- Physics model independent of UI rendering
- Test calculations against analytical results
- Avoid unnecessary dependencies
- Build working milestones before generalizing
- AI interprets and explains; the deterministic solver calculates

## Project Documentation

| Document | Purpose |
|---|---|
| `AGENTS.md` | Coding agent instructions and project rules |
| `CLAUDE.md` | Claude Code instructions |
| `docs/PROJECT_CONTEXT.md` | Physics scope and long-term architecture |
| `docs/PROJECT_GOAL.md` | Academic evaluation goals and product direction |
| `docs/ARCHITECTURE.md` | Current implementation structure and runtime flow |
| `docs/PHASE1_TRACEABILITY.md` | Phase 1 requirement status and test mapping |
| `docs/PHASE2_TRACEABILITY.md` | Phase 2 AI workflow status and test mapping |
| `docs/PHASE3_TRACEABILITY.md` | Phase 3 multi-body constraint engine traceability |

## License

The project license has not yet been finalized.
