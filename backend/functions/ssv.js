import { verify } from 'node:crypto';

export function verifySsvQuery(rawQuery, keys, now) {
  // Verify the original bytes, before URL decoding or reordering any parameter.
  const marker = rawQuery.indexOf('&signature=');
  if (marker <= 0) throw new Error('Missing signature');
  const signed = rawQuery.slice(0, marker);
  const params = new URLSearchParams(rawQuery);
  for (const name of ['signature', 'key_id', 'transaction_id', 'timestamp', 'user_id', 'custom_data', 'ad_unit']) {
    if (params.getAll(name).length !== 1 || !params.get(name)) throw new Error('Invalid callback parameters');
  }
  if (!/^signature=[^&]+&key_id=\d+$/.test(rawQuery.slice(marker + 1))) throw new Error('Unsigned parameter');
  const key = keys.find(k => String(k.keyId) === params.get('key_id'));
  if (!key || !verify('sha256', Buffer.from(signed), key.pem, Buffer.from(params.get('signature'), 'base64url'))) throw new Error('Invalid signature');
  const timestamp = Number(params.get('timestamp'));
  if (!Number.isFinite(timestamp) || timestamp > now + 60_000 || now - timestamp > 24 * 3_600_000) throw new Error('Expired callback');
  return Object.fromEntries(params);
}
