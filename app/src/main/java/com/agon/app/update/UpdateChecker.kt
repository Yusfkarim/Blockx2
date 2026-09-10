package com.agon.app.update

import android.content.Context
import com.agon.app.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Remote update manifest hosted as a tiny JSON file (e.g. on Supabase Storage). Lets the app know
 * the newest published version and whether updating is mandatory, independent of the Play Store —
 * so the update prompt also works for side-loaded installs.
 *
 * Example JSON:
 * {
 *   "latestVersionCode": 178,
 *   "latestVersionName": "2.3.0",
 *   "forceUpdate": true,
 *   "minSupportedVersionCode": 170,
 *   "storeUrl": "https://play.google.com/store/apps/details?id=com.familyshield.protection",
 *   "message": { "ku": "...", "ar": "...", "en": "..." }
 * }
 */
@Serializable
private data class FileEntry(
    @SerialName("name") val name: String = "",
    @SerialName("public_url") val publicUrl: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
private data class FilesResponse(
    @SerialName("success") val success: Boolean = false,
    @SerialName("data") val data: List<FileEntry> = emptyList(),
)

@Serializable
data class UpdateManifest(
    @SerialName("latestVersionCode") val latestVersionCode: Int = 0,
    @SerialName("latestVersionName") val latestVersionName: String = "",
    @SerialName("forceUpdate") val forceUpdate: Boolean = false,
    @SerialName("minSupportedVersionCode") val minSupportedVersionCode: Int = 0,
    @SerialName("storeUrl") val storeUrl: String = "",
    @SerialName("message") val message: Map<String, String> = emptyMap(),
)

/** The decision the UI acts on after a check. */
sealed interface UpdateStatus {
    /** No update needed, or the check could not run (fail-open so the app is never bricked). */
    data object UpToDate : UpdateStatus

    /** An update exists; [mandatory] means the user must update before continuing. */
    data class Available(
        val manifest: UpdateManifest,
        val mandatory: Boolean,
    ) : UpdateStatus
}

object UpdateChecker {

    /**
     * Files-listing endpoint. Every uploaded file is timestamp-prefixed, so instead of a fixed
     * manifest URL the app lists all files, picks the NEWEST one whose name contains
     * [MANIFEST_MARKER], and reads that. This makes the manifest updatable simply by uploading a
     * fresh copy — no app change needed.
     */
    const val FILES_LIST_URL =
        "https://mckxjaonicxqhugjiztv.supabase.co/functions/v1/storage-api/files"

    /** Filename marker that identifies the update manifest among all stored files. */
    const val MANIFEST_MARKER = "blockx-update-manifest"

    const val DEFAULT_STORE_URL =
        "https://play.google.com/store/apps/details?id=com.familyshield.protection"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Fetches the manifest and compares it with the running build. Never throws: any network or
     * parse failure returns [UpdateStatus.UpToDate] so a connectivity problem can never lock the
     * user out of their own protection app.
     */
    fun check(@Suppress("UNUSED_PARAMETER") context: Context): UpdateStatus {
        val manifestUrl = newestManifestUrl() ?: return UpdateStatus.UpToDate
        val manifest = runCatching {
            val request = Request.Builder()
                .url(manifestUrl)
                .header("Cache-Control", "no-cache")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return UpdateStatus.UpToDate
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return UpdateStatus.UpToDate
                json.decodeFromString<UpdateManifest>(body)
            }
        }.getOrNull() ?: return UpdateStatus.UpToDate

        val current = BuildConfig.VERSION_CODE
        if (manifest.latestVersionCode <= current) return UpdateStatus.UpToDate

        // Mandatory when the publisher forces it, or the running build is below the minimum
        // supported version.
        val mandatory = manifest.forceUpdate ||
            (manifest.minSupportedVersionCode > 0 && current < manifest.minSupportedVersionCode)
        return UpdateStatus.Available(manifest, mandatory)
    }

    /** Lists all stored files and returns the public URL of the newest update manifest, or null. */
    private fun newestManifestUrl(): String? = runCatching {
        val request = Request.Builder()
            .url(FILES_LIST_URL)
            .header("Cache-Control", "no-cache")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            val parsed = json.decodeFromString<FilesResponse>(body)
            parsed.data
                .filter { it.name.contains(MANIFEST_MARKER) && it.publicUrl.isNotBlank() }
                .maxByOrNull { it.createdAt }
                ?.publicUrl
        }
    }.getOrNull()
}
