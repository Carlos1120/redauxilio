package co.redauxilio.publications;

import java.util.List;

/** Proyección pública sin correo, identificadores de cuenta ni datos internos del autor. */
public record MissingPersonSighting(
    long id,
    String observedAt,
    String location,
    double latitude,
    double longitude,
    String description,
    String createdAt,
    List<PublicationEvidenceService.Evidence> evidence) {}
