-- F5: notificações ao paciente e tokens dos links de ação (engagement).

CREATE TABLE notification (
  id                  uuid PRIMARY KEY,
  patient_id          uuid          NOT NULL,
  appointment_id      uuid,
  offer_id            uuid,
  referral_id         uuid,
  type                varchar(40)   NOT NULL,
  channel             varchar(10)   NOT NULL CHECK (channel IN ('SANDBOX','SMS','WHATSAPP')),
  status              varchar(10)   NOT NULL CHECK (status IN ('PENDING','SENT','FAILED')),
  -- SHA-256 do telefone: rastreia o destino sem expor o número (RN-21).
  destination_hash    varchar(64)   NOT NULL CHECK (destination_hash ~ '^[0-9a-f]{64}$'),
  body                varchar(1000) NOT NULL,           -- texto enviado (retenção de 90 dias — F8)
  attempts            smallint      NOT NULL DEFAULT 0,
  provider_message_id varchar(100),
  last_error          varchar(300),
  created_at          timestamptz   NOT NULL,
  sent_at             timestamptz
);
-- Idempotência (RN-24): uma notificação de cada tipo por agendamento/oferta/encaminhamento.
CREATE UNIQUE INDEX ux_notification_appointment_type ON notification (appointment_id, type) WHERE appointment_id IS NOT NULL;
CREATE UNIQUE INDEX ux_notification_offer ON notification (offer_id) WHERE offer_id IS NOT NULL;
CREATE UNIQUE INDEX ux_notification_referral_type ON notification (referral_id, type) WHERE referral_id IS NOT NULL AND appointment_id IS NULL;
CREATE INDEX ix_notification_patient ON notification (patient_id, created_at DESC);

-- Tokens dos links (RF-25): só o SHA-256 é guardado; um token por mensagem enviada.
CREATE TABLE patient_action_token (
  id           uuid PRIMARY KEY,
  token_hash   varchar(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
  purpose      varchar(12) NOT NULL CHECK (purpose IN ('APPOINTMENT','OFFER')),
  subject_id   uuid        NOT NULL,
  expires_at   timestamptz NOT NULL,
  created_at   timestamptz NOT NULL,
  last_used_at timestamptz
);
CREATE INDEX ix_patient_action_token_subject ON patient_action_token (purpose, subject_id);
