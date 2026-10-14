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
    Double longitude) {}
