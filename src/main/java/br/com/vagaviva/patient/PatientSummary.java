package br.com.vagaviva.patient;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Visão mínima do paciente para outros módulos (fila, agenda, mensagens): sem CPF nem nome
 * completo, e CNS só mascarado (RN-21). {@code firstName} respeita o nome social quando houver.
 */
public record PatientSummary(UUID id, String firstName, String cnsMasked, LocalDate birthDate, String municipalityCode, String phone,
        ContactChannel preferredChannel, boolean whatsappOptIn, boolean priorityGroup, boolean active) {
}
