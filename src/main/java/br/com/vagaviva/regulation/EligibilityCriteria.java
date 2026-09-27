package br.com.vagaviva.regulation;

import java.util.Set;

/** Filtro de elegibilidade (RN-10): municípios atendidos pela unidade da vaga — vazio = todos. */
public record EligibilityCriteria(Set<String> serviceArea) {

    public EligibilityCriteria {
        serviceArea = serviceArea == null ? Set.of() : Set.copyOf(serviceArea);
    }

    public static EligibilityCriteria anyMunicipality() {
        return new EligibilityCriteria(Set.of());
    }
}
