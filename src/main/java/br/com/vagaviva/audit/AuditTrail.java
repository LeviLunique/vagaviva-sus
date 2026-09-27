package br.com.vagaviva.audit;

/**
 * API pública do módulo de auditoria. Grava de forma síncrona, na transação de quem chama:
 * se a operação auditada for desfeita, o registro também é (e vice-versa).
 */
public interface AuditTrail {

    void record(AuditEntry entry);
}
