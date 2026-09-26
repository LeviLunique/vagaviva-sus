package br.com.vagaviva.engagement.domain;

import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Textos das mensagens (RN-20, RN-23) — Template Method leve: formato comum, conteúdo por tipo.
 * Apenas primeiro nome, rótulo genérico, data, hora, unidade e link. No SMS o texto vira ASCII e
 * cabe em 160 caracteres (uma parte GSM-7): se passar, o nome da unidade é encurtado e, em último
 * caso, usa-se a forma compacta do texto — o link nunca é cortado.
 */
public final class MessageComposer {

    public static final int SMS_MAX_LENGTH = 160;
    private static final String PREFIX = "VagaViva SUS: ";
    private static final String LINK_MARK = "{link}";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ZoneId zone;

    public MessageComposer(ZoneId zone) {
        this.zone = zone;
    }

    public String compose(NotificationType type, MessageData data, NotificationChannel channel) {
        String unit = data.unitName() == null ? "" : data.unitName();
        String link = data.link() == null ? "" : data.link();
        if (channel != NotificationChannel.SMS) {
            return withLink(render(type, data, unit), link);
        }
        String text = sms(render(type, data, unit), link);
        while (text.length() > SMS_MAX_LENGTH && !unit.isEmpty()) {
            unit = unit.substring(0, unit.length() - 1).strip();
            text = sms(render(type, data, unit), link);
        }
        return text.length() > SMS_MAX_LENGTH ? sms(compact(type, data), link) : text;
    }

    /** O texto vira ASCII, mas o link entra depois — ele nunca é alterado. */
    private static String sms(String template, String link) {
        return withLink(ascii(template), link);
    }

    private static String withLink(String template, String link) {
        return template.replace(LINK_MARK, link);
    }

    /** Forma mínima (sem nome e sem unidade) para links longos — mantém data, hora, prazo e link. */
    private String compact(NotificationType type, MessageData d) {
        String label = d.label().noun();
        return PREFIX + switch (type) {
            case APPOINTMENT_SCHEDULED -> "%s %s %s. Confirme ate %s: %s"
                    .formatted(label, day(d.startAt()), time(d.startAt()), day(d.deadline()), LINK_MARK);
            case CONFIRMATION_REMINDER -> "confirme %s de %s %s ate %s: %s"
                    .formatted(label, day(d.startAt()), time(d.startAt()), day(d.deadline()), LINK_MARK);
            case ATTENDANCE_REMINDER -> "%s amanha %s. Nao pode ir? %s".formatted(label, time(d.startAt()), LINK_MARK);
            case SLOT_OFFER -> "vaga de %s %s %s. Aceite ate %s: %s"
                    .formatted(label, day(d.startAt()), time(d.startAt()), time(d.deadline()), LINK_MARK);
            case REFERRAL_QUEUED, APPOINTMENT_CANCELLED_BY_UNIT -> render(type, d, "").substring(PREFIX.length());
        };
    }

    private String render(NotificationType type, MessageData d, String unit) {
        CareLabel label = d.label();
        return PREFIX + switch (type) {
            case REFERRAL_QUEUED -> "%s, seu pedido %s entrou na fila. Avisaremos quando %s for %s."
                    .formatted(d.firstName(), d.protocol(), label.withPossessive(), label.agree("agendad"));
            case APPOINTMENT_SCHEDULED -> "%s, %s em %s às %s, %s. Confirme até %s: %s"
                    .formatted(d.firstName(), label.noun(), day(d.startAt()), time(d.startAt()), unit,
                            day(d.deadline()), LINK_MARK);
            case CONFIRMATION_REMINDER -> "confirme %s de %s às %s até %s, ou a vaga irá para outro paciente: %s"
                    .formatted(label.withPossessive(), day(d.startAt()), time(d.startAt()), day(d.deadline()), LINK_MARK);
            case ATTENDANCE_REMINDER -> "lembrete: %s amanhã às %s, %s. Não pode ir? Libere a vaga: %s"
                    .formatted(label.noun(), time(d.startAt()), unit, LINK_MARK);
            case APPOINTMENT_CANCELLED_BY_UNIT -> "%s de %s foi %s pela unidade. Você continua na fila, na mesma posição."
                    .formatted(capitalize(label.withPossessive()), day(d.startAt()), label.agree("cancelad"));
            case SLOT_OFFER -> "%s, vaga de %s em %s às %s, %s. Aceite até %s: %s"
                    .formatted(d.firstName(), label.noun(), day(d.startAt()), time(d.startAt()), unit,
                            time(d.deadline()), LINK_MARK);
        };
    }

    private String day(@Nullable Instant instant) {
        return DAY.format(Objects.requireNonNull(instant, "data da mensagem").atZone(zone));
    }

    private String time(@Nullable Instant instant) {
        return TIME.format(Objects.requireNonNull(instant, "horário da mensagem").atZone(zone));
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** RN-23: remove acentos (NFD + diacríticos) — acento força UCS-2, com 70 caracteres por parte. */
    static String ascii(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** Dados mínimos da mensagem (RN-20); campos não usados pelo tipo podem ser nulos. */
    public record MessageData(String firstName, CareLabel label, @Nullable String protocol, @Nullable Instant startAt,
            @Nullable Instant deadline, @Nullable String unitName, @Nullable String link) {
    }
}
