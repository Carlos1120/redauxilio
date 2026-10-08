package co.redauxilio.identity;

/** Datos de cuenta que la sesión puede mostrar al titular autenticado. */
public record CitizenProfile(String displayName, String email) {}
