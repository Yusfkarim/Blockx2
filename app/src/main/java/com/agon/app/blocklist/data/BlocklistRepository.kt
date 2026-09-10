package com.agon.app.blocklist.data

import android.content.Context
import android.content.Intent
import com.agon.app.vpn.FamilyVpnService
import android.content.pm.PackageManager
import com.agon.app.blocklist.domain.AiContentClassifier
import com.agon.app.blocklist.domain.BlockEngine
import com.agon.app.blocklist.domain.BlockRule
import com.agon.app.blocklist.domain.BlockingType
import com.agon.app.blocklist.domain.ContentCategory
import com.agon.app.blocklist.domain.InstalledApp
import com.agon.app.blocklist.domain.ListMode
import com.agon.app.blocklist.domain.RuleType
import com.agon.app.blocklist.domain.TimedBlockingIds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlocklistRepository @Inject constructor(
    private val dao: BlocklistDao,
    private val classifier: AiContentClassifier,
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private companion object { const val DAY_MILLIS = 24L * 60L * 60L * 1000L }
    val aiConfigs: Flow<List<AiContentClassifier.CategoryConfig>> = dao.observeAiConfigs().map { entities ->
        ContentCategory.entries.map { category ->
            val entity = entities.firstOrNull { it.category == category.name }
            AiContentClassifier.CategoryConfig(
                category,
                // No persisted row yet: Adult ON by default, all other categories opt-in.
                // Any user-saved row keeps winning so an explicit choice is never reset.
                entity?.enabled ?: (category == ContentCategory.ADULT),
                entity?.threshold ?: 72,
            )
        }
    }
    val data: Flow<Pair<List<BlockRule>, Boolean>> = combine(dao.observeRules(), dao.observeSettings()) { entities, _ ->
        entities.map { it.toDomain() } to true
    }.distinctUntilChanged()

    init {
        // Blocklist protection is permanent. Repair older installations that had switched it off.
        scope.launch { dao.saveSettings(BlocklistSettingsEntity(enabled = true)) }
        // Delete any rules whose block period already elapsed (e.g. while the app was closed), so
        // expired blocks disappear from the list after a restart too.
        scope.launch {
            TimedBlockingStore.initialize(context)
            adoptLegacyDurations()
            purgeExpiredRules()
        }
        // Room is the single source of truth. This one observer keeps the in-memory engine in
        // sync for every mutation path (add / edit / delete / import / restore / enable), so
        // rules apply instantly without restarting the VPN.
        scope.launch { data.collect { (rules, enabled) -> updateEngine(rules, enabled) } }
        scope.launch { aiConfigs.collect(classifier::updateConfiguration) }
    }

    /** Permanently removes BLOCK rules whose expiry time has passed. Safe to call repeatedly. */
    suspend fun purgeExpiredRules() = withContext(Dispatchers.IO) {
        TimedBlockingStore.consumeExpired(context)
        val expired = dao.getRules().filter { entity ->
            entity.listMode == ListMode.BLOCK.name &&
                entity.blockUntil > 0L &&
                !isTimedLockActive(entity.id, entity.value, entity.type, entity.blockUntil)
        }
        if (expired.isNotEmpty()) {
            expired.forEach { TimedBlockingStore.clear(context, TimedBlockingIds.blocklist(it.id)) }
            dao.deleteRules(expired.map { it.id })
            notifyVpn()
        }
    }

    /** Adopts pre-upgrade wall-clock [RuleEntity.blockUntil] values into [TimedBlockingStore]. */
    private suspend fun adoptLegacyDurations() {
        dao.getRules().forEach { entity ->
            if (entity.listMode == ListMode.BLOCK.name && entity.blockUntil > 0L) {
                ensureTimedLock(entity.id, entity.value, entity.type, entity.blockUntil)
            }
        }
    }

    /**
     * Repairs the engine from the database. Cheap to call repeatedly: [BlockEngine.update]
     * ignores an unchanged snapshot, so no automaton is rebuilt when nothing moved.
     */
    suspend fun refreshEngine() = withContext(Dispatchers.IO) {
        // Drop expired rules first so a block whose time ran out is lifted on the next engine
        // refresh (which the VPN/guardian trigger regularly), even without any user action.
        purgeExpiredRules()
        updateEngine(dao.getRules().map { it.toDomain() }, enabled = true)
    }

    suspend fun add(values: List<Pair<String, String>>, type: RuleType, mode: ListMode, blockDays: Int = 0) {
        // A positive blockDays sets a per-rule expiry timestamp; 0 keeps the rule permanent.
        // Only BLOCK rules can expire — an allow-list entry never times out.
        val blockUntil = if (mode == ListMode.BLOCK && blockDays > 0) {
            System.currentTimeMillis() + blockDays.toLong() * DAY_MILLIS
        } else {
            0L
        }
        val normalized = values.mapNotNull { (value, label) ->
            normalize(value, type).takeIf { it.isNotBlank() }?.let { RuleEntity(value = it, label = label.ifBlank { it }, type = type.name, listMode = mode.name, blockUntil = blockUntil) }
        }.distinctBy { it.value.lowercase() }
        if (normalized.isEmpty()) return
        val insertedIds = try {
            dao.insertRules(normalized)
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            normalized.mapNotNull { item -> try { dao.insertRules(listOf(item)).firstOrNull() } catch (_: Exception) { null } }
        }
        if (mode == ListMode.BLOCK && blockDays > 0) {
            insertedIds.forEachIndexed { index, id ->
                if (id > 0L) {
                    val item = normalized.getOrNull(index)
                    TimedBlockingStore.start(
                        context,
                        id = TimedBlockingIds.blocklist(id),
                        type = blockingTypeOf(type),
                        target = item?.value.orEmpty(),
                        durationDays = blockDays,
                    )
                }
            }
        }
        normalized.forEach { audit("ADD", it.value, "${mode.name}_${type.name}") }
        classifier.invalidate(normalized.map { it.value })
        refreshEngine(); notifyVpn()
    }

    suspend fun update(rule: BlockRule, newValue: String) {
        val value = normalize(newValue, rule.type)
        require(value.isNotBlank())
        dao.updateRule(RuleEntity(rule.id, value, if (rule.type == RuleType.APP) rule.label else value, rule.type.name, rule.mode.name, rule.createdAt, System.currentTimeMillis(), rule.blockUntil))
        audit("EDIT", "${rule.value} → $value", "${rule.mode.name}_${rule.type.name}")
        classifier.invalidate(listOf(rule.value, value))
        refreshEngine(); notifyVpn()
    }

    suspend fun delete(rules: List<BlockRule>) {
        // Defense in depth: a rule whose block duration has not elapsed yet is never deletable,
        // even if a delete request somehow reaches here. Only expired or duration-less rules go
        // through. The UI already blocks the attempt and explains the remaining time.
        val deletable = rules.filter { !isTimedLockActive(it.id, it.value, it.type.name, it.blockUntil) }
        if (deletable.isEmpty()) return
        deletable.forEach { TimedBlockingStore.clear(context, TimedBlockingIds.blocklist(it.id)) }
        dao.deleteRules(deletable.map { it.id })
        deletable.forEach { audit("DELETE", it.value, "${it.mode.name}_${it.type.name}") }
        classifier.invalidate(deletable.map { it.value })
        refreshEngine(); notifyVpn()
    }

    /**
     * Strict timed app lock ("الحظر الزمني للتطبيقات"): the app is blocked for exactly
     * [durationMs] real, tamper-proof milliseconds. Like every timed rule here, the UI refuses
     * edits and deletion until the window honestly ends — there is no early-unlock path.
     */
    suspend fun addTimedAppLock(packageName: String, label: String, durationMs: Long) {
        val until = System.currentTimeMillis() + durationMs
        val entity = RuleEntity(
            value = packageName,
            label = label,
            type = RuleType.APP.name,
            listMode = ListMode.BLOCK.name,
            blockUntil = until,
        )
        val insertedIds = dao.insertRules(listOf(entity))
        val id = insertedIds.firstOrNull() ?: return
        if (id > 0L) {
            TimedBlockingStore.startMs(
                context,
                id = TimedBlockingIds.blocklist(id),
                type = BlockingType.APP,
                target = packageName,
                durationMs = durationMs,
            )
        }
        audit("ADD", packageName, "BLOCK_APP_TIMED")
        classifier.invalidate(listOf(packageName))
        refreshEngine(); notifyVpn()
    }

    suspend fun restore(rules: List<BlockRule>) {
        dao.insertRules(rules.map { RuleEntity(0, it.value, it.label, it.type.name, it.mode.name, it.createdAt, it.updatedAt, it.blockUntil) })
        rules.forEach { audit("RESTORE", it.value, "${it.mode.name}_${it.type.name}") }
        classifier.invalidate(rules.map { it.value })
        refreshEngine(); notifyVpn()
    }

    fun classify(signals: AiContentClassifier.Signals): AiContentClassifier.Result? = classifier.classify(signals)

    fun recordAiBlock(signals: AiContentClassifier.Signals, result: AiContentClassifier.Result, browserPackage: String) {
        scope.launch {
            dao.insertAiEvent(AiBlockEventEntity(url = signals.url, domain = signals.domain, category = result.category.name, confidence = result.confidence, browserPackage = browserPackage))
            audit("AI_BLOCK", signals.url.ifBlank { signals.domain }, result.category.name, "${result.reason} (${result.confidence}%)", browserPackage)
        }
    }

    fun setAiCategory(category: ContentCategory, enabled: Boolean, threshold: Int) { scope.launch { dao.saveAiConfig(AiCategoryConfigEntity(category.name, enabled, threshold.coerceIn(50, 99))) } }

    fun recordBlock(value: String, type: RuleType, reason: String = "", browserPackage: String = "") { scope.launch { audit("BLOCK", value, type.name, reason, browserPackage) } }

    suspend fun installedApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { info ->
                val packageName = info.activityInfo.packageName
                if (packageName == context.packageName) null else InstalledApp(packageName, info.loadLabel(pm).toString(), info.loadIcon(pm))
            }.distinctBy { it.packageName }.sortedBy { it.name.lowercase() }
    }

    /**
     * Buckets the rules in a single pass (the previous version scanned the list six times) and
     * publishes an immutable snapshot to the engine.
     */
    private fun updateEngine(rules: List<BlockRule>, enabled: Boolean) {
        val blockedWebsites = HashSet<String>()
        val allowedWebsites = HashSet<String>()
        val blockedApps = HashSet<String>()
        val allowedApps = HashSet<String>()
        val blockedKeywords = HashSet<String>()
        val allowedKeywords = HashSet<String>()

        for (rule in rules) {
            val value = rule.value.trim()
            if (value.isEmpty()) continue
            // Timed BLOCK rules stay active until TimedBlockingStore remaining hits 0. Wall-clock
            // is never used for this decision (clock-forward must not lift the block).
            if (rule.mode == ListMode.BLOCK && rule.blockUntil > 0L &&
                !isTimedLockActive(rule.id, rule.value, rule.type.name, rule.blockUntil)
            ) continue
            val block = rule.mode == ListMode.BLOCK
            when (rule.type) {
                RuleType.WEBSITE -> (if (block) blockedWebsites else allowedWebsites).add(value.lowercase())
                RuleType.APP -> (if (block) blockedApps else allowedApps).add(value)
                RuleType.KEYWORD -> (if (block) blockedKeywords else allowedKeywords).add(value.lowercase())
            }
        }

        BlockEngine.update(
            BlockEngine.Snapshot(
                enabled = true,
                blockedWebsites = blockedWebsites,
                allowedWebsites = allowedWebsites,
                blockedApps = blockedApps,
                allowedApps = allowedApps,
                blockedKeywords = blockedKeywords,
                allowedKeywords = allowedKeywords,
            ),
        )
    }
    private fun notifyVpn() { context.sendBroadcast(Intent(FamilyVpnService.ACTION_RELOAD_RULES).setPackage(context.packageName)) }

    private fun blockingTypeOf(type: RuleType): BlockingType = when (type) {
        RuleType.WEBSITE -> BlockingType.WEBSITE
        RuleType.APP -> BlockingType.APP
        RuleType.KEYWORD -> BlockingType.KEYWORD
    }

    private fun ensureTimedLock(ruleId: Long, value: String, typeName: String, blockUntil: Long) {
        val id = TimedBlockingIds.blocklist(ruleId)
        if (TimedBlockingStore.has(context, id)) return
        val type = runCatching { blockingTypeOf(RuleType.valueOf(typeName)) }.getOrDefault(BlockingType.KEYWORD)
        TimedBlockingStore.adoptLegacy(context, id, type, value, blockUntil)
    }

    /** True while a timed BLOCK rule must stay applied and undeletable. */
    private fun isTimedLockActive(ruleId: Long, value: String, typeName: String, blockUntil: Long): Boolean {
        if (blockUntil <= 0L) return false
        ensureTimedLock(ruleId, value, typeName, blockUntil)
        return TimedBlockingStore.isLocked(context, TimedBlockingIds.blocklist(ruleId))
    }

    private suspend fun audit(action: String, value: String, type: String, reason: String = "", browserPackage: String = "") = dao.insertAudit(RuleAuditEntity(action = action, value = value, type = type, reason = reason, browserPackage = browserPackage))
    private fun normalize(value: String, type: RuleType): String = when (type) { RuleType.WEBSITE -> value.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore('/').trimEnd('.'); RuleType.APP -> value.trim(); RuleType.KEYWORD -> value.trim().lowercase() }
    private fun RuleEntity.toDomain() = BlockRule(id, value, label, RuleType.valueOf(type), ListMode.valueOf(listMode), createdAt, updatedAt, blockUntil)
}
