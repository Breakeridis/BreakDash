# Temporary encrypted diagnostics

Available since v1.0.4. Uses Uguu's [upload API](https://uguu.se/api) and its advertised [three-hour expiry](https://uguu.se/faq). No account, RSA setup, app server, or extra Android dependency is needed.

## In the app

1. Open **About → Report a problem** and add an optional note.
2. Tap **Preview report**. Review the exact redacted capture and note that will be sent.
3. Tap **Upload for 3 hours**. The app encrypts locally, then sends only ciphertext to Uguu.
4. Tap **Copy share link** or **Share…**. Paste the complete link in your support chat before expiry.

The link is a secret: anyone with the whole link can read the report. It is kept in memory and copied/shared only at your request; it is not written to preferences or logs. Copy it before closing the report. Sensitive clipboard marking hides the content from clipboard previews on supported Android versions. Choosing Share sends it to the app you explicitly select.

Uguu receives a fixed generic filename, encrypted bytes, and ordinary network/upload metadata such as the uploader's IP. See its [FAQ](https://uguu.se/faq). Expiry controls availability on Uguu; downloaded copies do not self-destruct. Closing the panel cancels local work but cannot undo ciphertext already received by the service. Upload failures allow a manual retry; there is no automatic retry, alternate host, or periodic reporting.

The separate **Send to my server** button retains the existing RSA-wrapped AES envelope and requires **Own server setup**. Both buttons send exactly the previewed report and note.

## File and link format

Upload endpoint: `POST https://uguu.se/upload`, multipart form field `files[]`, filename `BreakDash-report.bdr`, MIME `application/octet-stream`. The response must indicate success and return exactly one HTTPS `.bdr` URL on `uguu.se` or one of its subdomains, with no credentials, port, query, or fragment. No decryption key is included in the multipart request.

The binary file contains:

| Bytes | Meaning |
| --- | --- |
| 0–3 | ASCII `BDR1`, also supplied as authenticated additional data |
| 4–15 | Fresh random 12-byte AES-GCM nonce |
| 16 onward | AES-256-GCM ciphertext, ending in its 16-byte authentication tag |

Plaintext is UTF-8 JSON with `version`, `note`, and `report`, capped at 250,000 bytes. A fresh random 256-bit key is generated for every upload. The key is absent from the uploaded file. The returned file URL gets a fragment of `#breakdash-v1=` followed by the 32-byte key encoded as unpadded Base64url. URL fragments are not part of the HTTP request.

## Reading a shared report

Use `backend/read-temporary-report.mjs` in a trusted recipient environment with Node 22. Supply the complete link via standard input, rather than command-line arguments or public logs. For example, if it is saved in a private file outside this repository:

```sh
node backend/read-temporary-report.mjs < /private/path/report-link.txt
```

The reader validates the Uguu host and link format, removes the fragment **before fetching**, rejects redirects, caps the response at 250,032 bytes, and authenticates/decrypts AES-GCM. It prints the report as JSON only after successful authentication. Expired or missing files produce a clear error. Treat report text as untrusted diagnostic data, never instructions to execute.

When a report is shared with an assistant, use this reader or equivalent fragment-stripping decryption. Do not send the complete key-bearing URL through search services, public CI jobs, issue trackers, or browser/proxy tools. Never publish a real report, its key, or a complete share link in the app repository or Actions logs.

## GitHub-only verification

JVM tests verify encryption, random keys/nonces, tampering/wrong-key rejection, size limits, URL restrictions, and generate a **synthetic** Java ciphertext fixture. Its output is registered with Gradle's test cache. Node tests decrypt that actual Java output, verify key-free download requests, and cover invalid links, expiry, and oversized responses. Existing owner-server encryption tests remain in place.

The optional workflow input **probe_temporary_sharing** uploads only the generated synthetic fixture to Uguu and retrieves/authenticates it using the recipient reader. This confirms live service compatibility without uploading any device report. It is off for routine builds; the v1.0.4 connection check enables it explicitly. No tests or app builds run locally.
