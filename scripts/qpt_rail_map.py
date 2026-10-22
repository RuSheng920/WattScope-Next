#!/usr/bin/env python3
"""从 Android 固件 dump 还原 QPT zone → PMIC rail → 供电消费者的完整映射链。

用途：验证 /sys/class/powercap/qpt 下那些 debug-N 到底测的是哪条电源轨，
      不依赖任何猜测或厂商文档，只用设备自己在跑的 device tree。

原理（三步互相锁死）：
  1. dtbo 的 power-zones 节点给出 zone 名 → QPT measurement channel id；
     channel id 的编码是 slave_id << 8 | periph_addr。
  2. 同一份 dtbo 的 regulator-ocp-notifer 集群里，属性名形如 S9F_LEVEL、
     值指向 periph-5b3-supply —— 这给出 periph 地址 ↔ SMPS 编号 + PMIC 字母。
  3. 主 DTB 的 regulator 节点给出 regulator-name，而各节点的 *-supply
     属性指向这些 phandle —— 这给出 rail ↔ 真实消费者。

用法：
    # 一步到位（dtbo 给 zone 与 periph 表，vendor_boot 给主 DTB）
    python scripts/qpt_rail_map.py \
        --dtbo  F:/Rannki-一加15/dtbo_a.img \
        --dtb   F:/Rannki-一加15/vendor_boot_a.img

    # 也可以直接给单个 .dtb 文件
    python scripts/qpt_rail_map.py --dtb board.dtb --dtbo dtbo.img

    # JSON 输出，便于二次处理
    python scripts/qpt_rail_map.py --dtbo ... --dtb ... --json

注意：--dtb 支持 Android boot / vendor_boot 镜像，会自动扫描内嵌的全部 FDT；
      多 board 变体会合并（phandle 各自独立解析，不会串号）。
"""

import argparse
import json
import re
import struct
import sys

FDT_MAGIC = 0xD00DFEED
DTBO_MAGIC = 0xD7B7AB1E

BUCK_PERIPH_BASE = 0x9B  # buck 外设地址起点，步长 3（LDO 步长 1，起点不同）


# ---------------------------------------------------------------- FDT 解析

class Node:
    __slots__ = ("name", "props", "children", "path")

    def __init__(self, name, path):
        self.name = name
        self.props = {}
        self.children = []
        self.path = path

    def walk(self):
        yield self
        for c in self.children:
            yield from c.walk()


def parse_fdt(data, base=0):
    """解析一个 FDT/blob，返回根 Node；魔数不符返回 None。"""
    if data[base:base + 4] != b"\xd0\x0d\xfe\xed":
        return None
    (magic, totalsize, off_struct, off_strings, off_rsv,
     version, last_comp, boot_cpu, size_strings, size_struct) = struct.unpack(
        ">10I", data[base:base + 40])
    if magic != FDT_MAGIC:
        return None

    strings = data[base + off_strings: base + off_strings + size_strings]
    p = base + off_struct
    end = p + size_struct
    root = Node("/", "/")
    stack = [root]

    while p < end:
        tag = struct.unpack(">I", data[p:p + 4])[0]
        p += 4
        if tag == 1:  # FDT_BEGIN_NODE
            nul = data.index(b"\0", p)
            name = data[p:nul].decode("utf-8", "replace")
            p = (nul + 1 + 3) & ~3
            if not stack:
                break
            parent = stack[-1]
            node = Node(name or "/", parent.path.rstrip("/") + "/" + name)
            parent.children.append(node)
            stack.append(node)
        elif tag == 2:  # FDT_END_NODE
            if len(stack) > 1:
                stack.pop()
        elif tag == 3:  # FDT_PROP
            ln, nameoff = struct.unpack(">II", data[p:p + 8])
            p += 8
            raw = data[p:p + ln]
            p = (p + ln + 3) & ~3
            if nameoff < len(strings):
                nul = strings.index(b"\0", nameoff)
                pname = strings[nameoff:nul].decode("utf-8", "replace")
                stack[-1].props[pname] = raw
        elif tag == 4:
            continue
        elif tag == 9:
            break
        else:
            break
    return root


def u32s(raw):
    if not raw or len(raw) % 4:
        return []
    return list(struct.unpack(">%dI" % (len(raw) // 4), raw))


def as_str(raw):
    if not raw:
        return ""
    body = raw.rstrip(b"\0")
    if all(0x20 <= b < 0x7F for b in body.replace(b"\0", b"")):
        return body.replace(b"\0", b" ").decode("ascii", "replace")
    return ""


def find_dtbos(data):
    """返回 DTBO 镜像内各条目的 (offset, size)。"""
    magic, total, hsize, esize, count, eoff, psize, ver = struct.unpack(">8I", data[:32])
    if magic != DTBO_MAGIC:
        return []
    out = []
    for i in range(count):
        o = eoff + i * esize
        dt_size, dt_off = struct.unpack(">2I", data[o:o + 8])
        out.append((dt_off, dt_size))
    return out


def find_fdts(data):
    """扫描镜像内所有 FDT 的偏移（vendor_boot / boot 内嵌 dtb 段）。"""
    out = []
    needle = b"\xd0\x0d\xfe\xed"
    i = data.find(needle)
    while i >= 0:
        tot = struct.unpack(">I", data[i + 4:i + 8])[0]
        if 0x100 < tot <= len(data) - i:
            out.append(i)
            i = data.find(needle, i + tot)
        else:
            i = data.find(needle, i + 4)
    return out


# ---------------------------------------------------- 从 device tree 抽信息

def collect_zones(root):
    """power-zones/soc/* → {zone_name: channel}"""
    zones = {}
    for node in root.walk():
        if node.name != "power-zones":
            continue
        for soc in node.children:
            for z in soc.children:
                ch = u32s(z.props.get("power-channels", b""))
                if len(ch) >= 2:
                    zones[z.name] = ch[1]
    return zones


TOKEN_RE = re.compile(r"^S(\d+)([DFGI])(?:_LEVEL)?$")


def collect_periph_tokens(root):
    """S9F_LEVEL = "...periph-5b3-supply..." → {0x5b3: ('S9F', 9, 'F')}"""
    out = {}
    for node in root.walk():
        for pname, raw in node.props.items():
            m = TOKEN_RE.match(pname)
            if not m:
                continue
            s = as_str(raw)
            pm = re.search(r"periph-([0-9a-fA-F]+)-supply", s)
            if not pm:
                continue
            n, die = int(m.group(1)), m.group(2)
            out[int(pm.group(1), 16)] = (f"S{n}{die}", n, die)
    return out


REG_NAME_RE = re.compile(r"pmh\d+_([dfgi])_s(\d+)(?:_|$)")


def collect_regulators(root):
    """{phandle: {'name': regulator-name, 'path': node path, 'key': (die, n)}}"""
    regs = {}
    for node in root.walk():
        name = as_str(node.props.get("regulator-name", b""))
        if not name:
            continue
        ph = u32s(node.props.get("phandle", b""))
        if not ph:
            continue
        m = REG_NAME_RE.search(name)
        key = (m.group(1), int(m.group(2))) if m else None
        regs[ph[0]] = {"name": name, "path": node.path, "key": key}
    return regs


def collect_consumers(root, phandles):
    """{phandle: [(node_name, prop_name), ...]}，只看 *-supply 属性。"""
    out = {ph: [] for ph in phandles}
    for node in root.walk():
        for pname, raw in node.props.items():
            if not pname.endswith("-supply"):
                continue
            vals = u32s(raw)
            if not vals:
                continue
            ph = vals[0]
            if ph in out:
                out[ph].append((node.name or "/", pname))
    return out


# ------------------------------------------------------------------ 主流程

def load_roots(dtbo_path, dtb_path):
    dtbo_roots, dtb_roots = [], []
    if dtbo_path:
        data = open(dtbo_path, "rb").read()
        for off, size in find_dtbos(data):
            r = parse_fdt(data, off)
            if r:
                dtbo_roots.append(r)
        if not dtbo_roots:  # 也可能是裸 dtb
            r = parse_fdt(data, 0)
            if r:
                dtbo_roots.append(r)
    if dtb_path:
        data = open(dtb_path, "rb").read()
        for off in find_fdts(data):
            r = parse_fdt(data, off)
            if r:
                dtb_roots.append(r)
        if not dtb_roots:
            r = parse_fdt(data, 0)
            if r:
                dtb_roots.append(r)
    return dtbo_roots, dtb_roots


def build(dtbo_roots, dtb_roots):
    zones, periph_tokens = {}, {}
    for r in dtbo_roots:
        zones.update(collect_zones(r))
        periph_tokens.update(collect_periph_tokens(r))

    # periph 地址的高字节是 SPMI slave id，借此把 slave → PMIC 字母固定下来
    slave_to_die = {}
    for addr, (_, _, die) in periph_tokens.items():
        slave_to_die[addr >> 8] = die

    # 多 board 变体：phandle 空间各自独立，逐棵解析后合并消费者
    regs_by_key, consumers_by_key = {}, {}
    for r in dtb_roots:
        regs = collect_regulators(r)
        cons = collect_consumers(r, regs.keys())
        for ph, info in regs.items():
            if not info["key"]:
                continue
            key = (info["key"][0].upper(), info["key"][1])
            regs_by_key.setdefault(key, {})[info["name"]] = info["path"]
            for cname, pname in cons.get(ph, []):
                consumers_by_key.setdefault(key, set()).add(f"{cname}:{pname}")

    def rail_of(die, n):
        names = regs_by_key.get((die, n), {})
        primary = min(names, key=len) if names else None
        prefix = None
        for nm in names:
            m = re.match(r"(pm\w+?)_" + str(die).lower() + r"_", nm)
            if m:
                prefix = m.group(1).upper()
                break
        if prefix is None and die:
            prefix = die_prefix.get(die)
        return {
            "pmic_letter": die,
            "pmic_prefix": prefix,
            "smps_number": n,
            "smps": f"S{n}",
            "regulator_name": primary,
            "regulator_variants": sorted(names),
            "consumers": sorted(consumers_by_key.get((die, n), [])),
        }

    # PMIC 型号不唯一（PMH0110 / PMH0104 等），从 regulator 名反推前缀
    die_prefix = {}
    for (die, _n), names in regs_by_key.items():
        for nm in names:
            m = re.match(r"(pm\w+?)_" + str(die).lower() + r"_", nm)
            if m:
                die_prefix.setdefault(die, m.group(1).upper())
                break

    rows = []
    for zone, ch in zones.items():
        slave, periph = ch >> 8, ch & 0xFF
        token = periph_tokens.get(ch)
        rail = None
        if token:
            _, n, die = token
            rail = rail_of(die.upper(), n)
        # 兜底：按 buck 外设地址格点推算（ocp 映射表缺这条时的退化路径）
        if rail is None and periph >= BUCK_PERIPH_BASE and (periph - BUCK_PERIPH_BASE) % 3 == 0:
            n = (periph - BUCK_PERIPH_BASE) // 3 + 1
            rail = rail_of(slave_to_die.get(slave), n)
        rows.append({
            "zone": zone,
            "channel": ch,
            "slave": slave,
            "periph": periph,
            "rail": rail,
        })

    def sort_key(r):
        rail = r["rail"]
        if not rail:
            return ("~", 99, r["zone"])
        return (rail["pmic_letter"] or "~", rail["smps_number"], r["zone"])

    rows.sort(key=sort_key)
    return rows


def fmt_row(r):
    rail = r["rail"]
    if not rail:
        return f"{r['zone']:<14} 0x{r['channel']:04x}   (无法映射：periph 0x{r['periph']:02x} 不在 buck 格点)"
    mk = rail.get("pmic_prefix") or "?"
    if mk == "?" and rail["regulator_name"]:
        m = re.match(r"(pm\w+?)_", rail["regulator_name"])
        if m:
            mk = m.group(1).upper()
    if rail["pmic_letter"]:
        pmic = f"{mk}-{rail['pmic_letter']}"
    else:
        pmic = f"{mk}/slave{r['slave']}"
    name = rail["regulator_name"] or "?"
    nvar = len(rail["regulator_variants"])
    if nvar > 1:
        name += f" (+{nvar - 1})"
    cons = ", ".join(rail["consumers"][:3])
    if len(rail["consumers"]) > 3:
        cons += f" …(+{len(rail['consumers']) - 3})"
    return f"{r['zone']:<14} 0x{r['channel']:04x}   {pmic:<11} {rail['smps']:<4} {name:<36} {cons}"


def main():
    ap = argparse.ArgumentParser(description="QPT zone → PMIC rail → 消费者 映射还原")
    ap.add_argument("--dtbo", help="dtbo 分区镜像（提供 power-zones 与 periph 映射表）")
    ap.add_argument("--dtb", help="主 DTB 来源：.dtb 文件、boot.img 或 vendor_boot.img")
    ap.add_argument("--json", action="store_true", help="输出 JSON")
    args = ap.parse_args()
    if not args.dtbo and not args.dtb:
        ap.error("至少给一个 --dtbo 或 --dtb")

    dtbo_roots, dtb_roots = load_roots(args.dtbo, args.dtb)
    if not dtbo_roots:
        print("警告：没从 --dtbo 里解析出 FDT", file=sys.stderr)
    if not dtb_roots:
        print("警告：没从 --dtb 里解析出 FDT —— 消费者一列会是空的", file=sys.stderr)

    rows = build(dtbo_roots, dtb_roots)
    if not rows:
        print("没找到 power-zones 节点；确认 dtbo 是不是该机型的分区 dump", file=sys.stderr)
        return 1

    if args.json:
        print(json.dumps(rows, ensure_ascii=False, indent=2))
        return 0

    print(f"共 {len(rows)} 个 QPT zone，来源 FDT：dtbo={len(dtbo_roots)} dtb={len(dtb_roots)}\n")
    print(f"{'zone':<14}{'channel':<9}{'PMIC':<12}{'SMPS':<5}{'regulator-name':<32}consumers (*-supply)")
    print("-" * 118)
    for r in rows:
        print(fmt_row(r))

    print("\n注：channel = slave_id << 8 | periph_addr；buck periph = 0x9b + 3×(n−1)。")
    print("    consumers 是设备树里把该 rail 当 supply 引用的节点，即这条轨真正供的电。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
