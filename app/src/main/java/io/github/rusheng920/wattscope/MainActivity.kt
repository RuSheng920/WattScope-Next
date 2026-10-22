package io.github.rusheng920.wattscope

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import io.github.rusheng920.wattscope.domain.ExportFormat
import io.github.rusheng920.wattscope.domain.ThemeMode
import io.github.rusheng920.wattscope.service.QptMonitorService
import io.github.rusheng920.wattscope.ui.QptApp
import io.github.rusheng920.wattscope.ui.QptViewModel
import io.github.rusheng920.wattscope.ui.theme.QptTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // 用户拒绝后引导到系统通知设置，避免「点了没反应」
        if (!granted) openNotificationSettings()
    }

    // 待写入的导出内容（先选保存位置，再异步生成写入）
    private var pendingExport: Pair<Long, ExportFormat>? = null
    private val createPngDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        uri?.let { writeExport(it) }
    }
    private val createJsonDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { writeExport(it) }
    }
    private val createCsvDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { writeExport(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 不在启动时自动申请权限：首次安装先进入独立的权限引导页，由用户逐项授权
        startMonitorService()

        val repository = (application as QptApplication).repository
        val updateRepository = (application as QptApplication).updateRepository
        // 启动时自动检测更新：开关关闭时不联网（更新检查属于联网行为，需用户显式开启）
        if (updateRepository.autoCheckEnabled) updateRepository.check()
        setContent {
            val themeMode by repository.themeModeFlow.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // 主题切换会重组整棵树：ViewModel 必须 remember，否则每次换肤都会重建采样订阅
            val viewModel = remember { QptViewModel(repository, updateRepository) }
            QptTheme(darkTheme = darkTheme) {
                // 系统栏图标随主题反转：浅色模式用深色图标
                val view = LocalView.current
                SideEffect {
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !darkTheme
                    controller.isAppearanceLightNavigationBars = !darkTheme
                }
                QptApp(
                    viewModel = viewModel,
                    onRequestOverlay = { requestOverlayPermission() },
                    onStartService = { startMonitorService() },
                    onRequestBatteryWhitelist = { requestIgnoreBatteryOptimizations() },
                    onOpenMiuiAutostart = { openMiuiAutostartSettings() },
                    onToggleBubble = { enabled ->
                        repository.bubbleEnabled = enabled
                        val action = if (enabled) QptMonitorService.ACTION_SHOW_BUBBLE else QptMonitorService.ACTION_HIDE_BUBBLE
                        // 注意：必须 startService（此前误用 startActivity 导致 ActivityNotFoundException 闪退）
                        startService(Intent(this, QptMonitorService::class.java).setAction(action))
                    },
                    onExportRecord = { recordId, format -> startExport(recordId, format) },
                    onRequestNotifications = { requestNotificationPermission() },
                    onRequestUsageAccess = { openUsageAccessSettings() },
                    onRequestAccessibility = { openAccessibilitySettings() },
                    onRequestInstall = { openInstallSettings() },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 前台可见才需要刷新首页的预览采样；退到后台由记录 / OSD 各自的循环负责
        (application as QptApplication).repository.setUiVisible(true)
    }

    override fun onResume() {
        super.onResume()
        // 从系统授权页返回时刷新权限状态（root / 悬浮窗 / 电池 / 使用情况）
        (application as QptApplication).repository.refreshPermissions()
        // 刚授予悬浮窗权限时补显示悬浮球 / OSD，不必重启服务
        ContextCompat.startForegroundService(
            this,
            Intent(this, QptMonitorService::class.java).setAction(QptMonitorService.ACTION_SYNC_OVERLAYS),
        )
    }

    override fun onStop() {
        super.onStop()
        (application as QptApplication).repository.setUiVisible(false)
    }

    private fun startExport(recordId: Long, format: ExportFormat) {
        pendingExport = recordId to format
        val suggestedName = "qpt_record_${recordId}_${System.currentTimeMillis()}.${format.ext}"
        when (format) {
            ExportFormat.JSON -> createJsonDoc.launch(suggestedName)
            ExportFormat.CSV -> createCsvDoc.launch(suggestedName)
            ExportFormat.PNG -> createPngDoc.launch(suggestedName)
        }
    }

    private fun writeExport(uri: Uri) {
        val (recordId, format) = pendingExport ?: return
        pendingExport = null
        val repository = (application as QptApplication).repository
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // PNG 走 Bitmap.compress，文本走字节写入；两者共用同一个 SAF 输出流
                    if (format == ExportFormat.PNG) {
                        val bitmap = repository.exportRecordPng(recordId) ?: error("bitmap")
                        contentResolver.openOutputStream(uri)?.use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        } ?: error(getString(R.string.error_open_output))
                    } else {
                        val content = if (format == ExportFormat.JSON) {
                            repository.exportRecordJson(recordId)
                        } else {
                            repository.exportRecordCsv(recordId)
                        }
                        contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(content.toByteArray())
                        } ?: error(getString(R.string.error_open_output))
                    }
                }
            }
            Toast.makeText(
                this@MainActivity,
                if (result.isSuccess) {
                    getString(R.string.toast_export_success)
                } else {
                    getString(R.string.toast_export_failed_fmt, result.exceptionOrNull()?.message.orEmpty())
                },
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /** 权限中心「通知」按钮：未授权时先弹系统权限框，被拒后自动进入通知设置。 */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings()
        }
    }

    private fun openNotificationSettings() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )
        }
    }

    /** 安装未知应用：应用内更新下完包必须靠它，否则安装框根本弹不出来。 */
    private fun openInstallSettings() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")),
            )
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)) }
        }
    }

    private fun openUsageAccessSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName")))
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        }
    }

    /**
     * 跳系统无障碍列表。
     *
     * 优先用 `ACTION_ACCESSIBILITY_DETAILS_SETTINGS` 直达本应用那一页；各家 ROM 对它
     * 的实现并不一致（部分直接抛 ActivityNotFound），失败一律回落到无障碍总列表。
     */
    private fun openAccessibilitySettings() {
        runCatching {
            // 常量在部分 SDK 上不可见，用字面值；ACTION_ACCESSIBILITY_DETAILS_SETTINGS
            startActivity(
                Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS", Uri.parse("package:$packageName")),
            )
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
    }

    private fun requestOverlayPermission() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun requestIgnoreBatteryOptimizations() {
        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
    }

    private fun openMiuiAutostartSettings() {
        runCatching {
            startActivity(
                Intent()
                    .setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun startMonitorService() {
        ContextCompat.startForegroundService(this, Intent(this, QptMonitorService::class.java))
    }
}
