# PHYSORA — Project Goal

## 1. Primary Objective

The primary objective is to develop a polished, interactive, and fully functional physics learning environment that demonstrates strong software engineering, thoughtful product design, and meaningful educational functionality.

This project is being developed as a showcase for academic evaluation. The teacher will evaluate it based on:

- Features and their usefulness
- Completeness and reliability of implementation
- Quality of UI and UX
- Visual design and originality
- Physics correctness
- Software architecture and code quality
- Overall presentation and demonstration experience

The project must feel deliberately designed, not like a collection of generated screens or disconnected features.

## 2. Product Philosophy

The application should allow students to construct, understand, execute, inspect, and modify physics models. It should combine an interactive physics laboratory, a visual modeling environment, an educational simulation platform, and a guided conceptual learning tool.

Students should be able to inspect the model, explore its behavior, and learn from results rather than only adjust sliders and watch an animation.

## 3. Quality Standards

### Functionality

Every implemented feature must work correctly.

- Controls must produce meaningful results.
- Navigation must lead to real, implemented destinations.
- Simulation behavior must be physically correct.
- Displayed values must correspond to the actual model.
- Playback, reset, and interaction states must behave consistently.
- Invalid inputs and edge cases must be handled.
- Do not include dead buttons, misleading functionality, or fake behavior presented as complete.

### UI and UX

The interface must be visually polished, cohesive, and professionally designed. Avoid generic dashboard layouts, excessive metric cards, arbitrary gradients, decorative effects, visual clutter, oversized empty panels, placeholder navigation, and inconsistent spacing or typography.

Prioritize:

- A distinct product identity
- Intentional visual hierarchy
- Consistent typography and spacing
- Clear navigation and meaningful information grouping
- Purposeful color and a visually engaging simulation workspace
- Intuitive interactions and responsive behavior at supported desktop sizes
- A cohesive design language across the application

The interface should feel purposeful and suited to an educational physics product.

### Physics and Engineering

Physics correctness is non-negotiable.

- Keep physics calculations separate from the UI.
- Use the tested physics module as the source of truth.
- Do not duplicate equations in UI components.
- Keep rendering, simulation time, physics calculations, and educational explanations conceptually separate.
- Validate calculations through automated tests.
- Do not sacrifice correctness for visual appearance.

## 4. Product Scope

### Normal Bench

The primary environment for constructing and exploring physics models. It should eventually include chapter navigation, a model workspace, Logic and Simulation views, interactive model parameters, visual execution, and educational inspection tools.

### AI Bench

A future AI-assisted workspace for Build My Model, Guide Me, Judge My Work, and Modify My Model. Implement AI only when it can be meaningfully integrated with the model architecture; do not use it as a shortcut for core physics functionality.

### Physics Curriculum

The planned curriculum is documented in `docs/PROJECT_CONTEXT.md`. Projectile Motion is the first working chapter and a foundation for the architecture, not the complete product. Future chapters should reuse architecture where appropriate while keeping their own correct physics models and solvers. Do not present unimplemented chapters as completed features.

## 5. Development Strategy

Development proceeds through working, demonstrable milestones. Each milestone must have a clear purpose, produce a usable result, preserve existing functionality, be tested, receive UI/UX review, and integrate with the larger application.

Before a major UI change, establish the user workflow, layout and navigation, visual hierarchy, component responsibilities, and expected behavior.

## 6. Definition of Done

A feature is complete when:

- It builds successfully.
- Its core behavior works and important edge cases are handled.
- Automated tests pass where applicable.
- It has been exercised in the running application.
- Its UI has been visually reviewed.
- It integrates coherently with the product and is ready to demonstrate.

Report build verification, test results, runtime verification, visual review, and remaining limitations separately. Do not claim visual review unless the application was launched and inspected.

## 7. Product Identity Direction

**PHYSORA — Physics, in motion** is the confirmed project name and current visual identity.

The intended direction is an experimental, editorial, interactive physics laboratory: expressive typography and composition, precise scientific diagrams, trajectory and vector graphics, deliberate negative space, and purposeful accent colors. The chapter introduction may be expressive; the working simulator should remain clear and precise.

Treat the supplied design references as inspiration for art direction and interaction principles. Translate useful ideas into native JavaFX patterns; do not copy websites or introduce a web stack. Every visual element and animation must support orientation, understanding, or feedback.

## 8. Final Vision

The product should demonstrate correct physics, meaningful functionality, strong architecture, excellent UI/UX, a cohesive identity, reliable interaction, and a polished demonstration experience.

**Build a thoughtful physics learning product that impresses because it works exceptionally well, not because it only looks impressive in a screenshot.**
