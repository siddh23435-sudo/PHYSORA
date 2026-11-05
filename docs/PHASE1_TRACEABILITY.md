# Phase 1 Requirement-to-Test Traceability

Status is specific to this repository revision. A passing unit test suite does not imply runtime/UI acceptance. The latest recorded `clean verify` execution passed 46 tests. The JavaFX application was also launched and visually inspected during this work; no automated end-to-end UI assertions are configured.

| Requirement | Status | Evidence |
|---|---|---|
| SI analytical projectile model; positive-up coordinates; configurable launch speed, angle, gravity, initial position and landing height | Implemented and unit-tested | `ProjectileMotionTest`; `ProjectileMotionEdgeCaseTest` |
| State, displacement, velocity, speed, direction, acceleration, apex, landing time and range | Implemented; core tests cover representative cases | `ProjectileMotionTest`; `ProjectileMotionEdgeCaseTest` |
| Height/position intersections return all nonnegative roots, including repeated and stationary-coordinate cases | Implemented and edge-tested | `ProjectileMotionEdgeCaseTest` |
| Unequal launch/landing heights and zero-gravity behavior | Implemented and tested | `ProjectileMotionEdgeCaseTest`; `ProjectileProblemSolverTest` |
| Distinguish straight-line displacement from trajectory distance; compute arc length | Implemented and tested; uses an analytical primitive with adaptive 8-point Gaussian quadrature fallback, relative-scale tolerance, and bounded refinement | `ProjectileMotionEdgeCaseTest` |
| Typed quantities and unit conversion | Implemented and tested | `ProjectileProblemSolverTest` |
| Direct position/velocity/speed/time/height/range/apex/arc-length problem targets | Implemented; solver tests cover supported target families and multiple roots | `ProjectileProblemSolverTest` |
| Inverse initial speed and launch angle | Implemented for encoded condition sets; tested for unique, repeated, impossible and underdetermined examples | `ProjectileProblemSolverTest` |
| Ordered multipart solving with references | Implemented and tested, including ambiguous dependency handling | `ProjectileProblemSolverTest` |
| Structured solution status, candidates, equation steps, assumptions and warnings | Implemented; tests assert key outputs/statuses, but not every metadata field for every target | `ProjectileProblemSolverTest` |
| Animation clock independent from physical equations; playback rate affects display time only | Implemented and controller-tested | `ProjectileSimulationControllerTest` |
| JavaFX rendering uses positive-up physical Y and draws analytical trajectory/current state | Implemented; renderer behavior has not yet got automated pixel/scene assertions | `ProjectileCanvasRenderer`; application visual check still required |
| Logic view consumes structured solver steps without duplicating the position equation | Implemented; solver itself is tested; JavaFX view wiring needs runtime review | `ProjectileLogicView`; `ProjectileProblemSolverTest` |
| Full UI controls, synchronization, validation, responsive layout and real app launch | Partial; controller tests cover clock/model lifecycle, not end-to-end control interaction or all display sizes | `ProjectileSimulationControllerTest`; runtime check required |
| Requirement-level comprehensive coverage and measured code coverage | Partial; broad case suite exists, no coverage report is configured, and not every requirement maps to a dedicated assertion | Test classes above; no JaCoCo report |
| AI, backend, database, other chapters, final story-mode redesign | Intentionally not implemented in Phase 1 | Explicit scope exclusion |

## Known verification gaps

- No automated JavaFX end-to-end test drives all controls and asserts rendered state.
- The full UI still fixes initial position and landing height at zero, although the domain model and problem backend support those inputs.
- Equation expressions are currently strings and are not validated as symbolic expressions.
- Numerical-range failures are explicit, but the finite `double` implementation cannot represent every mathematically finite real result.
- Coverage percentages are not available because a coverage tool is not configured.
