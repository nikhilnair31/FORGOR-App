package com.sil.others

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
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
import okhttp3.Response
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

        private const val SERVER_URL = BuildConfig.SERVER_URL
        // endregion

        // region Image Related
        fun uploadImageFile(context: Context, imageFile: File) {
            // Get the shared preferences for metadata values
            val sharedPrefs = context.getSharedPreferences("com.sil.buildmode.generalSharedPrefs", MODE_PRIVATE)
            val saveImage = sharedPrefs.getString("saveImageFiles", "false")
            val preprocessImage = sharedPrefs.getString("preprocessImage", "false")

            CoroutineScope(Dispatchers.IO).launch {
                // Start upload process
                scheduleImageUploadWork(
                    context,
                    "image",
                    imageFile,
                    saveImage,
                    preprocessImage
                )
            }
        }
        fun scheduleImageUploadWork(context: Context, uploadType: String, file: File?, saveFile: String?, preprocessFile: String?) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = workDataOf(
                "uploadType" to uploadType,
                "filePath" to file?.absolutePath,
                "fileSave" to saveFile,
                "filePreprocess" to preprocessFile
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

        // region Server Related
        fun uploadImageFileToServer(context: Context, imageFile: File?, saveFile: String?, preprocessFile: String?) {
            Log.i("Helpers", "Uploading Image to Server...")

            try {
                imageFile?.let {
                    // Verify the file's readability and size
                    if (!it.exists() || !it.canRead() || it.length() <= 0) {
                        Log.e(TAG, "Image file does not exist, is unreadable or empty")
                        return
                    }

                    // Upload file
                    val generalSharedPrefs: SharedPreferences = context.getSharedPreferences("com.sil.buildmode.generalSharedPrefs", Context.MODE_PRIVATE)
                    val userName = generalSharedPrefs.getString("userName", null)

                    val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                        .addFormDataPart("username", userName.toString())
                        .addFormDataPart(
                            "image",
                            imageFile.name,
                            imageFile.asRequestBody("image/png".toMediaTypeOrNull())
                        )
                        .build()

                    val request = Request.Builder()
                        .url("$SERVER_URL/upload/image")
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
                    is AmazonServiceException -> Log.e(TAG, "Error uploading image to S3: ${e.message}")
                    is FileNotFoundException -> Log.e(TAG, "Image file not found: ${e.message}")
                    else -> Log.e(TAG, "Error in image S3 upload: ${e.localizedMessage}")
                }
                e.printStackTrace()
            }
        }
        fun uploadPostURLToServer(context: Context, postURL: String) {
            Log.i("Helpers", "Uploading URL to server...")

            val prefs = context.getSharedPreferences("com.sil.buildmode.generalSharedPrefs", Context.MODE_PRIVATE)
            val userName = prefs.getString("userName", "") ?: ""

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("username", userName)
                .addFormDataPart("url", postURL)
                .build()

            val request = Request.Builder()
                .url("$SERVER_URL/upload/url")
                .post(requestBody)
                .build()

            OkHttpClient().newCall(request).enqueue(object : Callback {
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
