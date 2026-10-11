package co.caudal.domain.schedule;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.Map;

/**
 * Life of a proposal of shifts (docs/Reglas-de-negocio.md, section 12). It is born pending review;
 * only the Board approves, changes or rejects it; only an approved one is published; only a
 * published one is closed with the day. Any other move answers {@code INVALID_STATE_TRANSITION}.
 *
 * @pattern P18 State
 */
public sealed interface ProposalState
    permits ProposalState.Draft,
        ProposalState.PendingReview,
        ProposalState.Approved,
        ProposalState.ApprovedWithChanges,
        ProposalState.Rejected,
        ProposalState.Published,
        ProposalState.Closed {

  /**
   * Restores the state from {@code ops.schedule_proposals.status}.
   *
   * @param status the stored status
   * @return the state
   * @throws IllegalArgumentException for an unknown status
   */
  static ProposalState of(String status) {
    return switch (status) {
      case "DRAFT" -> new Draft();
      case "PENDING_REVIEW" -> new PendingReview();
      case "APPROVED" -> new Approved();
      case "APPROVED_WITH_CHANGES" -> new ApprovedWithChanges();
      case "REJECTED" -> new Rejected();
      case "PUBLISHED" -> new Published();
      case "CLOSED" -> new Closed();
      default -> throw new IllegalArgumentException("Unknown proposal status");
    };
  }

  /**
   * The stored code.
   *
   * @return the status
   */
  String code();

  /**
   * The Board approves it as it is.
   *
   * @return the new state
   */
  default ProposalState approve() {
    throw refused("APPROVED");
  }

  /**
   * The Board approves it with changes.
   *
   * @return the new state
   */
  default ProposalState modify() {
    throw refused("APPROVED_WITH_CHANGES");
  }

  /**
   * The Board rejects it.
   *
   * @return the new state
   */
  default ProposalState reject() {
    throw refused("REJECTED");
  }

  /**
   * The approved schedule is published.
   *
   * @return the new state
   */
  default ProposalState publish() {
    throw refused("PUBLISHED");
  }

  /**
   * The day of service is closed.
   *
   * @return the new state
   */
  default ProposalState close() {
    throw refused("CLOSED");
  }

  private DomainException refused(String target) {
    return new DomainException(
        ErrorCode.INVALID_STATE_TRANSITION, Map.of("from", code(), "to", target));
  }

  /** Being built; never stored outside the transaction that builds it. */
  record Draft() implements ProposalState {
    @Override
    public String code() {
      return "DRAFT";
    }
  }

  /** Waiting for the Board. */
  record PendingReview() implements ProposalState {
    @Override
    public String code() {
      return "PENDING_REVIEW";
    }

    @Override
    public ProposalState approve() {
      return new Approved();
    }

    @Override
    public ProposalState modify() {
      return new ApprovedWithChanges();
    }

    @Override
    public ProposalState reject() {
      return new Rejected();
    }
  }

  /** Approved as proposed. */
  record Approved() implements ProposalState {
    @Override
    public String code() {
      return "APPROVED";
    }

    @Override
    public ProposalState publish() {
      return new Published();
    }
  }

  /** Approved after the Board changed some shifts. */
  record ApprovedWithChanges() implements ProposalState {
    @Override
    public String code() {
      return "APPROVED_WITH_CHANGES";
    }

    @Override
    public ProposalState publish() {
      return new Published();
    }
  }

  /** Rejected; a new proposal is made for the same day. */
  record Rejected() implements ProposalState {
    @Override
    public String code() {
      return "REJECTED";
    }
  }

  /** Published to the community. */
  record Published() implements ProposalState {
    @Override
    public String code() {
      return "PUBLISHED";
    }

    @Override
    public ProposalState close() {
      return new Closed();
    }
  }

  /** The day ended and was closed. */
  record Closed() implements ProposalState {
    @Override
    public String code() {
      return "CLOSED";
    }
  }
}
