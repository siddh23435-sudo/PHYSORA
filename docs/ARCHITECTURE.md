# Current Architecture

This document records the implementation that exists today. Product context and longer-term direction remain in `PROJECT_CONTEXT.md` and `PROJECT_GOAL.md`.

Verification baseline: **367 tests**, `.\mvnw.cmd test`, all passing. Recorded on `feature/physora-48hr-demo` at commit `f112909` (2026-11-15).

## Application entry point and shell

`ProjectileSimulatorApp` is the JavaFX `Application` entry point. It creates a `PhysoraApplicationShell`, which owns the chapter rail and the Normal Bench workspace. The chapter rail (`ChapterRail`) is a vertical sidebar listing available physics chapters; currently only "Projectile Motion" is active.

## Normal Bench workflow

`NormalBenchView` is the central workspace. It presents three tabs for the projectile motion chapter:

- **Simulation** — interactive projectile trajectory visualization with parameter controls
- **Logic** — structured equation derivation and analytical solution display
- **AI Problem** — AI-assisted text problem interpretation and grounded explanation

All three tabs share a single `ProjectileSimulationController`, which owns editable launch parameters, an immutable analytical `ProjectileMotion` model, and a JavaFX animation clock. Parameter changes rebuild the model and reset display time.

### Simulation tab

`ProjectileSimulationView` delegates canvas drawing to `ProjectileCanvasRenderer`. The renderer converts physical positive-up Y into screen-down coordinates and draws axes, optional landing surface, analytical trajectory, apex and landing event markers, current projectile position, and optional velocity-component vectors.

`NormalBenchView` provides launch speed, launch angle, gravity, initial x/y, and landing-height inputs, transport controls (play/pause/reset), time scrubbing, playback rate, and live calculated values. Invalid or non-finite coordinate text is rejected without replacing the last valid model input. Reset restores all parameter defaults and clears invalid text. Landing time uses the first future downward crossing of the configured landing height; an upward crossing alone is not treated as landing.

### Logic tab

`ProjectileLogicView` reads the same controller state, creates a JavaFX-independent `POSITION_AT_TIME` problem, and asks `ProjectileProblemSolver` for its solution steps. It renders `MathExpression.Equation` structure, typed substitutions, intermediate values, and final results. Playback rate affects only how quickly the clock advances; it does not modify model parameters or physics calculations.

### AI Problem tab

`AIProblemView` exposes mode selection (`AUTO`, `CLOUD_ONLY`, `LOCAL_AI`, `OFFLINE`), optional Ollama configuration (disabled by default), a read-only non-secret environment readiness summary, and a three-card attribution strip in **VERIFIED SOLUTION** showing **Problem Interpretation**, **Solution Explanation** (including ordered `FallbackHop` trails with sanitized student-friendly reasons), and **Numerical Verification** (`PHYSORA Deterministic Physics Solver`). Questions run asynchronously through `AIConversationService`, and providers are initialized lazily on submit via `AIProviderManagerFactory.createFromEnvironment(...)` (and safely closed on idle configuration changes via `closeActiveSession()` or window close via `dispose()`).

## Physics and problem-solving packages

- `edu.physics.projectile.ProjectileMotion` is an immutable analytical point-projectile model using SI units and positive-up Cartesian coordinates. It calculates state, events, intersections, range, and trajectory arc length.
- `edu.physics.projectile.LandingSurface` models flat and inclined landing surfaces with configurable height and slope.
- `edu.physics.problem.PhysicalUnit` and `PhysicalQuantity` represent supported dimensions and unit conversion.
- `ProjectileProblem` represents a target and givens independently of JavaFX.
- `ProjectileProblemSolver` validates supported inputs and solves direct and inverse projectile questions. Forward behavior is grounded in `ProjectileMotion`. `ProjectileSolution` carries statuses, candidates, requirements, assumptions, warnings, givens, coordinate convention, and ordered derivation steps.
- `CanonicalPhysicsProblem` and `CanonicalSolution` extend problem-solving to standardized physics question representations with typed target families.
- `MathExpression` is a small presentation-independent expression tree with symbols, literals, binary operations, functions, integrals, tuples, and equations. `ProjectileEquations` maps solver equation IDs to those structures.
- `ProjectileProblemSequence` and `ProjectileSequenceSolution` represent ordered dependent question parts and stop when a dependency is ambiguous.
- `PhysicsCapabilityRegistry` tracks supported problem types and solver capabilities.

## AI conversation and provider architecture

`AIProvider` is the provider-neutral async contract returning `ProviderResult<T>` with independent `ProviderAttribution` and `FallbackHop` diagnostics. `AIProviderManager` orchestrates routing across three providers:

- **`GeminiProvider`** — official Google GenAI Java SDK behind `GeminiTransport` (`gemini-3.1-flash-lite` default). Cloud provider requiring `GOOGLE_API_KEY`. Configurable model, timeout, retry attempts, and output token limits.
- **`OllamaProvider`** (strictly optional) — Java `HttpClient` behind `LazyOllamaTransport` (`qwen-physics-test` default). On-demand `/api/tags` probe with strict model matching and missing-model cooldown. Disabled by default (`PHYSORA_OLLAMA_ENABLED=false`).
- **`LocalAIProvider`** — built-in deterministic rule-based parser/explainer requiring zero external services. Always available. `FallbackAIProvider` wraps a primary provider with automatic `LocalAIProvider` fallback.

**Routing modes:**
- `AUTO` — cascades Gemini → optional Ollama → `LocalAIProvider`
- `CLOUD_ONLY` — Gemini only; never falls back silently
- `LOCAL_AI` — optional Ollama → `LocalAIProvider`; never contacts Gemini
- `OFFLINE` — `LocalAIProvider` only; zero network calls

**Validation pipeline:** `ProblemClassifier`, `ProblemValidator`, and `ProblemNormalizer` check units, references, confidence, ambiguity, and missing inputs before `SolverOrchestrator` delegates `READY` requests to `ProjectileProblemSolver`. `ConstraintCompletenessValidator` verifies that interpreted problems have sufficient constraints for a unique solution. `SolutionVerifier` checks completeness, dimensions, derivation association, and analytical residuals via `ProjectileMotion`. `ExplanationVerifier` rejects claims containing unsupported numeric literals. Only verified solver output is accepted.

Environment variables govern startup defaults and runtime limits. No network call or Ollama probe occurs before question submission. Live Gemini, live Ollama, and interactive desktop runtime verification remain pending Stage 3D Gates 2–4. The workflow does not grant AI authority to alter models, calculate answers, or execute simulation frames.

## Headless multi-body simulation engine

The `edu.physics.simulation` package hierarchy contains a headless multi-body dynamics and constraint engine. This is verified engineering infrastructure that is **not yet connected to the student-facing JavaFX application**. The UI continues to use the Phase 1 analytical `ProjectileMotion` model for all interactive features.

### Core simulation (`edu.physics.simulation`)

- `RigidBody` — 2D rigid body with mass, moment of inertia, position, velocity, and angular state. Factory methods: `pointMass(...)`, `dynamicBlock(...)`, `staticAnchor(...)`.
- `RigidBodyState` — immutable kinematic state snapshot.
- `PhysicalBodySpec` — specification record for body construction.
- `SimulationWorld` — headless simulation container managing bodies, forces, and symplectic integration steps.
- `SymplecticIntegrator` — semi-implicit Euler integrator for time-stepping.
- `ForceGenerator` and `UniformGravity` — force application interface and uniform gravitational field.
- `Vector2D` — immutable 2D vector with arithmetic, rotation, dot/cross products, and normalization.

### Constraint subsystem (`edu.physics.simulation.constraint`)

The constraint subsystem solves acceleration-level constraint equations for coupled rigid-body systems:

- **Bilateral constraints:** `ConstraintRow`, `CoordinateMapping`, `MechanicalConstraint`, `DistanceLinkConstraint`, `RevoluteJointConstraint`, `DirectLinearConstraintSolver` — equality constraints maintaining fixed geometric relationships.
- **Cable geometry (Stage 3C-2A.1):** `DirectCableGeometry`, `CableKinematics` — geometric and kinematic evaluation for direct point-to-point inextensible cables with endpoint normalization and pulling tension invariant.
- **Delassus solver (Stage 3C-2A.2):** `MassWhitenedDelassus`, `ColumnPivotedRRQR`, `ConstraintTier`, `DelassusRrqrSolver` — mass-whitened RRQR factorization with tier classification, Tier 1B regularization, and redundant-row compatibility verification.
- **Unilateral solver (Stage 3C-2A.3):** `CableState`, `UnilateralSolveResult`, `ActiveSetConvergenceException`, `UnilateralActiveSetSolver` — active-set method enforcing complementarity for tension-only cable constraints with velocity gating, cycle detection, and residual validation guardrail.

### Geometry (`edu.physics.simulation.geometry`)

`GeometricShape`, `CircleShape`, `RectangleShape`, `PolygonShape`, `PointMassShape`, `BoundingBox`, `ShapeType`, `Transform2D` — shape representations and 2D rigid transformations for body collision geometry.

### Scene definition (`edu.physics.simulation.scene`)

`LaboratoryScene` — declarative scene builder for composing bodies, anchors, and structural relationships (cables, distance links, revolute joints, springs, surface contacts). `SceneBody`, `SceneAnchor`, topology records (`CableTopology`, `DistanceLinkTopology`, `RevoluteJointTopology`, `LinearSpringTopology`, `SurfaceContactTopology`), and validation (`SceneValidationResult`, `SceneDiagnostic`).

## Tests and verification

The automated test suite consists of **30 JUnit test classes** (**367 tests**, plus the `FxTestRuntime` helper; 31 test source files total):

### Phase 1 — Projectile model and UI (56 tests at Phase 1 acceptance; 76 current including later additions)

- `ProjectileMotionTest` (7) and `ProjectileMotionEdgeCaseTest` (21, including parameterized) — model behavior, boundaries, roots, landing selection, numerical edge cases
- `LandingSurfaceAndInclineMotionTest` (15) — inclined surfaces and landing geometry
- `ProjectileProblemSolverTest` (17) — units, direct/inverse requests, statuses, root intermediates, multipart dependencies
- `MathExpressionTest` (2) — expression rendering, symbol indexing
- `ProjectileSimulationControllerTest` (9) — input validation, view switching, playback, reset, scrubbing, scene snapshots
- `PhysicsCapabilityRegistryTest` (9) — capability tracking

### Phase 2 — AI workflow (124 tests)

- `AIContractsTest` (5), `AIConversationServiceTest` (4), `AIProblemWorkflowTest` (18), `AIProviderManagerTest` (17) — contracts, session lifecycle, routing
- `ConstraintCompletenessValidatorTest` (19) — solvability completeness
- `FallbackAIProviderTest` (3), `GeminiProviderTest` (7), `LiveWorkflowVerificationTest` (1), `LocalAIProviderTest` (4), `OllamaProviderTest` (22) — provider behavior
- `ProblemValidatorTest` (16), `ProblemWorkflowTest` (4) — validation and orchestration
- `CanonicalPhysicsProblemSolverTest` (16) — canonical problem solving (partially cross-cutting with Phase 1 solver)
- `AIProblemViewTest` (24) — UI tab, attribution rendering, provider settings, resource disposal

### Phase 3 — Multi-body simulation engine (127 tests)

- `Vector2DTest` (15), `SimulationWorldTest` (12) — core mechanics
- `GeometricShapesTest` (8), `Transform2DTest` (5) — geometry
- `LaboratorySceneTest` (15) — scene composition and validation
- `BilateralConstraintsTest` (16) — bilateral constraint solving
- `DirectCableGeometryTest` (17) — cable geometry and kinematics (Stage 3C-2A.1)
- `DelassusRrqrSolverTest` (22) — Delassus RRQR solver (Stage 3C-2A.2)
- `UnilateralActiveSetSolverTest` (17) — unilateral active-set solver (Stage 3C-2A.3)

Note: Some tests cross-cut phase boundaries (e.g., `CanonicalPhysicsProblemSolverTest` exercises both Phase 1 solver logic and Phase 2 canonical representations). The per-phase counts are approximate groupings; the authoritative total is **367 tests** from `.\mvnw.cmd test`.

JaCoCo creates `target/site/jacoco/index.html` during `clean verify`. See `PHASE1_TRACEABILITY.md`, `PHASE2_TRACEABILITY.md`, and `PHASE3_TRACEABILITY.md` for per-phase coverage and verification status.

## Current limitations

- The multi-body simulation engine (`edu.physics.simulation`) is verified headless infrastructure. It is **not connected to the JavaFX application**. Interactive multi-body labs, cable visualization, and constraint playback are planned for future stages.
- Surefire runs JavaFX from the classpath, so JavaFX 21 reports that its classes came from an unnamed module. Tests start and show stages, fire controls, advance the animation, and capture scenes successfully. No current feature failure was observed. Classpath use is flagged as an unsupported JavaFX configuration and may present future compatibility risk.
- The CUA runtime does not initialize in the agent sandbox; JavaFX scene snapshots were visually reviewed at 1440×812 and 1120×700 rather than through desktop-window interaction.
- Live Gemini, live Ollama, and interactive desktop runtime verification remain pending Stage 3D Gates 2–4.
- AI beyond the bounded Phase 2 text-only projectile workflow, persistence, user accounts, additional chapters, and general-purpose model composition remain out of scope.
