-- F6: ofertas de encaixe em cascata (RN-16/RN-17).
CREATE TABLE slot_offer (
  id           uuid PRIMARY KEY,
  slot_id      uuid        NOT NULL,
  referral_id  uuid        NOT NULL,
  patient_id   uuid        NOT NULL,
  round        smallint    NOT NULL,
  status       varchar(12) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','DECLINED','EXPIRED','SUPERSEDED')),
  expires_at   timestamptz NOT NULL,
  responded_at timestamptz,
  created_at   timestamptz NOT NULL
);
-- Um paciente recebe no máximo uma oferta por vaga.
CREATE UNIQUE INDEX ux_slot_offer_slot_referral ON slot_offer (slot_id, referral_id);
-- Última barreira contra dois aceites da mesma vaga na mesma rodada (uma vaga aceita e depois
-- liberada volta ao encaixe numa rodada seguinte e pode ser aceita de novo).
CREATE UNIQUE INDEX ux_slot_offer_one_accepted ON slot_offer (slot_id, round) WHERE status = 'ACCEPTED';
-- RN-16: um paciente não recebe duas ofertas simultâneas.
CREATE UNIQUE INDEX ux_slot_offer_patient_pending ON slot_offer (patient_id) WHERE status = 'PENDING';
-- Job de expiração: pendentes vencidas.
CREATE INDEX ix_slot_offer_expiring ON slot_offer (expires_at) WHERE status = 'PENDING';

-- Um paciente pode receber ofertas de vagas diferentes ao longo do tempo: cada mensagem de oferta já é
-- única por offer_id (ux_notification_offer). A unicidade por encaminhamento vale só para os marcos
-- sem agendamento nem oferta (entrada na fila).
DROP INDEX ux_notification_referral_type;
CREATE UNIQUE INDEX ux_notification_referral_type ON notification (referral_id, type)
  WHERE referral_id IS NOT NULL AND appointment_id IS NULL AND offer_id IS NULL;
