package br.com.vagaviva.regulation.application.port.in;

import br.com.vagaviva.regulation.ReferralStatus;
import br.com.vagaviva.regulation.RiskClass;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Transparência ao cidadão (RF-17) e à sociedade (RF-18) — sem login e sem dado pessoal na resposta. */
public interface PublicTransparencyUseCase {

    /**
     * @throws br.com.vagaviva.shared.domain.NotFoundException genérica se o protocolo não existir
     *     ou a data de nascimento não conferir (não revela qual dos dois)
     */
    PublicQueuePosition position(String protocol, LocalDate birthDate, @Nullable String clientIp);

    List<PublicSpecialtyStats> stats();

    record PublicQueuePosition(String protocol, ReferralStatus status, String specialty, @Nullable RiskClass riskClass,
            @Nullable Integer position, @Nullable Integer totalInQueue, @Nullable Integer estimatedWaitDays,
            @Nullable Instant snapshotAt) {
    }

    record PublicSpecialtyStats(UUID specialtyId, String specialty, Map<RiskClass, Integer> waitingByRisk,
            int totalWaiting, @Nullable BigDecimal avgWaitDays, @Nullable BigDecimal throughputPerDay,
            Instant snapshotAt) {
    }
}
