# Optional encrypted diagnostic inbox

BreakDash can upload a report only after its owner configures an HTTPS endpoint and RSA public key and taps **Encrypt & upload report**. The default app build has no diagnostics endpoint or public key.

This receiver is a small Node 22 program for an **owner-controlled server**. It writes encrypted JSON files to a private server directory. It makes no outbound network calls, does not decrypt, has no GitHub integration, and uses no third-party storage provider. It is not deployed by the Android workflow. Cloudflare R2 and GitHub issue storage are deliberately absent.

## Envelope

`POST /report`, `Content-Type: application/json`:

```json
{"v":1,"key":"base64 RSA-wrapped AES key","iv":"base64 12-byte nonce","data":"base64 ciphertext plus 16-byte GCM authentication tag"}
```

The plaintext, visible only to the owner during decryption, is JSON containing `version`, `note`, and `report`. Encryption is AES-256-GCM with a random key and nonce per upload. The AES key is wrapped with RSA-OAEP, SHA-256 for both OAEP and MGF1. Public keys use SPKI PEM (`BEGIN PUBLIC KEY`); owner private keys use PKCS#8 PEM (`BEGIN PRIVATE KEY`).

The receiver caps requests at 384 KB, rejects malformed envelope lengths, saves only the four encrypted fields, generates its own filenames, uses exclusive file creation with owner-only permissions, and reports a generic error on storage failures. The single-owner server caps requests at three per minute without a background timer. Apply server authentication/firewall controls and a retention policy for your deployment; the envelope's public key alone cannot authenticate its sender.

## Owner setup (on your server or private cloud terminal)

These are deployment instructions, not commands run during development. No app compilation happens on your computer.

1. Generate a 3072-bit RSA key in a trusted private cloud terminal and download an offline backup of the private key:

   ```sh
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out diagnostics-private.pem
   openssl pkey -in diagnostics-private.pem -pubout -out diagnostics-public.pem
   ```

2. Put only `receiver.mjs` and `server.mjs` on your server. Run with Node 22 and `REPORTS_DIR` pointing at a private directory outside the web root:

   ```sh
   REPORTS_DIR=/var/lib/breakdash/encrypted-reports PORT=8787 node backend/server.mjs
   ```

3. Put an HTTPS reverse proxy in front of `127.0.0.1:8787` and expose only `/report`. The Node listener stays on loopback. Keep storage outside all public/static serving paths. Neither the server nor receiver needs the private RSA key.
4. In BreakDash **Settings → Connections**, enter your HTTPS `/report` URL and paste `diagnostics-public.pem`. Alternatively set repository Actions variables `BREAKDASH_DIAGNOSTICS_URL` and `BREAKDASH_DIAGNOSTICS_PUBLIC_KEY` for signed releases.
5. Preview a report, then send a test note from the head unit. Retrieve its encrypted JSON from your own server.
6. On an owner-controlled machine or private cloud terminal with the backed-up key:

   ```sh
   node backend/decrypt-report.mjs diagnostics-private.pem encrypted-report.json
   ```

The utility prints the decrypted report to that terminal; keep it out of public logs. Do not commit private keys or device reports to the public app repository. Losing the private key makes old reports unrecoverable.

The CI tests use generated ephemeral keys and mock storage to check Android-compatible encryption, authenticated decryption, field filtering, request limits, and storage error handling. They do not send a real report anywhere.
