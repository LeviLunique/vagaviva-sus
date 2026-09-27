package br.com.vagaviva.catalog;

/** Tipo de unidade de saúde (RF-06). */
public enum HealthUnitType {
    /** UBS: encaminha pacientes (REQUESTER). */
    PRIMARY_CARE,
    /** Unidade executante: oferta consultas e exames (SCHEDULER). */
    SPECIALIZED
}
