package br.com.vagaviva.patient.adapter.out.persistence;

import static br.com.vagaviva.patient.fixtures.PatientFixture.CLOCK;
import static br.com.vagaviva.patient.fixtures.PatientFixture.aPatientData;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vagaviva.patient.ContactChannel;
import br.com.vagaviva.patient.application.port.out.PatientRepository;
import br.com.vagaviva.patient.domain.Cns;
import br.com.vagaviva.patient.domain.Cpf;
import br.com.vagaviva.patient.domain.Patient;
import br.com.vagaviva.patient.domain.PhoneNumber;
import br.com.vagaviva.shared.domain.ConflictException;
import br.com.vagaviva.support.IntegrationTest;
import br.com.vagaviva.support.TestDocuments;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class PatientPersistenceAdapterIT {

    @Autowired PatientRepository repository;

    @Test
    @DisplayName("grava e relê o paciente; busca por CNS e por CPF (colunas char)")
    void shouldRoundTripAndFindByDocuments() {
        Cns cns = Cns.of(TestDocuments.randomCns());
        Cpf cpf = Cpf.of(TestDocuments.randomCpf());
        Patient saved = repository.save(Patient.register(aPatientData().cns(cns).cpf(cpf).socialName("Joana"), CLOCK));

        assertThat(repository.findById(saved.id())).hasValueSatisfying(p -> {
            assertThat(p.cns()).isEqualTo(cns);
            assertThat(p.socialName()).isEqualTo("Joana");
            assertThat(p.version()).isZero();
        });
        assertThat(repository.findByCns(cns)).isPresent();
        assertThat(repository.findByCpf(cpf)).isPresent();
        assertThat(repository.existsByCns(cns)).isTrue();
        assertThat(repository.existsByCpf(cpf)).isTrue();
    }

    @Test
    @DisplayName("RF-08 CA1: CNS e CPF únicos no banco ⇒ ConflictException com o código de cada documento")
    void shouldEnforceUniqueDocuments() {
        Cns cns = Cns.of(TestDocuments.randomCns());
        Cpf cpf = Cpf.of(TestDocuments.randomCpf());
        repository.save(Patient.register(aPatientData().cns(cns).cpf(cpf), CLOCK));

        assertThatThrownBy(() -> repository.save(Patient.register(aPatientData().cns(cns).cpf(null), CLOCK)))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("CNS_ALREADY_REGISTERED");
        assertThatThrownBy(() -> repository.save(Patient.register(
                aPatientData().cns(Cns.of(TestDocuments.randomCns())).cpf(cpf), CLOCK)))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("CPF_ALREADY_REGISTERED");
    }

    @Test
    @DisplayName("vários pacientes sem CPF convivem (UNIQUE ignora nulos) e o contato atualizado é persistido")
    void shouldAllowManyPatientsWithoutCpfAndPersistContact() {
        Patient first = repository.save(Patient.register(aPatientData().cns(Cns.of(TestDocuments.randomCns())).cpf(null), CLOCK));
        repository.save(Patient.register(aPatientData().cns(Cns.of(TestDocuments.randomCns())).cpf(null), CLOCK));

        first.updateContact(PhoneNumber.of("+5511977776666"), ContactChannel.SMS, false, CLOCK);
        repository.save(first);

        Patient reloaded = repository.findById(first.id()).orElseThrow();
        assertThat(reloaded.phone().value()).isEqualTo("+5511977776666");
        assertThat(reloaded.preferredChannel()).isEqualTo(ContactChannel.SMS);
        assertThat(reloaded.version()).isEqualTo(1);
    }
}
