# QPT debug zone 溯源与验证

本文回答一个问题：`/sys/class/powercap/qpt` 下那些 `debug-N` 到底测的是哪条电源轨，
以及**这个结论怎么被证实/证伪**。

结论本身不是本文重点，方法论才是——同一套方法可以拿去验证任何机型、任何固件版本。

## 一、结论

一加 15（SM8850，PMH0110 系列 PMIC）实测映射：

| QPT zone | channel | PMIC | SMPS | regulator | 语义 |
|---|---|---|---|---|---|
| `debug-0` | `0x5b3` | PMH0110-F (slave 5) | S9 | `pmh0110_f_s9_level` | **VDD_MXC**，多媒体子系统的 CX 轨 |
| `debug-2` | `0x6a7` | PMH0110-G (slave 6) | S5 | `pmh0110_g_s5_level` | **VDD_CX**，应用处理器核心域 |
| `debug-4` | `0x3ad` | PMH0110-D (slave 3) | S7 | `pmh0110_d_s7_level` | **VDD_MM**，多媒体子系统的 MX 轨 |
| `debug-1` | `0x8b6` | PMH0110-I (slave 8) | S10 | `pmh0110_i_s10_level` | **VDD_MX**，应用处理器 MX 轨 |

其余 zone 的完整映射用 `scripts/qpt_rail_map.py` 一条命令生成（见下）。

## 二、结论是怎么推出来的

三条独立信息互相锁死，缺一不可：

**① zone 名 → channel id**：`dtbo` 的 `power-zones` 节点里直接写着

```
debug-0 { power-channels = <phandle 0x5b3>; }
```

**② channel id → SPMI 外设地址**：channel 的编码是 `slave_id << 8 | periph_addr`，
而 PMIC 上 buck 的外设地址是等差数列 `0x9b + 3×(n−1)`（n = SMPS 编号），LDO 步长为 1。
同一份 `dtbo` 里 `regulator-ocp-notifer` 集群把这件事写死了：

```
S1D_LEVEL  = "...:periph-39b-supply:0"     S7D_LEVEL  = "...:periph-3ad-supply:0"
S9F_LEVEL  = "...:periph-5b3-supply:0"     S5G_LEVEL  = "...:periph-6a7-supply:0"
S10I_LEVEL = "...:periph-8b6-supply:0"
```

`regulator-ocp-notifer` 集群里 18 条 regulator 地址 **18/18** 命中格点，
21 个 zone 的低字节 **21/21** 命中格点。
如果 channel 只是"按顺序编号的序号"，不可能同时满足这个等差结构——这是排除巧合的关键。

**③ SMPS → 真实负载**：主 DTB 里 regulator 节点给出 `regulator-name`，
而各节点用 `*-supply` 属性引用这些 phandle，于是能反查出"这条轨给谁供电"：

```
pmh0110_g_s5_level  ← clock-controller@100000:vdd_cx-supply
                    ← clock-controller@3d90000:vdd_cx-supply
                    ← remoteproc-soccp / mss / cdsp :cx-supply
pmh0110_d_s7_level  ← clock-controller@{20f0000,24f0000,956d000}:vdd_mm-supply
pmh0110_f_s9_level  ← clock-controller@…:vdd_mxc-supply
                    ← clock-controller@…:vdd_mm_mxc_voter-supply
pmh0110_i_s10_level ← clock-controller@{100000,1760000,3d90000,…}:vdd_mx-supply
```

`vdd_cx` / `vdd_mx` / `vdd_mm` / `vdd_mxc` 的命名由消费者自己声明，不是推断。

## 三、三级验证

强度递增。L1 不需要设备，L3 才是真正的因果证据。

### L1 静态自洽（离线，秒级）

```bash
python scripts/qpt_rail_map.py \
    --dtbo <dtbo_a.img> \
    --dtb  <vendor_boot_a.img>
```

期望：输出一张 21 行的表，其中 `debug-0/2/4` 三行的 regulator 与消费者列如第一节所示。

判定：
- 消费者列**非空**且指向 `vdd_cx` / `vdd_mm` / `vdd_mxc` → 通过
- 消费者列为空但 regulator 名存在 → 该 rail 可能是 always-on 或仅由 RPMh 内部消费者持有，
  语义仍可从名字判定，但强度下降一档
- channel 低字节不在 `0x9b+3n` 格点 → 该机型的 PMIC 地址布局不同，脚本的兜底无效，
  只能靠 L2/L3

这一步证明的是"固件里是这么写的"，**不证明**"设备上跑的就是这份固件"。

### L2 运行时一致（设备，1 分钟）

```bash
adb push scripts/qpt_rail_probe.sh /data/local/tmp/
adb shell su -c "sh /data/local/tmp/qpt_rail_probe.sh"
```

期望输出三段：device-tree 的 zone/channel 表、QPT 实际注册的 zone 列表、regulator 实时状态。

判定：
- **[1] 的 channel 与 L1 输出逐行一致** → 固件 dump 与设备在跑的是同一份，L1 结论可直接搬到设备上
- 不一致 → L1 用了错误的固件镜像（比如 OTA 前后的 dtbo），以 L2 的 channel 为准重跑 L1
- [3] 能读到 `pmh0110_*_s9_level` 等行 → 顺带确认这些 regulator 确实被内核实例化（没有被裁掉）

设备上 zone 的编号（`qpt:0:4` 之类）与 zone 名的对应关系也由这一步给出，
不要假设"编号顺序 = 定义顺序"。

### L3 因果实验（设备，10 分钟）

这是唯一能把"名字对上了"升级成"行为对得上"的环节。

采样函数（root，1 Hz）：

```sh
snap() {
  for d in /sys/class/powercap/qpt/qpt:0/qpt:0:*/; do
    n=$(tr -d '\0' < "$d/name" 2>/dev/null)
    p=$(cat "$d/power_uw" 2>/dev/null)
    printf '%s=%s ' "$n" "${p:-0}"
  done; echo
}
```

实验矩阵：每组先在**息屏静置 60 s**采基线，再施加负载 60 s，逐 zone 求均值。

| 实验 | 施加方式 | 主看 | 预期 | 推翻条件 |
|---|---|---|---|---|
| A 静置 | 息屏 60 s | 三条 baseline | `debug-0`/`debug-4` 接近 0；`debug-2` 数十~数百 mW | `debug-0` 静置就有 W 级 → 不是 MXC |
| B 大核满载 | `taskset -c 6-7 yes > /dev/null` | `debug-2` | 显著上升 | **debug-2 不动 → CX 结论直接推翻** |
| C GPU 满载 | 游戏 / 3D 压测 | `gpu` zone，`debug-2` 次之 | gpu 主导 | `gpu` zone 不动 → 通道映射错 |
| D 4K 录像 | 相机录 60 s | `debug-0` 与 `debug-4` | **同步上升**，比值稳定 | 只有一条动 → MMCX 拆分假设错 |
| E UFS 顺序写 | `dd if=/dev/zero of=/data/local/tmp/t bs=4M count=512` | `debug-2` | 小幅上升（NoC/DDR 逻辑在 CX） | 完全不相关 → CX 不含内存逻辑 |

B 与 D 是两个决定性实验：

- **B 检验 `debug-2 = CX`**。CX 是 CPU 核心域的供电，大核满载时电流必然抬升，
  这是硅片级的因果关系，不受 DVFS 策略影响。若 `debug-2` 在 B 里毫无响应，
  前面所有推断作废。
- **D 检验 `debug-0`/`debug-4` 是 MMCX 的一对**。MM 与 MXC 分别供多媒体子系统的
  MX 侧与 CX 侧，相机录像会同时拉动两者。若两者相关系数 < 0.5，
  说明它们不是同一子系统的配套轨，语义需重新解释。

更省事的做法：WattScope 已经把每 tick 的 zone 功耗落库，导出一段包含
"游戏 / 录像 / 待机"的会话 CSV，直接跑回归：

```python
import pandas as pd
df = pd.read_csv("export.csv")
for zone in ["debug-0", "debug-2", "debug-4"]:
    print(df[[zone, "cpu_load", "gpu_load", "fps"]].corr()[zone])
```

判定：`debug-2` 与 CPU 负载相关性最高；`debug-0`/`debug-4` 彼此相关性 > 0.9
且与 GPU/相机场景强相关。

## 四、什么结果会推翻本文结论

明确列出，避免结论不可证伪：

1. **L2 发现 channel 与 L1 不一致** → 固件版本不同，本文的一加 15 数字失效（方法仍有效）。
2. **B 实验中 `debug-2` 对 CPU 满载无响应** → `debug-2 ≠ CX`。
3. **D 实验中 `debug-0` 与 `debug-4` 不相关** → 二者不是 MMCX 配套轨。
4. **A 静置时 `debug-0`/`debug-4` 有 W 级功耗** → 它们不是按需开关的多媒体轨。
5. **另一台 SM8850 机型复用本文数字但对不上** → zone 的 `debug-N` 由 OEM 在 `power-zones`
   里自行分配，编号不跨机型通用；必须用 `qpt_rail_map.py` 重新跑一遍。

第 5 点尤其重要：`debug-N` 是**占位名**，不是稳定的硬件标识。
同一个 SM8850 平台，不同 OEM 完全可能把 debug-0 指到别的通道上。

## 五、复现清单

```bash
# 1. 拿到固件（分区 dump 或 OTA 包解出的 dtbo / vendor_boot）
#    一加 15 的 dump 在本机 F:\Rannki-一加15

# 2. 离线还原映射
python scripts/qpt_rail_map.py --dtbo dtbo_a.img --dtb vendor_boot_a.img

# 3. 设备端核对运行时事实
adb push scripts/qpt_rail_probe.sh /data/local/tmp/
adb shell su -c "sh /data/local/tmp/qpt_rail_probe.sh"

# 4. 行为验证（按第三节实验矩阵）
```

工具依赖：Python 3（仅标准库）、设备 root。不需要 dtc、不需要装包。
