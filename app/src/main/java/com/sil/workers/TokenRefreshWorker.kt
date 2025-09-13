package com.sil.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sil.others.Helpers.Companion.getRefreshToken
import com.sil.others.Helpers.Companion.refreshAccessToken
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class TokenRefreshWorker(private val context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    // region Vars
    private val TAG = "TokenRefreshWorker"
    // endregion

    override suspend fun doWork(): Result {
        return suspendCancellableCoroutine { cont ->
            refreshAccessToken(context) { success, newToken ->
                when {
                    success -> {
                        Log.i(TAG, "Access token refreshed successfully in worker.")
                        cont.resume(Result.success())
                    }
                    else -> {
                        Log.e(TAG, "Access token refresh failed in worker.")
                        cont.resume(Result.failure())
                    }
                }
            }
        }
    }
}