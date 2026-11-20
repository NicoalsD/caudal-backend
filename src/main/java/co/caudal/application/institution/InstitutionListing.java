package co.caudal.application.institution;

/**
 * Full row of an institution as the database holds it. It never leaves the server: the public
 * directory keeps only {@link PublicInstitution}.
 *
 * @param slug public identifier
 * @param name display name
 * @param municipality municipality
 * @param demo true for an institution with simulated data
 * @param active true while the public page is enabled
 */
public record InstitutionListing(
    String slug, String name, String municipality, boolean demo, boolean active) {}
