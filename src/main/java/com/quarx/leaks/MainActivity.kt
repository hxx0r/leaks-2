package com.quarx.leaks

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import android.content.Intent
import android.widget.Button
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    // для ui
    private lateinit var passwordInput: TextInputEditText
    private lateinit var checkButton: Button
    private lateinit var resultContainer: LinearLayout
    private lateinit var resultTitle: TextView
    private lateinit var resultText: TextView
    private lateinit var resultIcon: ImageView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var detailsSection: LinearLayout
    private lateinit var hashValue: TextView
    private lateinit var prefixValue: TextView
    private lateinit var mainCard: CardView
    private lateinit var scrollView: ScrollView
    private lateinit var trafficMonitorButton: TextView

    // для coroutine scope
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // инициалтзируем ui компоненты
        initViews()

        // настраиваем 'listeners"
        setupListeners()

        // параллакс эффект для красоты
        setupParallaxEffect()
    }

    private fun initViews() {
        passwordInput = findViewById(R.id.passwordInput)
        checkButton = findViewById(R.id.checkButton)
        resultContainer = findViewById(R.id.resultContainer)
        resultTitle = findViewById(R.id.resultTitle)
        resultText = findViewById(R.id.resultText)
        resultIcon = findViewById(R.id.resultIcon)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        detailsSection = findViewById(R.id.detailsSection)
        hashValue = findViewById(R.id.hashValue)
        prefixValue = findViewById(R.id.prefixValue)
        mainCard = findViewById(R.id.mainCard)
        scrollView = findViewById(R.id.scrollView)
        trafficMonitorButton = findViewById(R.id.trafficMonitorButton)
    }

    private fun setupListeners() {
        checkButton.setOnClickListener {
            val password = passwordInput.text.toString().trim()
            if (password.isEmpty()) {
                showError("Введите пароль для проверки")
                return@setOnClickListener
            }
            checkPassword(password)
            val intent = Intent(this, TrafficMonitorActivity::class.java)
            startActivity(intent)
        }

        // провера сложности пароля в режиме реаьного времени (не работает)
        passwordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateButtonState(s?.toString()?.isNotEmpty() == true)
            }
        })

        // кнопкаа
        passwordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val password = passwordInput.text.toString().trim()
                if (password.isNotEmpty()) {
                    checkPassword(password)
                    // скрыть клавиатуру
                    val imm = getSystemService(InputMethodManager::class.java)
                    imm.hideSoftInputFromWindow(passwordInput.windowToken, 0)
                    return@setOnEditorActionListener true
                }
            }
            false
        }
    }

    private fun setupParallaxEffect() {
        // параллакс эффект для карточки
        scrollView.viewTreeObserver.addOnScrollChangedListener {
            val scrollY = scrollView.scrollY
            val translationY = scrollY * 0.3f
            mainCard.translationY = translationY
        }

        // touch listener для плавной анимации
        mainCard.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mainCard.animate()
                        .scaleX(0.98f)
                        .scaleY(0.98f)
                        .setDuration(150)
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mainCard.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .start()
                }
            }
            false
        }
    }

    private fun updateButtonState(enabled: Boolean) {
        checkButton.isEnabled = enabled
        checkButton.alpha = if (enabled) 1.0f else 0.6f
    }

    private fun checkPassword(password: String) {
        // спрятать клавиатуру
        hideKeyboard()

        // чтобы показать загрузку
        showLoading()

        scope.launch {
            try {
                // рассчет хэша sha-1
                val hash = calculateSHA1(password).uppercase()

                // показ детали хэша
                showHashDetails(hash)

                // вызов апи
                val result = withContext(Dispatchers.IO) {
                    checkPasswordWithAPI(hash)
                }

                // показать результат
                showResult(result)

            } catch (e: Exception) {
                showError("Ошибка при проверке: ${e.localizedMessage}")
            }
        }
    }

    private fun calculateSHA1(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1")
            .digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun checkPasswordWithAPI(hash: String): CheckResult {
        val prefix = hash.substring(0, 5)
        val suffix = hash.substring(5)

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("https://api.pwnedpasswords.com/range/$prefix")
            .addHeader("User-Agent", "Leaks-Android/1.0")
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("API Error: ${response.code}")
        }

        val body = response.body?.string() ?: ""
        val lines = body.split("\n")

        for (line in lines) {
            val parts = line.split(":")
            if (parts.size == 2 && parts[0].trim().uppercase() == suffix) {
                val count = parts[1].trim().toInt()
                return CheckResult(true, count)
            }
        }

        return CheckResult(false, 0)
    }

    private fun showLoading() {
        resultContainer.visibility = View.VISIBLE
        loadingIndicator.visibility = View.VISIBLE
        resultIcon.visibility = View.GONE
        detailsSection.visibility = View.GONE

        resultTitle.text = getString(R.string.checking_status)
        resultText.text = "Проверяем базы данных на наличие утечек..."

        // анимация рез-та
        resultContainer.startAnimation(
            AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        )
    }

    private fun showHashDetails(hash: String) {
        val prefix = hash.substring(0, 5)
        hashValue.text = hash
        prefixValue.text = prefix
    }

    private fun showResult(result: CheckResult) {
        loadingIndicator.visibility = View.GONE
        resultIcon.visibility = View.VISIBLE
        detailsSection.visibility = View.VISIBLE

        if (result.isPwned) {
            // пароль скомпроментирован
            resultIcon.setImageResource(R.drawable.ic_danger)
            resultIcon.setColorFilter(resources.getColor(R.color.danger_color, null))

            resultTitle.text = getString(R.string.pwned_password)
            resultText.text = "Этот пароль обнаружен в ${result.count} утечках данных. " +
                    "Рекомендуем заменить его!"

            // вибрация при обнаружении пароля в слитых бд
            performVibration(200)

        } else {
            // если пароль безопасн
            resultIcon.setImageResource(R.drawable.ic_safe)
            resultIcon.setColorFilter(resources.getColor(R.color.safe_color, null))

            resultTitle.text = getString(R.string.safe_password)
            resultText.text = "Этот пароль не найден в известных утечках данных. " +
                    "Однако это не гарантирует его абсолютную безопасность."
        }

        // показ рекомендций
        showRecommendations(result.isPwned)
    }

    private fun showError(message: String) {
        resultContainer.visibility = View.VISIBLE
        loadingIndicator.visibility = View.GONE
        resultIcon.visibility = View.VISIBLE
        detailsSection.visibility = View.GONE

        resultIcon.setImageResource(R.drawable.ic_error)
        resultIcon.setColorFilter(resources.getColor(R.color.warning_color, null))

        resultTitle.text = getString(R.string.error_message)
        resultText.text = message

        // вибрация при ошибке
        performVibration(100)
    }

    private fun showRecommendations(isPwned: Boolean) {
        // текст рекомендаии
        val recommendationsText = if (isPwned) {
            """
            Рекомендации:
            • Немедленно измените этот пароль везде, где он используется
            • Включите двухфакторную аутентификацию
            """.trimIndent()
        } else {
            """
            Рекомендации:
            • Продолжайте использовать уникальные пароли для каждого сервиса
            • Периодически обновляйте важные пароли
            • Включайте двухфакторную аутентификацию
            """.trimIndent()
        }

        // создание новго объекта TextView для рекоменадций
        val recommendationsView = TextView(this).apply {
            text = recommendationsText
            setTextColor(resources.getColor(R.color.text_secondary, null))
            textSize = 12f
            // установка межстрочного интервала
            setLineSpacing(4f, 1f)
            setPadding(16, 16, 16, 0)
        }

        // добавляем рекомендации
        val parent = resultContainer as LinearLayout
        // удаляем рекомендации, если они уже есть
        val existingIndex = parent.indexOfChild(detailsSection) + 1
        if (parent.childCount > existingIndex && parent.getChildAt(existingIndex).tag == "recommendations") {
            parent.removeViewAt(existingIndex)
        }

        recommendationsView.tag = "recommendations"
        parent.addView(recommendationsView, existingIndex)
    }

    private fun hideKeyboard() {
        val view = currentFocus
        view?.let {
            val imm = getSystemService(InputMethodManager::class.java)
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    @SuppressLint("MissingPermission")
    private fun performVibration(duration: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // для Android 8.0 и выше
                val vibrator = getSystemService(Vibrator::class.java)
                val vibrationEffect = VibrationEffect.createOneShot(
                    duration,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
                vibrator.vibrate(vibrationEffect)
            } else {
                // для старых версий Android
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Vibrator::class.java)
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } catch (e: Exception) {
            // игнорируем ошибку
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    data class CheckResult(val isPwned: Boolean, val count: Int)

}