package com.pippop.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.pippop.game.golf.Box as CourseBox
import com.pippop.game.golf.GolfShot
import com.pippop.game.golf.Hole
import com.pippop.game.golf.Pt
import com.pippop.game.golf.ShotKind
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** A ball on the course: whose ([id], a mark like 'm'), where, and its colour. */
data class Golf3DBall(val id: Char, val at: Pt, val color: Color)

/**
 * What the 3D view shows between shots: hole [key] ([hole]), the balls still out, whose turn
 * it is, whether you ([me]) can shoot now, and whether your aim line is hidden (the eyes).
 */
data class Golf3DState(
    val key: String,
    val hole: Hole,
    val balls: List<Golf3DBall>,
    val turn: Char?,
    val canShoot: Boolean,
    val noGuide: Boolean,
    val me: Char = 'm',
)

/** A shot for the 3D view to play: [id] tells shots apart, [who] shot it on hole [key]. */
data class Golf3DShot(val id: String, val key: String, val hole: Hole, val who: Char, val color: Color, val shot: GolfShot)

/**
 * Mini Golf's 3D course: a WebView running assets/golf3d (three.js), which draws [state],
 * plays [shot] once when it changes, and takes your touches. Swipe to turn, pinch to zoom,
 * and the side slider shoots: [onShoot] gets the kind of shot, its strength and its aim.
 * [onEvent] hears what happens as the shot plays ("wall", "holed", "kylian"...) and "played"
 * when it has finished on screen.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Golf3D(
    state: Golf3DState,
    shot: Golf3DShot?,
    onShoot: (ShotKind, Double, Double) -> Unit,
    onEvent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shootNow by rememberUpdatedState(onShoot)
    val eventNow by rememberUpdatedState(onEvent)
    var ready by remember { mutableStateOf(false) }
    // What went wrong in the page, shown over it (and in the log) instead of an empty sky.
    var problem by remember { mutableStateOf<String?>(null) }
    val main = remember { Handler(Looper.getMainLooper()) }
    val web = remember {
        WebView(context).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    Log.i("Golf3D", "${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                    return true
                }
            }
            // The page calls these on its own thread; everything they do happens on the main one.
            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun ready() {
                        main.post { ready = true }
                    }

                    @JavascriptInterface
                    fun shoot(kind: String, power: Double, aim: Double) {
                        main.post { if (power.isFinite() && aim.isFinite()) shootNow(ShotKind.from(kind), power.coerceIn(0.05, 1.0), aim) }
                    }

                    @JavascriptInterface
                    fun event(what: String) {
                        main.post {
                            if (what.startsWith("error ")) {
                                Log.w("Golf3D", what)
                                problem = what.removePrefix("error ")
                            } else {
                                eventNow(what)
                            }
                        }
                    }
                },
                "Android",
            )
            loadUrl("file:///android_asset/golf3d/index.html")
        }
    }
    DisposableEffect(web) { onDispose { web.destroy() } }
    // A shot is sent once, before the state that follows it, so the view plays it and then settles.
    var sent by remember { mutableStateOf<String?>(null) }
    val stateJson = remember(state) { state.toJson().toString() }
    LaunchedEffect(ready, shot?.id, stateJson) {
        if (!ready) return@LaunchedEffect
        if (shot != null && shot.id != sent) {
            sent = shot.id
            web.evaluateJavascript(reporting("golf.play(${shot.toJson()})"), null)
        }
        web.evaluateJavascript(reporting("golf.state($stateJson)"), null)
    }
    // A page that hasn't started after a while: say so rather than show an empty course.
    LaunchedEffect(Unit) {
        delay(10_000)
        if (!ready && problem == null) problem = "it didn't start"
    }
    Box(modifier) {
        AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
        problem?.let { why ->
            val p = AppTheme.palette
            Text(
                "The 3D course couldn't load on this phone: $why",
                color = p.text, fontSize = TextSize.Body, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(Space.L).background(p.card, Shapes.M).padding(Space.L),
            )
        }
    }
}

/** [call] in the page, with anything it throws told back to the app (which shows it). */
private fun reporting(call: String) = "try { $call } catch (e) { Android.event('error ' + e.message) }"

private fun Pt.json() = JSONArray().put(x).put(y)
private fun CourseBox.json() = JSONArray().put(x0).put(y0).put(x1).put(y1)
private fun Color.hex() = "#%06X".format(Locale.ROOT, toArgb() and 0xFFFFFF)

/** The hole as the 3D view reads it: the same shape as a line of courses.jsonl. */
fun Hole.toJson(): JSONObject = JSONObject()
    .put("par", par).put("len", len)
    .put("tee", tee.json()).put("cup", cup.json())
    .put("islands", JSONArray(islands.map { it.json() }))
    .put("bridges", JSONArray(bridges.map { it.json() }))
    .put("walls", JSONArray(walls.map { it.json() }))
    .put("sand", JSONArray(sand.map { it.json() }))
    .put("water", JSONArray(water.map { JSONArray().put(it.x).put(it.y).put(it.rx).put(it.ry) }))
    .put(
        "zones",
        JSONArray(
            zones.map { z ->
                JSONObject().put("type", z.type.key).apply {
                    z.box?.let { put("box", it.json()) }
                    z.at?.let { put("at", it.json()) }
                    z.to?.let { put("to", it.json()) }
                    z.dir?.let { put("dir", it.json()) }
                }
            },
        ),
    )

private fun Golf3DState.toJson() = JSONObject()
    .put("key", key).put("hole", hole.toJson())
    .put("balls", JSONArray(balls.map { JSONObject().put("id", it.id.toString()).put("x", it.at.x).put("y", it.at.y).put("color", it.color.hex()) }))
    .put("turn", turn?.toString() ?: JSONObject.NULL)
    .put("me", me.toString()).put("canShoot", canShoot).put("noGuide", noGuide)

private fun Double.short() = Math.round(this * 10000) / 10000.0

private fun Golf3DShot.toJson() = JSONObject()
    .put("key", key).put("hole", hole.toJson()).put("id", who.toString()).put("color", color.hex())
    .put("frames", JSONArray(shot.frames.map { JSONArray().put(it.x.short()).put(it.y.short()).put(it.h.short()) }))
    .put("events", JSONArray(shot.events.map { JSONArray().put(it.frame).put(it.what) }))
