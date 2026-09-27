package br.com.vagaviva.engagement;

import java.time.Instant;
import java.util.Map;

/** API do engajamento para os indicadores (F7). */
public interface EngagementApi {

    /** Mensagens enviadas ({@code SENT}) no período, por canal ({@code SANDBOX}, {@code SMS}, {@code WHATSAPP}). */
    Map<String, Long> sentByChannel(Instant from, Instant to);
}
