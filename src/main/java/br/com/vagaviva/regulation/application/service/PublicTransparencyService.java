package br.com.vagaviva.regulation.application.service;

import br.com.vagaviva.audit.AuditOutcome;
import br.com.vagaviva.catalog.CatalogApi;
import br.com.vagaviva.catalog.SpecialtySummary;
import br.com.vagaviva.catalog.SpecialtyType;
import br.com.vagaviva.patient.PatientApi;
import br.com.vagaviva.patient.PatientSummary;
import br.com.vagaviva.regulation.ReferralStatus;
import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.regulation.application.port.in.PublicTransparencyUseCase;
import br.com.vagaviva.regulation.application.port.out.QueueSnapshotRepository;
import br.com.vagaviva.regulation.application.port.out.QueueSnapshotRepository.QueuePositionSnapshot;
import br.com.vagaviva.regulation.application.port.out.QueueSnapshotRepository.SpecialtyQueueStats;
import br.com.vagaviva.regulation.application.port.out.ReferralRepository;
import br.com.vagaviva.regulation.domain.Protocol;
import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.shared.domain.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-17/RF-18. A consulta do cidadão exige protocolo + data de nascimento e responde o mesmo 404
 * para qualquer divergência; especialidade sensível aparece só pelo rótulo genérico (RN-20).
 */
@Service
class PublicTransparencyService implements PublicTransparencyUseCase {

    private final ReferralRepository referrals;
    private final QueueSnapshotRepository snapshots;
    private final PatientApi patients;
    private final CatalogApi catalog;
    private final RegulationAudit audit;

    PublicTransparencyService(ReferralRepository referrals, QueueSnapshotRepository snapshots, PatientApi patients,
            CatalogApi catalog, RegulationAudit audit) {
        this.referrals = referrals;
        this.snapshots = snapshots;
        this.patients = patients;
        this.catalog = catalog;
        this.audit = audit;
    }

    @Override
    @Transactional(noRollbackFor = NotFoundException.class)
    public PublicQueuePosition position(String protocol, LocalDate birthDate, @Nullable String clientIp) {
        Optional<Referral> found = Protocol.looksValid(protocol)
                ? referrals.findByProtocol(protocol.strip().toUpperCase(Locale.ROOT))
                : Optional.empty();
        Optional<Referral> matching = found.filter(referral -> patients.findSummary(referral.patientId())
                .map(PatientSummary::birthDate).filter(birthDate::equals).isPresent());
        if (matching.isEmpty()) {
            audit.record(null, RegulationAudit.PUBLIC_POSITION_READ, RegulationAudit.REFERRAL, null,
                    AuditOutcome.FAILURE, clientIp, Map.of());
            throw RegulationErrors.publicPositionNotFound();
        }
        Referral referral = matching.get();
        audit.record(null, RegulationAudit.PUBLIC_POSITION_READ, RegulationAudit.REFERRAL, referral.id(),
                AuditOutcome.SUCCESS, clientIp, Map.of());
        String specialty = catalog.findSpecialty(referral.specialtyId()).map(PublicTransparencyService::label)
                .orElse("Especialidade");
        Optional<QueuePositionSnapshot> snapshot = referral.status() == ReferralStatus.WAITING
                ? snapshots.findPosition(referral.id())
                : Optional.empty();
        if (snapshot.isEmpty()) {
            return new PublicQueuePosition(referral.protocol().value(), referral.status(), specialty,
                    referral.riskClass(), null, null, null, null);
        }
        QueuePositionSnapshot position = snapshot.get();
        BigDecimal throughput = snapshots.findStats(referral.specialtyId())
                .map(SpecialtyQueueStats::throughputPerDay).orElse(null);
        return new PublicQueuePosition(referral.protocol().value(), referral.status(), specialty,
                referral.riskClass(), position.position(), position.totalInQueue(),
                estimatedWaitDays(position.position(), throughput), position.snapshotAt());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicSpecialtyStats> stats() {
        return snapshots.findAllStats().stream()
                .flatMap(stats -> catalog.findSpecialty(stats.specialtyId()).stream().map(specialty -> {
                    Map<RiskClass, Integer> byRisk = new EnumMap<>(RiskClass.class);
                    byRisk.put(RiskClass.RED, stats.waitingRed());
                    byRisk.put(RiskClass.YELLOW, stats.waitingYellow());
                    byRisk.put(RiskClass.GREEN, stats.waitingGreen());
                    byRisk.put(RiskClass.BLUE, stats.waitingBlue());
                    return new PublicSpecialtyStats(stats.specialtyId(), specialty.name(), byRisk,
                            stats.totalWaiting(), stats.avgWaitDays(), stats.throughputPerDay(), stats.snapshotAt());
                }))
                .sorted((a, b) -> a.specialty().compareToIgnoreCase(b.specialty()))
                .toList();
    }

    /** RN-08: posição ÷ vazão diária, arredondado para cima; sem vazão ⇒ indisponível. */
    static @Nullable Integer estimatedWaitDays(int position, @Nullable BigDecimal throughputPerDay) {
        if (throughputPerDay == null || throughputPerDay.signum() <= 0) {
            return null;
        }
        return BigDecimal.valueOf(position).divide(throughputPerDay, 0, RoundingMode.CEILING).intValueExact();
    }

    /** RN-20: especialidade sensível não é nomeada fora do atendimento. */
    static String label(SpecialtySummary specialty) {
        if (!specialty.sensitive()) {
            return specialty.name();
        }
        return specialty.type() == SpecialtyType.EXAM ? "Exame especializado" : "Consulta especializada";
    }
}
