#!/system/bin/sh
# WattScope QPT rail 探针：在设备上导出 QPT zone 的真实 channel 定义与 rail 电压/负载。
#
# 作用：证明「离线反编译固件得到的映射」就是「设备此刻在跑的那份 device tree」，
#       而不是别的固件版本。它本身不做语义推断，只吐事实。
#
# 用法（设备已 root）：
#   adb push scripts/qpt_rail_probe.sh /data/local/tmp/
#   adb shell su -c "sh /data/local/tmp/qpt_rail_probe.sh"
#
# 输出三段：
#   [1] device-tree 里 power-zones 的 zone → power-channel（大端 u32 对，取第 2 个）
#   [2] /sys/class/powercap/qpt 实际注册的 zone 与名字（两侧应当一一对应）
#   [3] 可读时，debugfs regulator_summary 里对应 rail 的实时电压/负载
#
# 退出码：0 正常；1 无 root；2 没有 power-zones 节点（该机型不支持 QPT 或 BSP 未开）。

set -u

QPT=/sys/class/powercap/qpt
DTROOT=/proc/device-tree
[ -d "$DTROOT" ] || DTROOT=/sys/firmware/devicetree/base
BUCK_BASE=0x9b   # buck 外设地址起点，步长 3

if [ "$(id -u)" != "0" ]; then
    echo "需要 root（QPT 节点与 device-tree 均 root-only）" >&2
    exit 1
fi

# 读 device-tree 的字符串属性（去掉结尾 NUL）
rd_str() { [ -r "$1" ] && tr -d '\0' < "$1" 2>/dev/null; }

# 读 device-tree 的第 $2 个 u32（大端）：dd 取 4 字节 → od 转 hex → 拼成 0x....
rd_u32() {
    _h=$(dd if="$1" bs=1 skip=$(( $2 * 4 )) count=4 2>/dev/null | od -An -tx1 2>/dev/null | tr -d ' \n')
    [ ${#_h} -eq 8 ] && echo "0x$_h"
}

echo "===== [1] device-tree power-zones ====="
PZ=""
for d in $(find "$DTROOT" -maxdepth 5 -type d -name power-zones 2>/dev/null); do
    PZ=$d
    break
done
if [ -z "$PZ" ]; then
    echo "未找到 power-zones 节点：本机 BSP 未启用 QPT power-zones 描述" >&2
    exit 2
fi
echo "节点：$PZ"
printf '%-14s %-10s %-6s %-8s %s\n' zone channel slave periph 推断SMPS

SOC=""
for d in "$PZ"/*/; do
    [ -d "$d" ] || continue
    # power-zones 下第一层是 soc 聚合节点，其子节点才是各 rail
    if [ -d "${d}soc" ] || [ -f "${d}soc/name" ]; then
        SOC="${d}soc"
        break
    fi
done
[ -n "$SOC" ] || SOC="$PZ"

for z in "$SOC"/*/; do
    [ -d "$z" ] || continue
    _zc=$(rd_u32 "$z/power-channels" 1)
    [ -n "$_zc" ] || continue
    _zn=$(basename "$z")
    _slave=$(( _zc >> 8 ))
    _per=$(( _zc & 0xff ))
    if [ $_per -ge $BUCK_BASE ] && [ $(( (_per - BUCK_BASE) % 3 )) -eq 0 ]; then
        _n=$(( (_per - BUCK_BASE) / 3 + 1 ))
        _smps="S$_n"
    else
        _smps="(非 buck 格点)"
    fi
    printf '%-14s %-10s %-6s 0x%02x     %s\n' "$_zn" "$_zc" "$_slave" "$_per" "$_smps"
done

echo
echo "===== [2] /sys/class/powercap/qpt 实际注册的 zone ====="
if [ -d "$QPT" ]; then
    echo "enabled = $(rd_str "$QPT/enabled")"
    for d in "$QPT"/*/; do
        [ -f "$d/name" ] || continue
        for sub in "$d"*/; do
            [ -f "$sub/energy_uj" ] || continue
            printf '%-46s name=%-12s power_uw=%s\n' \
                "$(basename "$sub")" "$(rd_str "$sub/name")" \
                "$([ -f "$sub/power_uw" ] && echo yes || echo no)"
        done
    done
else
    echo "无 $QPT（QPT 未启用或平台不支持）"
fi

echo
echo "===== [3] debugfs regulator 实时状态 ====="
SUM=/sys/kernel/debug/regulator/regulator_summary
if [ -r "$SUM" ]; then
    grep -E 'pmh[0-9]+_[dfgi]_s(1|2|4|5|7|8|9|10)(_| )' "$SUM" 2>/dev/null \
        || echo "（summary 里没有匹配行）"
else
    echo "$SUM 不可读（未挂 debugfs 或内核未开 REGULATOR_DEBUG）"
fi

echo
echo "对照：把 [1] 的 zone/channel 与离线 qpt_rail_map.py 的输出比对，"
echo "      channel 完全一致才说明固件 dump 与设备在跑的是同一份。"
