package br.com.vagaviva.engagement.domain;

/**
 * RN-20: rótulo genérico do atendimento — nunca o nome da especialidade (psiquiatria ou
 * infectologia revelariam o diagnóstico). Carrega a concordância de gênero do texto.
 */
public enum CareLabel {
    CONSULTATION("consulta", true),
    EXAM("exame", false);

    private final String noun;
    private final boolean feminine;

    CareLabel(String noun, boolean feminine) {
        this.noun = noun;
        this.feminine = feminine;
    }

    public String noun() {
        return noun;
    }

    /** "sua consulta" / "seu exame". */
    public String withPossessive() {
        return (feminine ? "sua " : "seu ") + noun;
    }

    /** "agendada" / "agendado", "cancelada" / "cancelado". */
    public String agree(String stem) {
        return stem + (feminine ? "a" : "o");
    }
}
