package br.com.vagaviva.shared.security;

/** Papéis dos profissionais (RF-02). No token, viram as authorities {@code ROLE_<papel>}. */
public enum Role {

    /** Administra profissionais e consulta a auditoria. */
    ADMIN,
    /** Profissional da UBS que encaminha pacientes — pertence a uma unidade. */
    REQUESTER,
    /** Médico regulador da central de regulação. */
    REGULATOR,
    /** Agendador da unidade executante (oferta de vagas) — pertence a uma unidade. */
    SCHEDULER,
    /** Gestor da secretaria de saúde (indicadores). */
    MANAGER;

    /** {@code REQUESTER} e {@code SCHEDULER} atuam sempre em nome de uma unidade de saúde (RF-02 CA3). */
    public boolean requiresHealthUnit() {
        return this == REQUESTER || this == SCHEDULER;
    }
}
