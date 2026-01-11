package com.quarx.leaks

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class AppTrafficService : android.app.Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationManager: NotificationManager
    private var isMonitoring = false

    // База данных известных трекеров
    private val knownTrackers = listOf(
        Tracker(
            name = "Google Analytics",
            category = TrackerCategory.ANALYTICS,
            description = "Собирает данные об использовании приложения",
            domains = listOf("google-analytics.com", "www.google-analytics.com", "ssl.google-analytics.com")
        ),
        Tracker(
            name = "Facebook Analytics",
            category = TrackerCategory.ANALYTICS,
            description = "Трекинг от Facebook для аналитики и рекламы",
            domains = listOf("graph.facebook.com", "connect.facebook.net", "facebook.com")
        ),
        Tracker(
            name = "Firebase Analytics",
            category = TrackerCategory.ANALYTICS,
            description = "Google Firebase Analytics - сбор данных о пользователях",
            domains = listOf("firebase.google.com", "firebase-settings.crashlytics.com")
        ),
        Tracker(
            name = "AdMob",
            category = TrackerCategory.ADVERTISING,
            description = "Показ рекламы и сбор данных для таргетирования",
            domains = listOf("googleadservices.com", "admob.com", "doubleclick.net")
        ),
        Tracker(
            name = "AppsFlyer",
            category = TrackerCategory.ANALYTICS,
            description = "Атрибуция и аналитика мобильных приложений",
            domains = listOf("appsflyer.com", "t.appsflyer.com")
        ),
        Tracker(
            name = "Adjust",
            category = TrackerCategory.ANALYTICS,
            description = "Аналитика и атрибуция мобильных приложений",
            domains = listOf("adjust.com", "app.adjust.com")
        ),
        Tracker(
            name = "Crashlytics",
            category = TrackerCategory.CRASH_REPORTING,
            description = "Отчеты о крашах и аналитика",
            domains = listOf("crashlytics.com", "reports.crashlytics.com")
        )
    )

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "traffic_monitor"
        private const val CHANNEL_NAME = "Мониторинг трафика"

        const val ACTION_START_MONITORING = "START_MONITORING"
        const val ACTION_STOP_MONITORING = "STOP_MONITORING"
        const val ACTION_UPDATE_DATA = "UPDATE_DATA"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> startMonitoring()
            ACTION_STOP_MONITORING -> stopMonitoring()
            ACTION_UPDATE_DATA -> updateTrafficData()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        if (isMonitoring) return

        isMonitoring = true
        startForeground(NOTIFICATION_ID, createNotification("Мониторинг запущен"))

        serviceScope.launch {
            while (isMonitoring) {
                val trafficData = collectTrafficData()
                broadcastTrafficData(trafficData)
                delay(TimeUnit.SECONDS.toMillis(10)) // Обновляем каждые 10 секунд
            }
        }

        Log.d("AppTrafficService", "Мониторинг трафика запущен")
    }

    private fun stopMonitoring() {
        isMonitoring = false
        stopForeground(true)
        stopSelf()
        Log.d("AppTrafficService", "Мониторинг трафика остановлен")
    }

    private fun collectTrafficData(): List<TrafficData> {
        val trafficDataList = mutableListOf<TrafficData>()
        val packageManager = packageManager

        try {
            // Получаем список установленных приложений
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledApplications(0)
            }

            // Фильтруем системные приложения (опционально)
            val userApps = packages.filter {
                it.flags and ApplicationInfo.FLAG_SYSTEM == 0
            }

            for (appInfo in userApps.take(20)) { // Ограничиваем для производительности
                try {
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val uid = appInfo.uid

                    // Получаем статистику трафика через TrafficStats
                    val txBytes = TrafficStats.getUidTxBytes(uid)
                    val rxBytes = TrafficStats.getUidRxBytes(uid)

                    // Получаем разрешения приложения
                    val permissions = try {
                        @Suppress("DEPRECATION")
                        packageManager.getPackageInfo(appInfo.packageName, PackageManager.GET_PERMISSIONS)
                            .requestedPermissions?.toList() ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }

                    // Получаем иконку приложения
                    val icon = packageManager.getApplicationIcon(appInfo)

                    // Определяем трекеры по доменам (симуляция)
                    val trackers = detectTrackers(appInfo.packageName, permissions)

                    // Определяем уровень риска
                    val riskLevel = calculateRiskLevel(permissions, trackers)

                    val trafficData = TrafficData(
                        appName = appName,
                        packageName = appInfo.packageName,
                        appIcon = icon,
                        dataSent = txBytes,
                        dataReceived = rxBytes,
                        trackers = trackers,
                        permissions = permissions.take(10), // Ограничиваем для отображения
                        riskLevel = riskLevel
                    )

                    trafficDataList.add(trafficData)
                } catch (e: Exception) {
                    Log.e("AppTrafficService", "Ошибка сбора данных для ${appInfo.packageName}", e)
                }
            }
        } catch (e: Exception) {
            Log.e("AppTrafficService", "Ошибка сбора данных трафика", e)
        }

        return trafficDataList
    }

    private fun detectTrackers(packageName: String, permissions: List<String>?): List<Tracker> {
        val detectedTrackers = mutableListOf<Tracker>()

        // Простая эвристика для обнаружения трекеров
        permissions?.forEach { permission ->
            when {
                permission.contains("ACCESS_FINE_LOCATION") || permission.contains("ACCESS_COARSE_LOCATION") -> {
                    detectedTrackers.add(
                        Tracker(
                            name = "Location Tracking",
                            category = TrackerCategory.LOCATION,
                            description = "Слежение за местоположением",
                            domains = emptyList()
                        )
                    )
                }
                permission.contains("READ_CONTACTS") -> {
                    detectedTrackers.add(
                        Tracker(
                            name = "Contacts Access",
                            category = TrackerCategory.PROFILING,
                            description = "Доступ к контактам для профилирования",
                            domains = emptyList()
                        )
                    )
                }
                permission.contains("READ_SMS") || permission.contains("RECEIVE_SMS") -> {
                    detectedTrackers.add(
                        Tracker(
                            name = "SMS Monitoring",
                            category = TrackerCategory.PROFILING,
                            description = "Чтение SMS для верификации и анализа",
                            domains = emptyList()
                        )
                    )
                }
            }
        }

        // Проверяем известные трекеры по пакету
        val packageSpecificTrackers = knownTrackers.filter { tracker ->
            // Здесь можно добавить логику проверки пакета на наличие трекеров
            // Например, проверка на наличие определенных библиотек или классов
            packageName.contains("facebook", ignoreCase = true) &&
                    tracker.name.contains("Facebook", ignoreCase = true)
        }

        detectedTrackers.addAll(packageSpecificTrackers)

        return detectedTrackers.distinctBy { it.name }
    }

    private fun calculateRiskLevel(permissions: List<String>?, trackers: List<Tracker>): RiskLevel {
        var riskScore = 0

        permissions?.forEach { permission ->
            when {
                permission.contains("ACCESS_FINE_LOCATION") -> riskScore += 3
                permission.contains("READ_CONTACTS") -> riskScore += 2
                permission.contains("READ_SMS") -> riskScore += 3
                permission.contains("RECORD_AUDIO") -> riskScore += 3
                permission.contains("CAMERA") -> riskScore += 2
                permission.contains("READ_CALENDAR") -> riskScore += 1
                permission.contains("READ_CALL_LOG") -> riskScore += 2
            }
        }

        riskScore += trackers.size * 2

        return when {
            riskScore >= 10 -> RiskLevel.CRITICAL
            riskScore >= 7 -> RiskLevel.HIGH
            riskScore >= 4 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }

    private fun updateTrafficData() {
        serviceScope.launch {
            val trafficData = collectTrafficData()
            broadcastTrafficData(trafficData)
        }
    }

    private fun broadcastTrafficData(trafficData: List<TrafficData>) {
        val intent = Intent("TRAFFIC_DATA_UPDATED")
        intent.putExtra("traffic_data", ArrayList(trafficData))
        sendBroadcast(intent)
    }

    private fun createNotification(contentText: String): Notification {
        val intent = Intent(this, TrafficMonitorActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Монитор трафика")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Уведомления мониторинга сетевого трафика"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        isMonitoring = false
    }
}