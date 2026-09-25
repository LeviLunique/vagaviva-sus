package br.com.vagaviva.regulation;

/** Classe de risco definida pelo regulador; {@code rank} menor = atendido antes (RN-06). */
public enum RiskClass {
    RED(1),
    YELLOW(2),
    GREEN(3),
    BLUE(4);

    private final int rank;

    RiskClass(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
