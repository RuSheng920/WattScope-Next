package io.github.rusheng920.wattscope.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rusheng920.wattscope.R
import io.github.rusheng920.wattscope.core.sysinfo.ProcessDetail
import io.github.rusheng920.wattscope.core.sysinfo.ProcessEntry
import io.github.rusheng920.wattscope.core.sysinfo.formatKb
import io.github.rusheng920.wattscope.ui.components.EmptyState
import io.github.rusheng920.wattscope.ui.components.MiuixCard
import io.github.rusheng920.wattscope.ui.components.SegmentedControl
import io.github.rusheng920.wattscope.ui.components.SelectChip
import io.github.rusheng920.wattscope.ui.theme.LocalStatusColors
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class ProcessSort(val key: String, val labelRes: Int) {
    CPU("cpu", R.string.process_sort_cpu),
    MEM("mem", R.string.process_sort_mem),
    NAME("name", R.string.process_sort_name),
}

/**
 * 进程管理页（对应 OpenMonitor 的 Process Manager）。
 *
 * 数据一次性拉取 + 本地过滤排序：进程表的刷新粒度天然是「用户主动看」，
 * 给它挂 1Hz 轮询会把每次 `dumpsys cpuinfo` 的开销摊到被测设备上。
 */
@Composable
fun ProcessScreen(
    entries: List<ProcessEntry>,
    loading: Boolean,
    rootGranted: Boolean,
    onReload: () -> Unit,
    onKill: (ProcessEntry) -> Unit,
    onLoadDetail: suspend (Int) -> ProcessDetail?,
    bottomPadding: Dp = 24.dp,
) {
    var query by remember { mutableStateOf("") }
    var appsOnly by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(ProcessSort.CPU) }
    var pendingKill by remember { mutableStateOf<ProcessEntry?>(null) }
    var detailTarget by remember { mutableStateOf<ProcessEntry?>(null) }
    var detail by remember { mutableStateOf<ProcessDetail?>(null) }
    var detailLoading by remember { mutableStateOf(false) }

    val filtered = remember(entries, query, appsOnly, sort) {
        entries.asSequence()
            .filter { !appsOnly || it.isApp }
            .filter {
                query.isBlank() ||
                    it.label.contains(query, true) ||
                    it.name.contains(query, true) ||
                    it.packageName?.contains(query, true) == true
            }
            .sortedWith(
                when (sort) {
                    ProcessSort.CPU -> compareByDescending { it.cpuPercent }
                    ProcessSort.MEM -> compareByDescending { it.rssKb }
                    ProcessSort.NAME -> compareBy { it.label.lowercase() }
                },
            )
            .toList()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                onReload = onReload,
                loading = loading,
            )
        }
        item {
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.process_filter_all),
                    stringResource(R.string.process_filter_apps),
                ),
                selectedIndex = if (appsOnly) 1 else 0,
                onSelect = { appsOnly = it == 1 },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProcessSort.entries.forEach { option ->
                    SelectChip(
                        text = stringResource(option.labelRes),
                        selected = sort == option,
                        onClick = { sort = option },
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.process_count_fmt, filtered.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        if (filtered.isEmpty()) {
            item {
                // 首次拉取要跑 top 两帧 + 遍历 /proc，约 1–2 秒。
                // 这段时间不能显示「没有匹配的进程」——那会被读成「一个进程都没有」。
                val waiting = loading && entries.isEmpty()
                EmptyState(
                    icon = Icons.Rounded.Search,
                    title = stringResource(
                        when {
                            waiting -> R.string.process_loading_title
                            rootGranted -> R.string.process_empty_title
                            else -> R.string.process_no_root_title
                        },
                    ),
                    description = stringResource(
                        when {
                            waiting -> R.string.process_loading_desc
                            rootGranted -> R.string.process_empty_desc
                            else -> R.string.process_no_root_desc
                        },
                    ),
                )
            }
        }

        items(filtered, key = { it.pid }) { entry ->
            ProcessRow(
                entry = entry,
                onOpen = {
                    detailTarget = entry
                    detail = null
                },
                onKill = { pendingKill = entry },
            )
        }
    }

    LaunchedEffect(detailTarget) {
        val target = detailTarget ?: return@LaunchedEffect
        detailLoading = true
        try {
            detail = onLoadDetail(target.pid)
        } finally {
            detailLoading = false
        }
    }

    detailTarget?.let { target ->
        AlertDialog(
            onDismissRequest = {
                detailTarget = null
                detail = null
            },
            title = { Text(target.label) },
            text = {
                if (detailLoading || detail == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.process_detail_loading), style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    val d = detail!!
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DetailRow(stringResource(R.string.process_detail_pid), "${d.pid}")
                        DetailRow(stringResource(R.string.process_detail_uid), d.uid?.toString() ?: "—")
                        DetailRow(stringResource(R.string.process_detail_state), d.state ?: "—")
                        DetailRow(stringResource(R.string.process_detail_threads), d.threads?.toString() ?: "—")
                        DetailRow(stringResource(R.string.process_detail_fds), d.fdCount?.toString() ?: "—")
                        DetailRow(stringResource(R.string.process_detail_pss), d.pssKb?.let(::formatKb) ?: "—")
                        DetailRow(stringResource(R.string.process_detail_rss), d.rssKb?.let(::formatKb) ?: "—")
                        DetailRow(stringResource(R.string.process_detail_vss), d.vssKb?.let(::formatKb) ?: "—")
                        d.cmdline?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingKill = target
                        detailTarget = null
                        detail = null
                    },
                ) {
                    Text(stringResource(R.string.process_kill_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    detailTarget = null
                    detail = null
                }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    pendingKill?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingKill = null },
            title = { Text(stringResource(R.string.process_kill_title, target.label)) },
            text = {
                Text(
                    if (target.isApp) {
                        stringResource(R.string.process_kill_desc_app)
                    } else {
                        stringResource(R.string.process_kill_desc_pid, target.pid)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onKill(target)
                        pendingKill = null
                    },
                ) {
                    Text(stringResource(R.string.process_kill_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingKill = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onReload: () -> Unit,
    loading: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    stringResource(R.string.process_search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MiuixTheme.colorScheme.primary,
            )
        } else {
            IconButton(onClick = onReload, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.action_refresh),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ProcessRow(entry: ProcessEntry, onOpen: () -> Unit, onKill: () -> Unit) {
    MiuixCard(contentPadding = 12.dp, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProcessIcon(entry)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    entry.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    buildString {
                        append("PID ${entry.pid}")
                        append(" · ")
                        append("%.1f%%".format(entry.cpuPercent))
                        append(" · ")
                        append(formatKb(entry.rssKb))
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onKill, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.process_kill_action),
                    tint = loadTone(entry.cpuPercent),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ProcessIcon(entry: ProcessEntry) {
    val context = LocalContext.current
    val bitmap = remember(entry.packageName) {
        entry.packageName?.let { pkg ->
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(pkg)
                val w = drawable.intrinsicWidth.takeIf { it in 1..512 } ?: 96
                val h = drawable.intrinsicHeight.takeIf { it in 1..512 } ?: 96
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, w, h)
                drawable.draw(canvas)
                bmp.asImageBitmap()
            }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
    } else {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                entry.label.take(1).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun loadTone(percent: Float) = when {
    percent >= 20f -> LocalStatusColors.current.errorFg
    percent >= 5f -> LocalStatusColors.current.warnFg
    else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
}
