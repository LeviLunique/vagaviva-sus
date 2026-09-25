package br.com.vagaviva.scheduling;

/** Como a vaga chegou ao paciente. */
public enum AppointmentOrigin {
    /** Primeira alocação da vaga pela fila. */
    REGULAR,
    /** Vaga liberada por outro paciente e realocada pela fila (release_count > 0). */
    REALLOCATED,
    /** Encaixe de última hora aceito pelo paciente (F6). */
    SHORT_NOTICE_OFFER
}
