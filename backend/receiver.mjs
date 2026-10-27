// Provider-neutral encrypted inbox logic. Storage is supplied by the owner's server.
// This module never decrypts reports or contacts an external service.
const LIMIT = 384000;
const json = (status, message) => Response.json({ message }, { status, headers: { 'Cache-Control': 'no-store' } });

export async function handle(request, store) {
  if (new URL(request.url).pathname !== '/report') return json(404, 'Not found');
  if (request.method !== 'POST') return json(405, 'Use POST');
  if (!request.headers.get('Content-Type')?.startsWith('application/json')) return json(415, 'Use application/json');
  let envelope;
  try {
    if (Number(request.headers.get('Content-Length')) > LIMIT) return json(413, 'Report too large');
    const reader = request.body?.getReader();
    if (!reader) return json(400, 'Empty report');
    const chunks = []; let size = 0;
    try {
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        size += value.byteLength;
        if (size > LIMIT) { await reader.cancel(); return json(413, 'Report too large'); }
        chunks.push(value);
      }
    } finally { reader.releaseLock(); }
    const bytes = new Uint8Array(size); let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk,offset); offset += chunk.length; }
    const body = JSON.parse(new TextDecoder().decode(bytes));
    if (body.v !== 1) throw new Error('version');
    for (const key of ['key','iv','data']) if (typeof body[key] !== 'string' || !/^[A-Za-z0-9+/]+={0,2}$/.test(body[key])) throw new Error('base64');
    if (![256,384,512].includes(atob(body.key).length) || atob(body.iv).length !== 12 || atob(body.data).length < 16) throw new Error('sizes');
    envelope = { v:1, key:body.key, iv:body.iv, data:body.data };
  } catch { return json(400, 'Invalid encrypted report'); }
  const id = `${crypto.randomUUID()}.json`;
  try { await store(id,JSON.stringify(envelope)); }
  catch { return json(503, 'Encrypted report could not be saved'); }
  return json(201, `Encrypted report saved · ${id}`);
}
