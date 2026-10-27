// Opt-in GitHub-only connection probe. Uploads synthetic test data, never a device report.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { parseShareLink,readTemporaryReport } from './read-temporary-report.mjs';

const folder=process.env.BREAKDASH_TEMPORARY_FIXTURE_DIR;
if(!folder) throw new Error('Missing synthetic fixture directory');
const file=readFileSync(join(folder,'synthetic.bdr'));
const { key }=parseShareLink(readFileSync(join(folder,'synthetic-link.txt'),'utf8'));
const form=new FormData(); form.append('files[]',new Blob([file],{ type:'application/octet-stream' }),'BreakDash-report.bdr');
const response=await fetch('https://uguu.se/upload',{ method:'POST',body:form,redirect:'error',signal:AbortSignal.timeout(30000) });
if(!response.ok) throw new Error(`Uguu probe upload failed (HTTP ${response.status})`);
const metadata=await response.json();
if(metadata.success!==true || !Array.isArray(metadata.files) || metadata.files.length!==1) throw new Error('Uguu did not accept the synthetic file');
const shareLink=`${metadata.files[0].url}#breakdash-v1=${key.toString('base64url')}`;
assert.deepEqual(await readTemporaryReport(shareLink),{ version:'synthetic-ci',note:'Synthetic upload test',report:'Synthetic diagnostic data only' });
console.log('Uguu synthetic upload, download and authenticated decryption passed. No device data was uploaded.');
