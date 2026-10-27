// GitHub Actions only: storage is mocked, with no actual device data or network calls.
import test from 'node:test';
import assert from 'node:assert/strict';
import { handle } from './receiver.mjs';
import { decryptReport } from './decrypt-report.mjs';

const pair = await crypto.subtle.generateKey({ name:'RSA-OAEP', modulusLength:2048, publicExponent:new Uint8Array([1,0,1]), hash:'SHA-256' },true,['encrypt','decrypt']);
const base64 = value => Buffer.from(value).toString('base64');
const privatePem = `-----BEGIN PRIVATE KEY-----\n${base64(await crypto.subtle.exportKey('pkcs8',pair.privateKey))}\n-----END PRIVATE KEY-----`;
async function envelope() {
  const key = await crypto.subtle.generateKey({ name:'AES-GCM', length:256 },true,['encrypt']);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const payload = new TextEncoder().encode(JSON.stringify({ report:'MCU source = 4', note:'Radio seek issue', version:'1.0.0' }));
  return { v:1, iv:base64(iv), key:base64(await crypto.subtle.encrypt('RSA-OAEP',pair.publicKey,await crypto.subtle.exportKey('raw',key))), data:base64(await crypto.subtle.encrypt({ name:'AES-GCM', iv },key,payload)) };
}
const request = body => new Request('https://owner.example/report',{ method:'POST', headers:{ 'Content-Type':'application/json' }, body:JSON.stringify(body) });

test('stores only encrypted fields on the owner server',async () => {
  const stored = [], body = await envelope(); body.note = 'accidental plaintext';
  const response = await handle(request(body),async (...args) => stored.push(args));
  assert.equal(response.status,201); assert.equal(stored.length,1);
  assert.deepEqual(Object.keys(JSON.parse(stored[0][1])),['v','key','iv','data']);
  assert.ok(!stored[0][1].includes('Radio seek issue')); assert.ok(!stored[0][1].includes('accidental plaintext'));
  assert.match(stored[0][0],/^[0-9a-f-]+\.json$/);
});
test('owner utility decrypts Android-compatible OAEP SHA256 and AES-GCM',async () => {
  assert.deepEqual(decryptReport(await envelope(),privatePem),{ report:'MCU source = 4', note:'Radio seek issue', version:'1.0.0' });
});
test('tampered ciphertext fails authenticated decryption',async () => {
  const body = await envelope(), damaged = Buffer.from(body.data,'base64'); damaged[0] ^= 1; body.data = damaged.toString('base64');
  assert.throws(() => decryptReport(body,privatePem));
});
test('rejects malformed envelopes without saving',async () => {
  assert.equal((await handle(request({ v:1,key:'bad',iv:'bad',data:'bad' }),() => { throw new Error('No write expected'); })).status,400);
});
test('caps streamed request sizes',async () => {
  assert.equal((await handle(request({ data:'a'.repeat(400000) }),() => {})).status,413);
});
test('does not disclose internal storage details on failure',async () => {
  const response = await handle(request(await envelope()),() => { throw new Error('private directory path'); });
  assert.equal(response.status,503); assert.ok(!(await response.text()).includes('private directory path'));
});
