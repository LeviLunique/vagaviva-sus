package br.com.vagaviva.insights.application.service;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code vagaviva.insights}: custo estimado por mensagem enviada, em reais, por canal. */
@Validated
@ConfigurationProperties("vagaviva.insights")
public record InsightsProperties(@NotNull Map<String, BigDecimal> messagingCostBrl) {
}
