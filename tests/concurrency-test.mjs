// Fires several users at the SAME seat at the same moment and checks that
// exactly one of them wins the lock, then that only the winner can book it.
//
// Usage (Node 18+, all services running):
//   node tests/concurrency-test.mjs [eventId] [seatNumber] [users]
//   node tests/concurrency-test.mjs 1 A1 6
//
// Pick a seat that is still AVAILABLE. Requests go through the API Gateway.

const GATEWAY = process.env.GATEWAY_URL ?? "http://localhost:8080";
const [eventId = "1", seatNumber = "A1", userCount = "6"] = process.argv.slice(2);
const N = Number(userCount);
const stamp = Date.now();

async function call(path, { method = "GET", token, body } = {}) {
  const res = await fetch(`${GATEWAY}${path}`, {
    method,
    headers: {
      ...(body ? { "Content-Type": "application/json" } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: res.status, text: await res.text() };
}

async function makeUser(i) {
  const email = `race${stamp}_${i}@test.com`;
  const password = "secret1";
  const reg = await call("/auth/register", { method: "POST", body: { name: `Racer ${i}`, email, password } });
  if (reg.status >= 400) throw new Error(`register failed (${reg.status}): ${reg.text}`);
  const login = await call("/auth/login", { method: "POST", body: { email, password } });
  if (login.status !== 200) throw new Error(`login failed (${login.status}): ${login.text}`);
  return { i, token: login.text.trim() };
}

function check(condition, label) {
  console.log(`${condition ? "PASS" : "FAIL"}  ${label}`);
  if (!condition) process.exitCode = 1;
}

const users = await Promise.all(Array.from({ length: N }, (_, i) => makeUser(i + 1)));
console.log(`Created ${users.length} users. All lock seat ${seatNumber} of event ${eventId} at once...\n`);

const lockResults = await Promise.all(
  users.map((u) => call(`/seats/lock/${eventId}/${seatNumber}`, { method: "PUT", token: u.token }))
);
lockResults.forEach((r, k) => console.log(`user ${k + 1}: HTTP ${r.status}  ${r.status === 200 ? "(got the lock)" : r.text}`));

const winners = users.filter((_, k) => lockResults[k].status === 200);
const losers = users.filter((_, k) => lockResults[k].status !== 200);
console.log("");
check(winners.length === 1, `exactly one user obtained the lock (${winners.length})`);
check(losers.every((_, k) => lockResults[users.indexOf(losers[k])].status === 409), "every other user received 409 Conflict");

if (winners.length === 1) {
  const loserBooking = await call(`/bookings?eventId=${eventId}&seatNumber=${seatNumber}`, { method: "POST", token: losers[0].token });
  check(loserBooking.status === 409, `a user without the lock cannot book the seat (HTTP ${loserBooking.status})`);

  const race = await Promise.all([
    call(`/bookings?eventId=${eventId}&seatNumber=${seatNumber}`, { method: "POST", token: winners[0].token }),
    call(`/bookings?eventId=${eventId}&seatNumber=${seatNumber}`, { method: "POST", token: winners[0].token }),
  ]);
  const created = race.filter((r) => r.status === 201).length;
  check(created === 1, `two simultaneous booking requests from the lock owner create exactly one booking (${created})`);

  const mine = await call("/bookings/my", { token: winners[0].token });
  const count = JSON.parse(mine.text).filter((b) => b.seatNumber === seatNumber && String(b.eventId) === String(eventId)).length;
  check(count === 1, `the database holds one booking for this seat (${count})`);
}
console.log(process.exitCode ? "\nRESULT: FAILED" : "\nRESULT: all concurrency checks passed");
