package co.redauxilio.publications;

/**
 * Campos enviados al publicar. title es el nombre del punto o el tramo de la vía; description no
 * admite datos personales sensibles. La ubicación siempre termina en coordenadas editables.
 */
public record CreatePublicationRequest(
    String category,
    String assistanceSubtype,
    String title,
    String description,
    String location,
    String operationalStatus,
    String impactType,
    Double latitude,
    Double longitude,
    String needType,
    String approximateQuantity,
    String declaredUrgency,
    String requesterReference,
    MissingPersonDetails missingPerson,
    String duplicateJustification) {
  /** Compatibilidad: los llamadores anteriores no declaran justificacion. */
  public CreatePublicationRequest(
      String category,
      String assistanceSubtype,
      String title,
      String description,
      String location,
      String operationalStatus,
      String impactType,
      Double latitude,
      Double longitude,
      String needType,
      String approximateQuantity,
      String declaredUrgency,
      String requesterReference,
      MissingPersonDetails missingPerson) {
    this(
        category,
        assistanceSubtype,
        title,
        description,
        location,
        operationalStatus,
        impactType,
        latitude,
        longitude,
        needType,
        approximateQuantity,
        declaredUrgency,
        requesterReference,
        missingPerson,
        null);
  }

  /** Compatibilidad RA-11: conserva el contrato de las otras categorías. */
  public CreatePublicationRequest(
      String category,
      String assistanceSubtype,
      String title,
      String description,
      String location,
      String operationalStatus,
      String impactType,
      Double latitude,
      Double longitude,
      String needType,
      String approximateQuantity,
      String declaredUrgency,
      String requesterReference) {
    this(
        category,
        assistanceSubtype,
        title,
        description,
        location,
        operationalStatus,
        impactType,
        latitude,
        longitude,
        needType,
        approximateQuantity,
        declaredUrgency,
        requesterReference,
        null);
  }

  /** Compatibilidad RA-03/04: las otras categorías no incorporan datos propios de ayuda. */
  public CreatePublicationRequest(
      String category,
      String assistanceSubtype,
      String title,
      String description,
      String location,
      String operationalStatus,
      String impactType,
      Double latitude,
      Double longitude) {
    this(
        category,
        assistanceSubtype,
        title,
        description,
        location,
        operationalStatus,
        impactType,
        latitude,
        longitude,
        null,
        null,
        null,
        null);
  }
}
