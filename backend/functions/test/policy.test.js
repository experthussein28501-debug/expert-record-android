import test from 'node:test';
import assert from 'node:assert/strict';
import { adsStart, addMonths, grantReward, verifiedSubscription, HOUR } from '../policy.js';
const at = s => Date.parse(s + 'T00:00:00Z');

test('two calendar months from launch, at least one month for a new account', () => {
  assert.equal(adsStart(at('2026-09-19'), at('2026-09-19')), at('2026-11-19'));
  assert.equal(adsStart(at('2026-09-19'), at('2026-12-05')), at('2027-01-05'));
  assert.equal(addMonths(at('2026-01-31'), 1), at('2026-02-28'));
});
test('five rewards total twelve hours; next cycle starts at two', () => {
  let state = {}; const now = 1000;
  for (const hours of [2,4,6,8,12,14]) { state = grantReward(state, now); assert.equal(state.rewardUntil, now + hours * HOUR); }
  assert.equal(state.rewardCount, 1);
  const expired = grantReward(state, state.rewardUntil + 1);
  assert.equal(expired.rewardCount, 1);
  assert.equal(expired.rewardUntil, state.rewardUntil + 1 + 2*HOUR);
});
test('only verified known products belonging to account give entitlement', () => {
  const purchase = { subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE', externalAccountIdentifiers: { obfuscatedExternalAccountId:'account' },
    lineItems:[{productId:'khabir_ad_free',offerDetails:{basePlanId:'monthly'},expiryTime:'2027-01-01T00:00:00Z'}] };
  assert.equal(verifiedSubscription(purchase,'account',at('2026-12-01')),at('2027-01-01'));
  assert.throws(()=>verifiedSubscription(purchase,'other',0));
  for (const state of ['SUBSCRIPTION_STATE_PENDING','SUBSCRIPTION_STATE_ON_HOLD','SUBSCRIPTION_STATE_EXPIRED','SUBSCRIPTION_STATE_PAUSED']) {
    assert.equal(verifiedSubscription({...purchase,subscriptionState:state},'account',0),0);
  }
  assert.equal(verifiedSubscription(purchase,'account',at('2027-02-01')),0);
  assert.throws(()=>verifiedSubscription({...purchase,lineItems:[]},'account',0));
});
