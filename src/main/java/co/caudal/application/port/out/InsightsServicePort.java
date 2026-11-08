package co.caudal.application.port.out;

import co.caudal.application.autopilot.InsightsAnswer;
import co.caudal.application.autopilot.InsightsCallResult;
import co.caudal.application.autopilot.InsightsRecommendations;
import co.caudal.application.autopilot.InsightsReport;
import co.caudal.application.autopilot.InsightsSnapshot;
import co.caudal.application.autopilot.ReportRequest;

/**
 * The remote insights service of {@code caudal-ia} (docs/Contrato-IA.md, insights section). It
 * reports a failure instead of throwing, so the proxy can decide.
 */
public interface InsightsServicePort {

  /**
   * Asks for recommendations.
   *
   * @param snapshot the state of the aqueduct
   * @return the answer or the failure
   */
  InsightsCallResult<InsightsRecommendations> recommend(InsightsSnapshot snapshot);

  /**
   * Asks for a narrative report.
   *
   * @param request the aggregates of the period
   * @return the answer or the failure
   */
  InsightsCallResult<InsightsReport> report(ReportRequest request);

  /**
   * Asks a question about the state of the aqueduct.
   *
   * @param question the question
   * @param snapshot the state of the aqueduct
   * @return the answer or the failure
   */
  InsightsCallResult<InsightsAnswer> ask(String question, InsightsSnapshot snapshot);
}
