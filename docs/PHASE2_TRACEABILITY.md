# Phase 2 Traceability — Multi-Provider AI Projectile Questions

## Phase 2 scope

Phase 2 added a bounded AI-assisted workflow for text-only projectile questions using a multi-tier provider manager. The active AI provider interprets the prompt and prepares explanation prose; PHYSORA validates the structured interpretation and obtains all accepted numerical results exclusively from its deterministic projectile solver.

## Phase 2 verification baseline

Phase 2 automated tests cover contracts, provider routing, validation, verification, workflow orchestration, and JavaFX UI integration. The Phase 2 test classes contain **124 tests** (approximate; some cross-cut with Phase 1 solver logic).

Phase 2 was implemented and verified within a project that has grown to **367 total tests** across all phases. The 367-test figure is the current repository-wide baseline (verified on `feature/physora-48hr-demo` at commit `f112909`, 2026-11-15, via `.\mvnw.cmd test`). It includes Phase 1 projectile model tests, Phase 2 AI workflow tests, and Phase 3 multi-body simulation engine tests. The 367-test count is not a Phase 2 metric; it reflects the entire project.

End-to-end live runtime verification on a Windows desktop (`.\\mvnw.cmd javafx:run`, live Gemini API with network access, live Ollama + Qwen, and live fallback scenarios) remains **Pending (Stage 3D Gates 2–4)** and is labeled honestly below.

## Requirement traceability

| Requirement | Implementation | Verification | Status |
|---|---|---|---|
| Provider-neutral async boundary | `AIProvider`, `ProviderResult`, `ProviderAttribution`, `FallbackHop`, typed `AIProviderException` (`Kind` and `Reason`) | `AIContractsTest` (5 tests) | Implemented; unit tested |
| Multi-tier provider routing & operating modes | `AIProviderManager`, `AIProviderManagerFactory`, `ProviderMode` (`AUTO`, `CLOUD_ONLY`, `LOCAL_AI`, `OFFLINE`), `ProviderOwnership`, per-stage attribution & ordered fallback trails | `AIProviderManagerTest` (17 tests) | Implemented; unit tested |
| Google Gemini provider | Official Google GenAI Java SDK behind `GeminiTransport`, JSON schemas, retry/timeout/limit bounds (`GeminiProviderConfiguration`, default `gemini-3.1-flash-lite`) | `GeminiProviderTest` (7 tests, transport double); `LiveWorkflowVerificationTest` (1 test, verified offline fallback path) | Implemented; unit tested; live API verification pending Gate 3 |
| Optional local Ollama provider | `OllamaProvider`, `OllamaConfiguration` (`qwen-physics-test` default, `45 s` request / `500 ms` probe), `LazyOllamaTransport`, exact tag matching, `MODEL_UNAVAILABLE` cooldown, opt-in default `false` | `OllamaProviderTest` (22 tests, fake transport) | Implemented; unit tested; live Ollama/Qwen check pending Gate 3 |
| Built-in local fallback provider | `LocalAIProvider` deterministic rule-based parser/explainer (no LLM or external runtime required); `FallbackAIProvider` legacy wrapper | `LocalAIProviderTest` (4 tests), `FallbackAIProviderTest` (3 tests) | Implemented; unit tested |
| Text problem interpretation | Providers return typed target, givens, units, references, assumptions, clarifications, and confidence | Provider & workflow test suites | Implemented; unit tested |
| Classification, validation & completeness | `ProblemClassifier`, `ProblemValidator`, `ProblemNormalizer`, `ConstraintCompletenessValidator`; dimensional unit normalization, supported targets, ambiguity/missing inputs, backward-only part references, and solvability checks | `ProblemValidatorTest` (16 tests), `ConstraintCompletenessValidatorTest` (19 tests) | Implemented; unit tested |
| Deterministic solving & canonical problems | `SolverOrchestrator` routes validated inputs to `ProjectileProblemSolver` for both `ProjectileProblem` and `CanonicalPhysicsProblem` targets | `ProblemWorkflowTest` (4 tests), `CanonicalPhysicsProblemSolverTest` (16 tests) | Implemented; unit tested |
| Numerical output verification | `SolutionVerifier` checks completeness, dimensions, derivation association, and analytical residuals via `ProjectileMotion` | `ProblemWorkflowTest`, `AIProblemWorkflowTest` | Implemented; unit tested |
| Grounded explanation | `ExplanationVerifier` accepts only references to verified result keys and rejects unsupported numeric literal prose | `AIProblemWorkflowTest` (18 tests), `GeminiProviderTest` | Implemented; unit tested |
| Clarification and session lifecycle | `AIConversationService` supports clarification turns, revision, cancellation, session isolation, stale-result suppression, and safe disposal | `AIConversationServiceTest` (4 tests) | Implemented; unit tested |
| JavaFX UI integration, controls & attribution | `AIProblemView` in `NormalBenchView`: lazy `AIProviderManagerFactory` init, 4-mode & optional Ollama settings, read-only non-secret summary, independent 3-stage attribution cards (Interpretation, Explanation with ordered fallback hops, Numerical Verification), idempotent `closeActiveSession()` / `dispose()` | `AIProblemViewTest` (24 tests), `ProjectileSimulationControllerTest` (9 tests) via `FxTestRuntime` | Implemented; automated UI tested; live desktop walkthrough pending Gate 2–4 |
| Configuration & key handling | `PHYSORA_AI_MODE`, legacy `PHYSORA_AI_PROVIDER`, `PHYSORA_OLLAMA_ENABLED`, `GOOGLE_API_KEY`, `PHYSORA_GEMINI_MODEL`, bounded limits; no secret values logged or shown in UI | `AIProviderManagerTest`, `AIProblemViewTest`, source audit | Implemented; unit & UI tested |

## Known limits and follow-up

- **Live runtime checks pending (Gates 2–4)**: Live Gemini API calls, live local Ollama + Qwen inference, interactive `.\mvnw.cmd javafx:run` walkthroughs, and real network-disconnect fallback have not yet been executed in this offline sandbox.
- **Primary Gemini default**: Set to `gemini-3.1-flash-lite` in `GeminiProviderConfiguration`; overridable via `PHYSORA_GEMINI_MODEL`.
- **Optional Ollama**: Disabled by default (`PHYSORA_OLLAMA_ENABLED=false`) with zero startup probes (`LazyOllamaTransport`). When enabled, `OllamaProvider` matches only the configured model tag (plus `:latest` equivalence) and will not silently switch to another installed model.
- **Operating modes**: `AUTO` routes Gemini → optional Ollama → `LocalAIProvider`; `CLOUD_ONLY` uses Gemini with no silent fallback; `LOCAL_AI` uses optional Ollama → `LocalAIProvider` without Gemini; `OFFLINE` uses `LocalAIProvider` only with zero network calls.
- **Scope boundary**: Typed projectile-motion text questions only. Images/diagrams, other physics chapters, AI model editing, simulation control, accounts, and persistence remain out of scope. Distributed releases must move cloud API credentials behind a trusted server boundary.
