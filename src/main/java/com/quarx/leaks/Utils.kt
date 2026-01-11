package com.quarx.leaks

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object NetworkUtils {

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }
}

object PasswordUtils {

    fun calculatePasswordStrength(password: String): Int {
        var strength = 0

        if (password.length >= 8) strength += 20
        if (password.length >= 12) strength += 20
        if (password.any { it.isUpperCase() }) strength += 20
        if (password.any { it.isLowerCase() }) strength += 20
        if (password.any { it.isDigit() }) strength += 20
        if (password.any { !it.isLetterOrDigit() }) strength += 20

        return minOf(strength, 100)
    }

    fun generateHashInfo(hash: String): String {
        return """
            Полный хеш: $hash
            Префикс (отправляется): ${hash.substring(0, 5)}
            Суффикс (проверяется локально): ${hash.substring(5)}
            Длина: ${hash.length} символов
        """.trimIndent()
    }
}