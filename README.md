# PHYSORA

A Java-based interactive physics learning, modeling, and simulation platform for Class 11–12 students preparing for JEE Main and JEE Advanced.

## What PHYSORA Does

PHYSORA helps students construct physical models, understand governing equations, execute simulations, inspect results, and learn physics through experimentation. It combines an interactive projectile motion simulator with an AI-assisted problem-solving workflow and a foundational multi-body dynamics engine.

## Current Capabilities

### Chapter 01 — Kinematics (LIVE)

The Kinematics laboratory provides a complete five-mode learning cycle for two-dimensional projectile motion:

- **Experiment** — Interactive trajectory simulation with isotropic scaling, 1-2-5 decade grid ticks, adjustable parameters ($v_0, \theta, g, x_0, y_0, y_{\text{land}}$), velocity component overlays ($v_x, v_y$), and event markers (Apex, Landing).
- **Predict** — Scientific hypothesis testing where predictions ($H_{\text{max}}, T, R$) are locked before launch, evaluated strictly against analytical truth upon landing, and protected against post-lock tampering.
- **Compare** — Multi-experiment overlay comparing two trajectories on an identical physical coordinate system (e.g., $30^\circ$ vs $60^\circ$ complementary launch symmetry) with parameter delta tables and JEE insight cards.
- **Understand** — Step-by-step 6-stage guided derivations for key quantities ($H_{\text{max}}, T, R, t_{\text{apex}}, v_{\text{land}}$) with algebraic formulas, value substitutions, and physical limit discussions.
- **Ask AI** — Grounded problem-solving companion for natural-language projectile questions. Providers (Google Gemini or offline rule-based fallback) interpret problems and draft explanations; PHYSORA's deterministic solver computes all numerical answers.

### Chapter 02 — Relative Motion (LIVE)

Four dedicated interactive workbenches for two-dimensional relative kinematics:

- **Two-Projectile Pursuit & Reference Frames** — Real-time simulation of two projectiles under constant gravity with dynamic Galilean transformations between Ground, Observer A, and Observer B frames, live velocity triangle ($\vec{v}_B(t) = \vec{v}_A(t) + \vec{v}_{B/A}$), and collision detection.
- **River-Boat Vector Crossing** — Drift and heading resolution with live resultant velocity triangle ($\vec{v}_{b/g} = \vec{v}_{b/r} + \vec{u}$) and presets for Shortest Path and Quickest Crossing.
- **Rain-Man Umbrella Alignment** — Geometric umbrella orientation modeling with real-time Angular Alignment Error ($|\Delta\phi|$) inspector, Alignment Rating badges, and snap-to-optimal tilt.
- **Inverse Rain Investigator** — Five-stage educational workflow (Evidence $\to$ Ambiguity $\to$ Geometry $\to$ Reconstruction $\to$ Verification) for solving the inverse problem from two observer measurements, equipped with analytical conditioning diagnostics ($\kappa_{\text{down}}$).

### Chapters 03–08 — Curriculum Roadmap (PLANNED)

Chapters 03 through 08 (Laws of Motion, Work & Energy, Momentum & Collisions, Circular Motion, Rotation, Oscillations) are explicitly structured as planned curriculum roadmaps with comprehensive syllabus topics and seamless one-click return to active laboratories.

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

**602 automated tests**, all passing (0 failures, 0 errors, 0 skipped). Verified via `.\mvnw.cmd test` on branch `chore/physora-final-cleanup`.

| Scope | Approximate Tests |
|---|---|
| Kinematics (Models, Solvers, Predictions, Comparisons, Five Modes) | ~110 |
| AI Workflow (Gemini, Fallback, Validation, Explanations, Repro Tests) | ~140 |
| Relative Motion (Pursuit, River-Boat, Rain-Man, Inverse Investigator) | ~145 |
| Multi-Body Simulation Engine (Rigid Bodies, Constraints, Cables, Delassus) | ~127 |
| UI & Visual Lifecycle (Responsive Scaling, Screen Resolutions, Nav Audits) | ~80 |
| **Total Passing Tests** | **602** |

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
