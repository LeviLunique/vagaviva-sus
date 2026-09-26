/**
 * Engajamento do paciente (confirmação ativa): mensagens por SMS/WhatsApp com link seguro, ações
 * do paciente sem login (ver, confirmar, cancelar, desistir) e lembretes. O envio é assíncrono:
 * outbox do Modulith → SQS (com DLQ) → canal, com retentativas e circuit breaker.
 */
@ApplicationModule(displayName = "Engajamento do paciente")
package br.com.vagaviva.engagement;

import org.springframework.modulith.ApplicationModule;
