package co.caudal.application.network;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A tank and the range of its painted gauge.
 *
 * @param id the tank
 * @param name the name
 * @param gaugeMin minimum of the gauge
 * @param gaugeMax maximum of the gauge
 * @param gaugeStep reading precision
 * @param capacityLiters capacity, or null
 * @param active true if in use
 */
public record TankView(
    UUID id,
    String name,
    BigDecimal gaugeMin,
    BigDecimal gaugeMax,
    BigDecimal gaugeStep,
    Integer capacityLiters,
    boolean active) {}
