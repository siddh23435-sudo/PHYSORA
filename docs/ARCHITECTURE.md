# Current Architecture

This document records the implementation that exists today. Product context and longer-term direction remain in `PROJECT_CONTEXT.md` and `PROJECT_GOAL.md`.

## Runtime flow

`ProjectileSimulatorApp` creates the JavaFX shell. `PhysoraApplicationShell` owns a `ProjectileSimulationController` shared by the Normal Bench's simulation and logic views. The controller owns editable launch parameters, an immutable analytical `ProjectileMotion` model, and a JavaFX animation clock. Parameter changes rebuild the model and reset display time. The clock advances display time only; state and event quantities are queried from the model.

`NormalBenchView` provides launch speed, launch angle, gravity, initial x/y, landing-height inputs, transport controls, time scrubbing, playback rate, and live values. Invalid or non-finite coordinate text is rejected without replacing the last valid model input. Reset restores all parameter defaults and clears invalid text. `ProjectileSimulationView` delegates canvas drawing to `ProjectileCanvasRenderer`, which converts physical positive-up Y into screen-down coordinates and draws the axes, optional landing surface, analytical trajectory, apex and landing event markers, current projectile, and optional velocity-component vectors. Landing time uses the first future downward crossing of the configured landing height. An upward crossing alone is not treated as landing.

`ProjectileLogicView` reads the same controller state, creates a JavaFX-independent `POSITION_AT_TIME` problem, and asks `ProjectileProblemSolver` for its solution steps. It renders `MathExpression.Equation` structure, typed substitutions, intermediate values, and final results rather than maintaining a second copy of the position equations. Direct position queries report horizontal displacement, vertical linear change, and gravity drop. Velocity queries report gravity's vertical velocity contribution. Inverse solvers expose the calculated trig terms, vertical velocity terms, and quadratic root candidates they use. Root-based forward queries report candidate crossing times calculated by the analytical model. Playback rate affects only how quickly the clock advances; it does not modify model parameters or physics calculations.

## Physics and problem-solving packages

- `edu.physics.projectile.ProjectileMotion` is an immutable analytical point-projectile model using SI units and positive-up Cartesian coordinates. It calculates state, events, intersections, range, and trajectory arc length.
- `edu.physics.problem.PhysicalUnit` and `PhysicalQuantity` represent supported dimensions and unit conversion.
- `ProjectileProblem` represents a target and givens independently of JavaFX.
- `ProjectileProblemSolver` validates supported inputs and solves direct and inverse projectile questions. Forward behavior is grounded in `ProjectileMotion`. `ProjectileSolution` carries statuses, candidates, requirements, assumptions, warnings, givens, coordinate convention, and ordered derivation steps.
- `MathExpression` is a small presentation-independent expression tree with symbols, literals, binary operations, functions, integrals, tuples, and equations. `ProjectileEquations` maps solver equation IDs to those structures. Each solution step carries typed substitutions, intermediates, results, a precision policy, and an interpretation identity. Intermediate values are supplied for meaningful calculated terms and root candidates; direct calculations without staged work do not fabricate values.
- `ProjectileProblemSequence` and `ProjectileSequenceSolution` represent ordered dependent question parts and stop when a dependency is ambiguous.

## Tests and verification

The automated test suite consists of **17** JUnit test classes (**159** tests, plus the `FxTestRuntime` helper for 18 test source files):

- `ProjectileMotionTest` and `ProjectileMotionEdgeCaseTest` cover model behavior, boundaries, roots, landing selection, and numerical behavior.
- `ProjectileProblemSolverTest` covers units, direct/inverse requests, statuses, root intermediates, and multipart dependencies.
- `MathExpressionTest` checks expression rendering, symbol indexing, and invalid expression construction.
- `ProjectileSimulationControllerTest` and `AIProblemViewTest` share a single JavaFX toolkit lifecycle (`FxTestRuntime`), launch views/application stages on the FX thread, check input validation, view switching, output synchronization, playback, reset, scrubbing, provider settings, per-stage attribution rendering, `1120x700` layout fit, and resource disposal. `ProjectileSimulationControllerTest` also captures phase-1 scene snapshots under ignored `target/phase1-visual-check/` (scene snapshot review, not native desktop-window captures).
- `AIContractsTest`, `AIConversationServiceTest`, `AIProblemWorkflowTest`, `AIProviderManagerTest`, `OllamaProviderTest`, `GeminiProviderTest`, `LocalAIProviderTest`, `FallbackAIProviderTest`, `ProblemValidatorTest`, `ProblemWorkflowTest`, and `LiveWorkflowVerificationTest` cover contracts, routing, cooldown/reliability, validation, verification, and workflow orchestration.
- JaCoCo creates `target/site/jacoco/index.html` during `verify`. See `PHASE1_TRACEABILITY.md` and `PHASE2_TRACEABILITY.md` for coverage and verification status.

## Phase 2 AI workflow

`AIProblemView` is a separate Normal Bench tab (`AI PROBLEM`). It exposes mode selection (`AUTO`, `CLOUD_ONLY`, `LOCAL_AI`, `OFFLINE`), optional Ollama configuration (disabled by default), a read-only non-secret environment readiness summary, and a three-card attribution strip in **VERIFIED SOLUTION** showing **Problem Interpretation**, **Solution Explanation** (including ordered `FallbackHop` trails with sanitized student-friendly reasons), and **Numerical Verification** (`PHYSORA Deterministic Physics Solver`). Questions run asynchronously through `AIConversationService`, and providers are initialized lazily on submit via `AIProviderManagerFactory.createFromEnvironment(...)` (and safely closed on idle configuration changes via `closeActiveSession()` or window close via `dispose()`).

`AIProvider` is the provider-neutral async contract returning `ProviderResult<T>` with independent `ProviderAttribution` and `FallbackHop` diagnostics. `AIProviderManager` orchestrates routing across:
- `GeminiProvider` — official Google GenAI Java SDK behind `GeminiTransport` (`gemini-3.1-flash-lite` default);
- `OllamaProvider` (strictly optional) — Java `HttpClient` behind `LazyOllamaTransport` (`qwen-physics-test` default, on-demand `/api/tags` probe with strict model matching and missing-model cooldown);
- `LocalAIProvider` — built-in deterministic rule-based parser/explainer requiring zero external services.

`CLOUD_ONLY` never falls back silently; `LOCAL_AI` never contacts Gemini; `OFFLINE` makes zero network calls; `AUTO` cascades Gemini → optional Ollama → `LocalAIProvider`. `ProblemClassifier`, `ProblemValidator`, and `ProblemNormalizer` check units, references, confidence, ambiguity, and missing inputs before `SolverOrchestrator` delegates `READY` requests to `ProjectileProblemSolver`. `SolutionVerifier` verifies completeness, dimensions, derivation association, and analytical residuals via `ProjectileMotion`. Only verified solver output is passed to `ExplanationVerifier`, which rejects claims containing unsupported numeric literals.

Environment variables govern startup defaults and runtime limits: `PHYSORA_AI_MODE`, legacy `PHYSORA_AI_PROVIDER`, `PHYSORA_OLLAMA_ENABLED`, `GOOGLE_API_KEY`, `PHYSORA_GEMINI_MODEL`, `PHYSORA_AI_TIMEOUT_SECONDS`, `PHYSORA_AI_MAX_ATTEMPTS`, `PHYSORA_AI_MAX_QUESTION_CHARS`, `PHYSORA_AI_MAX_OUTPUT_TOKENS`, `PHYSORA_OLLAMA_URL`, `PHYSORA_OLLAMA_MODEL`, and `PHYSORA_OLLAMA_TIMEOUT_SECONDS`. No network call or Ollama probe occurs before question submission. Live Gemini, live Ollama, and interactive desktop runtime verification remain pending Stage 3D Gates 2–4.

The first desktop iteration is text-only and does not persist conversations. A desktop API key can be extracted from a distributed application; use a trusted server-side credential boundary before public distribution. The workflow does not grant AI authority to alter models, calculate answers, or execute simulation frames. See `PHASE2_TRACEABILITY.md` for requirements and remaining verification items.

## Current limitations

- The JavaFX scene snapshots were visually reviewed at 1440x812 and 1120x700. The CUA runtime still fails to initialize, and no native top-level window was exposed to the agent desktop session, so this is scene snapshot review rather than desktop-window interaction. Controls and scene lifecycle are exercised by application tests.
- Not all solver branches or every field in every solver step have a dedicated assertion. Intermediate values are populated where meaningful, while some direct solver steps correctly have none.
- Surefire runs JavaFX from the classpath, so JavaFX 21 reports that its classes came from an unnamed module. Local runtime bytecode confirms the warning is emitted unless JavaFX graphics is loaded as its expected named, non-open `javafx.graphics` module. Tests start and show stages, fire controls, advance the animation, and capture scenes successfully. No current feature failure was observed. Classpath use is nevertheless flagged as an unsupported JavaFX configuration and may present future compatibility risk. `module-info.java` was not added solely to silence the warning; modularization needs deliberate Maven/Surefire module-path and test-access configuration. Revisit if packaging or a JavaFX feature requires it.
- AI beyond the bounded Phase 2 text-only projectile workflow, persistence, user accounts, additional chapters, and general-purpose model composition remain out of scope.
