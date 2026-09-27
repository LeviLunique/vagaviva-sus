package br.com.vagaviva.reallocation;

/** Estados da oferta de encaixe. */
public enum OfferStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    EXPIRED,
    /** Outro paciente aceitou a mesma vaga antes. */
    SUPERSEDED
}
