package com.sil.workers

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.sil.others.Helpers
import java.io.File

class UploadWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        Log.i("UploadWorker", "doWork | inputData: $inputData")

        val uploadType = inputData.getString("uploadType")

        if (uploadType == "image") {
            val filePath = inputData.getString("filePath")
            Log.i("UploadWorker", "doWork | filePath: $filePath")

            if (!filePath.isNullOrEmpty()) {
                val file = File(filePath)
                Helpers.uploadImageFileToServer(
                    applicationContext,
                    file
                )
            }

            return Result.success()
        }
        else if (uploadType == "text") {
            val postText = inputData.getString("postText")

            if (!postText.isNullOrEmpty()) {
                Helpers.uploadPostTextToServer(
                    applicationContext,
                    postText
                )
                return Result.success()
            }
        }

        return Result.failure()
    }
}