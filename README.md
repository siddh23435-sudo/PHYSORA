# PHYSORA

A Java-based interactive physics learning, modeling, and simulation platform for Class 11–12 students, particularly those preparing for JEE Main and JEE Advanced.

## Vision

The platform aims to help students construct physical models, understand governing equations, execute simulations, inspect results, and learn physics through experimentation.

It is intended to evolve beyond static animations and conventional question-solving tools into a unified environment for physical modeling, mathematical reasoning, simulation, and AI-assisted learning.

## Current Development Milestone

The first feature is a **Projectile Motion Simulator**.

The initial simulator is intended to support:

- Adjustable launch speed
- Adjustable launch angle
- Adjustable gravity
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

The current Phase 1 foundation includes an analytical projectile model, a JavaFX simulator, a presentation-independent projectile problem solver, and JUnit coverage for model, solver, and simulation-clock behavior. The problem backend supports more inputs and queries than the UI currently exposes; see `docs/PHASE1_TRACEABILITY.md` for tested capabilities and known gaps. A passing Maven build is not by itself a claim that all Phase 1 acceptance criteria are complete.

AI integration, additional physics chapters, model composition, and advanced learning features are planned for later milestones.

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

## License

The project license has not yet been finalized.




