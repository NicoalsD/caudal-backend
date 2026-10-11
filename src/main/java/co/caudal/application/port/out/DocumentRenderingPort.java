package co.caudal.application.port.out;

import co.caudal.application.publication.PosterData;

/** Renders the documents the system prints. */
public interface DocumentRenderingPort {

  /**
   * The PDF poster of a schedule. The same data always give the same bytes.
   *
   * @param data what it says
   * @return the PDF
   */
  byte[] poster(PosterData data);
}
