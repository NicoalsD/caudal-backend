package co.caudal.api.mapper;

import co.caudal.api.dto.response.ProposalResponse;
import co.caudal.api.dto.response.ProposalSummaryResponse;
import co.caudal.application.schedule.ProposalDetail;
import co.caudal.application.schedule.ProposalSummary;

/** Maps proposals of shifts to response DTOs. */
public final class ScheduleMapper {

  private ScheduleMapper() {}

  /**
   * Maps a full proposal.
   *
   * @param detail the proposal
   * @return the response
   */
  public static ProposalResponse toResponse(ProposalDetail detail) {
    ProposalSummary s = detail.summary();
    return new ProposalResponse(
        s.id(),
        s.serviceDate(),
        s.status(),
        s.ruleSetId(),
        s.tankLevelSnapshot(),
        s.tankBand(),
        s.availableHours(),
        s.forecastRunId(),
        s.strategyCode(),
        s.generatedAt(),
        s.lockVersion(),
        detail.items().stream()
            .map(
                i ->
                    new ProposalResponse.Item(
                        i.id(),
                        i.sectorId(),
                        i.sequence(),
                        i.startAt(),
                        i.endAt(),
                        i.origin(),
                        i.reasons().stream()
                            .map(r -> new ProposalResponse.Reason(r.code(), r.params(), r.text()))
                            .toList()))
            .toList(),
        detail.unserved().stream()
            .map(u -> new ProposalResponse.Unserved(u.sectorId(), u.reason()))
            .toList(),
        detail.decisions().stream()
            .map(
                d ->
                    new ProposalResponse.Decision(
                        d.decision(), d.reason(), d.decidedBy(), d.decidedAt()))
            .toList(),
        detail.snapshotTaken());
  }

  /**
   * Maps a proposal without its shifts.
   *
   * @param s the proposal
   * @return the response
   */
  public static ProposalSummaryResponse toResponse(ProposalSummary s) {
    return new ProposalSummaryResponse(
        s.id(),
        s.serviceDate(),
        s.status(),
        s.ruleSetId(),
        s.tankLevelSnapshot(),
        s.tankBand(),
        s.availableHours(),
        s.forecastRunId(),
        s.strategyCode(),
        s.generatedAt(),
        s.lockVersion());
  }
}
