package br.com.vagaviva.scheduling;

import br.com.vagaviva.catalog.HealthUnitType;
import br.com.vagaviva.catalog.application.port.out.HealthUnitRepository;
import br.com.vagaviva.catalog.domain.Cnes;
import br.com.vagaviva.catalog.domain.HealthUnit;
import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.regulation.application.port.out.ReferralRepository;
import br.com.vagaviva.regulation.domain.Protocol;
import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.shared.domain.MunicipalityCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** Dados de apoio dos ITs da agenda: unidade executante e encaminhamentos na fila, via portas públicas. */
@Component
public class SchedulingTestData {

    private static final AtomicLong PROTOCOLS = new AtomicLong(3_000_000 + System.nanoTime() % 1_000_000);

    private final HealthUnitRepository units;
    private final ReferralRepository referrals;
    private final Clock clock;

    SchedulingTestData(HealthUnitRepository units, ReferralRepository referrals, Clock clock) {
        this.units = units;
        this.referrals = referrals;
        this.clock = clock;
    }

    public UUID specializedUnit(Set<String> serviceArea) {
        String cnes = String.valueOf(ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999));
        return units.save(HealthUnit.register(Cnes.of(cnes), "AME Teste " + cnes, HealthUnitType.SPECIALIZED,
                MunicipalityCode.of("3550308"), "São Paulo", "Rua Teste, 1",
                serviceArea.stream().map(MunicipalityCode::of).collect(java.util.stream.Collectors.toSet()), clock)).id();
    }

    public Referral waitingReferral(UUID specialty, RiskClass risk, Instant queueEntry) {
        Clock entryClock = Clock.fixed(queueEntry, ZoneOffset.UTC);
        Referral referral = Referral.create(Protocol.of(2097, PROTOCOLS.incrementAndGet()), UUID.randomUUID(), specialty,
                UUID.randomUUID(), UUID.randomUUID(), "Justificativa", null, false, MunicipalityCode.of("3550308"),
                entryClock);
        referral.approve(risk, false, UUID.randomUUID(), entryClock);
        return referrals.save(referral);
    }
}
