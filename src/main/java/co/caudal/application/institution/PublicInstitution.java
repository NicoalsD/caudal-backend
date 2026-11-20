package co.caudal.application.institution;

/**
 * An institution as the public directory shows it: nothing else.
 *
 * @param slug public identifier, used to choose the institution at login
 * @param name display name
 */
public record PublicInstitution(String slug, String name) {}
