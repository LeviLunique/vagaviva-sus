package br.com.vagaviva.regulation.application.service;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code vagaviva.regulation}: intervalo do snapshot (RN-07), janela da vazão (RN-08) e limite da RN-13. */
@Validated
@ConfigurationProperties("vagaviva.regulation")
public record RegulationProperties(@NotNull Duration queueSnapshotInterval, @NotNull Duration throughputWindow,
        @Min(1) int maxMissedConfirmations) {
}
