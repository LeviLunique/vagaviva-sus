-- F2: cadastros de referência (catalog) e pacientes (patient).

CREATE TABLE health_unit (
  id                uuid PRIMARY KEY,
  cnes              char(7)      NOT NULL UNIQUE,
  name              varchar(160) NOT NULL,
  type              varchar(20)  NOT NULL CHECK (type IN ('PRIMARY_CARE','SPECIALIZED')),
  municipality_code char(7)      NOT NULL,
  municipality_name varchar(80)  NOT NULL,
  address           varchar(200) NOT NULL,
  active            boolean      NOT NULL DEFAULT true,
  created_at        timestamptz  NOT NULL,
  updated_at        timestamptz  NOT NULL,
  version           bigint       NOT NULL DEFAULT 0
);

-- Área de atendimento: vazia = a unidade atende todos os municípios (RF-06).
CREATE TABLE health_unit_service_area (
  health_unit_id    uuid    NOT NULL REFERENCES health_unit(id) ON DELETE CASCADE,
  municipality_code char(7) NOT NULL,
  PRIMARY KEY (health_unit_id, municipality_code)
);

CREATE TABLE specialty (
  id         uuid PRIMARY KEY,
  code       varchar(20)  NOT NULL UNIQUE,
  name       varchar(120) NOT NULL,
  type       varchar(20)  NOT NULL CHECK (type IN ('CONSULTATION','EXAM')),
  sensitive  boolean      NOT NULL DEFAULT false,
  active     boolean      NOT NULL DEFAULT true,
  created_at timestamptz  NOT NULL
);

CREATE TABLE patient (
  id                uuid PRIMARY KEY,
  cns               char(15)     NOT NULL UNIQUE,
  cpf               char(11)     UNIQUE,
  full_name         varchar(160) NOT NULL,
  social_name       varchar(160),
  birth_date        date         NOT NULL,
  municipality_code char(7)      NOT NULL,
  phone             varchar(16)  NOT NULL,           -- E.164, ex.: +5511987654321
  preferred_channel varchar(10)  NOT NULL CHECK (preferred_channel IN ('WHATSAPP','SMS')),
  whatsapp_opt_in   boolean      NOT NULL DEFAULT false,
  pregnant          boolean      NOT NULL DEFAULT false,
  disability        boolean      NOT NULL DEFAULT false,
  active            boolean      NOT NULL DEFAULT true,
  created_at        timestamptz  NOT NULL,
  updated_at        timestamptz  NOT NULL,
  version           bigint       NOT NULL DEFAULT 0
);
