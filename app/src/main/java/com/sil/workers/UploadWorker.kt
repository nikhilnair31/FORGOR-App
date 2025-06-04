package com.sil.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.sil.others.Helpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class UploadWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    // region Vars
    val TAG = "UploadWorker"
    // endregion

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uploadType = inputData.getString("uploadType")

        Log.i(TAG, "doWork | type=$uploadType, data=$inputData")

        try {
            when (uploadType) {
                "image"-> {
                    val filePath = inputData.getString("filePath")
                    if (!filePath.isNullOrEmpty()) {
                        val file = File(filePath)
                        Helpers.uploadImageFileToServer(applicationContext, file)
                        return@withContext Result.success()
                    }
                }

                "pdf" -> {
                    val filePath = inputData.getString("filePath")
                    if (!filePath.isNullOrEmpty()) {
                        val file = File(filePath)
                        Helpers.uploadPdfFileToServer(applicationContext, file)
                        return@withContext Result.success()
                    }
                }

                "text" -> {
                    val content = inputData.getString("fileContent")
                    if (!content.isNullOrEmpty()) {
                        Helpers.uploadPostTextToServer(applicationContext, content)
                        return@withContext Result.success()
                    }
                }

                "url" -> {
                    val url = inputData.getString("fileContent")
                    if (!url.isNullOrEmpty()) {
                        Helpers.uploadPostUrlToServer(applicationContext, url)
                        return@withContext Result.success()
                    }
                }

                else -> {
                    Log.e(TAG, "Unknown upload type: $uploadType")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in doWork: ${e.localizedMessage}", e)
        }

        return@withContext Result.retry()
    }
}