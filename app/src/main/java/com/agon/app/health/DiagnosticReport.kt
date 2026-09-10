package com.agon.app.health

import android.content.Context
import android.os.Build
import com.agon.app.battery.BatteryOptimizationManager
import com.agon.app.blocklist.domain.BlockEngine
import com.agon.app.data.ShieldRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticReport {

    fun build(
        context: Context,
        batteryManager: BatteryOptimizationManager,
        autostartManager: AutostartManager = AutostartManager(context),
    ): String {
        val snap = ProtectionHealth.evaluate(context, batteryManager, autostartManager)
        val state = ShieldRepository.state.value
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return buildString {
            appendLine("=== BlockX LaAbrah Diagnostic ===")
            appendLine("Time: ${df.format(Date(snap.generatedAt))}")
            appendLine("App: ${context.packageName}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.BRAND} ${Build.MODEL}")
            appendLine("Vendor: ${snap.vendor} / ${snap.vendorLabel}")
            appendLine("Score: ${snap.score}/100 requiredOk=${snap.allRequiredOk}")
            appendLine("--- Checks ---")
            snap.items.forEach { item ->
                appendLine("${item.id}: ok=${item.ok} required=${item.required}")
            }
            appendLine("--- Runtime ---")
            appendLine("accessibilityEnabled=${state.accessibilityEnabled}")
            appendLine("vpnEnabled=${state.vpnEnabled} vpnRunning=${state.vpnRunning}")
            appendLine("blockEngineInit=${BlockEngine.isInitialized()} inspectText=${BlockEngine.shouldInspectText()}")
            appendLine("exactAlarm=${ExactAlarmHelper.isAllowed(context)}")
            appendLine("notifications=${NotificationHealth.areNotificationsHealthy(context)}")
            appendLine("autostartSatisfied=${autostartManager.isSatisfied()} needsAction=${autostartManager.requiresUserAction()}")
            val bat = runCatching { batteryManager.readState() }.getOrNull()
            appendLine("battery=$bat")
            appendLine("=== End ===")
        }
    }
}
