package br.com.vagaviva.scheduling.application.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code vagaviva.scheduling}: antecedências (RN-09), prazo de confirmação (RN-11) e o motor de alocação. */
@Validated
@ConfigurationProperties("vagaviva.scheduling")
public record SchedulingProperties(@NotNull Duration regularAllocationMinLead, @NotNull Duration shortNoticeMinLead,
        @NotNull Duration confirmationDeadlineBeforeStart, @NotNull Duration allocationInterval,
        @Min(1) @Max(1000) int allocationBatchSize) {
}
