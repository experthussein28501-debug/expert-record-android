export const PRODUCT_ID = 'khabir_ad_free';
export const BASE_PLANS = new Set(['monthly', 'quarterly', 'halfyear', 'annual']);
export const HOUR = 3_600_000;

export function addMonths(time, count) {
  if (!Number.isFinite(time)) throw new Error('Invalid date');
  const d = new Date(time);
  const day = d.getUTCDate();
  d.setUTCDate(1);
  d.setUTCMonth(d.getUTCMonth() + count);
  const last = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 0)).getUTCDate();
  d.setUTCDate(Math.min(day, last));
  return d.getTime();
}
export function adsStart(launchAt, firstSeenAt) {
  return Math.max(addMonths(launchAt, 2), addMonths(firstSeenAt, 1));
}
export function grantReward(state, now) {
  const previous = Number(state.rewardUntil || 0);
  const count = previous > now ? Number(state.rewardCount || 0) : 0;
  const next = count + 1;
  return { rewardCount: next % 5, rewardUntil: Math.max(now, previous) + (next === 5 ? 4 : 2) * HOUR };
}
export function verifiedSubscription(data, expectedAccount, now) {
  if (data.externalAccountIdentifiers?.obfuscatedExternalAccountId !== expectedAccount) throw new Error('Account mismatch');
  const lines = (data.lineItems || []).filter(l => l.productId === PRODUCT_ID && BASE_PLANS.has(l.offerDetails?.basePlanId));
  if (!lines.length) throw new Error('Unknown product or plan');
  const entitled = new Set(['SUBSCRIPTION_STATE_ACTIVE', 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD', 'SUBSCRIPTION_STATE_CANCELED']);
  const expiry = Math.max(...lines.map(l => Date.parse(l.expiryTime) || 0));
  return entitled.has(data.subscriptionState) && expiry > now ? expiry : 0;
}
