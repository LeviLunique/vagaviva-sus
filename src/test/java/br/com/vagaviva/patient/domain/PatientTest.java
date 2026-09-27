package br.com.vagaviva.patient.domain;

import static br.com.vagaviva.patient.fixtures.PatientFixture.CLOCK;
import static br.com.vagaviva.patient.fixtures.PatientFixture.SAO_PAULO_ZONE;
import static br.com.vagaviva.patient.fixtures.PatientFixture.aPatient;
import static br.com.vagaviva.patient.fixtures.PatientFixture.aPatientData;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.patient.ContactChannel;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PatientTest {

    @Test
    @DisplayName("cadastro gera paciente ativo com UUIDv7, nomes aparados e nome social vazio ignorado")
    void shouldRegisterActivePatient() {
        Patient patient = Patient.register(aPatientData().fullName("  Maria da Silva  ").socialName("  "), CLOCK);

        assertThat(patient.isActive()).isTrue();
        assertThat(patient.id().version()).isEqualTo(7);
        assertThat(patient.fullName()).isEqualTo("Maria da Silva");
        assertThat(patient.socialName()).isNull();
        assertThat(patient.version()).isNull();
    }

    @Test
    @DisplayName("RN-06: completa 60 anos hoje (fuso de São Paulo) ⇒ grupo prioritário; na véspera, não")
    void shouldBecomePriorityOnSixtiethBirthday() {
        Patient patient = Patient.register(aPatientData().birthDate(LocalDate.of(1966, 9, 25)), CLOCK);
        Clock justAfterMidnightInSaoPaulo = Clock.fixed(Instant.parse("2026-09-25T03:30:00Z"), SAO_PAULO_ZONE);
        Clock dayBefore = Clock.fixed(Instant.parse("2026-09-25T02:30:00Z"), SAO_PAULO_ZONE);

        assertThat(patient.isPriorityGroup(justAfterMidnightInSaoPaulo)).isTrue();
        assertThat(patient.isPriorityGroup(dayBefore)).isFalse();
        assertThat(patient.ageOn(LocalDate.of(2026, 9, 24))).isEqualTo(59);
    }

    @Test
    @DisplayName("RN-06: gestante ou pessoa com deficiência é grupo prioritário em qualquer idade")
    void shouldBePriorityWhenPregnantOrDisabled() {
        assertThat(aPatient().isPriorityGroup(CLOCK)).isFalse();
        assertThat(Patient.register(aPatientData().pregnant(true), CLOCK).isPriorityGroup(CLOCK)).isTrue();
        assertThat(Patient.register(aPatientData().disability(true), CLOCK).isPriorityGroup(CLOCK)).isTrue();
    }

    @Test
    @DisplayName("primeiro nome vem do nome social quando existir")
    void shouldUseSocialNameForFirstName() {
        assertThat(aPatient().firstName()).isEqualTo("Maria");
        assertThat(Patient.register(aPatientData().socialName("Joana Fictícia"), CLOCK).firstName()).isEqualTo("Joana");
    }

    @Test
    @DisplayName("RF-09: atualiza só os campos informados e indica se houve mudança")
    void shouldUpdateContactPartially() {
        Patient patient = aPatient();
        Clock later = Clock.fixed(CLOCK.instant().plusSeconds(60), SAO_PAULO_ZONE);

        assertThat(patient.updateContact(null, null, null, later)).isFalse();
        assertThat(patient.updateContact(PhoneNumber.of("+5511999990001"), ContactChannel.WHATSAPP, true, later)).isFalse();
        assertThat(patient.updatedAt()).isEqualTo(CLOCK.instant());

        assertThat(patient.updateContact(PhoneNumber.of("+5511988887777"), null, null, later)).isTrue();
        assertThat(patient.updateContact(null, ContactChannel.SMS, false, later)).isTrue();

        assertThat(patient.phone().value()).isEqualTo("+5511988887777");
        assertThat(patient.preferredChannel()).isEqualTo(ContactChannel.SMS);
        assertThat(patient.whatsappOptIn()).isFalse();
        assertThat(patient.updatedAt()).isEqualTo(later.instant());
    }
}
