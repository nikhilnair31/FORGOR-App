package com.sil.others

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.sil.buildmode.BuildConfig
import com.sil.workers.UploadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.buffer
import okio.sink
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class Helpers {
    companion object {
        // region API Keys
        private const val TAG = "Helper"
        private const val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"
        private const val SERVER_URL = BuildConfig.SERVER_URL
        private const val APP_KEY = BuildConfig.APP_KEY
        private const val USER_AGENT = BuildConfig.USER_AGENT
        // endregion

        // region Image Related
        fun uploadImageFileToServer(context: Context, imageFile: File?) {
            Log.i(TAG, "Uploading Image to Server...")

            if (imageFile == null || !imageFile.exists() || !imageFile.canRead() || imageFile.length() <= 0) {
                Log.e(TAG, "Image file does not exist or is unreadable.")
                return
            }

            val prefs = context.getSharedPreferences(PREFS_GENERAL, Context.MODE_PRIVATE)
            val token = prefs.getString("access_token", "") ?: ""
            if (token.isEmpty()) {
                showToast(context, "Not logged in")
                return
            }

            fun sendRequest(token: String) {
                val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("image", imageFile.name, imageFile.asRequestBody("image/png".toMediaTypeOrNull()))
                    .build()

                val request = Request.Builder()
                    .url("$SERVER_URL/api/upload/image")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("X-App-Key", APP_KEY)
                    .post(requestBody)
                    .build()

                OkHttpClient().newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Upload failed: ${e.localizedMessage}")
                        showToast(context, "Upload failed!")
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

                        if (response.isSuccessful) {
                            showToast(context, "Image uploaded successfully!")
                        } else {
                            Log.e(TAG, "Server error: ${response.code}")
                            showToast(context, "Upload failed!")
                        }
                    }
                })
            }

            sendRequest(token)
        }
        fun uploadImageFile(context: Context, imageFile: File) {
            CoroutineScope(Dispatchers.IO).launch {
                // Start upload process
                scheduleImageUploadWork(
                    context,
                    "image",
                    imageFile
                )
            }
        }
        fun scheduleImageUploadWork(context: Context, uploadType: String, file: File?) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = workDataOf(
                "uploadType" to uploadType,
                "filePath" to file?.absolutePath
            )

            val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .setConstraints(constraints)
                .setInputData(inputData)
                .build()

            val appContext = context.applicationContext
            WorkManager.getInstance(appContext).enqueue(uploadWorkRequest)
        }

        fun deleteFile(context: Context, fileName: String) {
            Log.i(TAG, "Deleting file: $fileName")

            val prefs = context.getSharedPreferences(PREFS_GENERAL, Context.MODE_PRIVATE)
            val token = prefs.getString("access_token", "") ?: ""
            if (token.isEmpty()) {
                showToast(context, "Not logged in")
                return
            }

            fun sendRequest(token: String) {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file_name", fileName)
                    .build()

                val request = Request.Builder()
                    .url("$SERVER_URL/api/delete/file")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("X-App-Key", APP_KEY)
                    .post(requestBody)
                    .build()

                OkHttpClient().newCall(request).enqueue(object : Callback {
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

                        if (response.isSuccessful) {
                            showToast(context, "File deleted successfully!")
                        } else {
                            Log.e(TAG, "Server error: ${response.code}")
                            showToast(context, "File delete failed!")
                        }
                    }
                })
            }

            sendRequest(token)
        }

        fun getImageURL(context: Context, imageUrl: String): GlideUrl? {
            // Log.i(TAG, "getImageURL | getting image URL...")

            try {
                val generalSharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_GENERAL, Context.MODE_PRIVATE)
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
        fun isImageFile(fileName: String): Boolean {
            Log.i(TAG, "isImageFile | fileName: $fileName")

            val lowerCaseName = fileName.lowercase()
            return lowerCaseName.endsWith(".jpg") ||
                    lowerCaseName.endsWith(".jpeg") ||
                    lowerCaseName.endsWith(".png") ||
                    lowerCaseName.endsWith(".webp")
        }
        // endregion

        // region Pdf Related
        fun uploadPdfFileToServer(context: Context, pdfFile: File?) {
            Log.i(TAG, "Uploading Pdf to Server...")

            if (pdfFile == null || !pdfFile.exists() || !pdfFile.canRead() || pdfFile.length() <= 0) {
                Log.e(TAG, "Pdf file does not exist or is unreadable.")
                return
            }

            val prefs = context.getSharedPreferences(PREFS_GENERAL, Context.MODE_PRIVATE)
            val token = prefs.getString("access_token", "") ?: ""
            if (token.isEmpty()) {
                showToast(context, "Not logged in")
                return
            }

            fun sendRequest(token: String) {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "pdf",
                        pdfFile.name,
                        pdfFile.asRequestBody("application/pdf".toMediaTypeOrNull())
                    )
                    .build()

                val request = Request.Builder()
                    .url("$SERVER_URL/api/upload/pdf")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("X-App-Key", APP_KEY)
                    .post(requestBody)
                    .build()

                OkHttpClient().newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Upload Pdf failed: ${e.localizedMessage}")
                        showToast(context, "Upload Pdf failed!")
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

                        if (response.isSuccessful) {
                            showToast(context, "Pdf uploaded successfully!")
                        } else {
                            Log.e(TAG, "Server error: ${response.code}")
                            showToast(context, "Pdf upload failed!")
                        }
                    }
                })
            }

            sendRequest(token)
        }

        fun downloadPdfToCache(context: Context, url: String): File {
            val generalSharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_GENERAL, Context.MODE_PRIVATE)
            val accessToken = generalSharedPrefs.getString("access_token", "") ?: ""
            if (accessToken.isEmpty()) {
                Log.e(TAG, "Access token missing")
                showToast(context, "Not logged in")
                return File("")
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("X-App-Key", APP_KEY)
                .build()

            try {
                val response = OkHttpClient().newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to download PDF (code ${response.code})")
                    throw IOException("Failed to download PDF (code ${response.code})")
                }

                val file = File(context.cacheDir, "shared_${System.currentTimeMillis()}.pdf")
                val sink = file.sink().buffer()
                sink.writeAll(response.body!!.source())
                sink.close()

                return file
            }
            catch (e: Exception) {
                Log.e(TAG, "Error in PDF download: ${e.localizedMessage}")
                throw e
            }
        }

        fun isPdfFile(fileName: String): Boolean {
            return fileName.lowercase().endsWith(".pdf")
        }
        // endregion

        // region Text Related
        fun uploadPostTextToServer(context: Context, postText: String) {
            Log.i(TAG, "Uploading text to server...")

            val generalSharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_GENERAL, Context.MODE_PRIVATE)
            val accessToken = generalSharedPrefs.getString("access_token", "") ?: ""
            if (accessToken.isEmpty()) {
                Log.e(TAG, "Access token missing")
                showToast(context, "Not logged in")
                return
            }

            fun sendRequest(token: String) {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("text", postText)
                    .build()

                val request = Request.Builder()
                    .url("$SERVER_URL/api/upload/text")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("X-App-Key", APP_KEY)
                    .post(requestBody)
                    .build()

                // ✅ Custom OkHttpClient with longer timeouts
                val client = OkHttpClient.Builder()
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Upload failed: ${e.localizedMessage}")
                        showToast(context, "Text save failed!")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.code == 401) {
                            refreshAccessToken(context) { success, newToken ->
                                if (success && newToken != null) sendRequest(newToken)
                                else showToast(context, "Login expired")
                            }
                            return
                        }

                        if (response.isSuccessful) {
                            showToast(context, "Text saved!")
                        } else {
                            showToast(context, "Text save failed!")
                        }
                    }
                })
            }

            sendRequest(accessToken)
        }
        fun uploadPostText(context: Context, postText: String) {
            CoroutineScope(Dispatchers.IO).launch {
                // Start upload process
                schedulePostURLUploadWork(
                    context,
                    "text",
                    postText
                )
            }
        }
        fun schedulePostURLUploadWork(context: Context, uploadType: String, postText: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = workDataOf(
                "uploadType" to uploadType,
                "postText" to postText,
            )

            val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .setConstraints(constraints)
                .setInputData(inputData)
                .build()

            val appContext = context.applicationContext
            WorkManager.getInstance(appContext).enqueue(uploadWorkRequest)
        }

        fun getTxtToCache(context: Context, url: String): File? {
            try {
                val generalSharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_GENERAL, Context.MODE_PRIVATE)
                val accessToken = generalSharedPrefs.getString("access_token", "") ?: ""
                if (accessToken.isEmpty()) {
                    Log.e(TAG, "Access token missing")
                    showToast(context, "Not logged in")
                    return null
                }

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("X-App-Key", APP_KEY)
                    .build()

                val client = OkHttpClient()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) throw IOException("Failed to download txt (code ${response.code})")

                val file = File(context.cacheDir, "shared_${System.currentTimeMillis()}.txt")
                val sink = file.sink().buffer()
                sink.writeAll(response.body!!.source())
                sink.close()

                return file
            }
            catch (e: Exception) {
                when (e) {
                    is FileNotFoundException -> Log.e(TAG, "text file not found: ${e.message}")
                    else -> Log.e(TAG, "Error in text download: ${e.localizedMessage}")
                }
                e.printStackTrace()
                return null
            }
        }

        fun isTextFile(fileName: String): Boolean {
            return fileName.lowercase().endsWith(".txt")
        }
        // endregion

        // region Auth Related
        fun refreshAccessToken(context: Context, onComplete: (success: Boolean, newToken: String?) -> Unit) {
            val prefs = context.getSharedPreferences(PREFS_GENERAL, Context.MODE_PRIVATE)
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

            val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("$SERVER_URL/api/refresh_token")
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("X-App-Key", APP_KEY)
                .post(requestBody)
                .build()

            OkHttpClient().newCall(request).enqueue(object : Callback {
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

        fun authRegisterToServer(context: Context, username: String, password: String, callback: (success: Boolean) -> Unit) {
            Log.i(TAG, "Trying to register with $username/$password")

            val jsonBody = """
            {
                "username": "$username",
                "password": "$password"
            }
            """.trimIndent()

            val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("$SERVER_URL/api/register")
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("X-App-Key", APP_KEY)
                .post(requestBody)
                .build()

            OkHttpClient().newCall(request).enqueue(object : Callback {
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
                        authLoginToServer(context, username, password) { loginSuccess ->
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
        fun authLoginToServer(context: Context, username: String, password: String, callback: (success: Boolean) -> Unit) {
            Log.i(TAG, "Trying to login...")

            val jsonBody = """
            {
                "username": "$username",
                "password": "$password"
            }
            """.trimIndent()

            val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("$SERVER_URL/api/login")
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("X-App-Key", APP_KEY)
                .post(requestBody)
                .build()

            OkHttpClient().newCall(request).enqueue(object : Callback {
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
                            val generalSharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_GENERAL, Context.MODE_PRIVATE)
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
        fun authEditUsernameToServer(context: Context, newUsername: String, callback: (success: Boolean) -> Unit) {
            Log.i(TAG, "Trying to edit username to $newUsername")

            val generalSharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_GENERAL, Context.MODE_PRIVATE)
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
                val request = Request.Builder()
                    .url("$SERVER_URL/api/update-username")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("X-App-Key", APP_KEY)
                    .post(requestBody)
                    .build()

                OkHttpClient().newCall(request).enqueue(object : Callback {
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

        fun autoLogout(context: Context) {
            Log.i(TAG, "autoLogout | Logging out user...")

            val prefs = context.getSharedPreferences(PREFS_GENERAL, Context.MODE_PRIVATE)
            prefs.edit {
                remove("access_token")
                remove("refresh_token")
            }

            showToast(context, "Session expired. Please sign in again.")

            val intent = Intent(context, com.sil.buildmode.Welcome::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            context.startActivity(intent)
        }
        // endregion

        // region Search Related
        fun searchToServer(context: Context, query: String, callback: (response: String?) -> Unit) {
            Log.i(TAG, "Trying to search for $query")

            val generalSharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_GENERAL, Context.MODE_PRIVATE)
            val accessToken = generalSharedPrefs.getString("access_token", "") ?: ""
            if (accessToken.isEmpty()) {
                Log.e(TAG, "Access token missing")
                showToast(context, "Not logged in")
                return
            }

            fun sendRequest(token: String) {
                val jsonBody = """
                {
                    "searchText": "$query"
                }
            """.trimIndent()

                val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())
                val startTime = System.currentTimeMillis()

                val request = Request.Builder()
                    .url("$SERVER_URL/api/query")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("X-App-Key", APP_KEY)
                    .post(requestBody)
                    .build()

                OkHttpClient().newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Query failed: ${e.localizedMessage}")
                        showToast(context, "Query failed!")
                        callback(null)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.code == 401) {
                            refreshAccessToken(context) { success, newToken ->
                                if (success && newToken != null) sendRequest(newToken)
                                else showToast(context, "Login expired")
                            }
                            return
                        }

                        if (response.isSuccessful) {
                            val endTime = System.currentTimeMillis()
                            val elapsedTime = endTime - startTime
                            Log.i(TAG, "Query roundtrip time: $elapsedTime ms")
                            val responseBody = response.body?.string()
                            callback(responseBody)
                        } else {
                            showToast(context, "Query failed!")
                        }
                    }
                })
            }

            sendRequest(accessToken)
        }
        // endregion

        // region Content Related
        private fun queryContentResolver(context: Context, uri: Uri, selection: String?, selectionArgs: Array<String>?): String? {
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
