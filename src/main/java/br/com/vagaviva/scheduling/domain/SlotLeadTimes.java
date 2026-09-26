package br.com.vagaviva.scheduling.domain;

import java.time.Duration;

/** RN-09: antecedência mínima da alocação regular (5 dias) e do encaixe (2 h). */
public record SlotLeadTimes(Duration regularAllocation, Duration shortNotice) {

    public SlotLeadTimes {
        if (shortNotice.compareTo(regularAllocation) >= 0) {
            throw new IllegalArgumentException("A antecedência do encaixe deve ser menor que a da alocação regular");
        }
    }
}
