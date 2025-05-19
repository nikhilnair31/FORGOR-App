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
            val fileSave = inputData.getString("fileSave")
            val filePreprocess = inputData.getString("filePreprocess")

            if (!filePath.isNullOrEmpty()) {
                val file = File(filePath)
                Helpers.uploadImageFileToServer(
                    applicationContext,
                    file,
                    fileSave,
                    filePreprocess
                )
            }

            return Result.success()
        }
        else if (uploadType == "text") {
            val postURL = inputData.getString("postURL")

            if (!postURL.isNullOrEmpty()) {
                Helpers.uploadPostURLToServer(
                    applicationContext,
                    postURL
                )
                return Result.success()
            }
        }

        return Result.failure()
    }
}