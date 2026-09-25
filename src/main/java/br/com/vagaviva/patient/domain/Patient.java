package br.com.vagaviva.patient.domain;

import br.com.vagaviva.patient.ContactChannel;
import br.com.vagaviva.shared.domain.Ids;
import br.com.vagaviva.shared.domain.MunicipalityCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Paciente do SUS (RF-08). */
public final class Patient {

    static final int ELDERLY_AGE = 60;

    private final UUID id;
    private final Cns cns;
    private final @Nullable Cpf cpf;
    private final String fullName;
    private final @Nullable String socialName;
    private final LocalDate birthDate;
    private final MunicipalityCode municipalityCode;
    private PhoneNumber phone;
    private ContactChannel preferredChannel;
    private boolean whatsappOptIn;
    private final boolean pregnant;
    private final boolean disability;
    private final boolean active;
    private final Instant createdAt;
    private Instant updatedAt;
    private final @Nullable Long version;

    private Patient(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.cns = Objects.requireNonNull(b.cns, "cns");
        this.cpf = b.cpf;
        this.fullName = Objects.requireNonNull(b.fullName, "fullName");
        this.socialName = b.socialName;
        this.birthDate = Objects.requireNonNull(b.birthDate, "birthDate");
        this.municipalityCode = Objects.requireNonNull(b.municipalityCode, "municipalityCode");
        this.phone = Objects.requireNonNull(b.phone, "phone");
        this.preferredChannel = Objects.requireNonNull(b.preferredChannel, "preferredChannel");
        this.whatsappOptIn = b.whatsappOptIn;
        this.pregnant = b.pregnant;
        this.disability = b.disability;
        this.active = b.active;
        this.createdAt = Objects.requireNonNull(b.createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(b.updatedAt, "updatedAt");
        this.version = b.version;
    }

    /** Novo paciente ativo; nomes sem espaços nas pontas e nome social em branco tratado como ausente. */
    public static Patient register(Builder data, Clock clock) {
        Instant now = clock.instant();
        return new Patient(data
                .id(Ids.newId())
                .fullName(data.fullName == null ? null : data.fullName.strip())
                .socialName(data.socialName == null || data.socialName.isBlank() ? null : data.socialName.strip())
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .version(null));
    }

    public static Patient restore(Builder data) {
        return new Patient(data);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Idade na data civil do relógio (fuso de negócio). */
    public int ageOn(LocalDate date) {
        return Period.between(birthDate, date).getYears();
    }

    /** RN-06: idoso (≥ 60 anos), gestante ou pessoa com deficiência. */
    public boolean isPriorityGroup(Clock clock) {
        return ageOn(LocalDate.now(clock)) >= ELDERLY_AGE || pregnant || disability;
    }

    /** Nome usado nas mensagens: primeiro nome do nome social, se houver (respeito à identidade). */
    public String firstName() {
        String name = socialName != null ? socialName : fullName;
        return name.strip().split("\\s+")[0];
    }

    /** RF-09: atualiza apenas os campos informados. Devolve {@code true} se algo mudou. */
    public boolean updateContact(@Nullable PhoneNumber newPhone, @Nullable ContactChannel newChannel,
            @Nullable Boolean newWhatsappOptIn, Clock clock) {
        boolean changed = false;
        if (newPhone != null && !newPhone.equals(phone)) {
            phone = newPhone;
            changed = true;
        }
        if (newChannel != null && newChannel != preferredChannel) {
            preferredChannel = newChannel;
            changed = true;
        }
        if (newWhatsappOptIn != null && newWhatsappOptIn != whatsappOptIn) {
            whatsappOptIn = newWhatsappOptIn;
            changed = true;
        }
        if (changed) {
            updatedAt = clock.instant();
        }
        return changed;
    }

    public UUID id() {
        return id;
    }

    public Cns cns() {
        return cns;
    }

    public @Nullable Cpf cpf() {
        return cpf;
    }

    public String fullName() {
        return fullName;
    }

    public @Nullable String socialName() {
        return socialName;
    }

    public LocalDate birthDate() {
        return birthDate;
    }

    public MunicipalityCode municipalityCode() {
        return municipalityCode;
    }

    public PhoneNumber phone() {
        return phone;
    }

    public ContactChannel preferredChannel() {
        return preferredChannel;
    }

    public boolean whatsappOptIn() {
        return whatsappOptIn;
    }

    public boolean pregnant() {
        return pregnant;
    }

    public boolean disability() {
        return disability;
    }

    public boolean isActive() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public @Nullable Long version() {
        return version;
    }

    /** Montagem do agregado (13+ atributos): usado no cadastro e na reconstrução pela persistência. */
    public static final class Builder {

        private UUID id;
        private Cns cns;
        private Cpf cpf;
        private String fullName;
        private String socialName;
        private LocalDate birthDate;
        private MunicipalityCode municipalityCode;
        private PhoneNumber phone;
        private ContactChannel preferredChannel;
        private boolean whatsappOptIn;
        private boolean pregnant;
        private boolean disability;
        private boolean active;
        private Instant createdAt;
        private Instant updatedAt;
        private Long version;

        private Builder() {
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder cns(Cns cns) {
            this.cns = cns;
            return this;
        }

        public Builder cpf(@Nullable Cpf cpf) {
            this.cpf = cpf;
            return this;
        }

        public Builder fullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public Builder socialName(@Nullable String socialName) {
            this.socialName = socialName;
            return this;
        }

        public Builder birthDate(LocalDate birthDate) {
            this.birthDate = birthDate;
            return this;
        }

        public Builder municipalityCode(MunicipalityCode municipalityCode) {
            this.municipalityCode = municipalityCode;
            return this;
        }

        public Builder phone(PhoneNumber phone) {
            this.phone = phone;
            return this;
        }

        public Builder preferredChannel(ContactChannel preferredChannel) {
            this.preferredChannel = preferredChannel;
            return this;
        }

        public Builder whatsappOptIn(boolean whatsappOptIn) {
            this.whatsappOptIn = whatsappOptIn;
            return this;
        }

        public Builder pregnant(boolean pregnant) {
            this.pregnant = pregnant;
            return this;
        }

        public Builder disability(boolean disability) {
            this.disability = disability;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder version(@Nullable Long version) {
            this.version = version;
            return this;
        }
    }
}
