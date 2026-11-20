package co.caudal.application.port.out;

import co.caudal.application.autopilot.InsightsAnswer;
import co.caudal.application.autopilot.InsightsRecommendations;
import co.caudal.application.autopilot.InsightsReport;
import co.caudal.application.autopilot.InsightsSnapshot;
import co.caudal.application.autopilot.ReportRequest;

/**
 * The assistant as the autopilot uses it. It always answers: when the remote service cannot, the
 * answer comes from deterministic rules with source {@code RULES}. It never throws and it must not
 * be called inside a transaction, because it may wait for the network.
 */
public interface InsightsPort {

  /**
   * Recommends what to do with the valves.
   *
   * @param snapshot the state of the aqueduct
   * @return the recommendations
   */
  InsightsRecommendations recommend(InsightsSnapshot snapshot);

  /**
   * Writes the narrative report of a period.
   *
   * @param request the aggregates of the period
   * @return the report
   */
  InsightsReport report(ReportRequest request);

  /**
   * Answers a question about the state of the aqueduct.
   *
   * @param question the question
   * @param snapshot the state of the aqueduct
   * @return the answer
   */
  InsightsAnswer ask(String question, InsightsSnapshot snapshot);
}
