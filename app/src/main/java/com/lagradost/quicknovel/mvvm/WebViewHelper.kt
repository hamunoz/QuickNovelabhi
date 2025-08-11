package com.lagradost.quicknovel.utils

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object SessionCookieProvider {
    private const val LOGIN_URL = "https://m.webnovel.com"

    suspend fun getValidCookie(context: Context): String = withContext(Dispatchers.Main) {
        suspendCoroutine { cont ->
            var isResumed = false

            val webView = WebView(context)
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)

            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (isResumed) return
                    //isResumed = true

                    val cookies = cookieManager.getCookie(LOGIN_URL)
                    if (cookies.contains("_csrfToken")) {
                        isResumed = true
                        cont.resume(cookies)
                        webView.destroy()
                    } else {
                        view?.postDelayed({ onPageFinished(view, url) }, 1000)
                    }
                }
            }

            webView.loadUrl(LOGIN_URL)
        }
    }
}


object GrayCitySessionProvider {
    private const val BASE_URL = "https://graycity.net/"
    private const val PREFS_NAME = "graycity_prefs"
    private const val COOKIE_KEY = "session_cookie"
    private const val TAG = "GrayCitySession"

    /** Get saved cookie if available, otherwise fetch via WebView */
    suspend fun getSessionCookie(context: Context): String = withContext(Dispatchers.Main) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedCookie = prefs.getString(COOKIE_KEY, null)

        if (!savedCookie.isNullOrBlank() && savedCookie.contains("PHPSESSID")) {
            Log.d(TAG, "Using saved session cookie: $savedCookie")
            return@withContext savedCookie
        }

        Log.d(TAG, "No saved cookie found, fetching via WebView…")

        suspendCoroutine { cont ->
            var isResumed = false

            val webView = WebView(context)
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)

            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (isResumed) return

                    val cookies = cookieManager.getCookie(BASE_URL)
                    Log.d(TAG, "Cookies: $cookies")

                    if (cookies?.contains("PHPSESSID") == true) {
                        isResumed = true
                        Log.d(TAG, "Fetched new session cookie: $cookies")

                        // Save for reuse
                        prefs.edit().putString(COOKIE_KEY, cookies).apply()

                        cont.resume(cookies)
                        webView.destroy()
                    } else {
                        // Retry after 500ms if PHPSESSID not yet set
                        view?.postDelayed({ onPageFinished(view, url) }, 500)
                    }
                }
            }

            webView.loadUrl(BASE_URL)
        }
    }

    /** Manually update cookie if obtained elsewhere */
    fun saveSessionCookie(context: Context, cookie: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(COOKIE_KEY, cookie).apply()
        Log.d(TAG, "Manually saved cookie: $cookie")
    }

    /** Clear cookie from storage */
    fun clearSessionCookie(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(COOKIE_KEY).apply()
        Log.d(TAG, "Session cookie cleared")
    }
}
