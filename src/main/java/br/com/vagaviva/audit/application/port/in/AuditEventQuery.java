package br.com.vagaviva.audit.application.port.in;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Filtros opcionais da consulta à trilha; {@code from} inclusivo e {@code to} exclusivo. */
public record AuditEventQuery(
        @Nullable String resourceType,
        @Nullable String resourceId,
        @Nullable UUID actorId,
        @Nullable Instant from,
        @Nullable Instant to) {
}
