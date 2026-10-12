package co.caudal.application.aqueduct;

import java.util.UUID;

/**
 * An aqueduct as the use cases need it.
 *
 * @param id the aqueduct
 * @param slug the public identifier
 * @param name the name
 * @param timezone the IANA time zone
 * @param demo true if it only holds simulated data
 * @param publicPageEnabled true if the public schedule page is on
 */
public record AqueductRef(
    UUID id, String slug, String name, String timezone, boolean demo, boolean publicPageEnabled) {}
