package br.com.vagaviva.engagement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vagaviva.engagement.domain.MessageComposer.MessageData;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MessageComposerTest {

    private static final String LINK = "https://vagaviva.test/p/AbCdEfGhIjKlMnOpQrStUv";
    private final MessageComposer composer = new MessageComposer(ZoneId.of("America/Sao_Paulo"));

    /** Consulta em 05/10 08:00 (SP), prazo 02/10 23:59 (SP). */
    private static MessageData data(CareLabel label, String unit) {
        return new MessageData("Maria", label, "VV-2026-0000123", Instant.parse("2026-10-05T11:00:00Z"),
                Instant.parse("2026-10-03T02:59:00Z"), unit, LINK);
    }

    @ParameterizedTest(name = "{0} ({1})")
    @CsvSource({
        "REFERRAL_QUEUED, CONSULTATION, referral_queued-sms",
        "APPOINTMENT_SCHEDULED, CONSULTATION, appointment_scheduled-sms",
        "CONFIRMATION_REMINDER, CONSULTATION, confirmation_reminder-sms",
        "ATTENDANCE_REMINDER, CONSULTATION, attendance_reminder-sms",
        "APPOINTMENT_CANCELLED_BY_UNIT, CONSULTATION, appointment_cancelled_by_unit-sms",
        "APPOINTMENT_CANCELLED_BY_UNIT, EXAM, appointment_cancelled_by_unit-exam-sms"
    })
    @DisplayName("RN-20/RN-23: SMS igual ao golden file — ASCII, ≤ 160 caracteres, concordância de gênero")
    void smsMatchesGoldenFile(NotificationType type, CareLabel label, String golden) throws IOException {
        String sms = composer.compose(type, data(label, "UBS Vila Esperança"), NotificationChannel.SMS);

        assertThat(sms).isEqualTo(golden(golden));
        assertThat(sms).hasSizeLessThanOrEqualTo(MessageComposer.SMS_MAX_LENGTH).matches("\\p{ASCII}*");
    }

    @Test
    @DisplayName("fora do SMS (WhatsApp/sandbox) o texto mantém os acentos")
    void keepsAccentsOutsideSms() throws IOException {
        assertThat(composer.compose(NotificationType.APPOINTMENT_SCHEDULED, data(CareLabel.CONSULTATION,
                "UBS Vila Esperança"), NotificationChannel.SANDBOX)).isEqualTo(golden("appointment_scheduled-sandbox"));
    }

    @Test
    @DisplayName("RN-23: unidade de nome longo é encurtada para caber em 160 — o link nunca é cortado")
    void truncatesUnitNameButNeverTheLink() {
        String longUnit = "Ambulatório Médico de Especialidades Doutor Fulano de Tal Fictício da Região Norte";

        String sms = composer.compose(NotificationType.APPOINTMENT_SCHEDULED, data(CareLabel.EXAM, longUnit),
                NotificationChannel.SMS);

        assertThat(sms).hasSizeLessThanOrEqualTo(160).endsWith(LINK).contains("Ambulatorio Medico").contains("exame em 05/10");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"APPOINTMENT_SCHEDULED", "CONFIRMATION_REMINDER", "ATTENDANCE_REMINDER", "SLOT_OFFER"})
    @DisplayName("RN-23: com link longo (domínio da CloudFront ou maior) o SMS cabe em 160 e termina no link inteiro")
    void longLinksStillFitInOneSms(NotificationType type) {
        for (String base : new String[] {"https://ddueuk9rcxkpp.cloudfront.net", "https://agenda-regulacao.saude.prefeitura-ficticia.sp.gov.br"}) {
            String link = base + "/p/AbCdEfGhIjKlMnOpQrStUv";
            var data = new MessageData("Maria Aparecida", CareLabel.CONSULTATION, "VV-2026-0000123",
                    Instant.parse("2026-10-05T11:00:00Z"), Instant.parse("2026-10-03T02:59:00Z"),
                    "Ambulatório Médico de Especialidades da Zona Norte", link);

            String sms = composer.compose(type, data, NotificationChannel.SMS);

            assertThat(sms).hasSizeLessThanOrEqualTo(160).endsWith(link).matches("\\p{ASCII}*");
        }
    }

    @Test
    @DisplayName("RN-20: a mensagem só tem o rótulo genérico — o compositor nem recebe o nome da especialidade")
    void onlyGenericLabels() {
        String text = composer.compose(NotificationType.REFERRAL_QUEUED, data(CareLabel.EXAM, "X"), NotificationChannel.SMS);

        assertThat(text).contains("seu exame for agendado");
        assertThat(CareLabel.values()).extracting(CareLabel::noun).containsExactly("consulta", "exame");
    }

    @Test
    @DisplayName("oferta de encaixe (F6) informa o horário limite para aceitar")
    void slotOfferShowsAcceptDeadline() {
        String text = composer.compose(NotificationType.SLOT_OFFER, new MessageData("Maria", CareLabel.CONSULTATION,
                null, Instant.parse("2026-10-05T11:00:00Z"), Instant.parse("2026-10-04T18:00:00Z"), "AME", LINK),
                NotificationChannel.SMS);

        assertThat(text).isEqualTo("VagaViva SUS: Maria, vaga de consulta em 05/10 as 08:00, AME. Aceite ate 15:00: " + LINK);
    }

    private static String golden(String name) throws IOException {
        try (InputStream in = MessageComposerTest.class.getResourceAsStream("/messages/" + name + ".txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
