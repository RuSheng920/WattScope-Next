package io.github.rusheng920.wattscope.core.root

import android.content.ComponentName
import io.github.rusheng920.wattscope.core.root.RootShellPool.Lane

/**
 * 用 root 一次性把其余权限补齐（学 OpenMonitor 的授权动线：先要 root，再由 root 代授权）。
 *
 * 为什么值得做：悬浮窗、使用情况访问、电池白名单这三项在系统设置里分散在三个不同页面，
 * 无障碍更是藏在「无障碍 → 已安装的服务」里。用户点五次、跳四个界面，最后往往漏掉一两项，
 * 表现为「记录能开但一切后台就停」。root 已经把门打开了，再用 `pm` / `appops` / `settings`
 * 直接写，一次点击就能全部落到位。
 *
 * 能做什么、不能做什么：
 * - `pm grant` 只能给**运行时权限**（POST_NOTIFICATIONS），不能给「特殊访问」；
 * - `appops set` 能给悬浮窗（SYSTEM_ALERT_WINDOW）与使用情况访问（GET_USAGE_STATS）；
 * - 电池白名单走 `dumpsys deviceidle whitelist +<pkg>`；
 * - 无障碍是把组件名追加进 `enabled_accessibility_services`，系统会自行绑定；
 * - **自启动/后台弹出界面**属于厂商私有开关（HyperOS、ColorOS 各有自己的 provider），
 *   root 也没有稳定接口，仍然需要用户手动去点，这里不假装能代劳。
 *
 * 全部命令拼成一条 `su` 往返，每步用 `echo STEP:<key>:$?` 回传退出码，UI 据此逐项汇报。
 */
class PermissionGranter(private val rootManager: RootManager) {

    /** @return key → 是否成功。key 与 [Step] 一一对应。 */
    suspend fun grantAll(packageName: String, accessibilityService: ComponentName): Map<String, Boolean> {
        val component = accessibilityService.flattenToShortString()
        val command = buildString {
            append("pm grant $packageName android.permission.POST_NOTIFICATIONS 2>/dev/null; ")
            append("echo STEP:notifications:\$?; ")

            append("appops set $packageName SYSTEM_ALERT_WINDOW allow 2>/dev/null; ")
            append("echo STEP:overlay:\$?; ")

            append("appops set $packageName GET_USAGE_STATS allow 2>/dev/null; ")
            append("echo STEP:usage:\$?; ")

            append("dumpsys deviceidle whitelist +$packageName >/dev/null 2>&1; ")
            append("echo STEP:battery:\$?; ")

            // 无障碍：把组件名追加进已启用列表。
            // 两个坑：
            // 1) 值里不能出现字面引号（`"$cur:'$comp'"` 会把单引号原样写进值里），
            //    系统认不出这个组件，整条 setting 会被静默丢弃；
            // 2) `settings put` 失败也返回 0，所以成败必须回读校验，不能只看退出码。
            append("comp='$component'; ")
            append("cur=\$(settings get secure enabled_accessibility_services 2>/dev/null); ")
            append("case \"\$cur\" in ")
            append("*\"\$comp\"*) ;; ")
            append("\"\"|\"null\") settings put secure enabled_accessibility_services \"\$comp\";; ")
            append("*) settings put secure enabled_accessibility_services \"\$cur:\$comp\";; ")
            append("esac; ")
            append("settings put secure accessibility_enabled 1; ")
            append("val=\$(settings get secure enabled_accessibility_services 2>/dev/null); ")
            append("case \"\$val\" in *\"\$comp\"*) echo STEP:accessibility:0;; ")
            append("*) echo STEP:accessibility:1;; esac")
        }
        val out = rootManager.execute(command, Lane.CONTROL)
        return Steps.associate { step ->
            val line = out.firstOrNull { it.startsWith("STEP:${step.key}:") }
            val code = line?.substringAfterLast(':')?.trim()
            step.key to (code == "0")
        }
    }

    enum class Step(val key: String) {
        NOTIFICATIONS("notifications"),
        OVERLAY("overlay"),
        USAGE("usage"),
        BATTERY("battery"),
        ACCESSIBILITY("accessibility"),
    }

    companion object {
        /** UI 逐项汇报时按这个顺序展示，与执行顺序一致。 */
        val Steps = Step.entries
    }
}
