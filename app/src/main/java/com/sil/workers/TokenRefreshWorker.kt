package com.sil.workers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sil.buildmode.BuildConfig
import com.sil.others.Helpers
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okio.IOException
import java.util.TimeZone
import kotlin.coroutines.resume

class TokenRefreshWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    // region Vars
    private val TAG = "TokenRefreshWorker"
    private val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"
    private val SERVER_URL = BuildConfig.SERVER_URL
    private val APP_KEY = BuildConfig.APP_KEY
    private val USER_AGENT = BuildConfig.USER_AGENT
    // endregion

    override suspend fun doWork(): Result {
        val sharedPrefs = context.getSharedPreferences(PREFS_GENERAL, Context.MODE_PRIVATE)
        val refreshToken = sharedPrefs.getString("refresh_token", null) ?: return Result.failure()

        return suspendCancellableCoroutine { cont ->
            val jsonBody = JSONObject().put("refresh_token", refreshToken).toString()
            val request = Request.Builder()
                .url("${SERVER_URL}/api/refresh_token")
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("X-App-Key", APP_KEY)
                .addHeader("X-Timezone", TimeZone.getDefault().id)
                .post(RequestBody.create("application/json".toMediaTypeOrNull(), jsonBody))
                .build()

            OkHttpClient().newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Token refresh failed: ${e.message}")
                    cont.resume(Result.retry())
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        val token = JSONObject(body ?: "").optString("access_token")
                        if (token.isNotEmpty()) {
                            sharedPrefs.edit {
                                putString("access_token", token)
                            }
                            Log.i(TAG, "Access token refreshed")
                            cont.resume(Result.success())
                        } else {
                            cont.resume(Result.failure())
                        }
                    } else {
                        cont.resume(Result.failure())
                    }
                }
            })
        }
    }
}