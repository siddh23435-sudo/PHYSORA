# AGENTS.md — Project Instructions

## 1. Project Identity

This repository contains a Java-based interactive physics learning, modeling, and simulation platform for Class 11–12 students, particularly those preparing for JEE Main and JEE Advanced.

The platform is intended to help students construct physical models, understand governing equations, execute simulations, inspect results, and learn physics through experimentation.

Read `docs/PROJECT_CONTEXT.md` for the physics scope and long-term architecture. Read `docs/PROJECT_GOAL.md` before major product or UI decisions; it defines the academic evaluation bar, quality standards, and working KINETIK design direction.

## 2. Mandatory Requirements

- Java 17 is a mandatory project requirement.
- Do not replace the Java implementation with TypeScript, JavaScript, Python, or another language.
- Do not initialize a TypeScript/Vite application as the primary project.
- JavaFX is the approved desktop UI framework. Maven is the approved build system; use the checked-in Maven Wrapper. JUnit is used for automated tests.
- The old ParticleForge repository is not being continued. This is a fresh project.
- The first development milestone is a working Projectile Motion Simulator.
- Physics correctness takes priority over visual polish.
- Do not introduce AI integration into the first milestone.

## 3. Role of the Coding Agent

Act as a careful software engineer, Java developer, physics implementation partner, and code reviewer.

The student is learning software development. Explain important technical decisions clearly and accessibly.

Do not assume the student already understands every abstraction.

Do not merely produce code. Help the student understand what is being built and why.

## 4. Before Making Changes

Always inspect the actual repository first.

Determine:

- Current directory structure
- Existing source files
- Existing project configuration
- Java version
- Available build tools
- Existing tests
- Existing instructions
- Current implementation status

Do not assume the repository is empty.

Do not overwrite or replace existing work without understanding it.

If a project rubric or explicit requirement exists in the repository, follow it.

## 5. Architecture Principles

Keep responsibilities separated.

For the first simulator, the following separation is desirable:

- Physics model and calculations
- Application/controller logic
- User interface
- Rendering
- Tests

The physics calculations must not be embedded entirely inside UI event handlers.

The physics model should remain independent of the rendering coordinate system.

Do not introduce a general-purpose physics engine for idealized projectile motion unless a concrete requirement justifies it.

Do not create abstractions merely because they may be useful for future chapters.

Build a working feature first. Extract reusable architecture when actual requirements justify it.

## 6. Physics Correctness

Physics must be mathematically and conceptually correct.

Check:

- Equations
- Initial conditions
- Units
- Sign conventions
- Coordinate systems
- Physical assumptions
- Boundary conditions
- Edge cases
- Dimensional consistency
- Limiting cases

Do not silently change a physics assumption to make a visualization look better.

For projectile motion, use the equations and assumptions documented in `docs/PROJECT_CONTEXT.md`.

Java trigonometric functions use radians. Convert degrees to radians before calling `Math.sin()` or `Math.cos()`.

Use SI units internally.

If screen y increases downward, map physical positive y upward correctly.

Playback speed must not change the physical trajectory or calculated results.

## 7. Development Workflow

For each bounded task:

1. Inspect relevant files.
2. Understand the current behavior.
3. State the task objective.
4. Identify relevant constraints.
5. Implement the smallest reasonable change.
6. Add or update tests.
7. Run the relevant tests.
8. Run the build when appropriate.
9. Review the resulting code.
10. Report what changed and what remains.

Do not implement the entire platform in one task.

Do not introduce dependencies without explaining why they are needed.

Do not claim that tests or builds passed unless they were actually executed.

## 8. Testing

Tests are part of the implementation, not an optional final step.

For physics calculations, compare results against analytical values wherever possible.

Include meaningful edge cases.

A test must verify actual behavior. Avoid tests that merely instantiate a class or confirm that a method does not throw an exception.

Report:

- Tests added
- Tests executed
- Actual results
- Tests not executed
- Remaining known issues

## 9. Agent Communication

When a task is ambiguous:

- If the decision is important or difficult to reverse, ask before proceeding.
- If the decision is small and reversible, choose a minimal reasonable solution and document the assumption.

Do not silently make large architectural decisions.

Do not ask the student to repeat requirements already documented here.

Do not falsely present proposals as finalized decisions.

Distinguish between:

- Confirmed requirement
- Confirmed decision
- Proposed architecture
- Current implementation
- Future possibility
- Unresolved question

## 10. Scope Control

The initial feature is the Projectile Motion Simulator.

Do not implement the following unless explicitly requested:

- AI question interpretation
- AI Copilot
- Database persistence
- User accounts
- Collaboration
- All planned physics chapters
- A universal physics solver
- A complete Learning Execution Engine
- A general-purpose model composer

Keep the current milestone visible.

## 11. Code Quality

Prefer:

- Clear names
- Small, cohesive classes
- Understandable methods
- Explicit assumptions
- Appropriate error handling
- Testable calculations
- Minimal dependencies
- Simple architecture

Avoid:

- Giant classes
- Unnecessary design patterns
- Premature generalization
- Duplicated physics calculations
- Hidden global state
- Hardcoded UI values that should come from the model
- Unexplained magic numbers
- Unnecessary frameworks

## 12. Completion Report

At the end of each task, report:

1. Summary of changes
2. Files created or modified
3. Important design decisions
4. Physics or algorithmic behavior implemented
5. Tests executed and their actual results
6. Build status
7. Known issues
8. Recommended next task

Do not claim completion if the acceptance criteria have not been met.

## 13. Priority Order

Prioritize:

1. Correct physics
2. Working functionality
3. Clear user experience
4. Maintainable Java code
5. Automated testing
6. Appropriate architecture
7. Performance appropriate to the current scope
8. Visual polish

Build a serious product without losing sight of the student who is learning to build it.



