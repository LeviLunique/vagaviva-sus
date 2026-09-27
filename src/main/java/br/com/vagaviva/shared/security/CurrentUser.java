package br.com.vagaviva.shared.security;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Profissional autenticado na requisição, extraído do JWT (sem ida ao banco). */
public record CurrentUser(UUID id, Role role, @Nullable UUID unitId, String name) {
}
