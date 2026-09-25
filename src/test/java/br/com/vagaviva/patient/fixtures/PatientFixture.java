package br.com.vagaviva.patient.fixtures;

import br.com.vagaviva.patient.ContactChannel;
import br.com.vagaviva.patient.domain.Cns;
import br.com.vagaviva.patient.domain.Cpf;
import br.com.vagaviva.patient.domain.Patient;
import br.com.vagaviva.patient.domain.PhoneNumber;
import br.com.vagaviva.shared.domain.MunicipalityCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Pacientes fictícios (CNS/CPF gerados pelos algoritmos oficiais, sem correspondência real). */
public final class PatientFixture {

    public static final ZoneId SAO_PAULO_ZONE = ZoneId.of("America/Sao_Paulo");
    /** 25/09/2026 09:00 em São Paulo. */
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), SAO_PAULO_ZONE);
    public static final String VALID_CNS = "115881399860000";
    public static final String OTHER_VALID_CNS = "741707536455688";
    public static final String VALID_CPF = "68469788019";

    private PatientFixture() {
    }

    public static Patient.Builder aPatientData() {
        return Patient.builder()
                .cns(Cns.of(VALID_CNS))
                .cpf(Cpf.of(VALID_CPF))
                .fullName("Maria da Silva Fictícia")
                .birthDate(LocalDate.of(1990, 5, 10))
                .municipalityCode(MunicipalityCode.of("3550308"))
                .phone(PhoneNumber.of("+5511999990001"))
                .preferredChannel(ContactChannel.WHATSAPP)
                .whatsappOptIn(true);
    }

    public static Patient aPatient() {
        return Patient.register(aPatientData(), CLOCK);
    }
}
