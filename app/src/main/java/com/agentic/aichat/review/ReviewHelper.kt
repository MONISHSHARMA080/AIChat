package com.agentic.aichat.review

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Thin wrapper around the Google Play In-App Review API
 * (com.google.android.play:review / review-ktx 2.0.2).
 *
 * The Play API itself decides whether the review dialog is actually shown
 * (based on the user's Play quota and whether one was shown recently), and it
 * never reports that back to the caller. This helper therefore only reports
 * whether the flow was *requested and launched* without an error, and adds its
 * own lightweight gating so the app does not even ask Play too often.
 *
 * Usage from an [androidx.activity.ComponentActivity]:
 *
 * ```
 * override fun onCreate(savedInstanceState: Bundle?) {
 * 	super.onCreate(savedInstanceState)
 * 	ReviewHelper.recordLaunch(this)
 * 	ReviewHelper.maybeRequestInAppReview(this, this)
 * }
 * ```
 */
object ReviewHelper {

	private const val TAG = "ReviewHelper"

	private const val PREFS_NAME = "in_app_review"
	private const val KEY_LAUNCH_COUNT = "launch_count"
	private const val KEY_LAST_PROMPT_MS = "last_prompt_ms"

	private const val DAY_MS = 24L * 60L * 60L * 1000L

	/**
	 * Records one app launch. Call this exactly once per launch (e.g. from
	 * `Activity.onCreate`) *before* [maybeRequestInAppReview], since the gate
	 * counts recorded launches against that function's `minLaunches` threshold.
	 *
	 * @param context any context, used to reach the app-level [Context.getSharedPreferences]
	 */
	fun recordLaunch(context: Context) {
		val prefs = context.applicationContext
			.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
		prefs.edit()
			.putInt(KEY_LAUNCH_COUNT, prefs.getInt(KEY_LAUNCH_COUNT, 0) + 1)
			.apply()
	}

	/**
	 * Returns whether the review prompt gate currently allows a request:
	 * enough launches have been recorded and at least [minDaysBetween] days
	 * have passed since the last prompt attempt (never allowed before the
	 * first prompt).
	 *
	 * @param context any context
	 * @param minLaunches minimum number of recorded launches before prompting
	 * @param minDaysBetween minimum days between two prompt attempts
	 */
	fun shouldPrompt(
		context: Context,
		minLaunches: Int = 5,
		minDaysBetween: Long = 30,
	): Boolean {
		val prefs = context.applicationContext
			.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
		if (prefs.getInt(KEY_LAUNCH_COUNT, 0) < minLaunches) return false
		val lastPromptMs = prefs.getLong(KEY_LAST_PROMPT_MS, 0L)
		if (lastPromptMs != 0L &&
			System.currentTimeMillis() - lastPromptMs < minDaysBetween * DAY_MS
		) {
			return false
		}
		return true
	}

	/**
	 * Gated entry point: if [shouldPrompt] allows it, marks the gate as "just
	 * prompted" and starts the full in-app review flow (see
	 * [requestInAppReview]) on the [LifecycleOwner]'s lifecycle scope. The
	 * caller does not need to be on the main thread, and any failure of the
	 * flow is logged and swallowed so the app's normal flow is never
	 * interrupted.
	 *
	 * @param context the lifecycle owner's context (its [LifecycleOwner] is
	 * resolved to host the flow)
	 * @param activity the activity used to launch the review dialog
	 * @param minLaunches minimum launches before the first prompt
	 * @param minDaysBetween minimum days between prompt attempts
	 * @return whether the gate allowed a prompt *and* the flow launched without error
	 */
	fun maybeRequestInAppReview(
		context: Context,
		activity: Activity,
		minLaunches: Int = 5,
		minDaysBetween: Long = 30,
	): Boolean {
		if (!shouldPrompt(context, minLaunches, minDaysBetween)) return false
		val accessor = context as? LifecycleOwner ?: return false
		// Stamp the timestamp before launching: the review flow may never be
		// shown by Play (quota), but treating the attempt as consumed keeps us
		// from hammering the API on every launch.
		context.applicationContext
			.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit()
			.putLong(KEY_LAST_PROMPT_MS, System.currentTimeMillis())
			.apply()
		accessor.lifecycleScope.launch {
			requestInAppReview(activity)
		}
		return true
	}

	/**
	 * Runs the full, ungated in-app review flow over suspend call boundaries.
	 *
	 * Steps:
	 * 1. [ReviewManagerFactory.create] builds the platform [com.google.android.play.core.review.ReviewManager].
	 * 2. The review-ktx `requestReview()` suspend extension wraps
	 *    `ReviewManager.requestReviewFlow()`, returning a [com.google.android.play.core.review.ReviewInfo].
	 * 3. The review-ktx `launchReview()` suspend extension wraps
	 *    `ReviewManager.launchReviewFlow()` and shows the dialog when Play allows it.
	 *
	 * Note: whether the user left a review — or whether the dialog was shown
	 * at all — is intentionally not observable via this API, so `true`
	 * here only means "requested and launched without an error".
	 *
	 * @param activity the activity to launch the dialog from
	 * @return `true` when [com.google.android.play.core.review.ReviewInfo] was
	 * fetched and the flow completed without an error, `false` otherwise
	 * @throws kotlinx.coroutines.CancellationException propagated, never swallowed
	 */
	suspend fun requestInAppReview(activity: Activity): Boolean {
		val manager = ReviewManagerFactory.create(activity)
		return try {
			val reviewInfo = withContext(Dispatchers.Main) {
				manager.requestReview()
			}
			withContext(Dispatchers.Main) {
				manager.launchReview(activity, reviewInfo)
			}
			true
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Log.w(TAG, "In-app review flow did not complete", e)
			false
		}
	}
}
