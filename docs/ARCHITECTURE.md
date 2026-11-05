# Current Architecture

This document describes the implementation that exists today. The long-term product context and vision remain in `PROJECT_CONTEXT.md` and `PROJECT_GOAL.md`; this file does not redefine them.

## Runtime flow

`ProjectileSimulatorApp` creates the JavaFX shell. `PhysoraApplicationShell` owns one `ProjectileSimulationController`, shared by the Normal Bench's simulation and logic views. The controller owns the editable launch parameters, the immutable `ProjectileMotion` model, and a JavaFX animation clock. Parameter changes rebuild the analytical model and reset displayed time. The clock advances displayed simulation time only; every displayed state is queried from `ProjectileMotion.stateAt(time)`.

`ProjectileSimulationView` asks `ProjectileCanvasRenderer` to draw the model trajectory and current state. The renderer maps physical positive-up Y coordinates to screen positive-down coordinates. `ProjectileLogicView` presents current values and requests a structured `POSITION_AT_TIME` solution from the JavaFX-independent `ProjectileProblemSolver`; it does not reimplement that equation. The summary event values are also read from the same `ProjectileMotion` instance.

## Domain and problem-solving packages

- `edu.physics.projectile.ProjectileMotion` is an immutable analytical point-projectile model using SI units and positive-up Cartesian coordinates. It owns state evaluation, event times, intersections, velocity, range, and trajectory arc length (analytical primitive with adaptive quadrature near cancellation).
- `edu.physics.problem.PhysicalUnit` and `PhysicalQuantity` represent typed inputs and convert supported units to SI.
- `ProjectileProblem` represents one target and its givens independently of JavaFX.
- `ProjectileProblemSolver` validates and solves the supported direct and inverse targets using `ProjectileMotion` for forward model behavior. Results use statuses, candidates, ordered equation steps, missing-input information, assumptions, and warnings.
- `ProjectileProblemSequence` and `ProjectileSequenceSolution` represent ordered dependent parts and stop at ambiguous dependencies rather than selecting one candidate implicitly.

## Tests

- `ProjectileMotionTest` covers the original analytical baseline.
- `ProjectileMotionEdgeCaseTest` covers broader boundary, root, invariant, and numerical behavior.
- `ProjectileProblemSolverTest` covers units, direct/inverse requests, statuses, and multipart dependencies.
- `ProjectileSimulationControllerTest` exercises clock progression, reset/rebuild, end-of-flight, and bounded frame delays on the JavaFX platform.

## Current limitations

The JavaFX UI exposes speed, angle, and gravity with the launch and landing surfaces fixed at the origin/ground. The problem backend can accept offsets and a landing height, but those values are not editable in the UI. The equation-step representation is structured but currently uses equation strings and numeric substitution maps rather than a typed symbolic expression tree. Teaching Explainer, AI, persistence, and the broader chapter/model-composition architecture are future scope and are not implemented.
