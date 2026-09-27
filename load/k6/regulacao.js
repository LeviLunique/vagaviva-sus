// Teste de carga (F8): mix de leituras e escritas do ciclo de regulação.
//
//   docker run --rm -i -v "$PWD/load/k6:/scripts" -e BASE_URL=... -e ADMIN_PASSWORD=... -e DEMO_PASSWORD=... \
//     -e RATE=100 -e DURATION=3m grafana/k6 run --summary-export=/scripts/out/summary.json /scripts/regulacao.js
//
// RATE = requisições por segundo no total (≈ 80% leituras e 20% escritas, a proporção da §11:
// ~1.400 leituras/s para ~350 escritas/s no pico). Cada iteração de escrita faz 3 escritas (cadastrar
// paciente, encaminhar, regular), então a taxa de iterações de escrita é RATE × 0,2 ÷ 3.
// RNF-01: p95 < 300 ms nas leituras e < 500 ms nas escritas (medido também no ALB, no CloudWatch).
import http from 'k6/http';
import { check, fail } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RATE = Number(__ENV.RATE || 50);
const DURATION = __ENV.DURATION || '3m';
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'admin@vagaviva.local';
const WRITE_ITERATIONS = Math.max(1, Math.round((RATE * 0.2) / 3));
const READ_RATE = Math.max(1, Math.round(RATE * 0.8));

http.setResponseCallback(http.expectedStatuses(200, 201, 404));

export const options = {
  discardResponseBodies: false,
  scenarios: {
    reads: {
      executor: 'constant-arrival-rate', exec: 'read', rate: READ_RATE, timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: Math.max(10, READ_RATE), maxVUs: Math.max(50, READ_RATE * 4),
    },
    writes: {
      executor: 'constant-arrival-rate', exec: 'write', rate: WRITE_ITERATIONS, timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: Math.max(5, WRITE_ITERATIONS * 2), maxVUs: Math.max(20, WRITE_ITERATIONS * 8),
    },
  },
  thresholds: {
    'http_req_duration{kind:read}': ['p(95)<300'],
    'http_req_duration{kind:write}': ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function login(email, password) {
  const res = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({ email, password }),
    { headers: { 'Content-Type': 'application/json' }, tags: { kind: 'setup' } });
  if (res.status !== 200) {
    fail(`login de ${email} falhou: ${res.status}`);
  }
  return { token: res.json('accessToken'), user: res.json('user') };
}

function auth(token, kind, name) {
  return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, tags: { kind, name } };
}

/** CNS provisório válido (começa com 7; soma ponderada 15..1 múltipla de 11), como na coleção Postman. */
function cns() {
  for (;;) {
    const digits = [7];
    let sum = 7 * 15;
    for (let i = 1; i < 14; i++) {
      const d = Math.floor(Math.random() * 10);
      digits.push(d);
      sum += d * (15 - i);
    }
    const last = (11 - (sum % 11)) % 11;
    if (last < 10) {
      digits.push(last);
      return digits.join('');
    }
  }
}

export function setup() {
  const admin = login(ADMIN_EMAIL, __ENV.ADMIN_PASSWORD);
  const requester = login('requester@vagaviva.local', __ENV.DEMO_PASSWORD);
  const regulator = login('regulator@vagaviva.local', __ENV.DEMO_PASSWORD);
  const scheduler = login('scheduler@vagaviva.local', __ENV.DEMO_PASSWORD);
  const specialties = http.get(`${BASE_URL}/api/v1/specialties`, auth(admin.token, 'setup', 'specialties'))
    .json().filter((s) => s.active).map((s) => s.id);
  if (specialties.length === 0) {
    fail('nenhuma especialidade cadastrada (o seed de demonstração precisa estar carregado)');
  }
  return {
    admin: admin.token, requester: requester.token, regulator: regulator.token, scheduler: scheduler.token,
    schedulerUnit: scheduler.user.healthUnitId, specialties,
  };
}

const pick = (list) => list[Math.floor(Math.random() * list.length)];

const READS = [
  (d) => http.get(`${BASE_URL}/api/v1/referrals?size=20`, auth(d.requester, 'read', 'referrals')),
  (d) => http.get(`${BASE_URL}/api/v1/queues/${pick(d.specialties)}?size=20`, auth(d.regulator, 'read', 'queue')),
  (d) => http.get(`${BASE_URL}/api/v1/appointments?unitId=${d.schedulerUnit}&size=20`, auth(d.scheduler, 'read', 'appointments')),
  (d) => http.get(`${BASE_URL}/api/v1/slots?unitId=${d.schedulerUnit}&size=20`, auth(d.scheduler, 'read', 'slots')),
  (d) => http.get(`${BASE_URL}/api/v1/health-units?type=SPECIALIZED`, auth(d.admin, 'read', 'health-units')),
  (d) => http.get(`${BASE_URL}/api/v1/public/queue-stats`, { tags: { kind: 'read', name: 'queue-stats' } }),
  (d) => http.post(`${BASE_URL}/api/v1/public/queue-position`,
    JSON.stringify({ protocol: `VV-2026-${String(Math.floor(Math.random() * 9999999)).padStart(7, '0')}`, birthDate: '1960-01-01' }),
    { headers: { 'Content-Type': 'application/json' }, tags: { kind: 'read', name: 'queue-position' } }),
];

export function read(data) {
  const res = pick(READS)(data);
  check(res, { 'leitura 2xx/404': (r) => r.status === 200 || r.status === 404 });
}

export function write(data) {
  const patient = http.post(`${BASE_URL}/api/v1/patients`, JSON.stringify({
    cns: cns(), fullName: 'Paciente Carga k6', birthDate: '1975-05-20', municipalityCode: '3550308',
    phone: '+5511999990000', preferredChannel: 'SMS',
  }), auth(data.requester, 'write', 'patient'));
  if (!check(patient, { 'paciente 201': (r) => r.status === 201 })) {
    return;
  }
  const referral = http.post(`${BASE_URL}/api/v1/referrals`, JSON.stringify({
    patientId: patient.json('id'), specialtyId: pick(data.specialties), clinicalJustification: 'Carga k6.',
    acceptsShortNotice: Math.random() < 0.5,
  }), auth(data.requester, 'write', 'referral'));
  if (!check(referral, { 'encaminhamento 201': (r) => r.status === 201 })) {
    return;
  }
  const regulation = http.post(`${BASE_URL}/api/v1/referrals/${referral.json('id')}/regulation`,
    JSON.stringify({ decision: 'APPROVE', riskClass: pick(['RED', 'YELLOW', 'GREEN', 'BLUE']) }),
    auth(data.regulator, 'write', 'regulation'));
  check(regulation, { 'regulação 200': (r) => r.status === 200 });
}
