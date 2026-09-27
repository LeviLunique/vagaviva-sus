-- F4: agenda (vagas e agendamentos) e registro de publicação de eventos do Spring Modulith (outbox).

CREATE TABLE slot (
  id                uuid PRIMARY KEY,
  unit_id           uuid         NOT NULL,
  specialty_id      uuid         NOT NULL,
  professional_name varchar(120) NOT NULL,
  start_at          timestamptz  NOT NULL,
  duration_minutes  smallint     NOT NULL CHECK (duration_minutes BETWEEN 5 AND 240),
  status            varchar(20)  NOT NULL CHECK (status IN ('AVAILABLE','ALLOCATED','OPEN_FOR_OFFERS','USED','MISSED','EXPIRED','CANCELLED')),
  release_count     smallint     NOT NULL DEFAULT 0,
  created_at        timestamptz  NOT NULL,
  updated_at        timestamptz  NOT NULL,
  version           bigint       NOT NULL DEFAULT 0
);
-- RF-19 CA1: o mesmo profissional não tem duas vagas no mesmo horário (sobreposição parcial é
-- validada na aplicação).
CREATE UNIQUE INDEX ux_slot_professional_start ON slot (unit_id, professional_name, start_at) WHERE status <> 'CANCELLED';
-- Motor de alocação: vagas disponíveis por horário (RF-20).
CREATE INDEX ix_slot_allocatable ON slot (start_at) WHERE status = 'AVAILABLE';
CREATE INDEX ix_slot_unit_start ON slot (unit_id, start_at);

CREATE TABLE appointment (
  id                    uuid PRIMARY KEY,
  slot_id               uuid        NOT NULL REFERENCES slot(id),
  referral_id           uuid        NOT NULL,
  patient_id            uuid        NOT NULL,
  unit_id               uuid        NOT NULL,
  specialty_id          uuid        NOT NULL,
  start_at              timestamptz NOT NULL,
  origin                varchar(20) NOT NULL CHECK (origin IN ('REGULAR','REALLOCATED','SHORT_NOTICE_OFFER')),
  status                varchar(24) NOT NULL CHECK (status IN ('PENDING_CONFIRMATION','CONFIRMED','ATTENDED','NO_SHOW','CANCELLED_BY_PATIENT','WITHDRAWN','EXPIRED_UNCONFIRMED','CANCELLED_BY_UNIT')),
  confirmation_deadline timestamptz,
  confirmed_at          timestamptz,
  cancelled_at          timestamptz,
  outcome_at            timestamptz,
  created_at            timestamptz NOT NULL,
  updated_at            timestamptz NOT NULL,
  version               bigint      NOT NULL DEFAULT 0
);
-- Uma vaga tem no máximo um agendamento "vivo" (última barreira contra alocação dupla).
CREATE UNIQUE INDEX ux_appointment_live_slot ON appointment (slot_id) WHERE status IN ('PENDING_CONFIRMATION','CONFIRMED','ATTENDED','NO_SHOW');
CREATE INDEX ix_appointment_deadline ON appointment (confirmation_deadline) WHERE status = 'PENDING_CONFIRMATION';
CREATE INDEX ix_appointment_unit_start ON appointment (unit_id, start_at);
CREATE INDEX ix_appointment_referral ON appointment (referral_id);

-- Spring Modulith 2.x — registro de publicação de eventos (outbox). Copiado de
-- spring-modulith-events-jdbc 2.1.1: schemas/v2/schema-postgresql.sql (o Flyway é o dono do schema).
CREATE TABLE event_publication (
  id                     uuid NOT NULL PRIMARY KEY,
  listener_id            text NOT NULL,
  event_type             text NOT NULL,
  serialized_event       text NOT NULL,
  publication_date       timestamptz NOT NULL,
  completion_date        timestamptz,
  status                 text,
  completion_attempts    int,
  last_resubmission_date timestamptz
);
CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
