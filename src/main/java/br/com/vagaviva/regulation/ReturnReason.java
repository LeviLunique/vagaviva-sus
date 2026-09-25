package br.com.vagaviva.regulation;

/** Motivo de volta à fila — sempre preservando a data de entrada original (RN-06). */
public enum ReturnReason {
    /** O paciente não confirmou no prazo (RN-13): conta uma não confirmação. */
    UNCONFIRMED,
    /** A unidade cancelou a vaga (motivo não imputável ao paciente). */
    UNIT_CANCELLED
}
