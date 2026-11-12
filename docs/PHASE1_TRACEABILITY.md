# Phase 1 Requirement-to-Test Traceability

Status reflects the Phase 1 final acceptance pass. `clean verify` results and current JaCoCo counters are recorded below. UI integration tests create and show a real JavaFX `Stage`, exercise controls and transitions, and capture rendered scene snapshots under the ignored `target/phase1-visual-check/`. Those scenes were inspected. The desktop-control runtime failed to initialize and the agent session exposed no native top-level window, so no Windows desktop screenshot was available.

| Requirement | Status | Evidence |
|---|---|---|
| Analytical SI projectile model in positive-up Cartesian coordinates; configurable speed, angle, gravity, initial x/y, and landing height | Implemented and unit-tested | `ProjectileMotionTest`; `ProjectileMotionEdgeCaseTest` |
| State, displacement, velocity, speed, direction, acceleration, apex, landing time, range, intersections, and trajectory distance | Implemented; representative regular and edge cases tested | `ProjectileMotionTest`; `ProjectileMotionEdgeCaseTest` |
| Height/position intersections include nonnegative roots, repeated roots, and stationary-coordinate cases | Implemented and edge-tested | `ProjectileMotionEdgeCaseTest` |
| Unequal launch/landing heights and zero-gravity behavior | Implemented and tested | `ProjectileMotionEdgeCaseTest`; `ProjectileProblemSolverTest` |
| Landing time selects the first future downward intersection; an ascending-only crossing is not called a landing | Implemented and edge-tested, including reachable/unreachable elevated surfaces and upward zero-gravity motion | `ProjectileMotionEdgeCaseTest`; `ProjectileProblemSolverTest.landingSolutionRetainsBothHeightRootsAndSelectsTheDescendingEvent` |
| Arc length, distinct from straight-line displacement, with analytical primitive and adaptive quadrature fallback | Implemented and tested for representative and numerically difficult cases | `ProjectileMotionEdgeCaseTest` |
| Typed inputs/units, direct and inverse target families, candidate/status reporting, and multipart dependencies | Implemented; supported target families and key ambiguous/impossible/repeated cases tested | `ProjectileProblemSolverTest` |
| Structured equations (AST), symbol definitions/units, typed substitutions/results, problem statement/givens, coordinate convention, and display precision policy | Implemented and tested on representative direct/inverse paths | `MathExpressionTest`; `ProjectileProblemSolverTest` |
| Solver-generated intermediate values | Populated where the solver/model computes meaningful terms: position components, gravity's velocity contribution, inverse-solver trig/velocity terms, quadratic angle candidates, and candidate crossing times. Values retain units and the solution step's precision policy. Final outputs remain associated with their step; no intermediate is fabricated for direct formulas without a meaningful staged value. | `ProjectileProblemSolverTest.arbitraryTimeQueriesUseTheSameAnalyticalModel`; `inverseSpeedAndAngleHandleUniqueMultipleAndImpossibleConditions`; `landingSolutionRetainsBothHeightRootsAndSelectsTheDescendingEvent`; UI assertion in `ProjectileSimulationControllerTest` |
| Initial x/y and landing-height controls; finite-number validation; reset and synchronization between views | Implemented and application-tested with real JavaFX controls | `ProjectileSimulationControllerTest.coordinateInputsUpdateTheModelAndRejectInvalidNumbers`; `playbackScrubbingAndResetWorkThroughApplicationControls` |
| Positive-up physics mapped to JavaFX down-positive screen coordinates; trajectory, landing surface, apex/landing markers, current projectile and optional velocity components | Implemented; scenes inspected for default and offset launches, higher/lower landing surfaces, changed speed/angle, Logic View, and 1120×700 layout. Displayed flight/range values are compared with the model in app tests. | `ProjectileCanvasRenderer`; `ProjectileSimulationControllerTest.capturesRealApplicationScenesForPhaseOneVisualAcceptance`; snapshots in ignored `target/phase1-visual-check/` |
| Animation clock independent from analytical physics; rate, play/pause/resume, completion, reset, and scrubbing | Implemented and controller/app tested, including real `AnimationTimer` pulses | `ProjectileSimulationControllerTest` |
| App startup, required controls, repeated Simulation/Logic navigation, invalid entry recovery, model outputs, and close lifecycle | Automated integration coverage with actual `ProjectileSimulatorApp.start(Stage)` and real JavaFX controls | `ProjectileSimulationControllerTest` |
| Coverage measurement | JaCoCo configured for `verify`; 96.7% line (1687/1744), 73.2% branch (480/656), 93.0% instruction (11166/12005). Branches were reviewed by critical behavior, not chased to an arbitrary threshold. | `target/site/jacoco/index.html` |
| AI, backend, database, additional chapters, and final story-mode redesign | Intentionally out of scope for Phase 1 | Project scope in `AGENTS.md` and `docs/PROJECT_CONTEXT.md` |

## Branch review and JavaFX module warning

JaCoCo's remaining uncovered branches are concentrated in less common inverse-input combinations, numerical overflow/fallback paths, optional/empty events, and UI no-op or teardown conditions. Tests were added for downward landing selection, root candidates, discriminant overflow, invalid solver input/unit-conversion overflow, actual animation-timer pulses, and application-level scene scenarios. Not every solver target and branch is exhausted; the overall coverage ratio is diagnostic rather than a goal.

Surefire loads JavaFX from the classpath, so JavaFX 21 reports that its classes came from an unnamed module. Local JavaFX runtime bytecode inspection confirms the warning is emitted unless JavaFX graphics is loaded as the expected named, non-open `javafx.graphics` module. Application tests start/show stages, fire controls, advance the animation, and capture scene snapshots successfully. No current feature failure was observed. Classpath use is nevertheless flagged as an unsupported JavaFX configuration and may present future compatibility risk. `module-info.java` was not added solely to silence the warning; modularization needs deliberate Maven/Surefire module-path and test-access configuration. Revisit if packaging or a JavaFX feature requires it.

## Verification

- `.\mvnw.cmd clean verify` — `BUILD SUCCESS`; 56 tests, 0 failures, 0 errors, 0 skipped.
- JaCoCo — report generated at `target/site/jacoco/index.html`; 96.7% line (1687/1744), 73.2% branch (480/656), 93.0% instruction (11166/12005) coverage.
- `.\mvnw.cmd javafx:run` — final launch stayed running without application exceptions. This session exposed no native top-level window; the shown JavaFX stages were captured and visually inspected through scene snapshots.
- `git diff --check` — passed.
- Generated build output and visual captures are under ignored `target/`; no generated artifacts are staged or tracked.

## Phase 1 acceptance verdict

**Accepted for Phase 1 scope.** The analytical model, simulator controls, landing selection, structured equation/intermediate output, app interactions, and rendered scene scenarios are implemented and exercised. This does not claim exhaustive branch coverage, native desktop capture from the agent session, modular JavaFX packaging, or Phase 2 functionality.
