// Run on an owner-controlled server behind an HTTPS reverse proxy; never started by the app.
import { createServer } from 'node:http';
import { Readable } from 'node:stream';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve, join } from 'node:path';
import { handle } from './receiver.mjs';

if (!process.env.REPORTS_DIR) throw new Error('Set REPORTS_DIR to a private directory outside the web root');
const directory = resolve(process.env.REPORTS_DIR);
await mkdir(directory, { recursive:true, mode:0o700 });
let windowStart = Date.now(), count = 0;
const server = createServer(async (incoming,outgoing) => {
  // A small single-owner inbox: at most 3 attempts per minute, without any timer or background loop.
  if (Date.now()-windowStart >= 60000) { windowStart = Date.now(); count = 0; }
  if (++count > 3) { outgoing.writeHead(429, { 'Retry-After':'60' }); outgoing.end('Please wait'); incoming.resume(); return; }
  try {
    const headers = new Headers();
    for (const [key,value] of Object.entries(incoming.headers)) if (value !== undefined) headers.set(key,Array.isArray(value) ? value.join(',') : value);
    const options = { method:incoming.method, headers };
    if (!['GET','HEAD'].includes(incoming.method)) { options.body = Readable.toWeb(incoming); options.duplex = 'half'; }
    const request = new Request(new URL(incoming.url,'http://localhost'),options);
    const response = await handle(request,(id,body) => writeFile(join(directory,id),body,{ encoding:'utf8', mode:0o600, flag:'wx' }));
    outgoing.writeHead(response.status,Object.fromEntries(response.headers)); outgoing.end(await response.text());
  } catch { outgoing.writeHead(400); outgoing.end('Invalid request'); }
});
server.requestTimeout = 15000;
server.headersTimeout = 10000;
server.listen(Number(process.env.PORT || 8787),'127.0.0.1');
