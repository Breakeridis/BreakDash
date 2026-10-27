// GitHub Actions only. Uses synthetic Java output and mocked fetch, with no real uploads.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { randomBytes } from 'node:crypto';
import { parseShareLink,decryptTemporaryReport,readTemporaryReport } from './read-temporary-report.mjs';

const folder=process.env.BREAKDASH_TEMPORARY_FIXTURE_DIR;
if(!folder) throw new Error('Run on GitHub after generating the Java temporary-sharing fixture');
const file=readFileSync(join(folder,'synthetic.bdr')),link=readFileSync(join(folder,'synthetic-link.txt'),'utf8');
const expected={ version:'synthetic-ci',note:'Synthetic upload test',report:'Synthetic diagnostic data only' };

test('decrypts the actual Java AES-GCM file with its separately shared key',()=>{
  assert.deepEqual(decryptTemporaryReport(file,parseShareLink(link).key),expected);
  assert.ok(!file.includes(Buffer.from(expected.report)));
});
test('never sends the fragment or decryption key to the download host',async()=>{
  const { key }=parseShareLink(link);
  const report=await readTemporaryReport(link,async(address,options)=>{
    assert.equal(address,'https://d.uguu.se/synthetic.bdr');
    assert.ok(!address.includes(key.toString('base64url'))); assert.equal(options.redirect,'error');
    return new Response(file);
  });
  assert.deepEqual(report,expected);
});
test('wrong keys, altered ciphertext and altered format headers fail',()=>{
  assert.throws(()=>decryptTemporaryReport(file,randomBytes(32)));
  const changed=Buffer.from(file); changed[changed.length-1]^=1;
  assert.throws(()=>decryptTemporaryReport(changed,parseShareLink(link).key));
  const header=Buffer.from(file); header[0]^=1;
  assert.throws(()=>decryptTemporaryReport(header,parseShareLink(link).key));
});
test('rejects arbitrary hosts, embedded credentials, non-HTTPS and missing keys',()=>{
  for(const bad of [link.replace('d.uguu.se','uguu.se.evil.test'),link.replace('https:','http:'),link.replace('d.uguu.se','user:pass@d.uguu.se'),link.split('#')[0],link.replace('.bdr#','.html#')]) {
    assert.throws(()=>parseShareLink(bad));
  }
});
test('expired files and oversized responses are reported without disclosing keys',async()=>{
  await assert.rejects(()=>readTemporaryReport(link,async()=>new Response(null,{ status:404 })),/expired/);
  await assert.rejects(()=>readTemporaryReport(link,async()=>new Response(new Uint8Array(250033))),/size limit/);
});
