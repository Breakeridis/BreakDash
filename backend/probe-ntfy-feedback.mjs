// Opt-in GitHub-only probe. Uses a disposable topic and synthetic recipient; never production feedback keys.
import {readFileSync} from 'node:fs';
import {join} from 'node:path';
import {randomBytes} from 'node:crypto';
import assert from 'node:assert/strict';
import {readLatestFeedback,topicUrl} from './read-ntfy-feedback.mjs';
const folder=process.env.BREAKDASH_TEMPORARY_FIXTURE_DIR;
if(!folder) throw new Error('Synthetic feedback fixture directory is missing');
const topic=`breakdash-ci-${randomBytes(16).toString('hex')}`;
const file=readFileSync(join(folder,'synthetic-feedback.bdn'));
const privatePem=readFileSync(join(folder,'synthetic-feedback-private.pem'),'utf8');
const response=await fetch(topicUrl(topic),{method:'PUT',body:file,redirect:'error',signal:AbortSignal.timeout(30000),headers:{
  'Content-Type':'application/octet-stream','X-Filename':'BreakDash-feedback.bdn','X-Title':'BreakDash encrypted feedback','X-Message':'Encrypted diagnostic report','X-Firebase':'no'
}});
if(!response.ok) throw new Error(`ntfy upload probe failed (HTTP ${response.status})`);
const receipt=await response.json();
assert.equal(receipt.event,'message'); assert.equal(receipt.topic,topic); assert.equal(receipt.attachment?.name,'BreakDash-feedback.bdn'); assert.equal(receipt.attachment?.size,file.length);
const report=await readLatestFeedback(topic,privatePem);
assert.equal(report.version,'synthetic-ci'); assert.equal(report.note,'ntfy synthetic feedback'); assert.equal(report.report,'Synthetic diagnostic data only');
console.log('ntfy fixed-topic upload, discovery and Java-to-Node decryption passed using synthetic data only.');
