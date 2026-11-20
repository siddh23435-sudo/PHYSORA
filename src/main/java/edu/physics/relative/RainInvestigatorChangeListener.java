package edu.physics.relative;

/**
 * Listener interface for state changes in {@link RainInvestigatorSessionModel}.
 *
 * <p>Implemented without any JavaFX dependencies to allow headless execution and testing.</p>
 */
@FunctionalInterface
public interface RainInvestigatorChangeListener {

    /**
     * Invoked when the session model state (parameters, stage, preset, or evaluation) changes.
     *
     * @param model The updated session model
     */
    void onSessionChanged(RainInvestigatorSessionModel model);
}

