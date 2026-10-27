# One-time recipient-key setup, not an app build or test. Private material stays in the ignored workspace folder.
$ErrorActionPreference = 'Stop'
$workspacePath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$feedbackPath = Join-Path $workspacePath '.feedback'
$protectedPath = Join-Path $feedbackPath 'reader-private.dpapi'
$infoPath = Join-Path $feedbackPath 'public-info.json'
if ((Test-Path -LiteralPath $protectedPath) -and (Test-Path -LiteralPath $infoPath)) {
    Write-Output 'Reusing the existing feedback recipient and fixed topic.'
    exit 0
}
if (Test-Path -LiteralPath $feedbackPath) { throw 'Incomplete feedback setup exists. Recover its key before generating another recipient.' }
New-Item -ItemType Directory -Path $feedbackPath | Out-Null
$acl = New-Object Security.AccessControl.DirectorySecurity
$acl.SetAccessRuleProtection($true,$false)
$ownerSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
foreach ($sid in @($ownerSid, (New-Object Security.Principal.SecurityIdentifier('S-1-5-18')))) {
    $rule = New-Object Security.AccessControl.FileSystemAccessRule($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow')
    $acl.AddAccessRule($rule)
}
Set-Acl -LiteralPath $feedbackPath -AclObject $acl
Push-Location $workspacePath
try {
    @'
import {generateKeyPairSync,randomBytes,createHash} from 'node:crypto';
import {writeFileSync} from 'node:fs';
const pair=generateKeyPairSync('rsa',{modulusLength:3072,publicKeyEncoding:{type:'spki',format:'pem'},privateKeyEncoding:{type:'pkcs8',format:'pem'}});
writeFileSync('.feedback/reader-private.pending.pem',pair.privateKey,{flag:'wx',mode:0o600});
writeFileSync('.feedback/recipient-public.pem',pair.publicKey,{flag:'wx'});
writeFileSync('.feedback/public-info.json',JSON.stringify({topic:'breakdash-feedback-'+randomBytes(16).toString('hex'),publicKeySha256:createHash('sha256').update(pair.publicKey).digest('hex'),createdAt:new Date().toISOString()},null,2),{flag:'wx'});
'@ | node --input-type=module
    if ($LASTEXITCODE -ne 0) { throw 'Recipient key generation failed. Preserve the folder for recovery.' }
    Add-Type -AssemblyName System.Security
    $plainPath = Join-Path $feedbackPath 'reader-private.pending.pem'
    $keyBytes = [IO.File]::ReadAllBytes($plainPath)
    $protectedBytes = [Security.Cryptography.ProtectedData]::Protect($keyBytes,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
    [IO.File]::WriteAllBytes($protectedPath,$protectedBytes)
    [Array]::Clear($keyBytes,0,$keyBytes.Length)
    Remove-Item -LiteralPath $plainPath
    Write-Output 'Created a fixed topic and dedicated RSA recipient. Private key is protected for this Windows user and machine.'
} finally { Pop-Location }
