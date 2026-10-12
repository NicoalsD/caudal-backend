package co.caudal.application.port.out;

import co.caudal.application.forecast.Forecaster;

/**
 * The outside forecaster: the remote service behind its proxy, with the simple estimate when the
 * service cannot answer.
 */
public interface ForecasterPort extends Forecaster {}
