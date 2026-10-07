package co.redauxilio.identity;

import java.time.Instant;

/** Datos internos de una cuenta; la contraseña nunca se devuelve en una respuesta HTTP. */
record CitizenAccount(
    long id,
    String email,
    String displayName,
    String passwordHash,
    int failedLoginAttempts,
    Instant lockedUntil,
    Instant createdAt) {}
