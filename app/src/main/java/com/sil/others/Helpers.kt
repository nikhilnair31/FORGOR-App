package com.sil.others

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.sil.buildmode.BuildConfig
import com.sil.models.FrequencyOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class Helpers {
    companion object {
        // region Vars
        private const val TAG = "Helper"
        private const val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"

        private const val SERVER_URL = BuildConfig.SERVER_URL
        private const val APP_KEY = BuildConfig.APP_KEY
        private const val USER_AGENT = BuildConfig.USER_AGENT

        private val httpClient = OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        // endregion

        // region API Related
        private fun buildAuthorizedRequest(url: String, method: String = "POST", token: String, body: RequestBody? = null): Request {
            val builder = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("X-App-Key", APP_KEY)

            val empty = ByteArray(0).toRequestBody()

            when (method.uppercase()) {
                "GET" -> builder.get()
                "POST" -> builder.post(body ?: empty)
                "PUT" -> builder.put(body ?: empty)
                "PATCH" -> builder.patch(body ?: empty)
                "DELETE" -> {
                    if (body != null) builder.delete(body) else builder.delete()
                }
                else -> builder.method(method.uppercase(), body) // fallback
            }

            return builder.build()
        }
        private fun withValidToken(context: Context, onValid: (token: String) -> Unit, onInvalid: (() -> Unit)? = null) {
            val prefs = context.getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)
            val token = prefs.getString("access_token", "") ?: ""
            if (token.isEmpty()) {
                showToast(context, "Not logged in")
                onInvalid?.invoke()
                return
            }
            onValid(token)
        }
        // endregion

        // region Image Related
        fun uploadImageFileToServer(context: Context, imageFile: File?) {
            Log.i(TAG, "Uploading Image to Server...")

            if (imageFile == null || !imageFile.exists() || !imageFile.canRead() || imageFile.length() <= 0) {
                Log.e(TAG, "Image file does not exist or is unreadable.")
                return
            }

            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val networkInfo = connectivityManager.activeNetworkInfo
            if (networkInfo == null || !networkInfo.isConnected) {
                Log.i(TAG, "No internet connection. Deferring upload with WorkManager.")

                val uploadWork = androidx.work.OneTimeWorkRequestBuilder<com.sil.workers.UploadWorker>()
                    .setInputData(
                        androidx.work.workDataOf(
                            "uploadType" to "image",
                            "filePath" to imageFile.absolutePath
                        )
                    )
                    .setConstraints(
                        androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                            .build()
                    )
                    .build()

                androidx.work.WorkManager.getInstance(context).enqueue(uploadWork)
                return
            }

            fun sendRequest(token: String) {
                val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("image", imageFile.name, imageFile.asRequestBody("image/png".toMediaTypeOrNull()))
                    .build()

                val request = buildAuthorizedRequest(
                    "$SERVER_URL/api/upload/image",
                    token = token,
                    body = requestBody
                )

                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Image save failed: ${e.localizedMessage}")
                        showToast(context, "Save failed!")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.code == 401) {
                            // Token expired, attempt refresh
                            refreshAccessToken(context) { success, newToken ->
                                if (success && !newToken.isNullOrEmpty()) {
                                    sendRequest(newToken) // Retry with new token
                                } else {
                                    showToast(context, "Session expired. Please log in again.")
                                }
                            }
                            return
                        }
                        if (response.code == 403) {
                            showToast(context, "Daily save limit reached")
                            return
                        }

                        if (response.isSuccessful) {
                            showToast(context, "Saved!")
                        } else {
                            Log.e(TAG, "Server error: ${response.code}")
                            showToast(context, "Save failed!")
                        }
                    }
                })
            }

            withValidToken(context, { token -> sendRequest(token) })
        }

        fun getImageURL(context: Context, imageUrl: String): GlideUrl? {
            // Log.i(TAG, "getImageURL | getting image URL...")

            try {
                val generalSharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)
                val accessToken = generalSharedPrefs.getString("access_token", "") ?: ""
                if (accessToken.isEmpty()) {
                    Log.e(TAG, "Access token missing")
                    showToast(context, "Not logged in")
                    return null
                }

                val glideUrl = GlideUrl(imageUrl, LazyHeaders.Builder()
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("X-App-Key", APP_KEY)
                    .build())

                return glideUrl
            }
            catch (e: Exception) {
                when (e) {
                    is FileNotFoundException -> Log.e(TAG, "Image file not found: ${e.message}")
                    else -> Log.e(TAG, "Error in image upload: ${e.localizedMessage}")
                }
                e.printStackTrace()
                return null
            }
        }

        fun isImageFile(fileName: String): Boolean {
            val lowerCaseName = fileName.lowercase()
            return lowerCaseName.endsWith(".jpg") ||
                    lowerCaseName.endsWith(".jpeg") ||
                    lowerCaseName.endsWith(".png") ||
                    lowerCaseName.endsWith(".webp")
        }
        // endregion

        // region File Related
        fun getSavesLeft(context: Context, callback: (Int) -> Unit) {
            Log.i(TAG, "Getting saves left from server...")

            fun sendRequest(token: String) {
                val request = buildAuthorizedRequest(
                    url = "$SERVER_URL/api/saves-left",
                    method = "GET",
                    token = token
                )

                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Failed to check saves: ${e.localizedMessage}")
                        showToast(context, "Failed to check saves!")
                        callback(0)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.code == 401) {
                            refreshAccessToken(context) { success, newToken ->
                                if (success && newToken != null) sendRequest(newToken)
                                else {
                                    showToast(context, "Login expired")
                                    callback(0)
                                }
                            }
                            return
                        }
                        if (response.code == 403) {
                            showToast(context, "Daily save limit reached")
                            return
                        }

                        if (response.isSuccessful) {
                            response.body?.string()?.let { body ->
                                try {
                                    val json = JSONObject(body)
                                    val savesLeft = json.getInt("uploads_left")
                                    val sharedPrefs = context.getSharedPreferences(TAG, MODE_PRIVATE)
                                    sharedPrefs.edit { putInt("cached_saves_left", savesLeft) }
                                    callback(savesLeft)
                                } catch (e: Exception) {
                                    Log.e(TAG, "JSON parsing error: ${e.localizedMessage}")
                                    callback(0)
                                }
                            } ?: run {
                                callback(0)
                            }
                        } else {
                            showToast(context, "Could not get saves left")
                            callback(0)
                        }
                    }
                })
            }

            withValidToken(context, { token -> sendRequest(token) })
        }

        fun deleteFile(context: Context, fileName: String) {
            Log.i(TAG, "Deleting file: $fileName")

            fun sendRequest(token: String) {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file_name", fileName)
                    .build()

                val request = buildAuthorizedRequest(
                    "$SERVER_URL/api/delete/file",
                    token = token,
                    body = requestBody
                )

                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "File delete failed: ${e.localizedMessage}")
                        showToast(context, "File delete failed!")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.code == 401) {
                            // Token expired, attempt refresh
                            refreshAccessToken(context) { success, newToken ->
                                if (success && !newToken.isNullOrEmpty()) {
                                    sendRequest(newToken) // Retry with new token
                                } else {
                                    showToast(context, "Session expired. Please log in again.")
                                }
                            }
                            return
                        }
                        if (response.code == 403) {
                            showToast(context, "Daily save limit reached")
                            return
                        }

                        if (response.isSuccessful) {
                            showToast(context, "File deleted successfully!")
                        } else {
                            Log.e(TAG, "Server error: ${response.code}")
                            showToast(context, "File delete failed!")
                        }
                    }
                })
            }

            withValidToken(context, { token -> sendRequest(token) })
        }
        // endregion

        // region Auth Related
        fun refreshAccessToken(context: Context, onComplete: (success: Boolean, newToken: String?) -> Unit) {
            val prefs = context.getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)
            val refreshToken = prefs.getString("refresh_token", "") ?: ""
            if (refreshToken.isEmpty()) {
                Log.e(TAG, "Refresh token missing")
                autoLogout(context)
                onComplete(false, null)
                return
            }

            val jsonBody = """
                {
                    "refresh_token": "$refreshToken"
                }
            """.trimIndent()

            val timeZoneId = TimeZone.getDefault().id

            val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("$SERVER_URL/api/refresh_token")
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("X-App-Key", APP_KEY)
                .addHeader("X-Timezone", timeZoneId)
                .post(requestBody)
                .build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Refresh token failed: ${e.localizedMessage}")
                    autoLogout(context)
                    onComplete(false, null)
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()
                    if (response.isSuccessful) {
                        try {
                            val json = JSONObject(responseBody)
                            val newToken = json.optString("access_token", "")
                            if (newToken.isNotEmpty()) {
                                prefs.edit { putString("access_token", newToken) }
                                Log.i(TAG, "Access token refreshed")
                                onComplete(true, newToken)
                                return
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing refresh token response: ${e.localizedMessage}")
                        }
                    } else {
                        Log.e(TAG, "Refresh token failed: $responseBody")
                    }
                    autoLogout(context)
                    onComplete(false, null)
                }
            })
        }

        fun authRegisterToServer(context: Context, username: String, email: String, password: String, timeZoneId: String, callback: (success: Boolean) -> Unit) {
            Log.i(TAG, "Trying to register with $username and $email and $password at $timeZoneId")

            val jsonBody = """
            {
                "username": "$username",
                "email": "$email",
                "password": "$password",
                "timezone": "$timeZoneId"
            }
            """.trimIndent()
            val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("$SERVER_URL/api/register")
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("X-App-Key", APP_KEY)
                .post(requestBody)
                .build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Register failed: ${e.localizedMessage}")
                    showToast(context, "Register failed!")
                    callback(false)
                }
                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()
                    if (response.isSuccessful) {
                        Log.i(TAG, "Register successful: $responseBody")

                        // Now attempt login and forward that result
                        authLoginToServer(context, username, email, password) { loginSuccess ->
                            Log.i(TAG, "Login success")
                            callback(loginSuccess)
                        }
                    } else {
                        Log.e(TAG, "Register error ${response.code}: $responseBody")
                        showToast(context, "Register failed!")
                        callback(false)
                    }
                }
            })
        }
        fun authLoginToServer(context: Context, username: String, email: String, password: String, callback: (success: Boolean) -> Unit) {
            Log.i(TAG, "Trying to login...")

            val jsonBody = """
            {
                "username": "$username",
                "email": "$email",
                "password": "$password"
            }
            """.trimIndent()

            val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

            val timeZoneId = TimeZone.getDefault().id

            val request = Request.Builder()
                .url("$SERVER_URL/api/login")
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("X-App-Key", APP_KEY)
                .addHeader("X-Timezone", timeZoneId)
                .post(requestBody)
                .build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Login failed: ${e.localizedMessage}")
                    showToast(context, "Login failed!")
                    callback(false)
                }
                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()
                    if (response.isSuccessful) {
                        Log.i(TAG, "Login successful: $response")

                        val json = JSONObject(responseBody)
                        val accessToken = json.optString("access_token", "")
                        val refreshToken = json.optString("refresh_token", "")

                        if (!accessToken.isEmpty() && !refreshToken.isEmpty()) {
                            Log.i(TAG, "Tokens received")
                            val generalSharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)
                            generalSharedPrefs.edit {
                                putString("access_token", accessToken.toString())
                                putString("refresh_token", refreshToken.toString())
                            }
                            callback(true)
                            return
                        }
                    } else {
                        Log.e(TAG, "Login error ${response.code}: $responseBody")
                        showToast(context, "Login failed!")
                        callback(false)
                    }
                }
            })
        }

        fun autoLogout(context: Context) {
            Log.i(TAG, "autoLogout | Logging out user...")

            val prefs = context.getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)
            prefs.edit {
                remove("username")
                remove("email")
                remove("access_token")
                remove("refresh_token")
            }

            showToast(context, "Session expired. Please sign in again.")

            val intent = Intent(context, com.sil.buildmode.Welcome::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            context.startActivity(intent)
        }
        // endregion

        // region User Account Related
        fun authAccountDelete(context: Context, callback: (success: Boolean) -> Unit) {
            Log.i(TAG, "Trying to delete account...")

            val generalSharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)
            val accessToken = generalSharedPrefs.getString("access_token", "") ?: ""
            if (accessToken.isEmpty()) {
                Log.e(TAG, "Access token missing")
                showToast(context, "Not logged in")
                return
            }

            fun sendRequest(token: String) {
                val request = buildAuthorizedRequest(
                    "$SERVER_URL/api/account_delete",
                    token = token,
                    method = "DELETE",
                    body = null
                )

                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Account delete failed: ${e.localizedMessage}")
                        showToast(context, "Delete failed!")
                        callback(false)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()

                        if (response.code == 401) {
                            refreshAccessToken(context) { success, newToken ->
                                if (success && !newToken.isNullOrEmpty()) {
                                    sendRequest(newToken) // Retry with refreshed token
                                } else {
                                    showToast(context, "Session expired. Please log in again.")
                                    callback(false)
                                }
                            }
                            return
                        }

                        if (response.isSuccessful) {
                            Log.i(TAG, "Account deleted successfully: $responseBody")
                            showToast(context, "Account deleted")
                            callback(true)
                        } else {
                            Log.e(TAG, "Delete error ${response.code}: $responseBody")
                            showToast(context, "Delete failed!")
                            callback(false)
                        }
                    }
                })
            }

            sendRequest(accessToken)
        }

        fun getSummaryFrequency(context: Context, callback: (success: Int) -> Unit) {
            Log.i(TAG, "Trying to get summary frequency index...")

            fun sendRequest(token: String) {
                val request = buildAuthorizedRequest(
                    url = "$SERVER_URL/api/summary-frequency",
                    method = "GET",
                    token = token
                )

                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Failed to get summary frequency index: ${e.localizedMessage}")
                        showToast(context, "Failed to get summary frequency index!")
                        callback(0)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.code == 401) {
                            refreshAccessToken(context) { success, newToken ->
                                if (success && newToken != null) sendRequest(newToken)
                                else {
                                    showToast(context, "Login expired")
                                    callback(0)
                                }
                            }
                            return
                        }

                        if (response.isSuccessful) {
                            response.body?.string()?.let { body ->
                                try {
                                    val json = JSONObject(body)
                                    val summaryFrequencyIndex = json.getInt("summary_index")
                                    val sharedPrefs = context.getSharedPreferences(TAG, MODE_PRIVATE)
                                    sharedPrefs.edit { putInt("summary_index", summaryFrequencyIndex) }
                                    callback(summaryFrequencyIndex)
                                } catch (e: Exception) {
                                    Log.e(TAG, "JSON parsing error: ${e.localizedMessage}")
                                    callback(0)
                                }
                            } ?: run {
                                callback(0)
                            }
                        } else {
                            showToast(context, "Could not get summary frequency index")
                            callback(0)
                        }
                    }
                })
            }

            withValidToken(context, { token -> sendRequest(token) })
        }
        fun getDigestFrequency(context: Context, callback: (success: Int) -> Unit) {
            Log.i(TAG, "Trying to get digest frequency index...")

            fun sendRequest(token: String) {
                val request = buildAuthorizedRequest(
                    url = "$SERVER_URL/api/digest-frequency",
                    method = "GET",
                    token = token
                )

                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Failed to get digest frequency index: ${e.localizedMessage}")
                        showToast(context, "Failed to get digest frequency index!")
                        callback(0)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.code == 401) {
                            refreshAccessToken(context) { success, newToken ->
                                if (success && newToken != null) sendRequest(newToken)
                                else {
                                    showToast(context, "Login expired")
                                    callback(0)
                                }
                            }
                            return
                        }

                        if (response.isSuccessful) {
                            response.body?.string()?.let { body ->
                                try {
                                    val json = JSONObject(body)
                                    val digestFrequencyIndex = json.getInt("digest_index")
                                    val sharedPrefs = context.getSharedPreferences(TAG, MODE_PRIVATE)
                                    sharedPrefs.edit { putInt("digest_index", digestFrequencyIndex) }
                                    callback(digestFrequencyIndex)
                                } catch (e: Exception) {
                                    Log.e(TAG, "JSON parsing error: ${e.localizedMessage}")
                                    callback(0)
                                }
                            } ?: run {
                                callback(0)
                            }
                        } else {
                            showToast(context, "Could not get digest frequency index")
                            callback(0)
                        }
                    }
                })
            }

            withValidToken(context, { token -> sendRequest(token) })
        }

        fun getAllFrequencies(callback: (List<FrequencyOption>) -> Unit) {
            val request = Request.Builder()
                .url("$SERVER_URL/api/frequencies")
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("X-App-Key", APP_KEY)
                .get()
                .build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Failed to fetch frequencies: ${e.localizedMessage}")
                    callback(emptyList())
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        response.body?.string()?.let { body ->
                            try {
                                val jsonArray = JSONArray(body)
                                val freqs = mutableListOf<FrequencyOption>()
                                for (i in 0 until jsonArray.length()) {
                                    val obj = jsonArray.getJSONObject(i)
                                    freqs.add(
                                        FrequencyOption(
                                            obj.getInt("id"),
                                            obj.getString("name")
                                        )
                                    )
                                }
                                callback(freqs)
                            } catch (e: Exception) {
                                Log.e(TAG, "JSON error: ${e.localizedMessage}")
                                callback(emptyList())
                            }
                        } ?: callback(emptyList())
                    } else {
                        callback(emptyList())
                    }
                }
            })
        }

        fun editUserUsernameToServer(context: Context, newUsername: String, callback: (success: Boolean) -> Unit) {
            Log.i(TAG, "Trying to edit username to $newUsername")

            val generalSharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)
            val accessToken = generalSharedPrefs.getString("access_token", "") ?: ""
            if (accessToken.isEmpty()) {
                Log.e(TAG, "Access token missing")
                showToast(context, "Not logged in")
                return
            }

            val jsonBody = """
            {
                "new_username": "$newUsername"
            }
            """.trimIndent()
            val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

            fun sendRequest(token: String) {
                val request = buildAuthorizedRequest(
                    "$SERVER_URL/api/update-username",
                    token = token,
                    method = "PUT",
                    body = requestBody
                )

                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Edit username failed: ${e.localizedMessage}")
                        showToast(context, "Edit failed!")
                        callback(false)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()

                        if (response.code == 401) {
                            refreshAccessToken(context) { success, newToken ->
                                if (success && !newToken.isNullOrEmpty()) {
                                    sendRequest(newToken) // Retry with refreshed token
                                } else {
                                    showToast(context, "Session expired. Please log in again.")
                                    callback(false)
                                }
                            }
                            return
                        }
                        if (response.code == 403) {
                            showToast(context, "Daily save limit reached")
                            return
                        }

                        if (response.isSuccessful) {
                            Log.i(TAG, "Edit username successful: $responseBody")
                            callback(true)
                        } else {
                            Log.e(TAG, "Edit username error ${response.code}: $responseBody")
                            showToast(context, "Edit failed!")
                            callback(false)
                        }
                    }
                })
            }

            sendRequest(accessToken)
        }
        fun editUserEmailToServer(context: Context, newEmail: String, callback: (success: Boolean) -> Unit) {
            Log.i(TAG, "Trying to edit email to $newEmail")

            val generalSharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)
            val accessToken = generalSharedPrefs.getString("access_token", "") ?: ""
            if (accessToken.isEmpty()) {
                Log.e(TAG, "Access token missing")
                showToast(context, "Not logged in")
                return
            }

            val jsonBody = """
            {
                "new_email": "$newEmail"
            }
            """.trimIndent()
            val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

            fun sendRequest(token: String) {
                val request = buildAuthorizedRequest(
                    "$SERVER_URL/api/update-email",
                    token = token,
                    method = "PUT",
                    body = requestBody
                )

                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Edit email failed: ${e.localizedMessage}")
                        showToast(context, "Edit failed!")
                        callback(false)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()

                        if (response.code == 401) {
                            refreshAccessToken(context) { success, newToken ->
                                if (success && !newToken.isNullOrEmpty()) {
                                    sendRequest(newToken) // Retry with refreshed token
                                } else {
                                    showToast(context, "Session expired. Please log in again.")
                                    callback(false)
                                }
                            }
                            return
                        }
                        if (response.code == 403) {
                            showToast(context, "Daily save limit reached")
                            return
                        }

                        if (response.isSuccessful) {
                            Log.i(TAG, "Edit email successful: $responseBody")
                            callback(true)
                        } else {
                            Log.e(TAG, "Edit email error ${response.code}: $responseBody")
                            showToast(context, "Edit failed!")
                            callback(false)
                        }
                    }
                })
            }

            sendRequest(accessToken)
        }
        fun editSummaryFreqToServer(context: Context, frequencyId: Int, callback: (success: Boolean) -> Unit) {
            Log.i(TAG, "Updating summary frequency to ID: $frequencyId")

            val generalSharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)
            val accessToken = generalSharedPrefs.getString("access_token", "") ?: ""
            if (accessToken.isEmpty()) {
                Log.e(TAG, "Access token missing")
                showToast(context, "Not logged in")
                return
            }

            val jsonBody = """
                {
                    "frequency_id": "$frequencyId"
                }
            """.trimIndent()
            val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

            fun sendRequest(token: String) {
                val request = buildAuthorizedRequest(
                    "$SERVER_URL/api/summary-frequency",
                    token = token,
                    method = "PUT",
                    body = requestBody
                )

                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Edit summary freq failed: ${e.localizedMessage}")
                        showToast(context, "Edit failed!")
                        callback(false)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val body = response.body?.string()

                        when (response.code) {
                            200 -> {
                                Log.i("Helpers", "Summary frequency updated: $body")
                                showToast(context, "Summary updated")
                                callback(true)
                            }
                            401 -> {
                                refreshAccessToken(context) { success, newToken ->
                                    if (success && !newToken.isNullOrEmpty()) {
                                        editSummaryFreqToServer(context, frequencyId, callback) // Retry
                                    } else {
                                        showToast(context, "Session expired")
                                        callback(false)
                                    }
                                }
                            }
                            else -> {
                                Log.e("Helpers", "Failed to update summary freq. Code: ${response.code}, Body: $body")
                                showToast(context, "Update failed!")
                                callback(false)
                            }
                        }

                    }
                })
            }

            sendRequest(accessToken)
        }
        fun editDigestFreqToServer(context: Context, frequencyId: Int, callback: (success: Boolean) -> Unit) {
            Log.i(TAG, "Updating digest frequency to ID: $frequencyId")

            val generalSharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)
            val accessToken = generalSharedPrefs.getString("access_token", "") ?: ""
            if (accessToken.isEmpty()) {
                Log.e(TAG, "Access token missing")
                showToast(context, "Not logged in")
                return
            }

            val jsonBody = """
                {
                    "frequency_id": $frequencyId
                }
            """.trimIndent()
            val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

            fun sendRequest(token: String) {
                val request = buildAuthorizedRequest(
                    "$SERVER_URL/api/digest-frequency",
                    token = token,
                    method = "PUT",
                    body = requestBody
                )

                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Edit digest freq failed: ${e.localizedMessage}")
                        showToast(context, "Edit failed!")
                        callback(false)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val body = response.body?.string()

                        when (response.code) {
                            200 -> {
                                Log.i("Helpers", "Digest frequency updated: $body")
                                showToast(context, "Digest updated")
                                callback(true)
                            }
                            401 -> {
                                refreshAccessToken(context) { success, newToken ->
                                    if (success && !newToken.isNullOrEmpty()) {
                                        editDigestFreqToServer(context, frequencyId, callback) // Retry
                                    } else {
                                        showToast(context, "Session expired")
                                        callback(false)
                                    }
                                }
                            }
                            else -> {
                                Log.e("Helpers", "Failed to update digest freq. Code: ${response.code}, Body: $body")
                                showToast(context, "Update failed!")
                                callback(false)
                            }
                        }
                    }
                })
            }

            sendRequest(accessToken)
        }
        // endregion

        // region Interaction Related
        fun insertPostInteraction(context: Context, fileId: Int, query: String, callback: (success: Boolean) -> Unit) {
            Log.i(TAG, "Trying to insert interaction for fileId $fileId with query $query")

            val generalSharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)
            val accessToken = generalSharedPrefs.getString("access_token", "") ?: ""
            if (accessToken.isEmpty()) {
                Log.e(TAG, "Access token missing")
                showToast(context, "Not logged in")
                return
            }

            // Valid JSON, no trailing comma. fileId stays numeric.
            val json = JSONObject().apply {
                put("fileId", fileId)
                put("query", query)
            }
            val requestBody = json.toString().toRequestBody("application/json".toMediaType())

            fun sendRequest(token: String) {
                val request = buildAuthorizedRequest(
                    "$SERVER_URL/api/insert-post-interaction",
                    token = token,
                    method = "PUT",
                    body = requestBody
                )

                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Insert interaction failed: ${e.localizedMessage}")
                        callback(false)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()

                        if (response.code == 401) {
                            refreshAccessToken(context) { success, newToken ->
                                if (success && !newToken.isNullOrEmpty()) {
                                    sendRequest(newToken) // retry with refreshed token
                                } else {
                                    showToast(context, "Session expired. Please log in again.")
                                    callback(false)
                                }
                            }
                            return
                        }

                        if (response.isSuccessful) {
                            Log.i(TAG, "Insert interaction successful: $responseBody")
                            callback(true)
                        } else {
                            Log.e(TAG, "Insert interaction error ${response.code}: $responseBody")
                            callback(false)
                        }
                    }
                })
            }

            sendRequest(accessToken)
        }
        // endregion

        // region Search Related
        fun searchToServer(context: Context, query: String, callback: (response: String?) -> Unit) {
            Log.i(TAG, "Trying to search for $query")

            val jsonBody = """
                {
                    "searchText": "$query"
                }
            """.trimIndent()
            val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())
            val startTime = System.currentTimeMillis()

            fun sendRequest(token: String) {
                val request = buildAuthorizedRequest(
                    "$SERVER_URL/api/query",
                    token = token,
                    body = requestBody
                )

                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Query failed: ${e.localizedMessage}")
                        showToast(context, "Query failed!")
                        callback(null)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.code == 401) {
                            refreshAccessToken(context) { success, newToken ->
                                if (success && !newToken.isNullOrEmpty()) sendRequest(newToken)
                                else {
                                    showToast(context, "Login expired")
                                    callback(null)
                                }
                            }
                            return
                        }
                        if (response.code == 403) {
                            showToast(context, "Daily save limit reached")
                            callback(null)
                            return
                        }

                        if (response.isSuccessful) {
                            val endTime = System.currentTimeMillis()
                            Log.i(TAG, "Query roundtrip time: ${endTime - startTime} ms")
                            callback(response.body?.string())
                        } else {
                            showToast(context, "Query failed!")
                            callback(null)
                        }
                    }
                })
            }

            withValidToken(context, { token -> sendRequest(token) }, onInvalid = { callback(null) })
        }

        fun getSimilarFromServer(context: Context, fileName: String, callback: (success: Boolean, resultJson: String?) -> Unit) {
            Log.i(TAG, "Getting similar content for file: $fileName")

            val startTime = System.currentTimeMillis()
            val requestUrl = "$SERVER_URL/api/get_similar/$fileName"

            fun sendRequest(token: String) {
                val request = buildAuthorizedRequest(
                    url = requestUrl,
                    method = "GET",
                    token = token
                )

                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Get similar failed: ${e.localizedMessage}")
                        showToast(context, "Get similar failed!")
                        callback(false, null)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val roundtripTime = System.currentTimeMillis() - startTime
                        val responseBody = response.body?.string()

                        if (response.code == 401) {
                            refreshAccessToken(context) { success, newToken ->
                                if (success && !newToken.isNullOrEmpty()) sendRequest(newToken)
                                else {
                                    showToast(context, "Login expired")
                                    callback(false, null)
                                }
                            }
                            return
                        }

                        if (response.isSuccessful) {
                            Log.i(TAG, "Get similar roundtrip time: ${roundtripTime}ms")
                            Log.d(TAG, "Get similar response: $responseBody")
                            callback(true, responseBody)
                        } else {
                            Log.e(TAG, "Get similar failed with code ${response.code}: $responseBody")
                            showToast(context, "Get similar failed!")
                            callback(false, null)
                        }
                    }
                })
            }

            withValidToken(context, { token -> sendRequest(token) }, onInvalid = {
                callback(false, null)
            })
        }
        // endregion

        // region Content Related
        fun queryContentResolver(context: Context, uri: Uri, selection: String?, selectionArgs: Array<String>?): String? {
            val projection = arrayOf(MediaStore.Images.Media.DATA)
            try {
                context.contentResolver.query(uri, projection, selection, selectionArgs, null)
                    .use { cursor ->
                        if (cursor != null && cursor.moveToFirst()) {
                            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                            return cursor.getString(columnIndex)
                        }
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }

        fun downloadAndShareFile(context: Context, fileName: String, downloadUrl: String) {
            Log.d(TAG, "Attempting to download from: $downloadUrl")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val sharedPrefs = context.getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)
                    val accessToken = sharedPrefs.getString("access_token", "") ?: ""
                    if (accessToken.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            showToast(context, "Not logged in")
                        }
                        return@launch
                    }

                    val client = httpClient
                    val request = Request.Builder()
                        .url(downloadUrl)
                        .addHeader("Authorization", "Bearer $accessToken")
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("X-App-Key", APP_KEY)
                        .build()
                    val response = client.newCall(request).execute()

                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        Log.e(TAG, "Error ${response.code}: $errorBody")
                        return@launch
                    }

                    val file = File(context.cacheDir, fileName)
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            when {
                                isImageFile(file.name) -> {
                                    type = getMimeType(file.name) ?: "image/*"
                                    val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", file)
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                else -> {
                                    type = getMimeType(file.name) ?: "*/*"
                                    val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", file)
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            }
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share file via"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception: ${e.message}")
                }
            }
        }

        fun requestDataExport(context: Context, callback: (success: Boolean) -> Unit) {
            Log.i(TAG, "Starting bulk download...")

            val generalSharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE)
            val accessToken = generalSharedPrefs.getString("access_token", "") ?: ""
            if (accessToken.isEmpty()) {
                Log.e(TAG, "Access token missing")
                showToast(context, "Not logged in")
                callback(false)
                return
            }

            fun sendRequest(token: String) {
                val request = buildAuthorizedRequest(
                    "$SERVER_URL/api/data-export",
                    token = token,
                    method = "GET",
                    body = null
                )

                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Bulk download failed: ${e.localizedMessage}")
                        showToast(context, "Download failed!")
                        callback(false)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        when (response.code) {
                            200 -> {
                                Log.i(TAG, "Bulk download successful!")
                                showToast(context, "Bulk download successful!")
                                callback(true)
                            }

                            401 -> {
                                refreshAccessToken(context) { success, newToken ->
                                    if (success && !newToken.isNullOrEmpty()) {
                                        sendRequest(newToken) // retry
                                    } else {
                                        showToast(context, "Session expired. Please log in again.")
                                        callback(false)
                                    }
                                }
                            }

                            403 -> {
                                showToast(context, "Forbidden or daily limit reached")
                                callback(false)
                            }
                            429 -> {
                                showToast(context, "Too many requests, slow down")
                                callback(false)
                            }

                            else -> {
                                Log.e(TAG, "Bulk download error ${response.code}")
                                showToast(context, "Download failed! Code: ${response.code}")
                                callback(false)
                            }
                        }
                    }
                })
            }

            sendRequest(accessToken)
        }

        fun getScreenshotsPath(): String? {
            // For most devices, screenshots are in DCIM/Screenshots or Pictures/Screenshots
            val dcimPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            val picturesPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

            val possiblePaths = listOf(
                File(picturesPath, "Screenshots"),
                // Some devices might use the root of DCIM or Pictures
                picturesPath
            )

            for (path in possiblePaths) {
                if (path.exists() && path.isDirectory) {
                    return path.absolutePath
                }
            }

            // If we can't find a known screenshots directory, default to DCIM/Screenshots
            val defaultPath = File(dcimPath, "Screenshots")
            defaultPath.mkdirs()

            return defaultPath.absolutePath
        }
        fun getRealPathFromUri(context: Context, uri: Uri): String? {
            var realPath: String? = null

            if (DocumentsContract.isDocumentUri(context, uri)) {
                // If it's a document, like Google Photos
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (split.size >= 2) {
                    val type = split[0]
                    val id = split[1]

                    if ("image" == type) {
                        // Try querying MediaStore
                        val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        val selection = "_id=?"
                        val selectionArgs = arrayOf(id)

                        realPath = queryContentResolver(context, contentUri, selection, selectionArgs)
                    }
                }
            } else if ("content".equals(uri.scheme, ignoreCase = true)) {
                // General content:// URI
                realPath = queryContentResolver(context, uri, null, null)
            } else if ("file".equals(uri.scheme, ignoreCase = true)) {
                // Direct file path
                realPath = uri.path
            }

            return realPath
        }
        fun getMimeType(fileName: String): String? {
            val extension = fileName.substringAfterLast('.', "")
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }

        fun copyUriToTempFile(context: Context, uri: Uri): File? {
            var tempFile: File? = null
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    tempFile = File.createTempFile("shared_image_", ".jpg", context.cacheDir)
                    val outputStream: OutputStream = FileOutputStream(tempFile)

                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while ((inputStream.read(buffer).also { bytesRead = it }) != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }

                    outputStream.close()
                    inputStream.close()
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
            return tempFile
        }
        // endregion

        // region URL Related
        fun sanitizeLinkLabel(url: String): String {
            return try {
                val u = url.toUri()
                val host = u.host?.replace("www.", "") ?: url
                val path = u.path.orEmpty().trim('/').takeIf { it.isNotEmpty() } ?: ""
                if (path.isEmpty()) host else "$host/$path"
            } catch (_: Exception) { url }
        }

        fun resolveHandleToUrl(appName: String, raw: String): String {
            val handle = raw.removePrefix("@")
            return when (appName.lowercase()) {
                "youtube" -> "https://www.youtube.com/@$handle"
                "instagram" -> "https://www.instagram.com/$handle/"
                "twitter", "x" -> "https://x.com/$handle"
                "tiktok" -> "https://www.tiktok.com/@$handle"
                "reddit" -> "https://www.reddit.com/user/$handle"
                else -> "https://www.google.com/search?q=${Uri.encode(raw)}"
            }
        }

        fun Context.openExternal(url: String) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) // `contextIntent` might be a custom helper; directly use Intent
            } catch (e: ActivityNotFoundException) { // Be more specific with the exception
                Log.e(TAG, "No activity found to handle opening $url: ${e.localizedMessage}")
            } catch (e: Exception) { // Catch other potential exceptions
                Log.e(TAG, "Failed to open $url: ${e.localizedMessage}")
            }
        }

        // endregion

        // region Service Related
        fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
            Log.i(TAG, "isServiceRunning | Checking if ${serviceClass.simpleName} is running...")

            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.name == service.service.className) {
                    return true
                }
            }
            return false
        }
        // endregion

        // region UI Related
        fun showToast(context: Context, message: String) {
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            mainHandler.post {
                Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
            }
        }
        // endregion
    }
}
