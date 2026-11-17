package com.kakario.synogallery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 썸네일 받기를 알림으로 띄워 두고 계속 진행 (화면이 꺼지거나 앱을 나가도 계속).
 * 알림에서 일시정지 / 재개 / 닫기 가능.
 */
class ThumbService : Service() {
    companion object {
        private const val CH = "thumbs"
        private const val ID = 7701
        const val ACTION_PAUSE = "pause"
        const val ACTION_RESUME = "resume"
        const val ACTION_CLOSE = "close"

        @Volatile var alive = false
            private set

        fun start(ctx: Context) {
            if (alive || !Settings.thumbsInBackground) return
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, ThumbService::class.java))
            } catch (e: Exception) {
                // 앱이 뒤에 있을 때는 안드로이드가 막음 → 다음에 앱을 켤 때 시작
                AppLog.err("썸네일", "알림 작업 시작 못 함", e)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        alive = true
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CH) == null) {
            nm.createNotificationChannel(NotificationChannel(CH, "썸네일 받기", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> ThumbControl.pause()
            ACTION_RESUME -> {
                Settings.thumbsPaused = false
                AppLog.add("썸네일", "재개 (알림)")
            }
            ACTION_CLOSE -> {
                ThumbControl.pause()
                finish(null)
                return START_NOT_STICKY
            }
        }
        val n = build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else startForeground(ID, n)
        if (loop == null) loop = scope.launch { run() }
        return START_STICKY
    }

    private suspend fun run() {
        AppLog.add("썸네일", "알림으로 계속 받기 시작")
        wake = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "synogallery:thumbs")
            .apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }
        try {
            // 앱이 꺼져 있다가 알림으로 다시 살아난 경우: 로그인과 목록을 먼저 준비
            if (Syno.sid == null && Settings.password.isNotEmpty()) runCatching { Syno.quickLogin() }
            val cached = ItemCache.load(com.kakario.synogallery.ui.galleryKey(AllSource))?.items.orEmpty()
            if (cached.isNotEmpty()) {
                ThumbPrefetch.ensure(cached)
                if (Settings.autoLocalThumbs) LocalThumbs.ensureAll(cached)
            }
            var idle = 0
            while (true) {
                getSystemService(NotificationManager::class.java).notify(ID, build())
                val busy = ThumbPrefetch.running || LocalThumbs.running
                if (!busy && !ThumbControl.paused) {
                    if (++idle >= 3) break
                } else idle = 0
                delay(2000)
            }
            AppLog.add("썸네일", "알림 작업 끝 (다 받음)")
            finish("썸네일을 다 받았어요")
        } catch (e: Exception) {
            AppLog.err("썸네일", "알림 작업 오류", e)
            finish(null)
        }
    }

    private fun finish(doneText: String?) {
        try {
            wake?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (doneText != null) {
            val n = NotificationCompat.Builder(this, CH)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("시놀 갤러리")
                .setContentText(doneText)
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .build()
            getSystemService(NotificationManager::class.java).notify(ID + 1, n)
        }
        stopSelf()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(act: String, req: Int): PendingIntent = PendingIntent.getService(
        this, req, Intent(this, ThumbService::class.java).setAction(act),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun build(): Notification {
        val total = ThumbPrefetch.total.coerceAtLeast(0)
        val left = ThumbPrefetch.pending
        val done = (total - left).coerceAtLeast(0)
        val loc = LocalThumbs.pending
        val paused = ThumbControl.paused
        val text = buildString {
            if (total > 0) append("썸네일 %,d / %,d".format(done, total))
            if (loc > 0) {
                if (isNotEmpty()) append("  ·  ")
                append("직접 만들기 %,d장 남음".format(loc))
            }
            if (isEmpty()) append("준비 중…")
        }
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(if (paused) android.R.drawable.ic_media_pause else android.R.drawable.stat_sys_download)
            .setContentTitle(if (paused) "썸네일 받기 일시정지" else "썸네일 받는 중")
            .setContentText(text)
            .setProgress(total.coerceAtLeast(1), done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp())
            .addAction(
                if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (paused) "재개" else "일시정지",
                action(if (paused) ACTION_RESUME else ACTION_PAUSE, 1),
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "닫기", action(ACTION_CLOSE, 2))
            .build()
    }

    // 안드로이드 15+: 데이터 작업 하루 사용 시간이 다 되면 호출됨 → 깔끔하게 멈추고 다음에 이어서
    override fun onTimeout(startId: Int, fgsType: Int) {
        AppLog.add("썸네일", "안드로이드 작업 시간 한도 → 멈춤, 앱을 켜면 이어서")
        finish(null)
    }

    override fun onDestroy() {
        alive = false
        scope.cancel()
        try {
            wake?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        super.onDestroy()
    }
}
