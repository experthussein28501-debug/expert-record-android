import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';
import { onRequest } from 'firebase-functions/v2/https';
import { onMessagePublished } from 'firebase-functions/v2/pubsub';
import { GoogleAuth } from 'google-auth-library';
import { createHash, randomUUID } from 'node:crypto';
import { adsStart, grantReward, verifiedSubscription, PRODUCT_ID } from './policy.js';
import { verifySsvQuery } from './ssv.js';

initializeApp();
const db = getFirestore();
const auth = new GoogleAuth({ scopes: ['https://www.googleapis.com/auth/androidpublisher'] });
const digest = value => createHash('sha256').update(value).digest('hex');
const packageName = () => process.env.PLAY_PACKAGE_NAME || 'com.khabir.app.combined';
const userRef = uid => db.collection('entitlements').doc(uid);
const launchTime = () => Date.parse(process.env.PLAY_LAUNCH_AT || '');
const adsEnabled = () => process.env.ADS_ENABLED === 'true' && Number.isFinite(launchTime());

async function publisher(path, method = 'GET', data) {
  const client = await auth.getClient();
  const result = await client.request({ url: `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(packageName())}/${path}`, method, data, timeout: 20_000 });
  return result.data;
}
async function fetchSubscription(token, uid) {
  const data = await publisher(`purchases/subscriptionsv2/tokens/${encodeURIComponent(token)}`);
  return { data, expiry: verifiedSubscription(data, digest(uid), Date.now()) };
}
async function recordPurchase(token, uid) {
  if (typeof token !== 'string' || token.length < 10 || token.length > 4096) throw new Error('Invalid token');
  const { data, expiry } = await fetchSubscription(token, uid);
  const ref = db.collection('playPurchases').doc(digest(token));
  await db.runTransaction(async tx => {
    const record = await tx.get(ref);
    if (record.exists && record.get('uid') !== uid) throw new Error('Token already assigned');
    tx.set(ref, { uid, token, expiry, checkedAt: Date.now() });
  });
  // The subscription is granted only after Google verified both product and account.
  if (expiry > Date.now() && data.acknowledgementState === 'ACKNOWLEDGEMENT_STATE_PENDING') {
    await publisher(`purchases/subscriptions/${PRODUCT_ID}/tokens/${encodeURIComponent(token)}:acknowledge`, 'POST', {});
  }
  return expiry;
}
async function currentEntitlements(uid) {
  const user = await getAuth().getUser(uid);
  const created = Date.parse(user.metadata.creationTime);
  const records = await db.collection('playPurchases').where('uid', '==', uid).get();
  let subscriptionUntil = 0;
  for (const record of records.docs) {
    // Revalidate refunds, cancellations and expiry; no device boolean grants premium.
    const { expiry } = await fetchSubscription(record.get('token'), uid);
    subscriptionUntil = Math.max(subscriptionUntil, expiry);
    await record.ref.update({ expiry, checkedAt: Date.now() });
  }
  const reward = (await userRef(uid).get()).data() || {};
  const now = Date.now();
  return { serverTime: now, adsEnabled: adsEnabled(), adsStartAt: Number.isFinite(launchTime()) ? adsStart(launchTime(), created) : 8_000_000_000_000,
    rewardUntil: reward.rewardUntil || 0, rewardCount: (reward.rewardUntil || 0) > now ? reward.rewardCount || 0 : 0, subscriptionUntil };
}

export const api = onRequest({ region: 'europe-west1', maxInstances: 10, cors: false }, async (req, res) => {
  res.set('Cache-Control', 'no-store');
  if (req.method !== 'POST') { res.status(405).end(); return; }
  if ((req.rawBody?.length || 0) > 16_384) { res.status(413).end(); return; }
  let identity;
  try {
    const match = /^Bearer (\S+)$/.exec(req.get('authorization') || '');
    if (!match) throw new Error('Missing token');
    identity = await getAuth().verifyIdToken(match[1], true);
    if (identity.firebase?.sign_in_provider !== 'google.com') throw new Error('Google sign-in required');
  } catch { res.status(401).json({ error: 'Authentication required' }); return; }
  try {
    const uid = identity.uid;
    const action = req.path.replace(/^\/+|\/+$/g, '');
    if (action === 'entitlements') { res.json(await currentEntitlements(uid)); return; }
    if (action === 'verifyPurchase') {
      await recordPurchase(req.body?.purchaseToken, uid);
      res.json(await currentEntitlements(uid)); return;
    }
    if (action === 'rewardSession') {
      const e = await currentEntitlements(uid);
      if (!e.adsEnabled || Date.now() < e.adsStartAt || e.subscriptionUntil > Date.now()) { res.status(409).json({ error: 'Rewards unavailable' }); return; }
      const sessionId = randomUUID();
      const session = db.collection('rewardSessions').doc(sessionId);
      await db.runTransaction(async tx => {
        const account = await tx.get(userRef(uid));
        if ((account.get('lastRewardSessionAt') || 0) > Date.now() - 15_000) throw new Error('Rate limited');
        tx.set(userRef(uid), { lastRewardSessionAt: Date.now() }, { merge: true });
        tx.create(session, { uid, createdAt: Date.now(), expiresAt: Timestamp.fromMillis(Date.now() + 20 * 60_000), consumed: false });
      });
      res.json({ sessionId }); return;
    }
    res.status(404).end();
  } catch { res.status(503).json({ error: 'Verification unavailable; retry later' }); }
});

let keyCache = { expires: 0, keys: [] };
async function verifierKeys() {
  if (keyCache.expires > Date.now()) return keyCache.keys;
  const response = await fetch('https://www.gstatic.com/admob/reward/verifier-keys.json', { signal: AbortSignal.timeout(10_000), redirect: 'error' });
  if (!response.ok) throw new Error('Key service unavailable');
  const result = await response.json();
  if (!Array.isArray(result.keys)) throw new Error('Invalid key response');
  keyCache = { expires: Date.now() + 3_600_000, keys: result.keys };
  return result.keys;
}
export const rewardSsv = onRequest({ region: 'europe-west1', maxInstances: 10 }, async (req, res) => {
  if (req.method !== 'GET' || req.originalUrl.length > 8192) { res.status(400).end(); return; }
  try {
    const query = req.originalUrl.slice(req.originalUrl.indexOf('?') + 1);
    const verified = verifySsvQuery(query, await verifierKeys(), Date.now());
    const units = (process.env.ADMOB_REWARDED_UNITS || '').split(',').map(s => s.trim()).filter(Boolean);
    if (!units.includes(verified.ad_unit)) throw new Error('Unknown ad unit');
    const sessionId = verified.custom_data;
    if (!/^[a-f0-9-]{36}$/.test(sessionId)) throw new Error('Invalid session');
    const sessionRef = db.collection('rewardSessions').doc(sessionId);
    const transactionRef = db.collection('rewardTransactions').doc(digest(verified.transaction_id));
    const accountRef = userRef(verified.user_id);
    await db.runTransaction(async tx => {
      const [session, transaction, account] = await Promise.all([tx.get(sessionRef), tx.get(transactionRef), tx.get(accountRef)]);
      if (transaction.exists) return; // A retry of a signed callback is idempotent.
      const signedAt = Number(verified.timestamp);
      if (!session.exists || session.get('uid') !== verified.user_id || session.get('consumed') ||
          signedAt < session.get('createdAt') - 60_000 || signedAt > session.get('expiresAt').toMillis()) throw new Error('Invalid reward session');
      tx.set(accountRef, grantReward(account.data() || {}, Date.now()), { merge: true });
      tx.update(sessionRef, { consumed: true });
      tx.create(transactionRef, { uid: verified.user_id, sessionId, receivedAt: Date.now() });
    });
    res.status(200).end('OK');
  } catch { res.status(400).end('Invalid callback'); }
});

export const playNotifications = onMessagePublished({ topic: 'play-billing-events', region: 'europe-west1', retry: true }, async event => {
  const message = event.data.message.json;
  if (message.packageName !== packageName()) return;
  const token = message.subscriptionNotification?.purchaseToken || message.voidedPurchaseNotification?.purchaseToken;
  if (!token) return;
  const record = await db.collection('playPurchases').doc(digest(token)).get();
  if (record.exists) await recordPurchase(token, record.get('uid'));
});
