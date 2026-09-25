package br.com.vagaviva.regulation.adapter.out.persistence;

import br.com.vagaviva.regulation.EligibilityCriteria;
import br.com.vagaviva.regulation.ReferralCandidate;
import br.com.vagaviva.regulation.ReferralStatus;
import br.com.vagaviva.regulation.RiskClass;
import br.com.vagaviva.regulation.application.port.in.QueryReferralsUseCase.ReferralFilter;
import br.com.vagaviva.regulation.application.port.out.ReferralRepository;
import br.com.vagaviva.regulation.domain.Referral;
import br.com.vagaviva.shared.domain.ConflictException;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class ReferralPersistenceAdapter implements ReferralRepository {

    private static final String ACTIVE_INDEX = "ux_referral_active_per_specialty";
    private static final Set<ReferralStatus> ACTIVE = EnumSet.of(ReferralStatus.PENDING_REGULATION,
            ReferralStatus.RETURNED, ReferralStatus.WAITING, ReferralStatus.SCHEDULED);
    /** RN-06 — mesma ordem da {@code PnrQueueOrderingPolicy} e do snapshot. */
    private static final Sort QUEUE_ORDER = Sort.by(Sort.Order.asc("riskRank"), Sort.Order.desc("priorityGroup"),
            Sort.Order.asc("queueEnteredAt"), Sort.Order.asc("id"));
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final ReferralJpaRepository repository;
    private final NamedParameterJdbcTemplate jdbc;

    ReferralPersistenceAdapter(ReferralJpaRepository repository, NamedParameterJdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    @Override
    public Referral save(Referral referral) {
        try {
            return repository.saveAndFlush(ReferralEntity.from(referral)).toDomain();
        } catch (DataIntegrityViolationException ex) {
            if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains(ACTIVE_INDEX)) {
                throw new ConflictException("REFERRAL_DUPLICATED",
                        "O paciente já possui encaminhamento ativo para esta especialidade.");
            }
            throw ex;
        }
    }

    @Override
    public Optional<Referral> findById(UUID id) {
        return repository.findById(id).map(ReferralEntity::toDomain);
    }

    @Override
    public Optional<Referral> findByProtocol(String protocol) {
        return repository.findByProtocol(protocol).map(ReferralEntity::toDomain);
    }

    @Override
    public boolean existsActive(UUID patientId, UUID specialtyId) {
        return repository.existsByPatientIdAndSpecialtyIdAndStatusIn(patientId, specialtyId, ACTIVE);
    }

    @Override
    public Page<Referral> search(ReferralFilter filter, Pageable pageable) {
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
        return repository.findAll(matching(filter), newestFirst).map(ReferralEntity::toDomain);
    }

    @Override
    public Page<Referral> findQueue(UUID specialtyId, Pageable pageable) {
        Pageable ordered = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), QUEUE_ORDER);
        return repository.findByStatusAndSpecialtyId(ReferralStatus.WAITING, specialtyId, ordered)
                .map(ReferralEntity::toDomain);
    }

    /**
     * {@code FOR UPDATE SKIP LOCKED}: cada transação recebe a próxima linha livre — duas alocações
     * simultâneas nunca pegam o mesmo paciente e nenhuma fica esperando a outra.
     */
    @Override
    public List<ReferralCandidate> lockWaiting(UUID specialtyId, EligibilityCriteria criteria, boolean shortNoticeOnly,
            Set<UUID> excludedPatients, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, patient_id, specialty_id, risk_class, priority_group, queue_entered_at, accepts_short_notice
                  FROM referral
                 WHERE status = 'WAITING' AND specialty_id = :specialtyId""");
        var params = new MapSqlParameterSource("specialtyId", specialtyId).addValue("limit", limit);
        if (!criteria.serviceArea().isEmpty()) {
            sql.append(" AND patient_municipality_code IN (:area)");
            params.addValue("area", criteria.serviceArea());
        }
        if (shortNoticeOnly) {
            sql.append(" AND accepts_short_notice");
        }
        if (!excludedPatients.isEmpty()) {
            sql.append(" AND patient_id NOT IN (:excluded)");
            params.addValue("excluded", excludedPatients);
        }
        sql.append(" ORDER BY risk_rank, priority_group DESC, queue_entered_at, id LIMIT :limit FOR UPDATE SKIP LOCKED");
        return jdbc.query(sql.toString(), params, (rs, row) -> new ReferralCandidate(
                rs.getObject("id", UUID.class),
                rs.getObject("patient_id", UUID.class),
                rs.getObject("specialty_id", UUID.class),
                RiskClass.valueOf(rs.getString("risk_class")),
                rs.getBoolean("priority_group"),
                rs.getTimestamp("queue_entered_at").toInstant(),
                rs.getBoolean("accepts_short_notice")));
    }

    private static Specification<ReferralEntity> matching(ReferralFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.status() != null) {
                predicates.add(builder.equal(root.get("status"), filter.status()));
            }
            if (filter.specialtyId() != null) {
                predicates.add(builder.equal(root.get("specialtyId"), filter.specialtyId()));
            }
            if (filter.requesterUnitId() != null) {
                predicates.add(builder.equal(root.get("requesterUnitId"), filter.requesterUnitId()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
