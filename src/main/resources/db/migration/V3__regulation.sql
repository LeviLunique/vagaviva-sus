-- F3: encaminhamentos, fila priorizada (RN-06) e read model da transparência (RN-07/RN-08).

CREATE SEQUENCE referral_protocol_seq;

CREATE TABLE referral (
  id                        uuid PRIMARY KEY,
  protocol                  varchar(20)   NOT NULL UNIQUE,     -- VV-2026-0000123
  patient_id                uuid          NOT NULL,
  specialty_id              uuid          NOT NULL,
  requester_unit_id         uuid          NOT NULL,
  requested_by              uuid          NOT NULL,
  clinical_justification    varchar(2000) NOT NULL,
  cid10                     varchar(8),
  accepts_short_notice      boolean       NOT NULL DEFAULT false,
  patient_municipality_code varchar(7)    NOT NULL CHECK (patient_municipality_code ~ '^[1-5][0-9]{6}$'),
  priority_group            boolean       NOT NULL DEFAULT false,
  risk_class                varchar(10)   CHECK (risk_class IN ('RED','YELLOW','GREEN','BLUE')),
  risk_rank                 smallint,
  status                    varchar(24)   NOT NULL CHECK (status IN ('PENDING_REGULATION','RETURNED','WAITING','SCHEDULED','COMPLETED','WITHDRAWN','CANCELLED')),
  queue_entered_at          timestamptz,
  regulated_by              uuid,
  regulated_at              timestamptz,
  return_reason             varchar(500),
  cancel_reason             varchar(500),
  missed_confirmations      smallint      NOT NULL DEFAULT 0,
  no_shows                  smallint      NOT NULL DEFAULT 0,
  scheduled_at              timestamptz,                        -- último agendamento (vazão e espera média — RN-08)
  created_at                timestamptz   NOT NULL,
  updated_at                timestamptz   NOT NULL,
  version                   bigint        NOT NULL DEFAULT 0,
  -- Quem está na fila tem sempre risco, rank e data de entrada.
  CHECK (status <> 'WAITING' OR (risk_class IS NOT NULL AND risk_rank IS NOT NULL AND queue_entered_at IS NOT NULL))
);

-- RN-05: no máximo 1 encaminhamento ativo por paciente + especialidade.
CREATE UNIQUE INDEX ux_referral_active_per_specialty ON referral (patient_id, specialty_id)
  WHERE status IN ('PENDING_REGULATION','RETURNED','WAITING','SCHEDULED');
-- Fila (RN-06) e seleção do próximo elegível (F4): o índice parcial cobre a ordenação oficial.
CREATE INDEX ix_referral_queue ON referral (specialty_id, risk_rank, priority_group DESC, queue_entered_at, id)
  WHERE status = 'WAITING';
CREATE INDEX ix_referral_status_created ON referral (status, created_at DESC);
CREATE INDEX ix_referral_requester_unit ON referral (requester_unit_id, created_at DESC);
-- Vazão da especialidade na janela recente (RN-08).
CREATE INDEX ix_referral_scheduled ON referral (specialty_id, scheduled_at) WHERE scheduled_at IS NOT NULL;

-- Read model da transparência (RN-07), recalculado pelo QueueSnapshotJob.
CREATE TABLE queue_position (
  referral_id    uuid PRIMARY KEY,
  specialty_id   uuid        NOT NULL,
  position       int         NOT NULL,
  total_in_queue int         NOT NULL,
  risk_class     varchar(10) NOT NULL,
  snapshot_at    timestamptz NOT NULL
);

CREATE TABLE queue_specialty_stats (
  specialty_id       uuid PRIMARY KEY,
  waiting_red        int NOT NULL,
  waiting_yellow     int NOT NULL,
  waiting_green      int NOT NULL,
  waiting_blue       int NOT NULL,
  avg_wait_days      numeric(8,1),   -- espera média (scheduled_at - queue_entered_at) na janela
  throughput_per_day numeric(10,2),  -- agendamentos/dia na janela (RN-08)
  snapshot_at        timestamptz NOT NULL
);
