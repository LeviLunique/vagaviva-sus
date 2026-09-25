-- F1: profissionais (identity), trilha de auditoria (audit) e travas de jobs (ShedLock).

CREATE TABLE staff_user (
  id                    uuid PRIMARY KEY,
  name                  varchar(120) NOT NULL,
  email                 varchar(160) NOT NULL,
  password_hash         varchar(100) NOT NULL,
  role                  varchar(20)  NOT NULL CHECK (role IN ('ADMIN','REQUESTER','REGULATOR','SCHEDULER','MANAGER')),
  health_unit_id        uuid,
  active                boolean      NOT NULL DEFAULT true,
  failed_login_attempts int          NOT NULL DEFAULT 0,
  locked_until          timestamptz,
  created_at            timestamptz  NOT NULL,
  updated_at            timestamptz  NOT NULL,
  version               bigint       NOT NULL DEFAULT 0
);
-- E-mail único sem diferenciar maiúsculas (RF-02 CA1); também atende a busca do login.
CREATE UNIQUE INDEX ux_staff_user_email ON staff_user (lower(email));

-- Trilha append-only (RF-05 / LGPD): sem FKs para ser gravável por qualquer módulo.
CREATE TABLE audit_event (
  id            uuid PRIMARY KEY,
  occurred_at   timestamptz NOT NULL,
  actor_id      uuid,
  actor_role    varchar(20),
  action        varchar(60) NOT NULL,
  resource_type varchar(40) NOT NULL,
  resource_id   varchar(64),
  outcome       varchar(10) NOT NULL CHECK (outcome IN ('SUCCESS','DENIED','FAILURE')),
  client_ip     varchar(45),
  details       jsonb
);
CREATE INDEX ix_audit_event_resource ON audit_event (resource_type, resource_id, occurred_at DESC);
CREATE INDEX ix_audit_event_actor    ON audit_event (actor_id, occurred_at DESC);
CREATE INDEX ix_audit_event_time     ON audit_event (occurred_at DESC);

CREATE TABLE shedlock (
  name       varchar(64)  PRIMARY KEY,
  lock_until timestamptz  NOT NULL,
  locked_at  timestamptz  NOT NULL,
  locked_by  varchar(255) NOT NULL
);
