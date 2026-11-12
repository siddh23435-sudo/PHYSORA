# PHYSORA

A Java-based interactive physics learning, modeling, and simulation platform for Class 11–12 students, particularly those preparing for JEE Main and JEE Advanced.

## Vision

The platform aims to help students construct physical models, understand governing equations, execute simulations, inspect results, and learn physics through experimentation.

It is intended to evolve beyond static animations and conventional question-solving tools into a unified environment for physical modeling, mathematical reasoning, simulation, and AI-assisted learning.

## Current Development Milestone

The foundation is a **Projectile Motion Simulator**. The current development milestone adds a bounded AI-assisted workflow for text-only projectile questions using a multi-tier provider manager (`Gemini`, optional local `Ollama`, and a built-in deterministic rule-based `LocalAIProvider` fallback). The active AI provider interprets the prompt and prepares explanation prose; PHYSORA validates the structured interpretation and obtains all accepted numerical results exclusively from its deterministic projectile solver.

The initial simulator is intended to support:

- Adjustable launch speed
- Adjustable launch angle
- Adjustable gravity
- Adjustable launch coordinates and landing height
- Projectile trajectory visualization
- Position and velocity calculations
- Time of flight
- Maximum height
- Horizontal range
- Play, pause, and reset controls
- Automated physics tests

## Technology

Java 17 is mandatory. The desktop UI will use JavaFX, and Maven is the build system. Use the checked-in Maven Wrapper (`mvnw.cmd` on Windows or `./mvnw` on macOS/Linux). JUnit is used for automated tests.

## Run locally

```powershell
.\mvnw.cmd javafx:run
```

Run the tests and package the project with:

```powershell
.\mvnw.cmd clean verify
```

## Project Status

The project is being developed incrementally.

The Phase 1 foundation includes an analytical projectile model, a JavaFX simulator, and a presentation-independent projectile problem solver with structured equation expressions. Phase 2 adds a constrained multi-provider AI question workflow (`AIProviderManager` across cloud Gemini, optional local Ollama, and built-in `LocalAIProvider` fallback) plus mode controls and per-stage attribution transparency in the `AI PROBLEM` tab. JaCoCo coverage is generated during `clean verify`. See `docs/PHASE1_TRACEABILITY.md` and `docs/PHASE2_TRACEABILITY.md` for tested capabilities and remaining live runtime verification items. A passing Maven build is not by itself a claim that all live provider or desktop acceptance checks are complete.

Additional physics chapters, model composition, and advanced learning features remain planned for later milestones.

## AI Provider Configuration (Phase 2)

PHYSORA does **not** require Ollama to be installed or a Gemini API key just to launch or solve problems offline. Provider construction happens lazily when a student submits a question, with zero startup probes.

### Operating Modes (`PHYSORA_AI_MODE` or UI Mode Selector)

- `AUTO` *(default)* — Tries Google Gemini (if `GOOGLE_API_KEY` is set), then optional Ollama (only if enabled), and otherwise falls back to the built-in deterministic rule-based `LocalAIProvider`.
- `CLOUD_ONLY` — Uses Google Gemini exclusively (`GOOGLE_API_KEY` required). Never falls back silently to Ollama or `LocalAIProvider`.
- `LOCAL_AI` — Uses optional local Ollama (when enabled) with fallback to `LocalAIProvider`. Never sends requests to Google Gemini.
- `OFFLINE` — Uses only the built-in rule-based `LocalAIProvider` with zero network calls.

### Environment Variables and Defaults

- **Routing & mode**:
  - `PHYSORA_AI_MODE` (default `AUTO`; accepts `AUTO`, `CLOUD_ONLY`, `LOCAL_AI`, `OFFLINE`, plus aliases `CLOUD`, `GEMINI`, `LOCAL_LLM`, `OLLAMA`, `LOCAL`, `RULE_BASED`)
  - `PHYSORA_AI_PROVIDER` (legacy fallback when `PHYSORA_AI_MODE` is unset/blank: `gemini` -> `AUTO`, `local` -> `OFFLINE`)
  - `PHYSORA_OLLAMA_ENABLED` (`true` or `false`, default `false`; Ollama is strictly opt-in)
- **Google Gemini (cloud, optional)**:
  - `GOOGLE_API_KEY` (set in local environment before launching; never commit API keys or display them in UI)
  - `PHYSORA_GEMINI_MODEL` (default `gemini-3.1-flash-lite`)
  - `PHYSORA_AI_TIMEOUT_SECONDS` (default `45`, positive integer)
  - `PHYSORA_AI_MAX_ATTEMPTS` (default `3`, range `1`–`5`)
  - `PHYSORA_AI_MAX_QUESTION_CHARS` (default `12000`, range `1`–`100000`)
  - `PHYSORA_AI_MAX_OUTPUT_TOKENS` (default `3000`, range `128`–`16384`)
- **Ollama (local LLM, optional)**:
  - `PHYSORA_OLLAMA_URL` (default `http://127.0.0.1:11434`)
  - `PHYSORA_OLLAMA_MODEL` (default `qwen-physics-test`; matched strictly against exact tag or `:latest` without silent model substitution)
  - `PHYSORA_OLLAMA_TIMEOUT_SECONDS` (default `45`; health probe timeout is `500 ms` on demand only)

Network access occurs only when submitting a question in a network-enabled mode (`AUTO` with Gemini/Ollama, `CLOUD_ONLY`, or enabled `LOCAL_AI`). In the UI, the **VERIFIED SOLUTION** panel displays independent attribution for **Problem Interpretation**, **Solution Explanation** (including ordered fallback hops and sanitized reasons), and **Numerical Verification** (`PHYSORA Deterministic Physics Solver`). The current desktop app reads local credentials directly; a distributed public release should place provider credentials behind a trusted server boundary.

## Development Principles

- Correct physics before visual polish.
- Keep the physics model separate from the UI.
- Test calculations against analytical results.
- Avoid unnecessary dependencies.
- Build working milestones before generalizing the architecture.
- Keep the student involved in important technical decisions.

## Project Documentation

- `AGENTS.md` — coding agent instructions
- `CLAUDE.md` — Claude Code instructions
- `docs/PROJECT_CONTEXT.md` — physics scope and long-term architecture
- `docs/PROJECT_GOAL.md` — academic evaluation goals, product quality bar, and design direction
- `docs/ARCHITECTURE.md` — current implementation structure and runtime flow
- `docs/PHASE1_TRACEABILITY.md` — Phase 1 requirement status and test mapping
- `docs/PHASE2_TRACEABILITY.md` — Phase 2 AI workflow status and test mapping

## License

The project license has not yet been finalized.




