package br.com.vagaviva.patient.adapter.out.persistence;

import br.com.vagaviva.patient.ContactChannel;
import br.com.vagaviva.patient.domain.Cns;
import br.com.vagaviva.patient.domain.Cpf;
import br.com.vagaviva.patient.domain.Patient;
import br.com.vagaviva.patient.domain.PhoneNumber;
import br.com.vagaviva.shared.domain.MunicipalityCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "patient")
class PatientEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 15)
    private String cns;

    @Column(length = 11)
    private String cpf;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(name = "social_name", length = 160)
    private String socialName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "municipality_code", nullable = false, length = 7)
    private String municipalityCode;

    @Column(nullable = false, length = 16)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_channel", nullable = false, length = 10)
    private ContactChannel preferredChannel;

    @Column(name = "whatsapp_opt_in", nullable = false)
    private boolean whatsappOptIn;

    @Column(nullable = false)
    private boolean pregnant;

    @Column(nullable = false)
    private boolean disability;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected PatientEntity() {
    }

    static PatientEntity from(Patient patient) {
        var entity = new PatientEntity();
        entity.id = patient.id();
        entity.cns = patient.cns().value();
        entity.cpf = patient.cpf() == null ? null : patient.cpf().value();
        entity.fullName = patient.fullName();
        entity.socialName = patient.socialName();
        entity.birthDate = patient.birthDate();
        entity.municipalityCode = patient.municipalityCode().value();
        entity.phone = patient.phone().value();
        entity.preferredChannel = patient.preferredChannel();
        entity.whatsappOptIn = patient.whatsappOptIn();
        entity.pregnant = patient.pregnant();
        entity.disability = patient.disability();
        entity.active = patient.isActive();
        entity.createdAt = patient.createdAt();
        entity.updatedAt = patient.updatedAt();
        entity.version = patient.version();
        return entity;
    }

    Patient toDomain() {
        return Patient.restore(Patient.builder()
                .id(id)
                .cns(Cns.of(cns))
                .cpf(cpf == null ? null : Cpf.of(cpf))
                .fullName(fullName)
                .socialName(socialName)
                .birthDate(birthDate)
                .municipalityCode(MunicipalityCode.of(municipalityCode))
                .phone(PhoneNumber.of(phone))
                .preferredChannel(preferredChannel)
                .whatsappOptIn(whatsappOptIn)
                .pregnant(pregnant)
                .disability(disability)
                .active(active)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version));
    }
}
