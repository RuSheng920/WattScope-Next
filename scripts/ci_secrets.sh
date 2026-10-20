#!/usr/bin/env bash
# 打印配置 GitHub Actions 签名 secrets 所需的四个值。
#
# 用法：在 Git Bash 里执行  scripts/ci_secrets.sh
# 然后把输出逐条粘到 GitHub → Settings → Secrets and variables → Actions → New repository secret。
#
# 注意：输出包含私钥与密码，只在本机终端显示，不要贴到聊天、Issue 或任何公开位置。
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -f release.jks ] || [ ! -f keystore.properties ]; then
    echo "缺少 release.jks 或 keystore.properties，请先在本地准备签名文件" >&2
    exit 1
fi

echo "RELEASE_KEYSTORE_BASE64=$(base64 -w0 release.jks)"
echo "RELEASE_STORE_PASSWORD=$(sed -n 's/^storePassword=//p' keystore.properties)"
echo "RELEASE_KEY_ALIAS=$(sed -n 's/^keyAlias=//p' keystore.properties)"
echo "RELEASE_KEY_PASSWORD=$(sed -n 's/^keyPassword=//p' keystore.properties)"
