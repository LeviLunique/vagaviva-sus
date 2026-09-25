-- F2: cadastros de referência (catalog) e pacientes (patient).
-- Códigos de tamanho fixo em varchar + CHECK de formato (char(n) completa com espaços e não traz
-- vantagem no PostgreSQL); o banco rejeita dado fora do formato mesmo fora da aplicação.

CREATE TABLE health_unit (
  id                uuid PRIMARY KEY,
  cnes              varchar(7)   NOT NULL UNIQUE CHECK (cnes ~ '^[0-9]{7}$'),
  name              varchar(160) NOT NULL,
  type              varchar(20)  NOT NULL CHECK (type IN ('PRIMARY_CARE','SPECIALIZED')),
  municipality_code varchar(7)   NOT NULL CHECK (municipality_code ~ '^[1-5][0-9]{6}$'),
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
  municipality_code varchar(7) NOT NULL CHECK (municipality_code ~ '^[1-5][0-9]{6}$'),
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
  cns               varchar(15)  NOT NULL UNIQUE CHECK (cns ~ '^[12789][0-9]{14}$'),
  cpf               varchar(11)  UNIQUE CHECK (cpf ~ '^[0-9]{11}$'),
  full_name         varchar(160) NOT NULL,
  social_name       varchar(160),
  birth_date        date         NOT NULL,
  municipality_code varchar(7)   NOT NULL CHECK (municipality_code ~ '^[1-5][0-9]{6}$'),
  phone             varchar(16)  NOT NULL CHECK (phone ~ '^\+55[1-9]{2}9[0-9]{8}$'),  -- E.164, ex.: +5511987654321
  preferred_channel varchar(10)  NOT NULL CHECK (preferred_channel IN ('WHATSAPP','SMS')),
  whatsapp_opt_in   boolean      NOT NULL DEFAULT false,
  pregnant          boolean      NOT NULL DEFAULT false,
  disability        boolean      NOT NULL DEFAULT false,
  active            boolean      NOT NULL DEFAULT true,
  created_at        timestamptz  NOT NULL,
  updated_at        timestamptz  NOT NULL,
  version           bigint       NOT NULL DEFAULT 0
);
