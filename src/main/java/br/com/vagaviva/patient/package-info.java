/**
 * Pacientes do SUS: identificação (CNS/CPF), contato e grupo prioritário. Dados pessoais
 * sensíveis — respostas mascaram CNS/CPF (RN-21), buscas usam POST (RN-22) e leituras são
 * auditadas (RF-05). Outros módulos consultam pela {@link br.com.vagaviva.patient.PatientApi}.
 */
@ApplicationModule(displayName = "Pacientes")
package br.com.vagaviva.patient;

import org.springframework.modulith.ApplicationModule;
