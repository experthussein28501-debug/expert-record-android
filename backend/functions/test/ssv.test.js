import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, sign } from 'node:crypto';
import { verifySsvQuery } from '../ssv.js';
const { privateKey, publicKey } = generateKeyPairSync('ec',{namedCurve:'prime256v1'});
const keys=[{keyId:42,pem:publicKey.export({type:'spki',format:'pem'})}];
const now=Date.now();
const raw=`ad_unit=unit&custom_data=session&timestamp=${now}&transaction_id=tx&user_id=user`;
const signature=sign('sha256',Buffer.from(raw),privateKey).toString('base64url');
const query=raw+'&signature='+signature+'&key_id=42';
test('valid AdMob-style signed bytes verify',()=>assert.equal(verifySsvQuery(query,keys,now).user_id,'user'));
test('modified user, replay, duplicate and unsigned params are rejected',()=>{
  assert.throws(()=>verifySsvQuery(query.replace('user_id=user','user_id=other'),keys,now));
  assert.throws(()=>verifySsvQuery(query,keys,now+25*3600000));
  assert.throws(()=>verifySsvQuery(query+'&user_id=other',keys,now));
  assert.throws(()=>verifySsvQuery(query+'&anything=1',keys,now));
  assert.throws(()=>verifySsvQuery(query,[],now));
});
