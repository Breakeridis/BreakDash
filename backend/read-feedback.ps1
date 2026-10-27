# Read-only retrieval when the user asks to check feedback. No app execution, uploads, or background subscription.
$ErrorActionPreference = 'Stop'
$feedbackPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../.feedback'))
$profile = Get-Content -LiteralPath (Join-Path $feedbackPath 'public-info.json') -Raw | ConvertFrom-Json
Add-Type -AssemblyName System.Security
$protectedBytes = [IO.File]::ReadAllBytes((Join-Path $feedbackPath 'reader-private.dpapi'))
$keyBytes = [Security.Cryptography.ProtectedData]::Unprotect($protectedBytes,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
try {
    $request = @{topic=$profile.topic; privateKey=[Text.Encoding]::UTF8.GetString($keyBytes)} | ConvertTo-Json -Compress
    $request | node (Join-Path $PSScriptRoot 'read-ntfy-feedback.mjs')
    if ($LASTEXITCODE -ne 0) { throw 'Feedback could not be retrieved. See the recipient reader error above.' }
} finally { [Array]::Clear($keyBytes,0,$keyBytes.Length); $request=$null }
