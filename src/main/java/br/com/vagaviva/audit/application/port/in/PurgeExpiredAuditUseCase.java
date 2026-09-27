package br.com.vagaviva.audit.application.port.in;

/** Retenção da trilha (SPEC §10): eventos mais antigos que o prazo configurado são apagados. */
public interface PurgeExpiredAuditUseCase {

    /** @return quantos eventos foram apagados */
    int purge();
}
