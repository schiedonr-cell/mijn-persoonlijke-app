import webpush from "web-push";

const APP_ORIGIN = "https://schiedonr-cell.github.io";
const APP_URL = "https://schiedonr-cell.github.io/mijn-persoonlijke-app/";
const VAPID_KEY = "config:vapid";
const DEVICE_PREFIX = "device:";
const DEFAULT_MOVE_TIME = "16:30";
const DEFAULT_RELAX_TIME = "21:00";
const MAX_DEVICE_AGE_MS = 1000 * 60 * 60 * 24 * 120;

const encoder = new TextEncoder();

function json(data, status = 200, origin = "") {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      ...(origin === APP_ORIGIN
        ? {
            "Access-Control-Allow-Origin": origin,
            "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
            "Access-Control-Allow-Headers": "Content-Type, X-Reminder-Token",
            "Access-Control-Max-Age": "86400",
          }
        : {}),
    },
  });
}

function safeText(value, max = 200) {
  return String(value ?? "").trim().slice(0, max);
}

function validTime(value, fallback) {
  const text = safeText(value, 5);
  return /^([01]\d|2[0-3]):[0-5]\d$/.test(text) ? text : fallback;
}

function validTimezone(value) {
  const zone = safeText(value, 80) || "Europe/Amsterdam";
  try {
    new Intl.DateTimeFormat("en-US", { timeZone: zone }).format(new Date());
    return zone;
  } catch {
    return "Europe/Amsterdam";
  }
}

function isValidSubscription(sub) {
  return Boolean(
    sub &&
      typeof sub === "object" &&
      typeof sub.endpoint === "string" &&
      sub.endpoint.startsWith("https://") &&
      sub.keys &&
      typeof sub.keys.p256dh === "string" &&
      typeof sub.keys.auth === "string"
  );
}

function deviceKey(deviceId) {
  return DEVICE_PREFIX + deviceId;
}

function isValidDeviceId(value) {
  return /^[a-zA-Z0-9_-]{12,120}$/.test(String(value || ""));
}

function isValidToken(value) {
  return /^[a-zA-Z0-9_-]{20,160}$/.test(String(value || ""));
}

async function ensureVapid(env) {
  let keys = await env.REMINDERS.get(VAPID_KEY, "json");
  if (keys?.publicKey && keys?.privateKey) return keys;

  keys = webpush.generateVAPIDKeys();
  await env.REMINDERS.put(VAPID_KEY, JSON.stringify(keys));
  return keys;
}

async function readDevice(env, deviceId) {
  if (!isValidDeviceId(deviceId)) return null;
  return env.REMINDERS.get(deviceKey(deviceId), "json");
}

async function writeDevice(env, record) {
  record.updatedAt = Date.now();
  await env.REMINDERS.put(deviceKey(record.deviceId), JSON.stringify(record));
}

function requireToken(record, request) {
  const supplied = request.headers.get("X-Reminder-Token") || "";
  return Boolean(record?.token && supplied && record.token === supplied);
}

function localClock(timeZone, date = new Date()) {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(date);
  const map = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return {
    dateKey: `${map.year}-${map.month}-${map.day}`,
    hhmm: `${map.hour}:${map.minute}`,
  };
}

function timeReached(now, target) {
  return now >= target;
}

function trimDayState(days, keepDate) {
  const entries = Object.entries(days || {}).sort(([a], [b]) => b.localeCompare(a));
  const kept = Object.fromEntries(entries.slice(0, 10));
  if (!kept[keepDate]) kept[keepDate] = {};
  return kept;
}

async function configureWebPush(env) {
  const keys = await ensureVapid(env);
  webpush.setVapidDetails(APP_URL, keys.publicKey, keys.privateKey);
  return keys;
}

async function sendPush(env, subscription, payload) {
  await configureWebPush(env);
  try {
    await webpush.sendNotification(subscription, JSON.stringify(payload), {
      TTL: 60 * 60 * 6,
      urgency: "normal",
    });
    return { ok: true };
  } catch (error) {
    const statusCode = Number(error?.statusCode || 0);
    if (statusCode === 404 || statusCode === 410) {
      return { ok: false, gone: true, statusCode };
    }
    console.error("Push send failed", statusCode, String(error?.message || error));
    return { ok: false, gone: false, statusCode };
  }
}

async function subscribe(request, env, origin) {
  const body = await request.json().catch(() => null);
  const deviceId = safeText(body?.deviceId, 120);
  const token = safeText(body?.token, 160);
  const subscription = body?.subscription;

  if (!isValidDeviceId(deviceId) || !isValidToken(token) || !isValidSubscription(subscription)) {
    return json({ error: "Ongeldige aanmelding." }, 400, origin);
  }

  const existing = await readDevice(env, deviceId);
  if (existing?.token && existing.token !== token) {
    return json({ error: "Niet toegestaan." }, 403, origin);
  }

  const record = {
    ...(existing || {}),
    deviceId,
    token,
    subscription,
    enabled: body?.enabled !== false,
    timezone: validTimezone(body?.timezone),
    moveTime: validTime(body?.moveTime, existing?.moveTime || DEFAULT_MOVE_TIME),
    relaxTime: validTime(body?.relaxTime, existing?.relaxTime || DEFAULT_RELAX_TIME),
    days: existing?.days && typeof existing.days === "object" ? existing.days : {},
    lastSeenAt: Date.now(),
  };

  await writeDevice(env, record);
  return json({ ok: true }, 200, origin);
}

async function updateSettings(request, env, origin) {
  const body = await request.json().catch(() => null);
  const deviceId = safeText(body?.deviceId, 120);
  const record = await readDevice(env, deviceId);
  if (!record || !requireToken(record, request)) return json({ error: "Niet toegestaan." }, 403, origin);

  record.enabled = body?.enabled !== false;
  record.timezone = validTimezone(body?.timezone || record.timezone);
  record.moveTime = validTime(body?.moveTime, record.moveTime || DEFAULT_MOVE_TIME);
  record.relaxTime = validTime(body?.relaxTime, record.relaxTime || DEFAULT_RELAX_TIME);
  record.lastSeenAt = Date.now();
  await writeDevice(env, record);
  return json({ ok: true }, 200, origin);
}

async function updateState(request, env, origin) {
  const body = await request.json().catch(() => null);
  const deviceId = safeText(body?.deviceId, 120);
  const record = await readDevice(env, deviceId);
  if (!record || !requireToken(record, request)) return json({ error: "Niet toegestaan." }, 403, origin);

  const dateKey = /^\d{4}-\d{2}-\d{2}$/.test(String(body?.dateKey || ""))
    ? String(body.dateKey)
    : localClock(record.timezone).dateKey;

  record.days = trimDayState(record.days, dateKey);
  const today = record.days[dateKey] || {};

  if (body?.move && typeof body.move === "object") {
    today.move = {
      done: Boolean(body.move.done),
      level: [1, 2, 3].includes(Number(body.move.level)) ? Number(body.move.level) : null,
    };
  }
  if (body?.relax && typeof body.relax === "object") {
    today.relax = {
      done: Boolean(body.relax.done),
      level: [1, 2, 3].includes(Number(body.relax.level)) ? Number(body.relax.level) : null,
    };
  }
  if ([1, 2, 3].includes(Number(body?.energy))) today.energy = Number(body.energy);

  record.days[dateKey] = today;
  record.lastSeenAt = Date.now();
  await writeDevice(env, record);
  return json({ ok: true }, 200, origin);
}

async function testPush(request, env, origin) {
  const body = await request.json().catch(() => null);
  const deviceId = safeText(body?.deviceId, 120);
  const record = await readDevice(env, deviceId);
  if (!record || !requireToken(record, request) || !isValidSubscription(record.subscription)) {
    return json({ error: "Niet toegestaan." }, 403, origin);
  }

  const result = await sendPush(env, record.subscription, {
    title: "Mijn app",
    body: "Test gelukt. Korte herinneringen kunnen op deze telefoon binnenkomen.",
    tag: "mijn-app-test",
    url: APP_URL,
  });

  if (result.gone) {
    record.subscription = null;
    record.enabled = false;
    await writeDevice(env, record);
    return json({ error: "De meldingstoestemming moet opnieuw worden aangezet." }, 410, origin);
  }
  if (!result.ok) return json({ error: "Testmelding kon niet worden verstuurd." }, 502, origin);

  return json({ ok: true }, 200, origin);
}

async function unsubscribe(request, env, origin) {
  const body = await request.json().catch(() => null);
  const deviceId = safeText(body?.deviceId, 120);
  const record = await readDevice(env, deviceId);
  if (!record || !requireToken(record, request)) return json({ ok: true }, 200, origin);
  await env.REMINDERS.delete(deviceKey(deviceId));
  return json({ ok: true }, 200, origin);
}

async function processDevice(env, keyName, nowDate) {
  const record = await env.REMINDERS.get(keyName, "json");
  if (!record) return;

  if (record.updatedAt && Date.now() - record.updatedAt > MAX_DEVICE_AGE_MS) {
    await env.REMINDERS.delete(keyName);
    return;
  }
  if (!record.enabled || !isValidSubscription(record.subscription)) return;

  const timezone = validTimezone(record.timezone);
  const { dateKey, hhmm } = localClock(timezone, nowDate);
  record.days = trimDayState(record.days, dateKey);
  const today = record.days[dateKey] || {};
  let changed = false;

  if (
    timeReached(hhmm, validTime(record.moveTime, DEFAULT_MOVE_TIME)) &&
    !today.move?.done &&
    today.moveReminderSent !== true
  ) {
    const result = await sendPush(env, record.subscription, {
      title: "Bewegen staat nog open",
      body: "Kleine versie doen? 5–10 minuten is vandaag genoeg.",
      tag: `bewegen-${dateKey}`,
      url: APP_URL,
    });
    if (result.gone) {
      record.subscription = null;
      record.enabled = false;
      changed = true;
    } else if (result.ok) {
      today.moveReminderSent = true;
      changed = true;
    }
  }

  if (
    record.enabled &&
    isValidSubscription(record.subscription) &&
    timeReached(hhmm, validTime(record.relaxTime, DEFAULT_RELAX_TIME)) &&
    !today.relax?.done &&
    today.relaxReminderSent !== true
  ) {
    const result = await sendPush(env, record.subscription, {
      title: "Bewust ontspannen staat nog open",
      body: "Doe vandaag alleen de korte versie. Vijf minuten telt ook.",
      tag: `ontspannen-${dateKey}`,
      url: APP_URL,
    });
    if (result.gone) {
      record.subscription = null;
      record.enabled = false;
      changed = true;
    } else if (result.ok) {
      today.relaxReminderSent = true;
      changed = true;
    }
  }

  if (changed) {
    record.days[dateKey] = today;
    await writeDevice(env, record);
  }
}

async function runReminders(env, scheduledTime) {
  let cursor;
  const nowDate = new Date(scheduledTime || Date.now());
  do {
    const page = await env.REMINDERS.list({ prefix: DEVICE_PREFIX, cursor, limit: 100 });
    for (const key of page.keys) {
      try {
        await processDevice(env, key.name, nowDate);
      } catch (error) {
        console.error("Reminder processing failed", key.name, String(error));
      }
    }
    cursor = page.list_complete ? undefined : page.cursor;
  } while (cursor);
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const origin = request.headers.get("Origin") || "";

    if (request.method === "OPTIONS") {
      if (origin !== APP_ORIGIN) return new Response(null, { status: 403 });
      return new Response(null, {
        status: 204,
        headers: {
          "Access-Control-Allow-Origin": origin,
          "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, X-Reminder-Token",
          "Access-Control-Max-Age": "86400",
        },
      });
    }

    if (request.method === "GET" && url.pathname === "/") {
      return json({ ok: true, service: "mijn-persoonlijke-app-reminders", version: 1 });
    }

    if (request.method === "GET" && url.pathname === "/vapid-public-key") {
      if (origin && origin !== APP_ORIGIN) return json({ error: "Niet toegestaan." }, 403);
      const keys = await ensureVapid(env);
      return json({ publicKey: keys.publicKey }, 200, origin);
    }

    if (origin !== APP_ORIGIN) return json({ error: "Niet toegestaan." }, 403);

    if (request.method === "POST" && url.pathname === "/subscribe") return subscribe(request, env, origin);
    if (request.method === "POST" && url.pathname === "/settings") return updateSettings(request, env, origin);
    if (request.method === "POST" && url.pathname === "/state") return updateState(request, env, origin);
    if (request.method === "POST" && url.pathname === "/test") return testPush(request, env, origin);
    if (request.method === "POST" && url.pathname === "/unsubscribe") return unsubscribe(request, env, origin);

    return json({ error: "Niet gevonden." }, 404, origin);
  },

  async scheduled(controller, env) {
    await runReminders(env, controller.scheduledTime);
  },
};
