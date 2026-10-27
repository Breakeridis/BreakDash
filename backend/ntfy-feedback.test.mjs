// GitHub-only tests. Synthetic Java ciphertext and disposable recipient keys; all fetches are mocked.
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {join} from 'node:path';
import {generateKeyPairSync} from 'node:crypto';
import {decryptFeedback,readLatestFeedback,attachmentUrl,topicUrl} from './read-ntfy-feedback.mjs';
const folder=process.env.BREAKDASH_TEMPORARY_FIXTURE_DIR;
if(!folder) throw new Error('Run on GitHub after Java creates the synthetic feedback fixture');
const file=readFileSync(join(folder,'synthetic-feedback.bdn')),key=readFileSync(join(folder,'synthetic-feedback-private.pem'),'utf8');
const expected={version:'synthetic-ci',note:'ntfy synthetic feedback',report:'Synthetic diagnostic data only'};
const topic='breakdash-test-123456',now=1700000000000;
const message=()=>({event:'message',id:'synthetic',topic,time:now/1000,attachment:{name:'BreakDash-feedback.bdn',url:'https://ntfy.sh/file/synthetic.bdn',size:file.length,expires:now/1000+10800}});
const mocked=(messages,binary=file)=>async address=>new Response(address.includes('/json?') ? messages.map(m=>JSON.stringify(m)).join('\n') : binary);
test('Node decrypts actual Java RSA-OAEP-SHA256 and AES-GCM output',()=>{
  assert.deepEqual(decryptFeedback(file,key),expected);
  assert.ok(!file.includes(Buffer.from(expected.report)));
});
test('fixed topic discovers a report without a per-report link or key',async()=>{
  const report=await readLatestFeedback(topic,key,async(address,options)=>{
    assert.equal(options.redirect,'error'); assert.ok(!address.includes('PRIVATE'));
    assert.ok([`https://ntfy.sh/${topic}/json?poll=1&since=3h`,'https://ntfy.sh/file/synthetic.bdn'].includes(address));
    return mocked([message()])(address);
  },now);
  assert.equal(report.note,expected.note); assert.equal(report.report,expected.report); assert.equal(report.postedAt,new Date(now).toISOString());
});
test('tampering and another recipient key cannot decrypt',()=>{
  const damaged=Buffer.from(file); damaged[damaged.length-1]^=1; assert.throws(()=>decryptFeedback(damaged,key));
  const other=generateKeyPairSync('rsa',{modulusLength:2048,privateKeyEncoding:{type:'pkcs8',format:'pem'},publicKeyEncoding:{type:'spki',format:'pem'}});
  assert.throws(()=>decryptFeedback(file,other.privateKey));
});
test('rejects external attachment hosts, topic injection and oversized responses',async()=>{
  for(const url of ['https://evil.test/file/id.bdn','http://ntfy.sh/file/id.bdn','https://ntfy.sh@evil.test/file/id.bdn','https://ntfy.sh/file/id.bdn?key=bad']) assert.throws(()=>attachmentUrl(url));
  assert.throws(()=>topicUrl('breakdash-test/other'));
  assert.throws(()=>topicUrl('breakdash-test-123456\n'));
  await assert.rejects(()=>readLatestFeedback(topic,key,async()=>new Response(new Uint8Array(262145)),now),/size limit/);
  const poisoned=message(); poisoned.attachment.url='https://evil.test/file/id.bdn';
  await assert.rejects(()=>readLatestFeedback(topic,key,mocked([poisoned]),now),/attachment URL/);
});
test('expired, absent and damaged newest reports are explicit, never an older report',async()=>{
  const expired=message(); expired.attachment.expires=now/1000-1;
  await assert.rejects(()=>readLatestFeedback(topic,key,mocked([expired]),now),/expired/);
  await assert.rejects(()=>readLatestFeedback(topic,key,mocked([]),now),/No recent/);
  const damaged=Buffer.from(file); damaged[damaged.length-1]^=1;
  await assert.rejects(()=>readLatestFeedback(topic,key,mocked([message()],damaged),now),/damaged/);
});
