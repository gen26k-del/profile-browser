package com.example.profilebrowser

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * প্রোফাইল ব্রাউজার: প্রতিটি প্রোফাইলের WebView আলাদা ডেটা ফোল্ডারে চলে,
 * তাই কুকি/লগইন এক প্রোফাইল থেকে আরেক প্রোফাইলে মেশে না।
 *
 * মূল কৌশল: WebView.setDataDirectorySuffix(profileId) — এই প্রসেসে প্রথম
 * WebView তৈরি হওয়ার আগেই ডাকতে হয়। তাই ব্রাউজার আলাদা প্রসেসে
 * (android:process=":browser") চলে, আর অ্যাক্টিভিটি শেষ হলে প্রসেস মেরে
 * ফেলা হয়, যাতে পরের প্রোফাইল নতুন প্রসেসে সঠিক suffix পায়।
 */
class BrowserActivity : ComponentActivity() {

    companion object {
        /** এই প্রসেসে এখন কোন suffix সেট আছে (প্রসেস পুনঃব্যবহার ধরতে)। */
        var activeSuffix: String? = null
    }

    private var webView: WebView? = null
    private var urlText by mutableStateOf("")
    private var currentTitle by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profileId = intent.getStringExtra("profile_id")
        val profile = profileId?.let { ProfileStore.get(this, it) }
        if (profile == null) {
            Toast.makeText(this, "প্রোফাইল পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val suffix = "profile_${profile.id}"
        // প্রসেস পুনঃব্যবহার হয়ে ভুল suffix সেট থাকলে প্রসেস নতুন করে চালু করো
        if (activeSuffix != null && activeSuffix != suffix) {
            restartWithProfile(profileId)
            finish()
            return
        }
        try {
            WebView.setDataDirectorySuffix(suffix)
            activeSuffix = suffix
        } catch (_: Exception) {
            // suffix আগেই সেট হয়ে গেছে (একই প্রোফাইল) — এগিয়ে যাও
        }
        urlText = profile.url
        currentTitle = profile.name

        setContent {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(4.dp)
                ) {
                    IconButton(onClick = { goBackOrFinish() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "পেছনে")
                    }
                    IconButton(onClick = { webView?.goForward() }) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "সামনে")
                    }
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { loadUrlText() })
                    )
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "রিফ্রেশ")
                    }
                    IconButton(onClick = { webView?.loadUrl(profile.url) }) {
                        Icon(Icons.Filled.Home, contentDescription = "হোম")
                    }
                    IconButton(onClick = { finish() }) {
                        Icon(Icons.Filled.Close, contentDescription = "বন্ধ")
                    }
                }
                AndroidView(
                    modifier = Modifier.weight(1f),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance()
                                .setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    if (url != null) urlText = url
                                    super.onPageFinished(view, url)
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    if (!title.isNullOrBlank()) currentTitle = title
                                }
                            }
                            loadUrl(profile.url)
                            webView = this
                        }
                    }
                )
            }
            BackHandler { goBackOrFinish() }
        }
    }

    private fun loadUrlText() {
        var u = urlText.trim()
        if (u.isEmpty()) return
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        webView?.loadUrl(u)
    }

    private fun goBackOrFinish() {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else finish()
    }

    /** ভুল suffix সেট থাকা প্রসেস মেরে একই প্রোফাইল দিয়ে আবার চালু করো। */
    private fun restartWithProfile(profileId: String) {
        try {
            val intent = Intent(this, BrowserActivity::class.java).apply {
                putExtra("profile_id", profileId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pi = PendingIntent.getActivity(
                this, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            am.set(
                android.app.AlarmManager.RTC,
                System.currentTimeMillis() + 400, pi
            )
        } catch (_: Exception) {
        }
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    override fun onDestroy() {
        // প্রোফাইল বদলালে পরের বার নতুন প্রসেসে সঠিক suffix পেতে প্রসেস মারো
        if (isFinishing) {
            webView?.destroy()
            webView = null
            activeSuffix = null
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        super.onDestroy()
    }
}

/** শর্টকাট আইকন: প্রোফাইলের নামের প্রথম অক্ষর নীল বৃত্তে। */
fun makeLetterIcon(letter: String): Icon {
    val size = 192
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val bg = Paint().apply { color = Color.parseColor("#1E88E5"); isAntiAlias = true }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, bg)
    val tp = Paint().apply {
        color = Color.WHITE
        textSize = size * 0.52f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    val y = size / 2f - (tp.descent() + tp.ascent()) / 2f
    canvas.drawText(letter, size / 2f, y, tp)
    return Icon.createWithBitmap(bitmap)
}

/** প্রোফাইলের হোম-স্ক্রিন শর্টকাট পিন করো (সরাসরি ওই প্রোফাইল খুলবে)। */
fun pinProfileShortcut(ctx: Context, profile: Profile): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val sm = ctx.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager
    if (!sm.isRequestPinShortcutSupported) return false
    val intent = Intent(ctx, BrowserActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra("profile_id", profile.id)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    val first = profile.name.trim().firstOrNull()?.toString() ?: "?"
    val info = ShortcutInfo.Builder(ctx, "profile_${profile.id}")
        .setShortLabel(profile.name)
        .setLongLabel("${profile.name} — ${profile.typeLabel()}")
        .setIntent(intent)
        .setIcon(makeLetterIcon(first))
        .build()
    return sm.requestPinShortcut(info, null)
}
