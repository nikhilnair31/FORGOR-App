package com.sil.others

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.amazonaws.AmazonServiceException
import com.sil.buildmode.BuildConfig
import com.sil.buildmode.Settings
import com.sil.workers.UploadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import androidx.core.content.edit
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.sil.buildmode.R
import com.sil.buildmode.ResultAdapter
import org.json.JSONObject

class Helpers {
    companion object {
        // region API Keys
        private const val TAG = "Helper"
        private const val PREFS_GENERAL = "com.sil.buildmode.generalSharedPrefs"
        private const val SERVER_URL = BuildConfig.SERVER_URL
        // endregion

        // region Image Related
        fun uploadImageFileToServer(context: Context, imageFile: File?) {
            Log.i("Helpers", "Uploading Image to Server...")

            try {
                imageFile?.let {
                    // Verify the file's readability and size
                    if (!imageFile.exists() || !imageFile.canRead() || imageFile.length() <= 0) {
                        Log.e(TAG, "Image file does not exist, is unreadable or empty")
                        return
                    }

                    val generalSharedPrefs: SharedPreferences =
                        context.getSharedPreferences(PREFS_GENERAL, Context.MODE_PRIVATE)
                    val token = generalSharedPrefs.getString("token", "") ?: ""
                    if (token.isEmpty()) {
                        Log.e("Helpers", "Token missing")
                        showToast(context, "Not logged in")
                        return
                    }

                    val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                        .addFormDataPart(
                            "image",
                            imageFile.name,
                            imageFile.asRequestBody("image/png".toMediaTypeOrNull())
                        )
                        .build()

                    val request = Request.Builder()
                        .url("$SERVER_URL/upload/image")
                        .addHeader("Authorization", "Bearer $token")
                        .post(requestBody)
                        .build()

                    val client = OkHttpClient()
                    client.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            Log.e(TAG, "Image upload to Flask failed: ${e.localizedMessage}")
                            showToast(context, "Save failed!")
                        }

                        override fun onResponse(call: Call, response: Response) {
                            if (response.isSuccessful) {
                                Log.i(TAG, "Image uploaded to Flask successfully: ${response.body?.string()}")
                                showToast(context, "Image saved!")
                            } else {
                                Log.e(TAG, "Flask server returned error ${response.code}: ${response.body?.string()}")
                                showToast(context, "Save failed!")
                            }
                        }
                    })
                }
            }
            catch (e: Exception) {
                when (e) {
                    is FileNotFoundException -> Log.e(TAG, "Image file not found: ${e.message}")
                    else -> Log.e(TAG, "Error in image upload: ${e.localizedMessage}")
                }
                e.printStackTrace()
            }
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

        fun getImageURL(context: Context, imageUrl: String): GlideUrl? {
            Log.i(TAG, "getImageURL | getting image URL...")

            try {
                val generalSharedPrefs: SharedPreferences =
                    context.getSharedPreferences(PREFS_GENERAL, Context.MODE_PRIVATE)
                val token = generalSharedPrefs.getString("token", "") ?: ""
                if (token.isEmpty()) {
                    Log.e("Helpers", "Token missing")
                    showToast(context, "Not logged in")
                    return null
                }

                val glideUrl = GlideUrl(imageUrl, LazyHeaders.Builder()
                    .addHeader("Authorization", "Bearer $token")
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

        // region URL Related
        fun uploadPostURLToServer(context: Context, postURL: String) {
            Log.i("Helpers", "Uploading URL to server...")

            val generalSharedPrefs: SharedPreferences =
                context.getSharedPreferences(PREFS_GENERAL, Context.MODE_PRIVATE)
            val token = generalSharedPrefs.getString("token", "") ?: ""
            if (token.isEmpty()) {
                Log.e("Helpers", "Token missing")
                showToast(context, "Not logged in")
                return
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("url", postURL)
                .build()

            val request = Request.Builder()
                .url("$SERVER_URL/upload/url")
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            // ✅ Custom OkHttpClient with longer timeouts
            val client = OkHttpClient.Builder()
                .readTimeout(20, TimeUnit.SECONDS)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("Helpers", "Upload failed: ${e.localizedMessage}")
                    showToast(context, "Save failed!")
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()
                    if (response.isSuccessful) {
                        Log.i("Helpers", "Upload successful: $responseBody")
                        showToast(context, "Image saved!")
                    } else {
                        Log.e("Helpers", "Upload error ${response.code}: $responseBody")
                        showToast(context, "Save failed!")
                    }
                }
            })
        }

        fun uploadPostURL(context: Context, postURL: String) {
            CoroutineScope(Dispatchers.IO).launch {
                // Start upload process
                schedulePostURLUploadWork(
                    context,
                    "text",
                    postURL
                )
            }
        }

        fun schedulePostURLUploadWork(context: Context, uploadType: String, postURL: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = workDataOf(
                "uploadType" to uploadType,
                "postURL" to postURL,
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
        // endregion

        // region Auth Related
        fun authRegisterToServer(context: Context, username: String, password: String, callback: (success: Boolean) -> Unit) {
            Log.i("Helpers", "Trying to register with $username/$password")

            val jsonBody = """
            {
                "username": "$username",
                "password": "$password"
            }
            """.trimIndent()

            val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("$SERVER_URL/register")
                .post(requestBody)
                .build()

            OkHttpClient().newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("Helpers", "Register failed: ${e.localizedMessage}")
                    showToast(context, "Register failed!")
                    callback(false)
                }
                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()
                    if (response.isSuccessful) {
                        Log.i("Helpers", "Register successful: $responseBody")

                        // Now attempt login and forward that result
                        authLoginToServer(context, username, password) { loginSuccess ->
                            Log.i(TAG, "Login success")
                            callback(loginSuccess)
                        }
                    } else {
                        Log.e("Helpers", "Register error ${response.code}: $responseBody")
                        showToast(context, "Register failed!")
                        callback(false)
                    }
                }
            })
        }
        fun authLoginToServer(context: Context, username: String, password: String, callback: (success: Boolean) -> Unit) {
            Log.i("Helpers", "Trying to login with $username/$password")

            val jsonBody = """
            {
                "username": "$username",
                "password": "$password"
            }
            """.trimIndent()

            val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("$SERVER_URL/login")
                .post(requestBody)
                .build()

            OkHttpClient().newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("Helpers", "Login failed: ${e.localizedMessage}")
                    showToast(context, "Login failed!")
                    callback(false)
                }
                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()
                    if (response.isSuccessful) {
                        Log.i("Helpers", "Login successful: $response")
                        val json = JSONObject(responseBody)
                        val token = json.optString("token", "")
                        if (!token.isEmpty()) {
                            Log.i(TAG, "Token received")
                            val generalSharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_GENERAL, Context.MODE_PRIVATE)
                            generalSharedPrefs.edit { putString("token", token.toString()) }
                            callback(true)
                            return
                        }
                    } else {
                        Log.e("Helpers", "Login error ${response.code}: $responseBody")
                        showToast(context, "Login failed!")
                    }
                }
            })
        }
        fun authEditUsernameToServer(context: Context, newUsername: String, callback: (success: Boolean) -> Unit) {
            Log.i("Helpers", "Trying to edit username to $newUsername")

            val generalSharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_GENERAL, Context.MODE_PRIVATE)
            val token = generalSharedPrefs.getString("token", "") ?: ""
            if (token.isEmpty()) {
                Log.e("Helpers", "Token missing")
                showToast(context, "Not logged in")
                return
            }

            val jsonBody = """
            {
                "new_username": "$newUsername"
            }
            """.trimIndent()

            val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("$SERVER_URL/update-username")
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            OkHttpClient().newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("Helpers", "Edit username failed: ${e.localizedMessage}")
                    callback(false)
                }
                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()
                    if (response.isSuccessful) {
                        Log.i("Helpers", "Edit username successful: $response")
                        callback(true)
                        return
                    } else {
                        Log.e("Helpers", "Edit username error ${response.code}: $responseBody")
                    }
                }
            })
        }
        // endregion

        // region Search Related
        fun searchToServer(context: Context, query: String) {
            Log.i("Helpers", "Trying to search for $query")

            val generalSharedPrefs: SharedPreferences =
                context.getSharedPreferences(PREFS_GENERAL, Context.MODE_PRIVATE)
            val token = generalSharedPrefs.getString("token", "") ?: ""
            if (token.isEmpty()) {
                Log.e("Helpers", "Token missing")
                showToast(context, "Not logged in")
                return
            }

            val jsonBody = """
                {
                    "searchText": "$query"
                }
            """.trimIndent()

            val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("$SERVER_URL/query")
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            OkHttpClient().newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("Helpers", "Query failed: ${e.localizedMessage}")
                    showToast(context, "Query failed!")
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()
                    if (response.isSuccessful && responseBody != null) {
                        Log.i("Helpers", "Query successful!")
                        try {
                            (context as Activity).runOnUiThread {
                                val json = JSONObject(responseBody)
                                val resultsArray = json.getJSONArray("results")
                                // Log.i("Helpers", "resultsArray: $resultsArray")

                                val resultList = mutableListOf<JSONObject>()
                                for (i in 0 until resultsArray.length()) {
                                    resultList.add(resultsArray.getJSONObject(i))
                                }
                                // Log.i("Helpers", "resultList: $resultList")

                                val recyclerView = context.findViewById<RecyclerView>(R.id.imageRecyclerView)
                                recyclerView.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
                                recyclerView.adapter = ResultAdapter(context, resultList)
                            }
                        } catch (e: Exception) {
                            Log.e("Helpers", "Error parsing response: ${e.localizedMessage}")
                        }
                    } else {
                        Log.e("Helpers", "Query error ${response.code}: $responseBody")
                        showToast(context, "Query error!")
                    }
                }
            })
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
