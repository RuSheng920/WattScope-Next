package io.github.rusheng920.wattscope.data.repo

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.FileProvider
import io.github.rusheng920.wattscope.BuildConfig
import io.github.rusheng920.wattscope.R
import io.github.rusheng920.wattscope.core.update.ReleaseInfo
import io.github.rusheng920.wattscope.core.update.SemVer
import io.github.rusheng920.wattscope.core.update.UpdateChecker
import io.github.rusheng920.wattscope.domain.UpdateChannel
import io.github.rusheng920.wattscope.domain.UpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用更新：偏好读写 + 检测 GitHub Releases + 下载安装包 + 调起系统安装器。
 *
 * 与 [MonitorRepository] 共用 `qpt_settings`，但单独成类：采样链路是 5Hz 热路径，
 * 不希望更新相关的状态机与网络协程混进同一个对象里。
 *
 * **不静默安装**：Android 上覆盖安装必须经过系统安装器确认，本类只负责把包下好并
 * 调起安装界面；`REQUEST_INSTALL_PACKAGES` 未授权时引导用户去系统设置开启。
 */
class UpdateRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("qpt_settings", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** 最近一次检测到的可用更新；[UpdateState] 之外单独保留，失败重试时不必重查网络。 */
    private val _pending = MutableStateFlow<ReleaseInfo?>(null)
    val pending: StateFlow<ReleaseInfo?> = _pending.asStateFlow()

    /**
     * 是否需要主动弹窗提示。
     *
     * 只有**启动时的自动检测**会置 true：手动点「检查更新」的结果在关于页当场可见，
     * 再弹一个模态框是重复打扰。
     */
    private val _autoPrompt = MutableStateFlow(false)
    val autoPrompt: StateFlow<Boolean> = _autoPrompt.asStateFlow()

    private val _autoCheck = MutableStateFlow(prefs.getBoolean(KEY_AUTO, false))
    val autoCheckFlow: StateFlow<Boolean> = _autoCheck.asStateFlow()

    private val _channel = MutableStateFlow(
        prefs.getString(KEY_CHANNEL, null)
            ?.let { key -> UpdateChannel.entries.firstOrNull { it.key == key } }
            ?: UpdateChannel.STABLE,
    )
    val channelFlow: StateFlow<UpdateChannel> = _channel.asStateFlow()

    private val _lastCheckMs = MutableStateFlow(prefs.getLong(KEY_LAST_CHECK, 0L))
    val lastCheckMs: StateFlow<Long> = _lastCheckMs.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    /** 启动时自动检测是否开启（默认关闭：更新检查属于联网行为，交用户显式同意）。 */
    val autoCheckEnabled: Boolean get() = _autoCheck.value

    var channel: UpdateChannel
        get() = _channel.value
        set(value) {
            _channel.value = value
            prefs.edit().putString(KEY_CHANNEL, value.key).apply()
        }

    fun setAutoCheck(enabled: Boolean) {
        _autoCheck.value = enabled
        prefs.edit().putBoolean(KEY_AUTO, enabled).apply()
    }

    /**
     * 检查更新。[manual] = true 表示用户主动点击，此时忽略「自动检查」开关。
     *
     * 选版本由 [UpdateChecker.fetchLatest] 完成：Beta 通道下候选包含预览版，
     * 但比较按版本号进行，正式版号更大时返回的就是正式版。
     */
    fun check(manual: Boolean = false) {
        if (!manual && !autoCheckEnabled) return
        if (checkJob?.isActive == true) return
        checkJob = scope.launch {
            _state.value = UpdateState.Checking
            val result = runCatching { UpdateChecker.fetchLatest(includeBeta = channel == UpdateChannel.BETA) }
            result.onSuccess { info ->
                val now = System.currentTimeMillis()
                prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
                _lastCheckMs.value = now
                val current = SemVer.parse(BuildConfig.VERSION_NAME)
                _state.value = when {
                    info == null -> UpdateState.Failed(R.string.update_err_no_release)
                    current != null && info.version <= current -> UpdateState.UpToDate
                    else -> {
                        _pending.value = info
                        _autoPrompt.value = !manual
                        UpdateState.Available(info)
                    }
                }
            }.onFailure {
                _state.value = UpdateState.Failed(R.string.update_err_network)
            }
        }
    }

    /** 下载并调起安装；安装包已存在（上次装到一半被拦下）时直接重试安装。 */
    fun downloadAndInstall() {
        val info = _pending.value ?: return
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            val cached = cachedApk(info)
            if (cached != null) {
                _state.value = UpdateState.ReadyToInstall(info)
                install(cached)
                return@launch
            }
            _state.value = UpdateState.Downloading(info, 0f)
            val downloaded = runCatching { download(info) }
            downloaded.onSuccess { file ->
                _state.value = UpdateState.ReadyToInstall(info)
                install(file)
            }.onFailure {
                _state.value = UpdateState.Failed(R.string.update_err_download)
            }
        }
    }

    /** 关闭「发现新版本」弹窗（用户点稍后，或安装器已接管）。 */
    fun dismissPrompt() {
        _autoPrompt.value = false
    }

    /** 安装包下载目录；命中时校验大小，避免半截文件被当成完整包。 */
    private fun cachedApk(info: ReleaseInfo): File? {
        val file = File(updateDir(), apkFileName(info))
        if (!file.isFile) return null
        return if (info.apkSizeBytes <= 0 || file.length() == info.apkSizeBytes) file else null
    }

    private fun updateDir(): File = File(context.cacheDir, "updates").apply { mkdirs() }

    private fun apkFileName(info: ReleaseInfo): String = "WattScope-Next-${info.versionName}.apk"

    /**
     * 下载 APK，官方地址不通就换镜像重试。
     *
     * 直连 `github.com/…/releases/download/…` 在国内经常几十 KB 后断流，
     * 表现是「下载到 12% 卡住」或直接失败。镜像只是 URL 前缀，逐个试代价很小。
     */
    private suspend fun download(info: ReleaseInfo): File = withContext(Dispatchers.IO) {
        // 只留当前版本：上一个版本的包没有再装的可能
        updateDir().listFiles()?.forEach { it.delete() }
        val target = File(updateDir(), apkFileName(info))
        var lastError: Throwable? = null
        for (url in UpdateChecker.downloadCandidates(info.apkUrl)) {
            try {
                copyTo(url, target, info)
                return@withContext target
            } catch (t: Throwable) {
                // 取消要原样抛出，不能被当成「换个镜像重试」
                if (t is kotlinx.coroutines.CancellationException) throw t
                target.delete()
                lastError = t
            }
        }
        throw lastError ?: java.io.IOException("download failed")
    }

    private suspend fun copyTo(url: String, target: File, info: ReleaseInfo) {
        val conn = openFollowing(url)
        try {
            conn.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    var lastEmit = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        read += n
                        // 进度按 150ms 节流：StateFlow 每字节更新会让 Compose 重组打成满帧
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastEmit >= 150) {
                            lastEmit = now
                            val total = info.apkSizeBytes
                            _state.value = UpdateState.Downloading(
                                info,
                                if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else -1f,
                            )
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** GitHub 资产地址会 302 到 CDN，HttpURLConnection 默认不跟随，需要手动跳。 */
    private fun openFollowing(url: String): HttpURLConnection {
        var current = url
        var redirects = 0
        while (true) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "WattScope-Next")
            }
            val code = conn.responseCode
            if (code in 300..399 && redirects < 5) {
                val next = conn.getHeaderField("Location")
                conn.disconnect()
                if (next.isNullOrBlank()) return conn
                current = if (next.startsWith("http")) next else URL(URL(current), next).toString()
                redirects++
                continue
            }
            return conn
        }
    }

    private fun install(file: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            // 未授权「安装未知应用」：安装器会静默失败，这里直接把人送到开关页
            _state.value = UpdateState.Failed(R.string.update_err_install_permission)
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { _state.value = UpdateState.Failed(R.string.update_err_install_permission) }
    }

    private companion object {
        const val KEY_AUTO = "auto_update_enabled"
        const val KEY_CHANNEL = "update_channel"
        const val KEY_LAST_CHECK = "update_last_check_ms"
    }
}
