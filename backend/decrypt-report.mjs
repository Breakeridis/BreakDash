// Owner-side utility. Never deploy a private key with the receiver.
import { createPrivateKey, privateDecrypt, constants, createDecipheriv } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

export function decryptReport(envelope,privatePem) {
  if (envelope.v !== 1) throw new Error('Unsupported envelope version');
  const key = privateDecrypt({ key:createPrivateKey(privatePem), padding:constants.RSA_PKCS1_OAEP_PADDING, oaepHash:'sha256' },Buffer.from(envelope.key,'base64'));
  const encrypted = Buffer.from(envelope.data,'base64');
  const decipher = createDecipheriv('aes-256-gcm',key,Buffer.from(envelope.iv,'base64'));
  decipher.setAuthTag(encrypted.subarray(-16));
  return JSON.parse(Buffer.concat([decipher.update(encrypted.subarray(0,-16)),decipher.final()]).toString('utf8'));
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  if (process.argv.length !== 4) {
    process.stderr.write('Usage: node backend/decrypt-report.mjs PRIVATE_KEY.pem ENCRYPTED_REPORT.json\n'); process.exitCode = 1;
  } else {
    const result = decryptReport(JSON.parse(readFileSync(process.argv[3],'utf8')),readFileSync(process.argv[2],'utf8'));
    process.stdout.write(`${JSON.stringify(result,null,2)}\n`);
  }
}
