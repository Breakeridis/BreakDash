// Recipient-side reader. The key is stripped from the URL before any network request.
import { createDecipheriv } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

const magic=Buffer.from('BDR1'),maxBytes=250032;
export function parseShareLink(input) {
  if(typeof input!=='string' || input.length>2048) throw new Error('Invalid BreakDash share link');
  let url; try { url=new URL(input.trim()); } catch { throw new Error('Invalid BreakDash share link'); }
  if(url.protocol!=='https:' || !(url.hostname==='uguu.se' || url.hostname.endsWith('.uguu.se'))
      || url.username || url.password || url.port || url.search || !/^\/[A-Za-z0-9_./-]+\.bdr$/.test(url.pathname)
      || !/^#breakdash-v1=[A-Za-z0-9_-]{43}$/.test(url.hash)) throw new Error('Invalid BreakDash share link');
  const encoded=url.hash.slice('#breakdash-v1='.length),key=Buffer.from(encoded,'base64url');
  if(key.length!==32 || key.toString('base64url')!==encoded) throw new Error('Invalid decryption key');
  url.hash=''; return { downloadUrl:url.href,key };
}

export function decryptTemporaryReport(file,key) {
  if(!Buffer.isBuffer(file) || file.length<33 || file.length>maxBytes || !file.subarray(0,4).equals(magic)
      || key.length!==32) throw new Error('Invalid encrypted report');
  let decoded;
  try {
    const cipher=createDecipheriv('aes-256-gcm',key,file.subarray(4,16));
    cipher.setAAD(magic); cipher.setAuthTag(file.subarray(-16));
    decoded=JSON.parse(Buffer.concat([cipher.update(file.subarray(16,-16)),cipher.final()]).toString('utf8'));
  } catch { throw new Error('Could not decrypt report: incorrect key or damaged file'); }
  if(!decoded || typeof decoded.version!=='string' || typeof decoded.note!=='string' || typeof decoded.report!=='string') throw new Error('Invalid diagnostic payload');
  return { version:decoded.version,note:decoded.note,report:decoded.report };
}

export async function readTemporaryReport(link,fetcher=fetch) {
  const { downloadUrl,key }=parseShareLink(link);
  const response=await fetcher(downloadUrl,{ redirect:'error',signal:AbortSignal.timeout(20000) });
  if(!response.ok) throw new Error(response.status===404 || response.status===410 ? 'Report expired or unavailable' : `Report download failed (HTTP ${response.status})`);
  if(!response.body) throw new Error('Empty report download');
  const reader=response.body.getReader(),chunks=[]; let size=0;
  try {
    while(true) {
      const { value,done }=await reader.read(); if(done) break;
      size+=value.length; if(size>maxBytes) throw new Error('Report exceeds size limit');
      chunks.push(Buffer.from(value));
    }
  } finally { await reader.cancel().catch(()=>{}); reader.releaseLock(); }
  return decryptTemporaryReport(Buffer.concat(chunks),key);
}

if(process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href) {
  try {
    // Supply the complete share link on stdin, keeping it out of command-line arguments and history.
    const result=await readTemporaryReport(readFileSync(0,'utf8').trim());
    process.stdout.write(`${JSON.stringify(result,null,2)}\n`);
  } catch(error) { process.stderr.write(`${error.message}\n`); process.exitCode=1; }
}
