package com.agon.app.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.agon.app.MainActivity
import com.agon.app.R
import com.agon.app.blocklist.data.BlocklistRepository
import com.agon.app.blocklist.data.SafeSearchForceStore
import com.agon.app.blocklist.data.YoutubeRestrictStore
import com.agon.app.blocklist.domain.AiContentClassifier
import com.agon.app.blocklist.domain.BlockEngine
import com.agon.app.blocklist.domain.BuiltInAdultDomains
import com.agon.app.blocklist.domain.CircumventionPolicy
import com.agon.app.blocklist.domain.MonitoredApps
import com.agon.app.blocklist.domain.RuleType
import com.agon.app.blocklist.domain.SearchPolicy
import com.agon.app.BlockedWebsiteActivity
import com.agon.app.data.ShieldRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.concurrent.thread

/**
 * DNS filtering tunnel.
 *
 * Reliability fixes over the previous revision:
 *  - sinkhole answers instead of NXDOMAIN, with TTL 0, so DNS caches cannot serve a blocked
 *    host after a refresh or a browser restart,
 *  - A, AAAA and HTTPS/SVCB queries are all sinkholed, closing the IPv6 and ECH bypass,
 *  - a single upstream socket per worker with bounded retries instead of a fresh socket per
 *    query, which is what degraded throughput during long sessions,
 *  - a watchdog that detects a dead tunnel and re-establishes it without ever creating a
 *    second VPN instance,
 *  - reusable buffers, so the packet loop performs no steady-state allocation.
 */
@AndroidEntryPoint
class FamilyVpnService : VpnService() {

    @Inject lateinit var blocklistRepository: BlocklistRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** True between a successful establish() and teardown. Guards against duplicate tunnels. */
    private val active = AtomicBoolean(false)

    /** Set while the packet loop thread is alive. */
    private val looping = AtomicBoolean(false)

    private val tunnelLock = Any()
    private var tunnel: ParcelFileDescriptor? = null

    private var watchdogJob: Job? = null

    private val connectivityManager: ConnectivityManager? by lazy {
        getSystemService(ConnectivityManager::class.java)
    }
    /** Re-points the tunnel at the newly active network the instant connectivity changes. */
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /** Incremented on every unexpected loop exit, reset after a healthy period. */
    private val consecutiveFailures = AtomicInteger(0)

    @Volatile
    private var lastPacketAt = 0L

    private val logLimiter = LogRateLimiter()

    /** Separate limiter so SafeSearch notices never crowd out real block events. */
    private val safeSearchLimiter = LogRateLimiter()

    /** Throttles Block Screen presentation so one page cannot stack several screens. */
    @Volatile
    private var lastBlockScreenAt = 0L

    private val ruleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_RELOAD_RULES) return
            // Rules live in memory; refreshing the engine is enough to apply them instantly
            // without tearing the tunnel down.
            serviceScope.launch { blocklistRepository.refreshEngine() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ShieldRepository.initialize(this)
        SafeSearchForceStore.initialize(this)
        // The bundled ~2M domain index must be memory-mapped before the first DNS query; it is
        // the only blocking source this tunnel consults.
        BuiltInAdultDomains.initialize(this)
        createChannel()
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            ruleReceiver,
            IntentFilter(ACTION_RELOAD_RULES),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        serviceScope.launch { blocklistRepository.refreshEngine() }
        registerNetworkCallback()
    }

    /**
     * Rebuilds the tunnel the moment the active network changes (Wi-Fi <-> Mobile, network
     * regained), closing the brief DNS-leak window that the 15s watchdog would otherwise leave.
     * Event-driven, so it costs nothing until connectivity actually changes.
     */
    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!active.get()) return
                runCatching { setUnderlyingNetworks(null) }
                if (synchronized(tunnelLock) { tunnel == null } || !looping.get()) {
                    serviceScope.launch { establishTunnel() }
                }
            }

            override fun onLost(network: Network) {
                if (active.get()) runCatching { setUnderlyingNetworks(null) }
            }
        }
        val request = NetworkRequest.Builder().build()
        runCatching { connectivityManager?.registerNetworkCallback(request, callback) }
            .onSuccess { networkCallback = callback }
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let { cb -> runCatching { connectivityManager?.unregisterNetworkCallback(cb) } }
        networkCallback = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Advance Safe Browsing commitment ledger on every command (incl. keep-alive).
        runCatching { CommitmentTimerModule.tick(this) }
        if (intent?.action == ACTION_STOP) {
            // Forced lock: ignore STOP while Safe Browsing commitment is still active.
            if (CommitmentTimerModule.isActive(this)) {
                ShieldRepository.setVpnDesired(true)
                startForegroundCompat(NOTIFICATION_ID, notification(activeProtectionText()))
                if (!active.get() || synchronized(tunnelLock) { tunnel == null } || !looping.get()) {
                    serviceScope.launch {
                        active.set(true)
                        blocklistRepository.refreshEngine()
                        establishTunnel()
                        startWatchdog()
                    }
                }
                return START_STICKY
            }
            teardown(stopService = true)
            return START_NOT_STICKY
        }
        // startForeground can throw ForegroundServiceStartNotAllowedException / SecurityException
        // on newer Android when the launch context is restricted — never crash on it.
        startForegroundCompat(NOTIFICATION_ID, notification(activeProtectionText()))
        if (active.compareAndSet(false, true)) {
            serviceScope.launch {
                // Rules must be live before the first packet is answered, otherwise the very
                // first requests after enabling protection leak through.
                blocklistRepository.refreshEngine()
                establishTunnel()
                startWatchdog()
            }
        } else {
            // Auto-reconnect fallback (field fix): a process still marked `active` but holding
            // a dead descriptor or frozen loop is the root of "shield says ON, nothing is
            // filtered". Every subsequent start command must repair it, not sit idle.
            serviceScope.launch {
                val tunnelDead = synchronized(tunnelLock) { tunnel == null } || !looping.get()
                if (tunnelDead) {
                    runCatching { blocklistRepository.refreshEngine() }
                    runCatching { establishTunnel() }
                    runCatching { startWatchdog() }
                }
            }
        }
        return START_STICKY
    }

    // ------------------------------------------------------------------ tunnel

    private fun establishTunnel(): Boolean = synchronized(tunnelLock) {
        if (!active.get()) return false
        closeTunnelLocked()
        // MIUI refuses createTun on a service that is not foreground yet on some HyperOS builds;
        // always be foreground BEFORE touching Builder.establish().
        startForegroundCompat(NOTIFICATION_ID, notification(activeProtectionText()))
        return try {
            val descriptor = Builder()
                .setSession("BlockX LaAbrah")
                .setMtu(MTU)
                // MIUI/HyperOS needs an explicit /24 address family (host-32 masks are dropped).
                // Route stays DNS-scoped exactly as before: only the virtual resolver rides the
                // tunnel, never application traffic (unchanged product behaviour).
                .addAddress(TUNNEL_ADDRESS, 24)
                .addDnsServer(VIRTUAL_DNS)
                .addRoute(VIRTUAL_DNS, 32)
                .setBlocking(true)
                .also { builder ->
                    // Never route our own traffic through the tunnel: it would deadlock the
                    // upstream lookups performed by this very service.
                    runCatching { builder.addDisallowedApplication(packageName) }
                }
                .establish()
            if (descriptor == null) {
                ShieldRepository.setVpnRunning(false)
                // Honesty guard (field fix): when the OS has no live VPN consent for us, the
                // shield must read OFF instead of faking it — an empty TUN interface can never
                // filter. The paused/strong choke in setVpnDesired still owns the OFF path.
                // MIUI recovery: re-request consent in the same frame so the user only taps
                // "Allow" once and the watchdog brings the tunnel back alone.
                val consent = runCatching { android.net.VpnService.prepare(this@FamilyVpnService) }.getOrNull()
                if (consent != null) {
                    runCatching { ShieldRepository.setVpnDesired(false) }
                    runCatching {
                        startActivity(consent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                false
            } else {
                tunnel = descriptor
                // Follow whatever network the OS considers active, so DNS never leaks during a
                // Wi-Fi <-> Mobile switch. null == "use the system default network".
                runCatching { setUnderlyingNetworks(null) }
                ShieldRepository.setVpnRunning(true)
                lastPacketAt = System.currentTimeMillis()
                isTunnelInterfaceUp = true
                if (looping.compareAndSet(false, true)) {
                    thread(name = "FamilyDnsTunnel", isDaemon = true) { runPacketLoop(descriptor) }
                }
                true
            }
        } catch (_: Throwable) {
            ShieldRepository.setVpnRunning(false)
            false
        }
    }

    private fun closeTunnelLocked() {
        runCatching { tunnel?.close() }
        tunnel = null
    }

    /**
     * Watchdog: recovers from a silently dead tunnel (revoked descriptor, network change,
     * OEM power management) without ever building a second VPN instance.
     */
    private fun startWatchdog() {
        if (watchdogJob?.isActive == true) return
        watchdogJob = serviceScope.launch {
            while (isActive && active.get()) {
                delay(WATCHDOG_INTERVAL_MS)
                if (!active.get()) break
                val descriptorLost = synchronized(tunnelLock) { tunnel == null }
                val loopDead = !looping.get()
                if (descriptorLost || loopDead) {
                    val restored = establishTunnel()
                    if (!restored) {
                        // Never give up while the user still wants protection. Back off with a
                        // capped delay and keep retrying forever instead of tearing the service
                        // down: an OEM power-management hiccup or a transient network loss must not
                        // permanently stop filtering. The guardian service supervises us in
                        // parallel, so the two keep each other alive.
                        val failures = consecutiveFailures.incrementAndGet()
                        val backoff = (RECOVERY_BACKOFF_MS * failures).coerceAtMost(MAX_RECOVERY_BACKOFF_MS)
                        delay(backoff)
                    } else {
                        consecutiveFailures.set(0)
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------- packet loop

    private fun runPacketLoop(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val packet = ByteArray(MTU)
        val dnsOut = ByteArray(MTU)
        val wire = ByteArray(MTU)
        // Dedicated scratch buffers for SafeSearch rewriting; reused, so the loop stays
        // allocation free in steady state.
        val rewriteBuffer = ByteArray(MTU)
        val safeResponseBuffer = ByteArray(MTU)
        val upstream = UpstreamResolver(::protect)

        try {
            while (active.get()) {
                val length = try {
                    input.read(packet)
                } catch (_: Throwable) {
                    break
                }
                if (length <= 0) {
                    if (length < 0) break else continue
                }
                lastPacketAt = System.currentTimeMillis()

                val responseLength = handlePacket(
                    packet, length, dnsOut, wire, rewriteBuffer, safeResponseBuffer, upstream,
                )
                if (responseLength > 0) {
                    try {
                        output.write(wire, 0, responseLength)
                    } catch (_: Throwable) {
                        break
                    }
                }
            }
        } finally {
            upstream.close()
            runCatching { input.close() }
            runCatching { output.close() }
            looping.set(false)
            synchronized(tunnelLock) {
                if (tunnel === descriptor) closeTunnelLocked()
            }
            if (active.get()) ShieldRepository.setVpnRunning(false)
        }
    }

    /** Returns the number of bytes written into [wire], or 0 when the packet is dropped. */
    private fun handlePacket(
        packet: ByteArray,
        length: Int,
        dnsOut: ByteArray,
        wire: ByteArray,
        rewriteBuffer: ByteArray,
        safeResponseBuffer: ByteArray,
        upstream: UpstreamResolver,
    ): Int {
        if (length < 29) return 0
        val version = (packet[0].toInt() ushr 4) and 0xF
        if (version != DnsPacket.IPV4) return 0
        val headerLength = (packet[0].toInt() and 0xF) * 4
        if (headerLength < DnsPacket.MIN_HEADER || length <= headerLength + 8) return 0
        if (packet[9].toInt() != DnsPacket.PROTO_UDP) return 0
        if (DnsPacket.u16(packet, headerLength + 2) != DnsPacket.DNS_PORT) return 0

        val dnsOffset = headerLength + 8
        val dnsLength = length - dnsOffset
        if (dnsLength < 12) return 0

        // Copy the DNS payload to the front of the scratch buffer, no allocation.
        System.arraycopy(packet, dnsOffset, dnsOut, 0, dnsLength)

        val domain = DnsPacket.parseDomain(dnsOut, dnsLength)
        if (domain.isEmpty()) return 0
        ShieldRepository.recordRequest()

        val qtype = DnsPacket.questionType(dnsOut, dnsLength)
        val decision = decide(domain)

        if (decision != null) {
            // Sinkhole A / AAAA / HTTPS-SVCB alike: answering HTTPS records with an empty
            // authoritative answer prevents Encrypted Client Hello from bypassing the block.
            val answerLength = DnsPacket.buildSinkholeResponse(dnsOut, dnsLength, dnsOut)
            if (answerLength <= 0) return 0
            reportBlock(domain, decision)
            return DnsPacket.wrapUdp(packet, headerLength, dnsOut, answerLength, wire)
        }

        // Optional YouTube Restricted Mode (settings toggle). The answer is BUILT LOCALLY with
        // the official anycast IPs of restrict.youtube.com — no upstream round trip, so local
        // DNS caches, direct-IP code paths and QUIC fallbacks all land on Google's restricted
        // frontend in one shot (works for the app, modified clients and every browser).
        // The media CDN (googlevideo) is never touched, so playback itself always stays fast.
        //
        // QUIC (UDP/443) is intentionally NOT dropped: this tunnel is DNS-scoped by design
        // (only the virtual resolver address is routed), and restricted mode is enforced by the
        // destination IP regardless of transport — QUIC to the restrict IPs is still restricted.
        if (YoutubeRestrictStore.isEnabled() && YoutubeRestrictStore.isYoutubeHost(domain) &&
            (qtype == DnsPacket.TYPE_A || qtype == DnsPacket.TYPE_AAAA)
        ) {
            val address = if (qtype == DnsPacket.TYPE_A) {
                YoutubeRestrictStore.RESTRICT_IPV4
            } else {
                YoutubeRestrictStore.RESTRICT_IPV6
            }
            val answerLength = DnsPacket.buildAddressResponse(
                dnsOut, dnsLength, address, 0, address.size, safeResponseBuffer,
            )
            if (answerLength > 0) {
                return DnsPacket.wrapUdp(packet, headerLength, safeResponseBuffer, answerLength, wire)
            }
            // Malformed packet: fall through to the normal resolution path below.
        }

        // Force SafeSearch (toggle, default ON): Google, Bing and DuckDuckGo resolve to their
        // provider-operated safe endpoints at DNS time — the browser receives the safe address
        // for the ASKED name, so no incognito/backdoor bypass exists. A rewrite that fails
        // upstream falls through to plain resolution below (fail-open, never a broken page).
        if (decision == null && SafeSearchForceStore.isEnabled() &&
            (qtype == DnsPacket.TYPE_A || qtype == DnsPacket.TYPE_AAAA)
        ) {
            val safeTarget = com.agon.app.blocklist.domain.SearchPolicy.safeSearchTarget(domain)
            if (safeTarget != null && !YoutubeRestrictStore.isYoutubeHost(domain)) {
                val rewritten = DnsPacket.rewriteQuestion(dnsOut, dnsLength, safeTarget, rewriteBuffer)
                if (rewritten > 0) {
                    // Same family-resolver for rewritten (SafeSearch) answers.
                    val answered = upstream.resolve(rewriteBuffer, rewritten, false)
                    if (answered > 0) {
                        val rdata = DnsPacket.findAddressRecord(rewriteBuffer, answered, qtype)
                        if (rdata > 0) {
                            val addressSize = if (qtype == DnsPacket.TYPE_AAAA) 16 else 4
                            val built = DnsPacket.buildAddressResponse(
                                dnsOut,
                                dnsLength,
                                rewriteBuffer,
                                rdata,
                                addressSize,
                                safeResponseBuffer,
                            )
                            if (built > 0) {
                                return DnsPacket.wrapUdp(packet, headerLength, safeResponseBuffer, built, wire)
                            }
                        }
                    }
                }
            }
        }

        // Forward everything the local policy did not sink to the family resolver — Cloudflare
        // for Family (1.1.1.3/1.0.0.3) filters malware + adult content at DNS answer time on
        // top of our own index. Same caller contract; only the upstream endpoint changed.
        val upstreamLength = upstream.resolve(dnsOut, dnsLength, false)
        if (upstreamLength <= 0) return 0
        return DnsPacket.wrapUdp(packet, headerLength, dnsOut, upstreamLength, wire)
    }

    /**
     * Rewrites a YouTube A/AAAA question to [YoutubeRestrictStore.RESTRICT_TARGET], resolves
     * upstream and re-emits the address under the originally asked name — so the client speaks
     * transparently to YouTube's restricted-mode endpoint (comments + mature content off).
     * Returns bytes written into [wire], or 0 to let the caller fall back to plain resolution.
     */
    private class Decision(val reason: String, val aiResult: AiContentClassifier.Result?)

    /**
     * Local policy decision for [domain], or null when the query may be forwarded.
     *
     * Product rule: the ONLY active web filter in this tunnel is the bundled ~2M domain index
     * ([BuiltInAdultDomains]). Circumvention rules, user-managed website rules, safe-search
     * enforcement and AI classification are intentionally not applied here. The user-managed
     * allow-list is still honoured so an explicitly allowed site is never blocked by the index.
     *
     * Clean True Positive Filtering Only (false-blocking audit): [BuiltInAdultDomains.matches]
     * is an EXACT hash-index lookup ([AssetDomainHashIndex]) over the full host and each
     * registrable suffix — never a substring/fuzzy match — so a domain is only ever sinkholed
     * here when it (or one of its parent domains) is a literal, curated entry in the bundled
     * index. Nothing outside that index, and nothing a user has not explicitly added to their
     * own blocklist, can reach a block decision through this path.
     *
     * Device-level Private DNS (DoT) preempts DNS tunnels by design: while it is configured
     * ("opportunistic" or an explicit hostname), local DNS blocking is skipped entirely so the
     * system's own DNS keeps working untouched and nothing is blocked on top of it.
     */
    private fun decide(domain: String): Decision? {
        if (isPrivateDnsActive()) return null
        if (BlockEngine.isWebsiteAllowed(domain)) return null
        if (!BuiltInAdultDomains.matches(domain)) return null
        return Decision("Blocked site", null)
    }

    // --- Private DNS awareness -------------------------------------------------------------

    @Volatile
    private var privateDnsActive = false

    @Volatile
    private var privateDnsCheckedAt = 0L

    /**
     * True while the device-level Private DNS feature is configured. The Global settings read
     * is in-memory after first load; the result itself is still cached for 2s because this runs
     * on every DNS query.
     */
    private fun isPrivateDnsActive(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - privateDnsCheckedAt > PRIVATE_DNS_RECHECK_MS) {
            privateDnsCheckedAt = now
            privateDnsActive = runCatching {
                val mode = Settings.Global.getString(contentResolver, PRIVATE_DNS_MODE).orEmpty()
                val specifier = Settings.Global.getString(contentResolver, PRIVATE_DNS_SPECIFIER)
                mode.equals("hostname", ignoreCase = true) ||
                    mode.equals("opportunistic", ignoreCase = true) ||
                    !specifier.isNullOrBlank()
            }.getOrDefault(false)
        }
        return privateDnsActive
    }

    /**
     * Records a block. Identical domains are rate limited so a page issuing dozens of lookups
     * for the same host produces one log entry instead of flooding Room and the UI.
     */
    private fun reportBlock(domain: String, decision: Decision) {
        if (!logLimiter.shouldLog(domain)) return
        ShieldRepository.recordBlocked(domain.ifBlank { "Blocked domain" }, decision.reason)
        val ai = decision.aiResult
        if (ai != null) {
            blocklistRepository.recordAiBlock(
                AiContentClassifier.Signals(domain = domain, url = domain),
                ai,
                "Safe Browsing Shield",
            )
        } else {
            blocklistRepository.recordBlock(domain, RuleType.WEBSITE, decision.reason, "Safe Browsing Shield")
        }
        presentBlockScreen(domain, decision)
    }

    /**
     * Raises the existing Block Screen on the very first DNS query for a blocked host.
     *
     * Without this the tunnel only sinkholes the lookup, so the user is left staring at the
     * browser's own "site can't be reached" page and has to refresh or retry before anything
     * visible happens. Showing it here means a blocked site is announced immediately, in every
     * browser, with no user action and no dependency on the accessibility service.
     *
     * A separate short window from the log limiter keeps a page that resolves many hosts
     * (trackers, CDNs) from stacking multiple screens.
     */
    private fun presentBlockScreen(domain: String, decision: Decision) {
        if (domain.isBlank()) return
        // Do not flash the full-screen block UI for passive/background lookups from other apps
        // (analytics SDKs like AppsFlyer, ad preloads, widgets…). The DNS sinkhole still answers
        // so the content never loads; the overlay is reserved for active browsing — the
        // accessibility browser inspector shows the same Block Screen when the user actually
        // opens a blocked page in a browser.
        val foreground = ShieldRepository.foregroundPackage
        if (foreground == null || !MonitoredApps.isBrowser(foreground)) return
        val now = System.currentTimeMillis()
        if (now - lastBlockScreenAt < BLOCK_SCREEN_THROTTLE_MS) return
        lastBlockScreenAt = now

        val category = decision.aiResult?.category?.displayName ?: "Website rule"
        val confidence = decision.aiResult?.confidence ?: 100
        val intent = Intent(this, BlockedWebsiteActivity::class.java)
            .putExtra(BlockedWebsiteActivity.EXTRA_DOMAIN, domain)
            .putExtra(BlockedWebsiteActivity.EXTRA_REASON, decision.reason)
            .putExtra(BlockedWebsiteActivity.EXTRA_CATEGORY, category)
            .putExtra(BlockedWebsiteActivity.EXTRA_CONFIDENCE, confidence)
            .putExtra(BlockedWebsiteActivity.EXTRA_TIME, now)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION,
            )

        // A direct start works while our own task is foreground. From Android 10 the platform
        // restricts background activity launches, so we always attach the same intent to a
        // full-screen-intent notification, which is the officially supported way for a
        // foreground service to surface UI immediately. Whichever path the OS permits, the user
        // sees the existing Block Screen without touching anything.
        val started = runCatching { startActivity(intent); true }.getOrDefault(false)
        if (!started || Build.VERSION.SDK_INT >= 29) {
            postBlockScreenFullScreenIntent(domain, decision.reason, intent)
        }
    }

    /**
     * Full-screen-intent notification carrying the Block Screen. On a locked or restricted
     * device the system launches it directly; otherwise it appears as a high-priority heads-up
     * alert that opens the same screen. Uses its own channel so the ongoing status notification
     * is unaffected.
     */
    private fun postBlockScreenFullScreenIntent(domain: String, reason: String, target: Intent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(BLOCK_CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    BLOCK_CHANNEL,
                    "Blocked websites",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = "Shown the moment a blocked website is opened" },
            )
        }
        val pending = PendingIntent.getActivity(
            this,
            BLOCK_REQUEST_CODE,
            target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, BLOCK_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(
                when (ShieldRepository.state.value.language) {
                    "en" -> "Blocked: $domain"
                    "ar" -> "محظور: $domain"
                    else -> "بلۆککراوە: $domain"
                },
            )
            .setContentText(reason)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .apply {
                // Android 14+: USE_FULL_SCREEN_INTENT is revocable. Only attach when it is live;
                // otherwise the high-priority heads-up entry remains and the direct start
                // attempt above still applies where permitted.
                val canFullScreen = android.os.Build.VERSION.SDK_INT < 34 ||
                    androidx.core.app.NotificationManagerCompat.from(this@FamilyVpnService).canUseFullScreenIntent()
                if (canFullScreen) setFullScreenIntent(pending, true)
            }
            .build()
        runCatching { manager.notify(BLOCK_NOTIFICATION_ID, notification) }
    }

    // ---------------------------------------------------------------- teardown

    private fun teardown(stopService: Boolean) {
        isTunnelInterfaceUp = false
        if (!active.compareAndSet(true, false)) {
            if (stopService) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return
        }
        watchdogJob?.cancel()
        watchdogJob = null
        synchronized(tunnelLock) { closeTunnelLocked() }
        ShieldRepository.setVpnRunning(false)
        if (stopService) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /**
     * Swiping the app away from Recents must never stop protection. Combined with
     * `android:stopWithTask="false"` in the manifest this keeps the tunnel alive, and the
     * watchdog repairs it if the platform tore the descriptor down anyway.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (active.get()) {
            synchronized(tunnelLock) {
                if (tunnel == null) serviceScope.launch { establishTunnel() }
            }
            startWatchdog()
        }
        // Deliberately not calling super: the default implementation stops the service.
    }

    override fun onRevoke() {
        teardown(stopService = true)
        super.onRevoke()
    }

    override fun onDestroy() {
        // If the system killed us while protection was still desired, ask to be restarted so
        // filtering resumes without any UI being open.
        val restartWanted = active.get() && ShieldRepository.state.value.vpnEnabled
        teardown(stopService = false)
        unregisterNetworkCallback()
        runCatching { unregisterReceiver(ruleReceiver) }
        serviceScope.cancel()
        // Dual watchdog: always keep the always-on supervisor alive. Even if this VPN cannot be
        // restarted immediately, the guardian will revive it on its next supervision tick, so
        // the two services mutually restart each other.
        runCatching {
            androidx.core.content.ContextCompat.startForegroundService(
                applicationContext,
                Intent(applicationContext, com.agon.app.services.ProtectionGuardianService::class.java),
            )
        }
        if (restartWanted) {
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(
                    applicationContext,
                    Intent(applicationContext, FamilyVpnService::class.java),
                )
            }
        }
        super.onDestroy()
    }

    // ----------------------------------------------------------- notifications

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val language = ShieldRepository.state.value.language
        val channelName = when (language) { "en" -> "Protection status"; "ar" -> "حالة الحماية"; else -> "دۆخی پاراستن" }
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, channelName, NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun activeProtectionText(): String = when (ShieldRepository.state.value.language) {
        "en" -> "BlockX LaAbrah protection is active"
        "ar" -> "حماية BlockX LaAbrah نشطة"
        else -> "پاراستنی BlockX LaAbrah چالاکە"
    }

    /**
     * Two-stage foreground bring-up (Android 14/15): declare BOTH service types first, and if
     * the platform refuses (BAL / ForegroundServiceStartNotAllowedException), fall back
     * instantly to the single specialUse type so the service never dies while starting.
     */
    private fun startForegroundCompat(id: Int, notification: android.app.Notification) {
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            val both = runCatching {
                startForeground(
                    id,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED or
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            }
            if (both.isSuccess) return
            runCatching {
                startForeground(
                    id,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            }
        } else {
            runCatching { startForeground(id, notification) }
        }
    }

    private fun notification(text: String): android.app.Notification {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("BlockX LaAbrah")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        /** Live signal used by ProtectionHealth: true only after Builder.establish() returned a
         * real TUN descriptor and the packet loop started; cleared on every teardown path.
         * (This is what TRANSPORT_VPN probing can never tell our own app, because we are
         * explicitly excluded from our own tunnel.) */
        @Volatile
        var isTunnelInterfaceUp: Boolean = false
            private set
        const val ACTION_STOP = "com.agon.app.STOP_VPN"
        const val ACTION_RELOAD_RULES = "com.familyshield.protection.RELOAD_VPN_RULES"

        private const val FAMILY_DNS = "1.1.1.3"
        private const val FAMILY_DNS_SECONDARY = "1.0.0.3"
        private const val STANDARD_DNS = "1.1.1.1"
        private const val STANDARD_DNS_SECONDARY = "1.0.0.1"
        // Device-level Private DNS (DoT) Global settings keys.
        private const val PRIVATE_DNS_MODE = "private_dns_mode"
        private const val PRIVATE_DNS_SPECIFIER = "private_dns_specifier"
        private const val PRIVATE_DNS_RECHECK_MS = 2_000L
        // MIUI/HyperOS-compatible subnet (documented MIUI quirk: the legacy 10.10.10.x range
        // is rejected by HyperOS 2.x TunnelInit; 10.1.10.0/24 passes on all tested builds).
        private const val VIRTUAL_DNS = "10.1.10.1"
        private const val TUNNEL_ADDRESS = "10.1.10.2"

        private const val MTU = 1500
        private const val CHANNEL = "shield_protection"
        private const val NOTIFICATION_ID = 41

        private const val BLOCK_CHANNEL = "shield_blocked_sites"
        private const val BLOCK_NOTIFICATION_ID = 44
        private const val BLOCK_REQUEST_CODE = 45
        private const val BLOCK_SCREEN_THROTTLE_MS = 2_500L
        private const val WATCHDOG_INTERVAL_MS = 15_000L
        private const val RECOVERY_BACKOFF_MS = 2_000L
        // Upper bound for the retry backoff. The watchdog retries indefinitely (never stops the
        // service on its own) so protection resumes the moment the platform allows a tunnel again.
        private const val MAX_RECOVERY_BACKOFF_MS = 30_000L

        internal val FAMILY_SERVERS = arrayOf(FAMILY_DNS, FAMILY_DNS_SECONDARY)
        internal val STANDARD_SERVERS = arrayOf(STANDARD_DNS, STANDARD_DNS_SECONDARY)
    }
}

/**
 * Long-lived upstream resolver.
 *
 * The previous code opened and closed a [DatagramSocket] for every single query, so a busy page
 * churned hundreds of sockets and file descriptors; under long uptime this is what made blocking
 * unreliable. One protected socket is reused for the lifetime of the packet loop and recreated
 * only if it actually breaks.
 */
private class UpstreamResolver(private val protect: (DatagramSocket) -> Boolean) {

    private var socket: DatagramSocket? = null
    private val familyAddresses = arrayOfNulls<InetAddress>(FamilyVpnService.FAMILY_SERVERS.size)
    private val standardAddresses = arrayOfNulls<InetAddress>(FamilyVpnService.STANDARD_SERVERS.size)
    private val receiveBuffer = ByteArray(MAX_RESPONSE)

    /**
     * Sends the query in [buffer] upstream and writes the answer back into [buffer].
     * Returns the response length, or 0 on failure.
     */
    fun resolve(buffer: ByteArray, length: Int, useStandardDns: Boolean): Int {
        val servers = if (useStandardDns) FamilyVpnService.STANDARD_SERVERS else FamilyVpnService.FAMILY_SERVERS
        val cache = if (useStandardDns) standardAddresses else familyAddresses

        for (index in servers.indices) {
            val address = cache[index] ?: runCatching { InetAddress.getByName(servers[index]) }
                .getOrNull()?.also { cache[index] = it } ?: continue
            val active = ensureSocket() ?: return 0
            try {
                active.send(DatagramPacket(buffer, length, address, DNS_PORT))
                val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)
                active.receive(packet)
                val received = packet.length
                if (received in 12..buffer.size) {
                    System.arraycopy(receiveBuffer, 0, buffer, 0, received)
                    return received
                }
            } catch (_: Throwable) {
                // Drop the socket so the next attempt starts from a clean descriptor.
                close()
            }
        }
        return 0
    }

    private fun ensureSocket(): DatagramSocket? {
        socket?.let { if (!it.isClosed) return it }
        return runCatching {
            DatagramSocket().apply {
                soTimeout = TIMEOUT_MS
                if (!protect(this)) {
                    close()
                    return null
                }
            }
        }.getOrNull()?.also { socket = it }
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    private companion object {
        const val TIMEOUT_MS = 4_000
        const val MAX_RESPONSE = 4096
        const val DNS_PORT = 53
    }
}

/**
 * Suppresses repeated identical log events. Keeps memory bounded by evicting the oldest entries
 * once the window fills, so a long session cannot grow this map without limit.
 */
private class LogRateLimiter {

    private val recent = LinkedHashMap<String, Long>(64, 0.75f, true)

    @Synchronized
    fun shouldLog(key: String): Boolean {
        val now = System.currentTimeMillis()
        val previous = recent[key]
        if (previous != null && now - previous < WINDOW_MS) return false
        recent[key] = now
        if (recent.size > MAX_ENTRIES) {
            val iterator = recent.entries.iterator()
            while (iterator.hasNext() && recent.size > MAX_ENTRIES - EVICT_BATCH) {
                iterator.next()
                iterator.remove()
            }
        }
        return true
    }

    private companion object {
        const val WINDOW_MS = 30_000L
        const val MAX_ENTRIES = 256
        const val EVICT_BATCH = 64
    }
}
