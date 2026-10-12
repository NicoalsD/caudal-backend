package co.caudal.domain.minutes;

import java.util.Locale;

/** The four sections of the minutes, by how sure the fact is (docs/Reglas-de-negocio.md, 16.1). */
public enum MinutesSection {
  /** What someone saw or measured. */
  OBSERVED("Observado"),
  /** What was calculated. */
  ESTIMATED("Estimado"),
  /** What is suspected. */
  INFERRED("Inferido"),
  /** What was checked in the field. */
  CONFIRMED("Confirmado");

  private final String title;

  MinutesSection(String title) {
    this.title = title;
  }

  /**
   * The title in Spanish.
   *
   * @return the title
   */
  public String title() {
    return title;
  }

  /**
   * The key in the JSON content.
   *
   * @return for example "observed"
   */
  public String key() {
    return name().toLowerCase(Locale.ROOT);
  }
}
