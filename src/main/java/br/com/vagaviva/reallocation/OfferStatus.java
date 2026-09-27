package br.com.vagaviva.reallocation;

/** Estados da oferta de encaixe (SPEC §5). */
public enum OfferStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    EXPIRED,
    /** Outro paciente aceitou a mesma vaga antes. */
    SUPERSEDED
}
