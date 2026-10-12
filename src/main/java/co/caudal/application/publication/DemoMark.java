package co.caudal.application.publication;

import co.caudal.shared.text.MessageTemplate;
import java.util.Map;

/**
 * The mark every public output of a demo aqueduct carries (docs/Datos-simulados.md): the page, the
 * WhatsApp message and the poster say that the data are simulated.
 */
public final class DemoMark {

  /** The short mark. */
  public static final String BANNER = "Datos simulados";

  private static final MessageTemplate NOTE =
      MessageTemplate.parse(
          "{marca}: este acueducto es de demostración y los turnos no son reales.");

  private DemoMark() {}

  /**
   * The banner of an aqueduct.
   *
   * @param demo true if its data are simulated
   * @return the banner, or null for a real aqueduct
   */
  public static String banner(boolean demo) {
    return demo ? BANNER : null;
  }

  /**
   * The sentence that explains the mark.
   *
   * @param demo true if its data are simulated
   * @return the sentence, or null for a real aqueduct
   */
  public static String note(boolean demo) {
    return demo ? NOTE.render(Map.of("marca", BANNER)) : null;
  }
}
