-- F7: fatos para os indicadores (projeções idempotentes dos eventos de agenda e encaixe).
CREATE TABLE appointment_fact (
  appointment_id   uuid PRIMARY KEY,
  specialty_id     uuid        NOT NULL,
  unit_id          uuid        NOT NULL,
  origin           varchar(20) NOT NULL,
  scheduled_at     timestamptz NOT NULL,     -- momento da alocação
  start_at         timestamptz NOT NULL,
  queue_entered_at timestamptz,
  final_status     varchar(24),
  confirmed        boolean     NOT NULL DEFAULT false,
  outcome_at       timestamptz,
  waiting_days     numeric(8,1)               -- start_at - queue_entered_at
);
CREATE INDEX ix_appointment_fact_period ON appointment_fact (start_at, specialty_id, unit_id);

CREATE TABLE slot_release_fact (
  id            uuid PRIMARY KEY,
  slot_id       uuid        NOT NULL,
  specialty_id  uuid        NOT NULL,
  unit_id       uuid        NOT NULL,
  reason        varchar(30) NOT NULL,        -- PATIENT_CANCELLED, WITHDRAWN, CONFIRMATION_EXPIRED
  released_at   timestamptz NOT NULL,
  outcome       varchar(20),                 -- REALLOCATED, OFFER_ACCEPTED, LOST
  outcome_at    timestamptz
);
CREATE INDEX ix_slot_release_fact_period ON slot_release_fact (released_at, specialty_id, unit_id);
-- Desfecho da liberação em aberto de cada vaga (atualizado pelos eventos seguintes).
CREATE INDEX ix_slot_release_fact_open ON slot_release_fact (slot_id) WHERE outcome IS NULL;
