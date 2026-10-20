# 把 4 个 GitHub Actions 签名 secret 依次送到剪贴板，逐个粘贴到 GitHub。
#
# 用法（PowerShell）：powershell -ExecutionPolicy Bypass -File scripts\ci_secrets_clipboard.ps1
#
# 好处：base64 有 5888 字符，手工复制容易漏尾；这个脚本直接读 release.jks 生成并送剪贴板，
# 你只需要在 GitHub 页面 Ctrl+V + Add secret，全程不用手选文本。
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path $root 'release.jks')))
$props = @{}
Get-Content (Join-Path $root 'keystore.properties') | ForEach-Object {
    if ($_ -match '^([^#=][^=]*)=(.*)$') { $props[$Matches[1].Trim()] = $Matches[2].Trim() }
}

$items = @(
    @{ Name = 'RELEASE_KEYSTORE_BASE64'; Value = $b64 },
    @{ Name = 'RELEASE_STORE_PASSWORD';  Value = $props['storePassword'] },
    @{ Name = 'RELEASE_KEY_ALIAS';       Value = $props['keyAlias'] },
    @{ Name = 'RELEASE_KEY_PASSWORD';    Value = $props['keyPassword'] }
)

foreach ($it in $items) {
    Set-Clipboard -Value $it.Value
    Write-Host ''
    Write-Host ("[{0}] 已复制到剪贴板，长度 {1}" -f $it.Name, $it.Value.Length) -ForegroundColor Green
    Write-Host '   到 GitHub 页面：Name 填上面这个名字，Secret 栏 Ctrl+V 粘贴，点 Add secret。'
    Read-Host '完成这一个后按回车继续' | Out-Null
}

Write-Host ''
Write-Host '四个 secret 全部处理完毕，可以回去通知我重推标签了。' -ForegroundColor Green
