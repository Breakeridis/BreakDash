import { constants,createPrivateKey,privateDecrypt,createDecipheriv } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

export const MAX_FEEDBACK_BYTES=251100;
const magic=Buffer.from('BDN1');
export function topicUrl(topic) {
  if(typeof topic!=='string' || !/^[A-Za-z0-9_-]{16,64}$/.test(topic) || /[\r\n]/.test(topic)) throw new Error('Invalid feedback topic');
  return `https://ntfy.sh/${topic}`;
}
export function decryptFeedback(file,privatePem) {
  if(!Buffer.isBuffer(file) || file.length<291 || file.length>MAX_FEEDBACK_BYTES || !file.subarray(0,4).equals(magic)) throw new Error('Invalid feedback envelope');
  const wrappedLength=file.readUInt16BE(4),nonceAt=6+wrappedLength;
  if(wrappedLength<256 || wrappedLength>1024 || nonceAt+28>=file.length) throw new Error('Invalid feedback envelope');
  let payload;
  try {
    const key=privateDecrypt({key:createPrivateKey(privatePem),padding:constants.RSA_PKCS1_OAEP_PADDING,oaepHash:'sha256'},file.subarray(6,nonceAt));
    if(key.length!==32) throw new Error('Invalid AES key');
    const aes=createDecipheriv('aes-256-gcm',key,file.subarray(nonceAt,nonceAt+12)); aes.setAAD(magic); aes.setAuthTag(file.subarray(-16));
    payload=JSON.parse(Buffer.concat([aes.update(file.subarray(nonceAt+12,-16)),aes.final()]).toString('utf8'));
  } catch { throw new Error('Feedback cannot be decrypted with this recipient key, or was damaged'); }
  if(!payload || typeof payload.version!=='string' || typeof payload.note!=='string' || typeof payload.report!=='string') throw new Error('Invalid diagnostic payload');
  return {version:payload.version,note:payload.note,report:payload.report};
}
export async function limitedDownload(address,limit,fetcher=fetch) {
  const response=await fetcher(address,{redirect:'error',signal:AbortSignal.timeout(20000)});
  if(!response.ok) throw new Error(response.status===404 || response.status===410 ? 'Feedback attachment expired or unavailable' : `Feedback request failed (HTTP ${response.status})`);
  if(!response.body) throw new Error('Empty feedback response');
  const reader=response.body.getReader(),chunks=[]; let length=0;
  try {
    while(true) { const {value,done}=await reader.read(); if(done) break; length+=value.length;
      if(length>limit) throw new Error('Feedback response exceeds size limit'); chunks.push(Buffer.from(value)); }
  } finally { await reader.cancel().catch(()=>{}); reader.releaseLock(); }
  return Buffer.concat(chunks);
}
export function attachmentUrl(value) {
  let url; try { url=new URL(value); } catch { throw new Error('Invalid ntfy attachment URL'); }
  if(url.origin!=='https://ntfy.sh' || url.username || url.password || url.search || url.hash
      || !/^\/file\/[A-Za-z0-9_-]+(?:\.[A-Za-z0-9]{1,16})?$/.test(url.pathname)) throw new Error('Invalid ntfy attachment URL');
  return url.href;
}
export async function readLatestFeedback(topic,privatePem,fetcher=fetch,now=Date.now()) {
  const inbox=`${topicUrl(topic)}/json?poll=1&since=3h`; let reports=[];
  for(let attempt=0;attempt<2;attempt++) {
    const messages=(await limitedDownload(inbox,262144,fetcher)).toString('utf8');
    let parsed; try { parsed=messages.split('\n').filter(Boolean).map(line=>JSON.parse(line)); } catch { throw new Error('Invalid inbox response'); }
    reports=parsed.map((message,index)=>({...message,index})).filter(m=>m.event==='message' && m.topic===topic && m.attachment?.name==='BreakDash-feedback.bdn')
      .sort((a,b)=>b.time-a.time || b.index-a.index);
    if(reports.length || attempt===1) break;
    // ntfy.sh batches cache writes asynchronously (about one second). Only this finite manual read retries.
    await new Promise(resolve=>setTimeout(resolve,1500));
  }
  if(!reports.length) throw new Error('No recent feedback found. Send feedback from BreakDash and check within 3 hours.');
  // Fail visibly on a damaged, forged, or expired newest report rather than quietly returning an older one.
  const latest=reports[0],attachment=latest.attachment;
  if(!Number.isFinite(latest.time) || !Number.isFinite(attachment.expires) || attachment.expires*1000<=now) throw new Error('Latest feedback attachment has expired. Send a new report.');
  if(!Number.isInteger(attachment.size) || attachment.size>MAX_FEEDBACK_BYTES || attachment.size<291) throw new Error('Invalid feedback attachment size');
  const file=await limitedDownload(attachmentUrl(attachment.url),MAX_FEEDBACK_BYTES,fetcher);
  if(file.length!==attachment.size) throw new Error('Feedback attachment size does not match');
  return {postedAt:new Date(latest.time*1000).toISOString(),expiresAt:new Date(attachment.expires*1000).toISOString(),...decryptFeedback(file,privatePem)};
}
if(process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href) {
  try {
    const input=JSON.parse(readFileSync(0,'utf8'));
    process.stdout.write(`${JSON.stringify(await readLatestFeedback(input.topic,input.privateKey),null,2)}\n`);
  } catch(error) { process.stderr.write(`${error.message}\n`); process.exitCode=1; }
}
