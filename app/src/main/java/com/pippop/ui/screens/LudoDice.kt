package com.pippop.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pippop.game.ludo.LudoView
import com.pippop.ui.theme.AppTheme
import org.json.JSONObject
import java.util.Locale

/**
 * Ludo's dice strip: a real 3D die (assets/ludodice, three.js and cannon.js in a WebView)
 * thrown along a felt strip and over the Roll button. Each new roll in [view] is thrown, left to
 * right for yours and right to left for everyone else's, landing on the number the game rolled.
 * With [canRoll], a tap on the strip or a swipe along it calls [onRoll]; [onLanded] hears each
 * roll's count once its die has stopped. A phone that can't show 3D gets the flat die and a
 * Roll button instead.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LudoDiceStrip(
    view: LudoView,
    canRoll: Boolean,
    felt: Color,
    onRoll: () -> Unit,
    onLanded: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val rollNow by rememberUpdatedState(onRoll)
    val landedNow by rememberUpdatedState(onLanded)
    var ready by remember { mutableStateOf(false) }
    var broken by remember { mutableStateOf(false) }
    val main = remember { Handler(Looper.getMainLooper()) }
    val web = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(AndroidColor.TRANSPARENT)
            settings.javaScriptEnabled = true
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    Log.i("LudoDice", "${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                    return true
                }
            }
            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun ready() {
                        main.post { ready = true }
                    }

                    @JavascriptInterface
                    @Suppress("UNUSED_PARAMETER")
                    fun roll(power: Double) {
                        main.post { rollNow() }
                    }

                    @JavascriptInterface
                    fun landed(count: Int) {
                        main.post { landedNow(count) }
                    }

                    @JavascriptInterface
                    fun event(what: String) {
                        main.post {
                            if (what.startsWith("error ")) {
                                Log.w("LudoDice", what)
                                broken = true
                            }
                        }
                    }
                },
                "Android",
            )
            loadUrl("file:///android_asset/ludodice/index.html")
        }
    }
    DisposableEffect(web) { onDispose { web.destroy() } }
    // A strip that hasn't started after a while (no WebGL on this phone): the flat die instead.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(8_000)
        if (!ready) broken = true
    }
    val ink = AppTheme.palette.ink
    val last = view.rolls.lastOrNull()
    val state = JSONObject()
        .put("count", view.rollCount)
        .put("value", last?.second ?: view.lastMove?.roll ?: 0)
        .put("who", (last?.first ?: view.lastMove?.who ?: 'o').toString())
        .put("mine", canRoll)
        .put("canRoll", canRoll)
        .put("felt", felt.hex())
        .put("ink", ink.hex())
        .toString()
    LaunchedEffect(ready, state) {
        if (ready) web.evaluateJavascript("try { dice.show($state) } catch (e) { Android.event('error ' + e.message) }", null)
    }
    if (broken) {
        // Without 3D the roll shows at once: the die "lands" straight away.
        LaunchedEffect(view.rollCount) { landedNow(view.rollCount) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
            DieFace(last?.second ?: view.roll ?: view.lastMove?.roll, rolled = view.rollCount)
            PrimaryButton("Roll", onRoll, Modifier.weight(1f), enabled = canRoll)
        }
        return
    }
    Box(modifier.fillMaxWidth().height(STRIP_HEIGHT_DP.dp)) {
        AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
    }
}

/** The strip's height: room for the die to tumble and the Roll button under it. */
private const val STRIP_HEIGHT_DP = 84

private fun Color.hex() = "#%06X".format(Locale.ROOT, toArgb() and 0xFFFFFF)
