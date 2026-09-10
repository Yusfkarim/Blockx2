package com.agon.app.review

import android.app.Activity
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Encapsulates the Google Play In-App Review API.
 *
 * Important limitations imposed by Google Play:
 * - The review dialog is shown at Google Play's discretion; it may not appear at all
 *   (quota exceeded, unsupported device, side-loaded install, etc.).
 * - The API provides NO way to know whether the user actually wrote or submitted a review.
 * - The dialog can only be shown a limited number of times; calling it too frequently
 *   causes Google Play to silently skip it.
 * - The flow is user-initiated only; the app must never force or gate features on it.
 *
 * Design decisions:
 * - We call the API and treat the *completion of the flow* (dialog dismissed, regardless
 *   of whether the user reviewed) as the signal to hide the prompt card, because:
 *   (a) Google Play gives us no stronger signal, and
 *   (b) re-prompting a user who already dismissed the dialog is counter-productive and
 *       will exhaust the quota.
 * - All exceptions are caught and swallowed — the app never crashes due to review flow.
 */
object InAppReviewManager {

    /**
     * Requests and launches the In-App Review flow.
     *
     * @return true if the review flow was successfully launched (dialog shown or completed),
     *         false if it could not be launched (no Google Play, quota, unsupported device, etc.).
     */
    suspend fun requestReview(activity: Activity): Boolean {
        return try {
            val manager = ReviewManagerFactory.create(activity)
            val reviewInfo = requestReviewInfo(manager)
            launchReviewFlow(activity, manager, reviewInfo)
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun requestReviewInfo(manager: ReviewManager): ReviewInfo =
        suspendCancellableCoroutine { continuation ->
            val task = manager.requestReviewFlow()
            task.addOnSuccessListener { info: ReviewInfo ->
                if (continuation.isActive) continuation.resume(info)
            }
            task.addOnFailureListener { exception: Exception ->
                if (continuation.isActive) continuation.resumeWithException(exception)
            }
            continuation.invokeOnCancellation {
                // Best-effort; the Task API has no cancel.
            }
        }

    private suspend fun launchReviewFlow(
        activity: Activity,
        manager: ReviewManager,
        reviewInfo: ReviewInfo,
    ): Unit = suspendCancellableCoroutine { continuation ->
        val task = manager.launchReviewFlow(activity, reviewInfo)
        task.addOnCompleteListener {
            if (continuation.isActive) continuation.resume(Unit)
        }
        continuation.invokeOnCancellation {
            // Best-effort; the Task API has no cancel.
        }
    }
}
