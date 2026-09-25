package br.com.vagaviva.audit;

/** Resultado da ação auditada. */
public enum AuditOutcome {
    SUCCESS,
    /** Negada por regra de acesso (ex.: login inválido ou bloqueado). */
    DENIED,
    FAILURE
}
