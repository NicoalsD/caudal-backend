package co.caudal.application.autopilot;

import co.caudal.application.event.DomainEventSubscriber;
import co.caudal.application.port.out.AutopilotDecisionPort;
import co.caudal.domain.device.ValveCommandState;
import co.caudal.domain.event.ValveCommandConfirmed;

/**
 * When the device confirms (ACKED) the command that a person accepted, the decision passes from
 * {@code ACCEPTED} to {@code EXECUTED} (HU-41).
 *
 * @pattern P17 Observer
 */
public class DecisionExecutionObserver implements DomainEventSubscriber<ValveCommandConfirmed> {

  private final AutopilotDecisionPort decisions;

  /**
   * Creates the observer.
   *
   * @param decisions the decisions
   */
  public DecisionExecutionObserver(AutopilotDecisionPort decisions) {
    this.decisions = decisions;
  }

  @Override
  public Class<ValveCommandConfirmed> eventType() {
    return ValveCommandConfirmed.class;
  }

  @Override
  public void on(ValveCommandConfirmed event) {
    if (ValveCommandState.ACKED.equals(event.result())) {
      decisions.markExecutedByCommand(event.commandId());
    }
  }
}
