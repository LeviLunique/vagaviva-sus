package br.com.vagaviva.catalog;

import java.util.UUID;

/** Visão pública de uma especialidade; {@code sensitive} proíbe o nome nas mensagens (RN-20). */
public record SpecialtySummary(UUID id, String code, String name, SpecialtyType type, boolean sensitive,
        boolean active) {
}
