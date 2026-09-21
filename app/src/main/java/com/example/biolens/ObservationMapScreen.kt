package com.example.biolens

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import android.annotation.TargetApi
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.biolens.ui.theme.BioLensGreen
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObservationMapScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var observations by remember { mutableStateOf<List<BirdObservation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        ObservationStorage.loadObservations(context) { list ->
            observations = list.filter { it.latitude != null && it.longitude != null }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "OBSERVATION MAP",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black
                )
            )
        },
        containerColor = Color.Black
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BioLensGreen)
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewRef = this
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                // Enable cross-origin access for file URLs
                                try {
                                    val clazz = settings.javaClass
                                    val method1 = clazz.getMethod("setAllowUniversalAccessFromFileURLs", Boolean::class.javaPrimitiveType)
                                    method1.invoke(settings, true)
                                    val method2 = clazz.getMethod("setAllowFileAccessFromFileURLs", Boolean::class.javaPrimitiveType)
                                    method2.invoke(settings, true)
                                } catch (e: Exception) {
                                    Log.e("WebView", "Error setting file access", e)
                                }
                                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            }
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    consoleMessage?.apply {
                                        Log.d("WebView", "${message()} -- From line ${lineNumber()} of ${sourceId()}")
                                    }
                                    return true
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    Log.d("WebView", "Page started loading: $url")
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    Log.d("WebView", "Page finished loading: $url")
                                    // Send data to JS
                                    val jsonArray = JSONArray()
                                    observations.forEach { obs ->
                                        val obj = JSONObject().apply {
                                            put("id", obs.id)
                                            put("speciesName", obs.speciesName.uppercase().replace("-", " "))
                                            put("confidence", obs.confidence)
                                            put("timestamp", dateFormat.format(Date(obs.timestamp)))
                                            put("location", obs.location ?: "Unknown Location")
                                            put("latitude", obs.latitude)
                                            put("longitude", obs.longitude)
                                        }
                                        jsonArray.put(obj)
                                    }
                                    val jsonStr = jsonArray.toString()
                                    val escapedJson = JSONObject.quote(jsonStr)
                                    Log.d("WebView", "Evaluating JS: initMap($escapedJson)")
                                    view?.evaluateJavascript("initMap($escapedJson)", null)
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    Log.e("WebView", "Error: ${error?.description} (${error?.errorCode}) at ${request?.url}")
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: SslError?
                                ) {
                                    Log.e("WebView", "SSL Error: $error")
                                    handler?.proceed()
                                }
                            }
                            loadUrl("file:///android_asset/map.html")
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = {
                        it.destroy()
                    }
                )
            }
        }
    }
}
