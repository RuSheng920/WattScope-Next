package io.github.rusheng920.wattscope.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rusheng920.wattscope.R
import io.github.rusheng920.wattscope.core.sysinfo.HardwareStatic
import io.github.rusheng920.wattscope.core.sysinfo.LiveStatus
import io.github.rusheng920.wattscope.core.sysinfo.formatFreq
import io.github.rusheng920.wattscope.core.sysinfo.formatKb
import io.github.rusheng920.wattscope.core.sysinfo.formatUptime
import io.github.rusheng920.wattscope.ui.components.ActionRow
import io.github.rusheng920.wattscope.ui.components.MiuixCard
import io.github.rusheng920.wattscope.ui.components.SectionTitle
import io.github.rusheng920.wattscope.ui.theme.LocalStatusColors
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 概览页（移植自 OpenMonitor 的 Overview Dashboard）。
 *
 * 与仪表盘的分工：仪表盘回答「功率花在哪」，概览回答「这台机器现在是什么状态」。
 * 因此这里的每一块都是**只读快照**，不参与记录、不落库，1Hz 刷新，页面不可见即停。
 */
@Composable
fun OverviewScreen(
    hardware: HardwareStatic?,
    live: LiveStatus?,
    onOpenProcesses: () -> Unit,
    bottomPadding: Dp = 24.dp,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle(stringResource(R.string.overview_group_device)) }
        item { DeviceCard(hardware) }

        item { SectionTitle(stringResource(R.string.overview_group_cpu)) }
        item { CpuCard(hardware, live) }

        item { SectionTitle(stringResource(R.string.overview_group_memory)) }
        item { MemoryCard(live) }

        item { SectionTitle(stringResource(R.string.overview_group_gpu)) }
        item { GpuCard(hardware, live) }

        item { SectionTitle(stringResource(R.string.overview_group_cache)) }
        item { CacheCard(hardware) }

        item { SectionTitle(stringResource(R.string.overview_group_system)) }
        item { SystemCard(live) }

        item {
            MiuixCard(contentPadding = 0.dp) {
                ActionRow(
                    title = stringResource(R.string.overview_open_processes),
                    subtitle = stringResource(R.string.overview_open_processes_desc),
                    icon = Icons.Rounded.Apps,
                    trailing = {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = onOpenProcesses,
                )
            }
        }
    }
}

// ---------- 设备 / SoC ----------

@Composable
private fun DeviceCard(hardware: HardwareStatic?) {
    MiuixCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                hardware?.deviceModel?.takeIf { it.isNotBlank() } ?: stringResource(R.string.state_waiting),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                buildString {
                    val vendor = hardware?.manufacturer.orEmpty()
                    if (vendor.isNotBlank()) append(vendor.replaceFirstChar { it.uppercase() })
                    val soc = hardware?.socModel.orEmpty()
                    if (soc.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(soc)
                    }
                }.ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            if (hardware != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TagChip("${hardware.possibleCores} ${stringResource(R.string.overview_unit_cores)}")
                    if (hardware.is64Bit) TagChip("64-bit")
                    if (hardware.abi.isNotBlank()) TagChip(hardware.abi)
                }
                InfoRow(stringResource(R.string.overview_device_hardware), hardware.hardware.ifBlank { "—" })
                InfoRow(stringResource(R.string.overview_device_board), hardware.board.ifBlank { "—" })
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.overview_device_clusters),
                        style = MaterialTheme.typography.bodySmall,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Text(
                        hardware.clusterSummary.ifBlank { "—" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** 只读标签胶囊（不是可点选的 [SelectChip]，避免误读为过滤器）。 */
@Composable
private fun TagChip(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

// ---------- CPU ----------

@Composable
private fun CpuCard(hardware: HardwareStatic?, live: LiveStatus?) {
    // /proc/stat 在 Android 上属于 SELinux 保护节点，未授权 root 时读不到 ——
    // 此时不能显示「0% / 0 核在线」，那会被读成「真的没负载」。统一显示 — 并提示需要 root。
    val usages = live?.coreUsages.orEmpty()
    val hasCores = usages.isNotEmpty()
    val load = live?.cpuUsagePercent ?: 0f
    MiuixCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (hasCores) "%.0f".format(load) else "—",
                    style = if (hasCores) {
                        MaterialTheme.typography.displaySmall
                    } else {
                        MaterialTheme.typography.headlineMedium
                    },
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (hasCores) loadTone(load) else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        stringResource(R.string.overview_cpu_load),
                        style = MaterialTheme.typography.labelSmall,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Text(
                        stringResource(
                            R.string.overview_cpu_online_fmt,
                            if (hasCores) usages.size else (hardware?.onlineCores ?: 0),
                            hardware?.possibleCores ?: 0,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            ThinBar(if (hasCores) load / 100f else 0f, loadTone(load), height = 8.dp)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat(
                    stringResource(R.string.overview_cpu_avg_freq),
                    live?.avgClusterKhz?.let { formatFreq(it) } ?: "—",
                    Modifier.weight(1f),
                )
                MiniStat(
                    stringResource(R.string.overview_cpu_temp),
                    live?.cpuTempC?.let { "%.1f℃".format(it) } ?: "—",
                    Modifier.weight(1f),
                )
            }

            if (hasCores) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    usages.chunked(4).forEachIndexed { chunkIndex, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEachIndexed { i, u ->
                                CoreCell(
                                    index = chunkIndex * 4 + i,
                                    usage = u,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.overview_need_root),
                    style = MaterialTheme.typography.labelSmall,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            val clusters = hardware?.clusters.orEmpty()
            if (clusters.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    clusters.forEach { c ->
                        InfoRow(
                            "CPU${c.compactCores} · ${c.governor}",
                            live?.clusterCurKhz?.get("CPU${c.cores}")?.let { formatFreq(it) }
                                ?: formatFreq(c.maxKhz),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoreCell(index: Int, usage: Float, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            "C$index",
            style = MaterialTheme.typography.labelSmall,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            "%.0f%%".format(usage),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MiuixTheme.colorScheme.onSurface,
        )
        ThinBar(usage / 100f, loadTone(usage), height = 4.dp)
    }
}

// ---------- 内存 ----------

@Composable
private fun MemoryCard(live: LiveStatus?) {
    val mem = live?.mem
    val usedPct = mem?.usedPercent ?: 0f
    MiuixCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (mem == null || mem.totalKb == 0L) "—" else "%.0f".format(usedPct),
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = loadTone(usedPct),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (mem == null || mem.totalKb == 0L) "—"
                    else "${formatKb(mem.usedKb)} / ${formatKb(mem.totalKb)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            ThinBar(usedPct / 100f, loadTone(usedPct), height = 8.dp)
            if (mem != null && mem.totalKb > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStat(
                        stringResource(R.string.overview_mem_available),
                        formatKb(mem.availKb),
                        Modifier.weight(1f),
                    )
                    MiniStat(
                        stringResource(R.string.overview_mem_swap),
                        if (mem.swapTotalKb > 0) "${formatKb(mem.swapUsedKb)} / ${formatKb(mem.swapTotalKb)}"
                        else "—",
                        Modifier.weight(1f),
                    )
                    MiniStat(
                        stringResource(R.string.overview_mem_zram),
                        if (mem.zramRatio > 0f) "%.2f×".format(mem.zramRatio) else "—",
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ---------- GPU ----------

@Composable
private fun GpuCard(hardware: HardwareStatic?, live: LiveStatus?) {
    val usage = live?.gpuUsagePercent
    MiuixCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    hardware?.gpuModel?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.overview_gpu_unknown),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (usage != null) {
                    Text(
                        "%.0f%%".format(usage),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        color = loadTone(usage),
                    )
                }
            }
            if (usage != null) ThinBar(usage / 100f, loadTone(usage), height = 6.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat(
                    stringResource(R.string.overview_gpu_cur),
                    live?.gpuFreqHz?.let { formatFreq(it / 1_000) } ?: "—",
                    Modifier.weight(1f),
                )
                MiniStat(
                    stringResource(R.string.overview_gpu_max),
                    hardware?.gpuMaxHz?.let { formatFreq(it / 1_000) } ?: "—",
                    Modifier.weight(1f),
                )
                MiniStat(
                    stringResource(R.string.overview_gpu_temp),
                    live?.gpuTempC?.let { "%.1f℃".format(it) } ?: "—",
                    Modifier.weight(1f),
                )
            }
            hardware?.gpuGovernor?.takeIf { it.isNotBlank() }?.let {
                InfoRow(stringResource(R.string.overview_gpu_governor), it)
            }
        }
    }
}

// ---------- 缓存 ----------

@Composable
private fun CacheCard(hardware: HardwareStatic?) {
    val caches = hardware?.caches.orEmpty()
    MiuixCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (caches.isEmpty()) {
                Text(
                    stringResource(R.string.overview_cache_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    caches.forEach { c ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    c.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                Text(
                                    c.size,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
            InfoRow(
                stringResource(R.string.overview_cache_neon),
                if (hardware?.neonSupported == true) {
                    stringResource(R.string.overview_supported)
                } else {
                    stringResource(R.string.overview_unsupported)
                },
            )
        }
    }
}

// ---------- 系统 ----------

@Composable
private fun SystemCard(live: LiveStatus?) {
    MiuixCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoRow(
                stringResource(R.string.overview_sys_power),
                live?.devicePowerUw?.let { "%.2f W".format(it / 1_000_000.0) } ?: "—",
            )
            InfoRow(
                stringResource(R.string.overview_sys_battery),
                live?.battery?.level?.let { "$it%" } ?: "—",
            )
            InfoRow(
                stringResource(R.string.overview_sys_voltage),
                live?.battery?.voltageUv?.let { "%.3f V".format(it / 1_000_000.0) } ?: "—",
            )
            InfoRow(
                stringResource(R.string.overview_sys_battery_temp),
                live?.battery?.tempC?.let { "%.1f℃".format(it) } ?: "—",
            )
            InfoRow(
                stringResource(R.string.overview_sys_android),
                "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
            )
            InfoRow(
                stringResource(R.string.overview_sys_uptime),
                live?.uptimeSec?.takeIf { it > 0 }?.let { formatUptime(it) } ?: "—",
            )
        }
    }
}

// ---------- 共用小组件 ----------

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun ThinBar(fraction: Float, color: Color, height: Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(color),
        )
    }
}

/** 负载配色沿用仪表盘的语义色：<50% 正常、<80% 偏高、≥80% 过载。 */
@Composable
private fun loadTone(percent: Float): Color {
    val c = LocalStatusColors.current
    return when {
        percent >= 80f -> c.errorFg
        percent >= 50f -> c.warnFg
        else -> c.okFg
    }
}
