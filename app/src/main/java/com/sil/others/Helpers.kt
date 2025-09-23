package com.sil.others

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.net.ConnectivityManager
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
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.sil.buildmode.BuildConfig
import com.sil.models.FrequencyOption
import com.sil.utils.ScreenshotServiceUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
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
        private const val USER_AGENT = BuildConfig.USER_AGENT

        private val httpClient = OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        object EP {
            // auth.py
            const val REFRESH                           = "$SERVER_URL/api/refresh_token"
            const val REGISTER                          = "$SERVER_URL/api/register"
            const val LOGIN                             = "$SERVER_URL/api/login"

            // data.py
            const val UPLOAD_IMAGE                      = "$SERVER_URL/api/upload/image"
            const val DELETE_FILE                       = "$SERVER_URL/api/delete/file"
            const val GET_FILE                          = "$SERVER_URL/api/get_file"
            const val GET_THUMBNAIL                     = "$SERVER_URL/api/get_thumbnail"
            const val DATA_EXPORT                       = "$SERVER_URL/api/data-export"

            // query.py
            const val GET_SIMILAR                       = "$SERVER_URL/api/get_similar"
            const val QUERY                             = "$SERVER_URL/api/query"

            // tracking.py
            const val GET_TRACKING_LINKS                = "$SERVER_URL/api/generate-tracking-links"
            const val INSERT_POST_INTERACTION           = "$SERVER_URL/api/insert-post-interaction"

            // users.py
            const val GET_ALL_FREQUENCIES               = "$SERVER_URL/api/frequencies"
            const val GET_SAVES                         = "$SERVER_URL/api/saves-left"
            const val SUMMARY_FREQUENCY                 = "$SERVER_URL/api/summary-frequency"
            const val DIGEST_FREQUENCY                  = "$SERVER_URL/api/digest-frequency"
            const val DELETE_ACCOUNT                    = "$SERVER_URL/api/account_delete"
            const val UPDATE_USERNAME                   = "$SERVER_URL/api/update-username"
            const val UPDATE_EMAIL                      = "$SERVER_URL/api/update-email"
            const val USER_TIER_INFO                    = "$SERVER_URL/api/user/tier_info"
        }
        // endregion

        // region API Related
        private fun jsonOf(vararg pairs: Pair<String, Any?>): String {
            val json = JSONObject()
            for ((k, v) in pairs) json.put(k, v)
            return json.toString()
        }
        private fun buildAuthorizedRequest(url: String, method: String = "POST", token: String, body: RequestBody? = null): Request {
            val builder = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("User-Agent", USER_AGENT)

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
            val token = context.getAccessToken()
            if (token.isEmpty()) {
                context.showToast("Not logged in")
                onInvalid?.invoke()
                return
            }
            onValid(token)
        }
        private fun performRequest(
            url: String,
            method: String = "GET",
            jsonBody: String? = null,
            customBody: RequestBody? = null,
            onSuccess: (String) -> Unit,
            onFailure: (String) -> Unit
        ) {
            val requestBody = customBody ?: jsonBody?.toRequestBody("application/json".toMediaTypeOrNull())

            val requestBuilder = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)

            when (method.uppercase()) {
                "GET" -> requestBuilder.get()
                "POST" -> requestBody?.let { requestBuilder.post(it) }
                "PUT" -> requestBody?.let { requestBuilder.put(it) }
                "DELETE" -> requestBody?.let { requestBuilder.delete(it) } ?: requestBuilder.delete()
                else -> {
                    onFailure("Unsupported HTTP method: $method")
                    return
                }
            }

            httpClient.newCall(requestBuilder.build()).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "performRequest failed: ${e.localizedMessage}")
                    onFailure(e.localizedMessage ?: "Network error")
                }

                override fun onResponse(call: Call, response: Response) {
                    val bodyStr = response.body.string()
                    if (response.isSuccessful) {
                        onSuccess(bodyStr)
                    } else {
                        Log.e(TAG, "Request failed: ${response.code} - $bodyStr")
                        onFailure("HTTP ${response.code}: $bodyStr")
                    }
                }
            })
        }
        private fun performPublicJsonPostRequest(
            context: Context,
            url: String,
            jsonBody: String,
            headers: Map<String, String> = emptyMap(),
            onSuccess: (String) -> Unit,
            onFailure: (String) -> Unit
        ) {
            val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("User-Agent", USER_AGENT)

            // Apply custom headers
            headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }

            val request = requestBuilder.build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "HTTP POST failed: ${e.localizedMessage}")
                    context.showToast("Request failed!")
                    onFailure(e.localizedMessage ?: "Unknown error")
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body.string()
                    if (response.isSuccessful) {
                        Log.i(TAG, "POST success: $responseBody")
                        onSuccess(responseBody)
                    } else {
                        Log.e(TAG, "POST error ${response.code}: $responseBody")
                        context.showToast("Server error!")
                        onFailure("HTTP ${response.code}")
                    }
                }
            })
        }
        private fun performAuthorizedRequest(
            context: Context,
            url: String,
            method: String = "GET",
            jsonBody: String? = null,
            customBody: RequestBody? = null,
            onSuccess: (String) -> Unit,
            onFailure: (String) -> Unit,
            onForbidden: (() -> Unit)? = null // <-- Add this
        ) {
            fun sendRequest(token: String) {
                val finalBody = customBody ?: jsonBody?.toRequestBody("application/json".toMediaTypeOrNull())
                val request = buildAuthorizedRequest(
                    url = url,
                    token = token,
                    method = method,
                    body = finalBody
                )

                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Request failed: ${e.localizedMessage}")
                        context.showToast("Request failed!")
                        onFailure(e.localizedMessage ?: "Network error")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val bodyStr = response.body.string()

                        when (response.code) {
                            401 -> {
                                refreshAccessToken(context) { success, newToken ->
                                    if (success && !newToken.isNullOrEmpty()) {
                                        sendRequest(newToken)
                                    } else {
                                        context.showToast("Session expired. Please log in again.")
                                        onFailure("401: Token expired")
                                    }
                                }
                            }

                            403 -> {
                                context.showToast("Access forbidden (403)")
                                onForbidden?.invoke()
                            }

                            in 200..299 -> {
                                onSuccess(bodyStr)
                            }

                            else -> {
                                Log.e(TAG, "Server error: ${response.code}: $bodyStr")
                                context.showToast("Request failed!")
                                onFailure("HTTP ${response.code}: $bodyStr")
                            }
                        }
                    }
                })
            }

            withValidToken(
                context = context,
                onValid = { token -> sendRequest(token) }
            )
        }
        fun performAuthorizedRequestMultipart(
            context: Context,
            url: String,
            method: String = "POST",
            multipartBody: MultipartBody,
            onSuccess: (String?) -> Unit,
            onFailure: (String) -> Unit,
            onForbidden: (() -> Unit)? = null
        ) {
            withValidToken(context, { token ->
                val request = buildAuthorizedRequest(
                    url = url,
                    token = token,
                    method = method,
                    body = multipartBody
                )

                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        onFailure(e.localizedMessage ?: "Unknown error")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val body = response.body.string()
                        when {
                            response.code == 401 -> {
                                refreshAccessToken(context) { success, newToken ->
                                    if (success && !newToken.isNullOrEmpty()) {
                                        performAuthorizedRequestMultipart(context, url, method, multipartBody, onSuccess, onFailure, onForbidden)
                                    } else {
                                        context.showToast("Session expired")
                                        onFailure("Unauthorized")
                                    }
                                }
                            }
                            response.code == 403 -> {
                                onForbidden?.invoke() ?: onFailure("Forbidden")
                            }
                            response.isSuccessful -> onSuccess(body)
                            else -> onFailure("Code ${response.code}: $body")
                        }
                    }
                })
            }, onInvalid = {
                onFailure("Token invalid")
            })
        }
        // endregion

        // region File Related
        fun uploadImageFileToServer(context: Context, imageFile: File?) {
            Log.i(TAG, "Uploading Image to Server...")

            if (imageFile == null || !imageFile.exists() || !imageFile.canRead() || imageFile.length() <= 0) {
                Log.e(TAG, "Image file does not exist or is unreadable.")
                return
            }

            // Use modern connectivity + speed check
            if (!isConnectedFast(context)) {
                Log.i(TAG, "No or slow internet. Deferring upload with WorkManager.")

                val uploadWork = OneTimeWorkRequestBuilder<com.sil.workers.UploadWorker>()
                    .setInputData(
                        workDataOf(
                            "uploadType" to "image",
                            "filePath" to imageFile.absolutePath
                        )
                    )
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()

                WorkManager.getInstance(context).enqueue(uploadWork)
                return
            }

            fun sendRequest(token: String) {
                val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("image", imageFile.name, imageFile.asRequestBody("image/png".toMediaTypeOrNull()))
                    .build()

                val request = buildAuthorizedRequest(
                    EP.UPLOAD_IMAGE,
                    token = token,
                    body = requestBody
                )

                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Image save failed: ${e.localizedMessage}")
                        context.showToast("Save failed!")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.code == 401) {
                            // Token expired, attempt refresh
                            refreshAccessToken(context) { success, newToken ->
                                if (success && !newToken.isNullOrEmpty()) {
                                    sendRequest(newToken) // Retry with new token
                                } else {
                                    context.showToast("Session expired. Please log in again.")
                                }
                            }
                            return
                        }
                        if (response.code == 403) {
                            context.showToast("Daily save limit reached")
                            return
                        }

                        if (response.isSuccessful) {
                            context.showToast("Saved!")
                        } else {
                            Log.e(TAG, "Server error: ${response.code}")
                            context.showToast("Save failed!")
                        }
                    }
                })
            }

            withValidToken(context, { token -> sendRequest(token) })
        }

        fun getImageURL(context: Context, imageUrl: String): GlideUrl? {
            // Log.i(TAG, "getImageURL | getting image URL...")

            try {
                val accessToken = context.getAccessToken()
                if (accessToken.isEmpty()) {
                    Log.e(TAG, "Access token missing")
                    context.showToast("Not logged in")
                    return null
                }

                val glideUrl = GlideUrl(imageUrl, LazyHeaders.Builder()
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("User-Agent", USER_AGENT)
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
        fun getUserTierInfo(context: Context, callback: (String, Int, Int) -> Unit) {
            Log.i(TAG, "Getting user tier info from server...")

            performAuthorizedRequest(
                context = context,
                url = EP.USER_TIER_INFO,
                method = "GET",
                onSuccess = { responseBody ->
                    try {
                        val json = JSONObject(responseBody)

                        val currTier = json.getString("tier")
                        val currSaves = json.getInt("current_saves")
                        val maxSaves = json.getInt("max_saves")

                        val sharedPrefs = context.getSharedPreferences(TAG, MODE_PRIVATE)
                        sharedPrefs.edit {
                            putString("cached_curr_tier", currTier)
                            putInt("cached_curr_saves", currSaves)
                            putInt("cached_max_saves", maxSaves)
                        }

                        callback(currTier, currSaves, maxSaves)
                    }
                    catch (e: Exception) {
                        Log.e(TAG, "JSON parsing error: ${e.localizedMessage}")
                        callback("FREE", 0, 3)
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to get user tier info: $error")
                    context.showToast("Failed to get user tier info!")
                    callback("FREE", 0, 3)
                }
            )
        }
        fun deleteFile(context: Context, fileName: String) {
            Log.i(TAG, "Deleting file: $fileName")

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file_name", fileName)
                .build()

            performAuthorizedRequestMultipart(
                context = context,
                url = EP.DELETE_FILE,
                method = "POST", // or "DELETE" if backend supports it
                multipartBody = requestBody,
                onSuccess = {
                    context.showToast("File deleted successfully!")
                },
                onFailure = { error ->
                    Log.e(TAG, "File delete failed: $error")
                    context.showToast("File delete failed!")
                },
                onForbidden = {
                    context.showToast("Daily save limit reached")
                }
            )
        }

        fun isImageFile(fileName: String): Boolean {
            val lowerCaseName = fileName.lowercase()
            return lowerCaseName.endsWith(".jpg") ||
                    lowerCaseName.endsWith(".jpeg") ||
                    lowerCaseName.endsWith(".png") ||
                    lowerCaseName.endsWith(".webp")
        }
        // endregion

        // region Auth Related
        fun refreshAccessToken(context: Context, onComplete: (success: Boolean, newToken: String?) -> Unit) {
            val refreshToken = context.getRefreshToken()
            if (refreshToken.isEmpty()) {
                Log.e(TAG, "Refresh token missing")
                autoLogout(context)
                onComplete(false, null)
                return
            }

            val jsonBody = jsonOf("refresh_token" to refreshToken)
            val timeZoneId = TimeZone.getDefault().id

            performPublicJsonPostRequest(
                context = context,
                url = EP.REFRESH,
                jsonBody = jsonBody,
                headers = mapOf("X-Timezone" to timeZoneId),
                onSuccess = { responseBody ->
                    try {
                        val json = JSONObject(responseBody)
                        val newToken = json.optString("access_token", "")
                        if (newToken.isNotEmpty()) {
                            context.saveTokens(newToken, refreshToken)
                            Log.i(TAG, "Access token refreshed")
                            onComplete(true, newToken)
                            return@performPublicJsonPostRequest
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing refresh token response: ${e.localizedMessage}")
                    }
                    autoLogout(context)
                    onComplete(false, null)
                },
                onFailure = { error ->
                    Log.e(TAG, "Refresh token failed: $error")
                    autoLogout(context)
                    onComplete(false, null)
                }
            )
        }

        fun authRegisterToServer(context: Context, username: String, email: String, password: String, timeZoneId: String, callback: (success: Boolean) -> Unit) {
            val jsonBody = jsonOf(
                "username" to username,
                "email" to email,
                "password" to password,
                "timezone" to timeZoneId
            )

            performPublicJsonPostRequest(
                context = context,
                url = EP.REGISTER,
                jsonBody = jsonBody,
                onSuccess = {
                    Log.i(TAG, "Register successful")
                    authLoginToServer(context, username, email, password, callback) // chain login
                },
                onFailure = {
                    callback(false)
                }
            )
        }
        fun authLoginToServer(context: Context, username: String, email: String, password: String, callback: (success: Boolean) -> Unit) {
            val jsonBody = jsonOf(
                "username" to username,
                "email" to email,
                "password" to password
            )

            val headers = mapOf("X-Timezone" to TimeZone.getDefault().id)

            performPublicJsonPostRequest(
                context = context,
                url = EP.LOGIN,
                jsonBody = jsonBody,
                headers = headers,
                onSuccess = { responseBody ->
                    try {
                        val json = JSONObject(responseBody)
                        val accessToken = json.optString("access_token", "")
                        val refreshToken = json.optString("refresh_token", "")

                        if (accessToken.isNotEmpty() && refreshToken.isNotEmpty()) {
                            context.saveTokens(accessToken, refreshToken)
                            callback(true)
                        } else {
                            Log.e(TAG, "Missing tokens in login response")
                            callback(false)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse login response", e)
                        callback(false)
                    }
                },
                onFailure = {
                    callback(false)
                }
            )
        }

        fun autoLogout(context: Context) {
            Log.i(TAG, "autoLogout | Logging out user...")

            context.showToast("Session expired. Please sign in again.")

            context.clearAuthSharedPrefs()

            val intent = Intent(context, com.sil.buildmode.Welcome::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            context.startActivity(intent)
        }
        // endregion

        // region User Account Related
        fun authAccountDelete(context: Context, callback: (Boolean) -> Unit) {
            performAuthorizedRequest(
                context = context,
                url = EP.DELETE_ACCOUNT,
                method = "DELETE",
                onSuccess = {
                    Log.i(TAG, "Account deleted: $it")
                    context.showToast("Account deleted")
                    callback(true)
                },
                onFailure = {
                    callback(false)
                }
            )
        }

        fun getAllFrequencies(callback: (List<FrequencyOption>) -> Unit) {
            performRequest(
                url = EP.GET_ALL_FREQUENCIES,
                method = "GET",
                onSuccess = { responseBody ->
                    try {
                        val jsonArray = JSONArray(responseBody)
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
                        Log.e(TAG, "JSON parsing error: ${e.localizedMessage}")
                        callback(emptyList())
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to fetch frequencies: $error")
                    callback(emptyList())
                }
            )
        }
        fun getSummaryFrequency(context: Context, callback: (Int) -> Unit) {
            performAuthorizedRequest(
                context = context,
                url = EP.SUMMARY_FREQUENCY,
                method = "GET",
                onSuccess = { body ->
                    try {
                        val json = JSONObject(body)
                        val index = json.getInt("summary_index")
                        context.getSharedPreferences(TAG, MODE_PRIVATE).edit { putInt("summary_index", index) }
                        callback(index)
                    } catch (e: Exception) {
                        Log.e(TAG, "Parse error: ${e.localizedMessage}")
                        callback(0)
                    }
                },
                onFailure = {
                    callback(0)
                }
            )
        }
        fun getDigestFrequency(context: Context, callback: (Int) -> Unit) {
            performAuthorizedRequest(
                context = context,
                url = EP.DIGEST_FREQUENCY,
                method = "GET",
                onSuccess = { body ->
                    try {
                        val json = JSONObject(body)
                        val index = json.getInt("digest_index")
                        context.getSharedPreferences(TAG, MODE_PRIVATE).edit { putInt("digest_index", index) }
                        callback(index)
                    } catch (e: Exception) {
                        Log.e(TAG, "Parse error: ${e.localizedMessage}")
                        callback(0)
                    }
                },
                onFailure = {
                    callback(0)
                }
            )
        }

        fun editUserUsernameToServer(context: Context, newUsername: String, callback: (Boolean) -> Unit) {
            val json = jsonOf(
                "new_username" to newUsername,
            )
            performAuthorizedRequest(
                context = context,
                url = EP.UPDATE_USERNAME,
                method = "PUT",
                jsonBody = json,
                onSuccess = {
                    Log.i(TAG, "Username updated: $it")
                    callback(true)
                },
                onFailure = {
                    callback(false)
                }
            )
        }
        fun editUserEmailToServer(context: Context, newEmail: String, callback: (Boolean) -> Unit) {
            val json = jsonOf(
                "new_email" to newEmail
            )

            performAuthorizedRequest(
                context = context,
                url = EP.UPDATE_EMAIL,
                method = "PUT",
                jsonBody = json,
                onSuccess = {
                    Log.i(TAG, "Email updated: $it")
                    callback(true)
                },
                onFailure = {
                    callback(false)
                }
            )
        }
        fun editSummaryFreqToServer(context: Context, frequencyId: Int, callback: (Boolean) -> Unit) {
            val json = jsonOf(
                "frequency_id" to frequencyId
            )

            performAuthorizedRequest(
                context = context,
                url = EP.SUMMARY_FREQUENCY,
                method = "PUT",
                jsonBody = json,
                onSuccess = {
                    Log.i(TAG, "Summary frequency updated: $it")
                    context.showToast("Summary updated")
                    callback(true)
                },
                onFailure = {
                    callback(false)
                }
            )
        }
        fun editDigestFreqToServer(context: Context, frequencyId: Int, callback: (Boolean) -> Unit) {
            val json = jsonOf(
                "frequency_id" to frequencyId
            )
            performAuthorizedRequest(
                context = context,
                url = EP.DIGEST_FREQUENCY,
                method = "PUT",
                jsonBody = json,
                onSuccess = {
                    Log.i(TAG, "Digest frequency updated: $it")
                    context.showToast("Digest updated")
                    callback(true)
                },
                onFailure = {
                    callback(false)
                }
            )
        }
        // endregion

        // region Interaction Related
        fun insertPostInteraction(context: Context, fileId: Int, query: String, callback: (success: Boolean) -> Unit) {
            Log.i(TAG, "Inserting interaction: fileId = $fileId, query = \"$query\"")

            if (query.isEmpty()) return

            val jsonBody = jsonOf(
                "fileId" to fileId,
                "query" to query
            )

            performAuthorizedRequest(
                context = context,
                url = EP.INSERT_POST_INTERACTION,
                method = "PUT",
                jsonBody = jsonBody,
                onSuccess = { responseBody ->
                    Log.i(TAG, "Insert interaction successful: $responseBody")
                    callback(true)
                },
                onFailure = { error ->
                    Log.e(TAG, "Insert interaction failed: $error")
                    callback(false)
                }
            )
        }
        // endregion

        // region Search Related
        fun searchToServer(context: Context, query: String, callback: (response: String?) -> Unit) {
            Log.i(TAG, "Trying to search for \"$query\"")

            val jsonBody = jsonOf("searchText" to query)
            val startTime = System.currentTimeMillis()

            performAuthorizedRequest(
                context = context,
                url = EP.QUERY,
                method = "POST",
                jsonBody = jsonBody,
                onSuccess = { responseBody ->
                    val endTime = System.currentTimeMillis()
                    Log.i(TAG, "Query round-trip time: ${endTime - startTime} ms")
                    callback(responseBody)
                },
                onFailure = {
                    Log.e(TAG, "Search query failed: $it")
                    callback(null)
                }
            )
        }
        fun getSimilarFromServer(context: Context, fileName: String, callback: (success: Boolean, resultJson: String?) -> Unit) {
            Log.i(TAG, "Getting similar content for file: $fileName")

            val requestUrl = "${EP.GET_SIMILAR}/$fileName"
            val startTime = System.currentTimeMillis()

            performAuthorizedRequest(
                context = context,
                url = requestUrl,
                method = "GET",
                onSuccess = { responseBody ->
                    val duration = System.currentTimeMillis() - startTime
                    Log.i(TAG, "Get similar round-trip time: ${duration}ms")
                    Log.d(TAG, "Get similar response: $responseBody")
                    callback(true, responseBody)
                },
                onFailure = {
                    Log.e(TAG, "Get similar failed: $it")
                    callback(false, null)
                }
            )
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
                    val accessToken = context.getAccessToken()
                    if (accessToken.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            context.showToast("Not logged in")
                        }
                        return@launch
                    }

                    val client = httpClient
                    val request = Request.Builder()
                        .url(downloadUrl)
                        .addHeader("Authorization", "Bearer $accessToken")
                        .addHeader("User-Agent", USER_AGENT)
                        .build()
                    val response = client.newCall(request).execute()

                    if (!response.isSuccessful) {
                        val errorBody = response.body.string()
                        Log.e(TAG, "Error ${response.code}: $errorBody")
                        return@launch
                    }

                    val file = File(context.cacheDir, fileName)
                    response.body.byteStream().use { input ->
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

            performAuthorizedRequest(
                context = context,
                url = EP.DATA_EXPORT,
                method = "GET",
                onSuccess = {
                    Log.i(TAG, "Bulk download successful!")
                    context.showToast("Bulk download successful!")
                    callback(true)
                },
                onFailure = { error ->
                    Log.e(TAG, "Bulk download failed: $error")
                    context.showToast("Download failed!")
                    callback(false)
                },
                onForbidden = {
                    context.showToast("Forbidden or daily limit reached")
                    callback(false)
                }
            )
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

        // region Tracking Related
        fun getTrackingLinks(context: Context, urls: JSONArray, callback: (JSONArray?) -> Unit) {
            val jsonBody = JSONObject().apply {
                put("urls", urls)
            }.toString()

            performAuthorizedRequest(
                context = context,
                url = EP.GET_TRACKING_LINKS,
                method = "POST",
                jsonBody = jsonBody,
                onSuccess = { body ->
                    try {
                        val json = JSONObject(body)
                        val links = json.optJSONArray("links") ?: JSONArray()
                        callback(links)
                    } catch (e: Exception) {
                        Log.e("Helper", "getTrackingLinks | JSON parsing failed: ${e.localizedMessage}")
                        callback(null)
                    }
                },
                onFailure = { error ->
                    Log.e("Helper", "getTrackingLinks | Failed: $error")
                    callback(null)
                }
            )
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

        fun resolveHandlesToUrls(appName: String, handles: JSONArray): JSONArray {
            val result = JSONArray()
            for (i in 0 until handles.length()) {
                val raw = handles.optString(i)?.trim().orEmpty()
                if (raw.isNotEmpty()) {
                    val handle = raw.removePrefix("@")
                    val url = when (appName.lowercase()) {
                        "youtube" -> "https://www.youtube.com/@$handle"
                        "instagram" -> "https://www.instagram.com/$handle/"
                        "twitter", "x" -> "https://x.com/$handle"
                        "tiktok" -> "https://www.tiktok.com/@$handle"
                        "reddit" -> "https://www.reddit.com/user/$handle"
                        else -> "https://www.google.com/search?q=${Uri.encode(raw)}"
                    }
                    result.put(url)
                }
            }
            return result
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

        // region Network Related
        fun isConnectedFast(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork ?: run {
                Log.w("NetworkCheck", "No active network")
                return false
            }

            val capabilities = cm.getNetworkCapabilities(network) ?: run {
                Log.w("NetworkCheck", "No capabilities for active network")
                return false
            }

            val hasInternet = capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isWifi = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
            val isCellular = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
            val downSpeedKbps = capabilities.linkDownstreamBandwidthKbps
            val upSpeedKbps = capabilities.linkUpstreamBandwidthKbps

            Log.i("NetworkCheck", "hasInternet=$hasInternet, isWifi=$isWifi, isCellular=$isCellular, down=$downSpeedKbps kbps, up=$upSpeedKbps kbps")

            val fastEnough = downSpeedKbps >= 500
            Log.i("NetworkCheck", "Network fast enough: $fastEnough")

            return hasInternet && (isWifi || isCellular) && fastEnough
        }
        // endregion

        // region Shared Prefs Related
        fun Context.getAccessToken(): String = getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE).getString("access_token", "") ?: ""
        fun Context.getRefreshToken(): String = getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE).getString("refresh_token", "") ?: ""
        fun Context.saveTokens(access: String, refresh: String) {
            getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE).edit {
                putString("access_token", access)
                putString("refresh_token", refresh)
            }
        }
        fun Context.clearAuthSharedPrefs() {
            getSharedPreferences(PREFS_GENERAL, MODE_PRIVATE).edit {
                remove("username")
                remove("email")
                remove("access_token")
                remove("refresh_token")
            }
        }
        // endregion

        // region UI Related
        fun Context.showToast(message: String) {
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            mainHandler.post {
                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            }
        }
        // endregion
    }
}
