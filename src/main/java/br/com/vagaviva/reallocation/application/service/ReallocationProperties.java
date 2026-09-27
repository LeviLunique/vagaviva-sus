package br.com.vagaviva.reallocation.application.service;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code vagaviva.reallocation}: tamanho do lote, rodadas e prazos das ofertas (RN-16/RN-17). */
@Validated
@ConfigurationProperties("vagaviva.reallocation")
public record ReallocationProperties(@Min(1) int offerBatchSize, @Min(1) int offerMaxRounds,
        @NotNull Duration offerResponseWindow, @NotNull Duration shortNoticeMinLead) {
}
