package io.github.rusheng920.wattscope

import android.Manifest
import android.content.ComponentName
import android.content.Intent
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
    private var pendingExport: Pair<Long, Boolean>? = null
    private val createJsonDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { writeExport(it) }
    }
    private val createCsvDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { writeExport(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestRuntimePermissions()
        startMonitorService()

        val repository = (application as QptApplication).repository
        setContent {
            val themeMode by repository.themeModeFlow.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // 主题切换会重组整棵树：ViewModel 必须 remember，否则每次换肤都会重建采样订阅
            val viewModel = remember { QptViewModel(repository) }
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
                    onExportRecord = { recordId, asJson -> startExport(recordId, asJson) },
                    onRequestNotifications = { requestNotificationPermission() },
                    onRequestUsageAccess = { openUsageAccessSettings() },
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
    }

    override fun onStop() {
        super.onStop()
        (application as QptApplication).repository.setUiVisible(false)
    }

    private fun startExport(recordId: Long, asJson: Boolean) {
        pendingExport = recordId to asJson
        val suggestedName = "qpt_record_${recordId}_${System.currentTimeMillis()}.${if (asJson) "json" else "csv"}"
        if (asJson) createJsonDoc.launch(suggestedName) else createCsvDoc.launch(suggestedName)
    }

    private fun writeExport(uri: Uri) {
        val (recordId, asJson) = pendingExport ?: return
        pendingExport = null
        val repository = (application as QptApplication).repository
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val content = if (asJson) repository.exportRecordJson(recordId) else repository.exportRecordCsv(recordId)
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(content.toByteArray())
                    } ?: error("无法打开输出流")
                }
            }
            Toast.makeText(
                this@MainActivity,
                if (result.isSuccess) "导出成功" else "导出失败：${result.exceptionOrNull()?.message}",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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

    private fun openUsageAccessSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName")))
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
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
