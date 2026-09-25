package br.com.vagaviva.regulation.fixtures;

import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.regulation.domain.Protocol;
import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.shared.domain.MunicipalityCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/** Encaminhamentos fictícios em cada estado da máquina (Test Data Builder). */
public final class ReferralFixture {

    public static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("America/Sao_Paulo"));
    public static final UUID UNIT = UUID.fromString("0199c0de-0000-7000-8000-000000000101");
    public static final UUID REQUESTER = UUID.fromString("0199c0de-0000-7000-8000-00000000aaaa");
    public static final UUID REGULATOR = UUID.fromString("0199c0de-0000-7000-8000-00000000bbbb");

    private ReferralFixture() {
    }

    public static Referral aPendingReferral() {
        return Referral.create(Protocol.of(2026, 123), UUID.randomUUID(), UUID.randomUUID(), UNIT, REQUESTER,
                "Dor torácica aos esforços há 3 meses.", "I20.9", true, MunicipalityCode.of("3550308"), CLOCK);
    }

    public static Referral aWaitingReferral() {
        Referral referral = aPendingReferral();
        referral.approve(RiskClass.YELLOW, false, REGULATOR, CLOCK);
        return referral;
    }

    public static Referral aScheduledReferral() {
        Referral referral = aWaitingReferral();
        referral.markScheduled(CLOCK);
        return referral;
    }

    public static Referral aReturnedReferral() {
        Referral referral = aPendingReferral();
        referral.returnForCorrection("Anexar ECG.", REGULATOR, CLOCK);
        return referral;
    }
}
