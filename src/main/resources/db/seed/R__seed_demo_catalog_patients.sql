-- Dados de DEMONSTRAÇÃO (RF-11), carregados só nos perfis local e demo/hml. Tudo fictício:
-- CNES com prefixo 99, CNS provisórios e CPFs gerados pelos algoritmos oficiais, telefones +55119999900xx.
-- Repetível e idempotente (ON CONFLICT DO NOTHING): reexecutar não duplica nem sobrescreve.

INSERT INTO health_unit (id, cnes, name, type, municipality_code, municipality_name, address, active, created_at, updated_at, version) VALUES
  ('0199c0de-0000-7000-8000-000000000101', '9900101', 'UBS Vila Esperança', 'PRIMARY_CARE', '3550308', 'São Paulo', 'Rua da Esperança, 100 - Vila Esperança', true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000102', '9900102', 'UBS Jardim das Flores', 'PRIMARY_CARE', '3550308', 'São Paulo', 'Av. das Flores, 250 - Jardim das Flores', true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000103', '9900103', 'UBS Parque Central', 'PRIMARY_CARE', '3550308', 'São Paulo', 'Rua do Parque, 45 - Parque Central', true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000104', '9900104', 'UBS Bonsucesso', 'PRIMARY_CARE', '3518800', 'Guarulhos', 'Rua Bonsucesso, 900 - Bonsucesso', true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000201', '9900201', 'AME Zona Norte', 'SPECIALIZED', '3550308', 'São Paulo', 'Av. Norte, 1500 - Santana', true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000202', '9900202', 'Policlínica Guarulhos', 'SPECIALIZED', '3518800', 'Guarulhos', 'Av. Tiradentes, 2000 - Centro', true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000203', '9900203', 'Hospital Dia Regional', 'SPECIALIZED', '3550308', 'São Paulo', 'Rua da Saúde, 300 - Vila Mariana', true, now(), now(), 0)
ON CONFLICT DO NOTHING;

INSERT INTO health_unit_service_area (health_unit_id, municipality_code) VALUES
  ('0199c0de-0000-7000-8000-000000000201', '3550308'),
  ('0199c0de-0000-7000-8000-000000000202', '3518800')
ON CONFLICT DO NOTHING;

INSERT INTO specialty (id, code, name, type, sensitive, active, created_at) VALUES
  ('0199c0de-0000-7000-8000-000000000301', 'CARDIO', 'Cardiologia', 'CONSULTATION', false, true, now()),
  ('0199c0de-0000-7000-8000-000000000302', 'ORTOP', 'Ortopedia', 'CONSULTATION', false, true, now()),
  ('0199c0de-0000-7000-8000-000000000303', 'OFTALMO', 'Oftalmologia', 'CONSULTATION', false, true, now()),
  ('0199c0de-0000-7000-8000-000000000304', 'NEURO', 'Neurologia', 'CONSULTATION', false, true, now()),
  ('0199c0de-0000-7000-8000-000000000305', 'DERMATO', 'Dermatologia', 'CONSULTATION', false, true, now()),
  ('0199c0de-0000-7000-8000-000000000306', 'PSIQ', 'Psiquiatria', 'CONSULTATION', true, true, now()),
  ('0199c0de-0000-7000-8000-000000000307', 'USG', 'Ultrassonografia', 'EXAM', false, true, now()),
  ('0199c0de-0000-7000-8000-000000000308', 'ECOCARDIO', 'Ecocardiograma', 'EXAM', false, true, now())
ON CONFLICT DO NOTHING;

INSERT INTO patient (id, cns, cpf, full_name, social_name, birth_date, municipality_code, phone, preferred_channel, whatsapp_opt_in, pregnant, disability, active, created_at, updated_at, version) VALUES
  ('0199c0de-0000-7000-8000-000000000401', '774614272172665', '15599822338', 'Ana Demonstração da Silva', NULL, '1954-10-19', '3550308', '+5511999990001', 'WHATSAPP', true, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000402', '706993093203088', '23309400921', 'Bruno Exemplo da Silva', NULL, '1959-04-21', '3550308', '+5511999990002', 'WHATSAPP', true, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000403', '792478510597416', NULL, 'Carla Demonstração da Silva', NULL, '1955-10-11', '3550308', '+5511999990003', 'WHATSAPP', true, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000404', '704997093098451', '91518634133', 'Daniel Demonstração da Silva', NULL, '1946-05-09', '3550308', '+5511999990004', 'SMS', false, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000405', '809516974102577', '62260608590', 'Elaine Demonstração da Silva', NULL, '1945-06-10', '3518800', '+5511999990005', 'WHATSAPP', true, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000406', '739993578349765', NULL, 'Fábio Modelo da Silva', NULL, '1948-08-11', '3550308', '+5511999990006', 'WHATSAPP', true, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000407', '763730989343797', '05434253809', 'Gabriela Fictícia da Silva', NULL, '1964-11-07', '3550308', '+5511999990007', 'WHATSAPP', true, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000408', '987180780966890', '74586732148', 'Heitor Teste da Silva', NULL, '1961-12-25', '3550308', '+5511999990008', 'SMS', false, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000409', '879915197568310', NULL, 'Isabela Demonstração da Silva', NULL, '1981-04-01', '3550308', '+5511999990009', 'WHATSAPP', true, true, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000410', '961950301495816', '97085838639', 'João Demonstração da Silva', NULL, '2002-04-14', '3518800', '+5511999990010', 'WHATSAPP', true, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000411', '915232477474123', '21518101437', 'Karina Demonstração da Silva', NULL, '1982-12-28', '3550308', '+5511999990011', 'WHATSAPP', true, false, true, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000412', '832397669718648', NULL, 'Lucas Exemplo da Silva', NULL, '2002-01-03', '3550308', '+5511999990012', 'SMS', false, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000413', '912475721899317', '79264199403', 'Mariana Teste da Silva', NULL, '1972-11-07', '3550308', '+5511999990013', 'WHATSAPP', true, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000414', '918561136425530', '43498785214', 'Nelson Exemplo da Silva', NULL, '1985-12-07', '3550308', '+5511999990014', 'WHATSAPP', true, true, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000415', '963388711961301', NULL, 'Olívia Demonstração da Silva', NULL, '1971-01-27', '3518800', '+5511999990015', 'WHATSAPP', true, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000416', '766688278685780', '32984427637', 'Paulo Teste da Silva', NULL, '2004-06-27', '3550308', '+5511999990016', 'SMS', false, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000417', '892360529414953', '14888832706', 'Queila Teste da Silva', NULL, '1977-04-06', '3550308', '+5511999990017', 'WHATSAPP', true, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000418', '863276253680991', NULL, 'Rafael Fictícia da Silva', NULL, '2004-07-09', '3550308', '+5511999990018', 'WHATSAPP', true, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000419', '731349819042374', '68510001995', 'Sandra Exemplo da Silva', NULL, '1983-11-23', '3550308', '+5511999990019', 'WHATSAPP', true, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000420', '760332513346241', '25919492635', 'Tiago Demonstração da Silva', NULL, '1990-06-05', '3518800', '+5511999990020', 'SMS', false, false, true, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000421', '888377021987961', NULL, 'Úrsula Demonstração da Silva', NULL, '2002-12-22', '3550308', '+5511999990021', 'WHATSAPP', true, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000422', '740794278369848', '24679024984', 'Vitor Fictícia da Silva', NULL, '1997-07-19', '3550308', '+5511999990022', 'WHATSAPP', true, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000423', '922786977464846', '96963355207', 'Wanda Modelo da Silva', NULL, '2000-03-05', '3550308', '+5511999990023', 'WHATSAPP', true, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000424', '858491770215246', NULL, 'Xavier Teste da Silva', 'Yasmin', '1982-10-08', '3550308', '+5511999990024', 'SMS', false, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000425', '884449896427923', '06610167222', 'Yasmin Modelo da Silva', NULL, '1970-06-17', '3518800', '+5511999990025', 'WHATSAPP', true, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000426', '780100359337769', '29094728257', 'Zeca Modelo da Silva', NULL, '2002-12-26', '3550308', '+5511999990026', 'WHATSAPP', true, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000427', '976076355406406', NULL, 'Benedita Fictícia da Silva', NULL, '1980-01-08', '3550308', '+5511999990027', 'WHATSAPP', true, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000428', '753094627062775', '70925246360', 'Cícero Demonstração da Silva', NULL, '2002-10-13', '3550308', '+5511999990028', 'SMS', false, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000429', '763986423950142', '92338491718', 'Dalva Exemplo da Silva', NULL, '1979-04-04', '3550308', '+5511999990029', 'WHATSAPP', true, false, false, true, now(), now(), 0),
  ('0199c0de-0000-7000-8000-000000000430', '776956808306644', NULL, 'Everaldo Exemplo da Silva', NULL, '1987-04-27', '3518800', '+5511999990030', 'WHATSAPP', true, false, false, true, now(), now(), 0)
ON CONFLICT DO NOTHING;
