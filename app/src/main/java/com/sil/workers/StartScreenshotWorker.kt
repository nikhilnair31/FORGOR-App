import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sil.services.ScreenshotService

class StartScreenshotWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val serviceIntent = Intent(applicationContext, ScreenshotService::class.java)

        // startForegroundService is still valid here
        applicationContext.startForegroundService(serviceIntent)

        return Result.success()
    }
}
