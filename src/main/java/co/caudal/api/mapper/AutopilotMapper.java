package co.caudal.api.mapper;

import co.caudal.api.dto.response.AssistantAnswerResponse;
import co.caudal.api.dto.response.AutopilotDecisionResponse;
import co.caudal.api.dto.response.AutopilotSettingsResponse;
import co.caudal.api.dto.response.LiveValveResponse;
import co.caudal.api.dto.response.NarrativeReportResponse;
import co.caudal.api.dto.response.ReportResponse;
import co.caudal.application.autopilot.DecisionView;
import co.caudal.application.autopilot.InsightsAnswer;
import co.caudal.application.autopilot.InsightsReport;
import co.caudal.application.autopilot.LiveValveView;
import co.caudal.application.autopilot.ReportView;
import co.caudal.application.autopilot.SettingsView;

/** Turns the views of the autopilot, the assistant and the reports into API responses. */
public final class AutopilotMapper {

  private AutopilotMapper() {}

  /**
   * The settings.
   *
   * @param view the settings
   * @return the response
   */
  public static AutopilotSettingsResponse toResponse(SettingsView view) {
    return new AutopilotSettingsResponse(
        view.mode(), view.operatorAbsenceMinutes(), view.reserveFloorPct(), view.updatedAt());
  }

  /**
   * A decision.
   *
   * @param view the decision
   * @return the response
   */
  public static AutopilotDecisionResponse toResponse(DecisionView view) {
    return new AutopilotDecisionResponse(
        view.id(),
        view.createdAt(),
        view.source(),
        view.model(),
        view.action(),
        view.valveId(),
        view.reason(),
        view.confidence(),
        view.status(),
        view.statusDetail(),
        view.decidedBy(),
        view.decidedAt(),
        view.valveCommandId());
  }

  /**
   * The answer of the assistant.
   *
   * @param answer the answer
   * @return the response
   */
  public static AssistantAnswerResponse toResponse(InsightsAnswer answer) {
    return new AssistantAnswerResponse(
        answer.source(), answer.model(), answer.answer(), answer.generatedAt());
  }

  /**
   * The numbers of a period.
   *
   * @param view the numbers
   * @return the response
   */
  public static ReportResponse toResponse(ReportView view) {
    return new ReportResponse(view.from(), view.to(), view.aggregates());
  }

  /**
   * The narrative report.
   *
   * @param report the report
   * @return the response
   */
  public static NarrativeReportResponse toResponse(InsightsReport report) {
    return new NarrativeReportResponse(
        report.source(),
        report.model(),
        report.generatedAt(),
        report.title(),
        report.summary(),
        report.findings(),
        report.recommendations(),
        report.markdown());
  }

  /**
   * The live state of a valve.
   *
   * @param view the valve
   * @return the response
   */
  public static LiveValveResponse toResponse(LiveValveView view) {
    LiveValveView.LastCommand last = view.lastCommand();
    return new LiveValveResponse(
        view.valveId(),
        view.sectorId(),
        view.state(),
        last == null
            ? null
            : new LiveValveResponse.LastCommand(
                last.id(),
                last.action(),
                last.status(),
                last.origin(),
                last.createdAt(),
                last.updatedAt()));
  }
}
