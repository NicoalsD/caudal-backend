package co.caudal.domain.incident;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.Map;

/**
 * The life of an incident (docs/Reglas-de-negocio.md, section 15): {@code REPORTED} goes to {@code
 * VERIFYING} or {@code DISMISSED}; {@code VERIFYING} to {@code CONFIRMED} or {@code DISMISSED};
 * {@code CONFIRMED} to {@code RESOLVED}. {@code RESOLVED} and {@code DISMISSED} are final. Each
 * state also says, in Spanish, what the person who reported it reads.
 *
 * @pattern P18 State
 */
public sealed interface IncidentState
    permits IncidentState.Reported,
        IncidentState.Verifying,
        IncidentState.Confirmed,
        IncidentState.Resolved,
        IncidentState.Dismissed {

  /**
   * The state of a status code.
   *
   * @param status the code stored in {@code ops.incidents.status}
   * @return the state
   * @throws IllegalArgumentException for an unknown code
   */
  static IncidentState of(String status) {
    return switch (status) {
      case "REPORTED" -> new Reported();
      case "VERIFYING" -> new Verifying();
      case "CONFIRMED" -> new Confirmed();
      case "RESOLVED" -> new Resolved();
      case "DISMISSED" -> new Dismissed();
      default -> throw new IllegalArgumentException("Unknown incident status");
    };
  }

  /**
   * The status code.
   *
   * @return the code
   */
  String code();

  /**
   * What the public reads about the report.
   *
   * @return the text in Spanish
   */
  String publicLabel();

  /**
   * Someone starts checking the report.
   *
   * @return the next state
   */
  default IncidentState verify() {
    throw refused("VERIFYING");
  }

  /**
   * The damage is real.
   *
   * @return the next state
   */
  default IncidentState confirm() {
    throw refused("CONFIRMED");
  }

  /**
   * The damage was fixed.
   *
   * @return the next state
   */
  default IncidentState resolve() {
    throw refused("RESOLVED");
  }

  /**
   * The report was not a damage.
   *
   * @return the next state
   */
  default IncidentState dismiss() {
    throw refused("DISMISSED");
  }

  private DomainException refused(String target) {
    return new DomainException(
        ErrorCode.INVALID_STATE_TRANSITION, Map.of("from", code(), "to", target));
  }

  /** Just reported. */
  record Reported() implements IncidentState {
    @Override
    public String code() {
      return "REPORTED";
    }

    @Override
    public String publicLabel() {
      return "Recibido";
    }

    @Override
    public IncidentState verify() {
      return new Verifying();
    }

    @Override
    public IncidentState dismiss() {
      return new Dismissed();
    }
  }

  /** Someone is checking it. */
  record Verifying() implements IncidentState {
    @Override
    public String code() {
      return "VERIFYING";
    }

    @Override
    public String publicLabel() {
      return "En revisión";
    }

    @Override
    public IncidentState confirm() {
      return new Confirmed();
    }

    @Override
    public IncidentState dismiss() {
      return new Dismissed();
    }
  }

  /** The damage is real and waits to be fixed. */
  record Confirmed() implements IncidentState {
    @Override
    public String code() {
      return "CONFIRMED";
    }

    @Override
    public String publicLabel() {
      return "Confirmado, en reparación";
    }

    @Override
    public IncidentState resolve() {
      return new Resolved();
    }
  }

  /** Fixed. Final. */
  record Resolved() implements IncidentState {
    @Override
    public String code() {
      return "RESOLVED";
    }

    @Override
    public String publicLabel() {
      return "Resuelto";
    }
  }

  /** Not a damage. Final. */
  record Dismissed() implements IncidentState {
    @Override
    public String code() {
      return "DISMISSED";
    }

    @Override
    public String publicLabel() {
      return "Cerrado sin daño confirmado";
    }
  }
}
