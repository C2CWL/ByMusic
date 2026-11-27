package com.sosauce.chocola.feature.playing.presentation.expand

import android.provider.Settings
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.sosauce.chocola.core.designsystem.components.GlassBackdrop
import com.sosauce.chocola.core.presentation.navigation.Screen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal data class PlayerDock(
    val bounds: SheetRect,
    val glass: Boolean,
    val backdrop: GlassBackdrop?,
    val bottomCornerDp: Float,
    val dragDown: ((Float) -> Unit)?,
    val stopDragDown: ((Boolean) -> Unit)?
)

internal val LocalPlayerSheet = staticCompositionLocalOf<PlayerSheetState?> { null }
internal val LocalPlayerDockEntry = staticCompositionLocalOf<PlayerDockEntry?> { null }

@Composable
internal fun PlayerDockEntry(route: Any, content: @Composable () -> Unit) {
    val sheet = LocalPlayerSheet.current
    val entry = remember(sheet, route) { sheet?.newEntry(route) }
    DisposableEffect(sheet, entry) {
        if (entry != null) sheet?.attach(entry)
        onDispose { if (entry != null) sheet?.detach(entry) }
    }
    CompositionLocalProvider(LocalPlayerDockEntry provides entry, content = content)
}

@Stable
internal class PlayerSheetState(private val scope: CoroutineScope) {
    private val motion = Animatable(0f).apply { updateBounds(0f, 1f) }
    private var motionJob: Job? = null
    private var target = 0f
    private var pendingOpen = false
    private var queuedNavigation: (() -> Unit)? = null

    private val docks = PlayerDockRegistry(mutableStateMapOf<PlayerDockEntry, PlayerDock?>())
    private var activeEntry: PlayerDockEntry? = null

    val progress get() = motion.value
    var settled by mutableStateOf<Float?>(0f)
        private set
    var ownsBack by mutableStateOf(false)
        private set
    var dragging by mutableStateOf(false)
        private set
    private var backGesture: Any? = null
    private val backInProgress get() = backGesture != null
    var reduceMotion = false
    private var canOpen = false

    fun newEntry(route: Any) = docks.newEntry(route)
    fun attach(entry: PlayerDockEntry) = docks.attach(entry)
    fun detach(entry: PlayerDockEntry) = docks.detach(entry)
    fun register(entry: PlayerDockEntry, dock: PlayerDock) = docks.update(entry, dock)
    fun unregister(entry: PlayerDockEntry) = docks.update(entry, null)
    fun dockEntry(route: Any) = docks.active(route)
    fun dock(entry: PlayerDockEntry?) = docks.descriptor(entry)

    fun updateHost(entry: PlayerDockEntry?, ready: Boolean) {
        if (entry == null || entry != activeEntry) reset()
        activeEntry = entry
        canOpen = ready
        if (ready && pendingOpen) open()
    }

    fun open() {
        if (backInProgress || dragging || queuedNavigation != null ||
            (ownsBack && target == 1f && !pendingOpen)
        ) return
        ownsBack = true
        if (!canOpen) {
            pendingOpen = true
            return
        }
        pendingOpen = false
        animate(1f)
    }

    fun cancelPendingOpen() {
        if (pendingOpen) close()
    }

    fun reverse() {
        if (backInProgress || dragging || queuedNavigation != null) return
        if (pendingOpen || target == 1f) close() else open()
    }

    fun close() {
        if (queuedNavigation != null || backInProgress ||
            (!pendingOpen && ownsBack && target == 0f && motion.isRunning)
        ) return
        pendingOpen = false
        dismissLyrics()
        animate(0f)
    }

    fun navigate(screen: Screen, navigate: (Screen) -> Unit) {
        if (queuedNavigation != null) return
        if (settled == 0f && !ownsBack) {
            navigate(screen)
            return
        }
        queuedNavigation = { navigate(screen) }
        pendingOpen = false
        dismissLyrics()
        animate(0f)
    }

    private fun reset() {
        canOpen = false
        pendingOpen = false
        queuedNavigation = null
        backGesture = null
        dragging = false
        ownsBack = false
        dismissLyrics()
        animate(0f, snap = true)
    }

    fun canStartGesture() = canOpen && !backInProgress && !dragging && !lyricsVisible && queuedNavigation == null

    fun beginDrag(): Float? {
        if (!canStartGesture()) return null
        val start = progress
        motionJob?.cancel()
        pendingOpen = false
        dragging = true
        settled = null
        ownsBack = true
        return start
    }

    fun dragTo(value: Float) {
        if (dragging && !reduceMotion) seek(value)
    }

    fun endDrag(value: Float, velocityY: Float, travel: Float, startedExpanded: Boolean) {
        if (!dragging) return
        dragging = false
        animate(
            destination = PlayerExpandMath.settleTarget(value, velocityY, startedExpanded),
            velocity = PlayerExpandMath.progressVelocity(velocityY, travel).coerceIn(-6f, 6f),
            release = true
        )
    }

    fun cancelDrag() {
        if (!dragging) return
        dragging = false
        animate(PlayerExpandMath.settleTarget(progress, 0f))
    }

    private fun seek(value: Float) {
        motionJob?.cancel()
        motionJob = scope.launch(start = CoroutineStart.UNDISPATCHED) { motion.snapTo(value) }
    }

    private fun animate(
        destination: Float,
        velocity: Float = motion.velocity,
        release: Boolean = false,
        snap: Boolean = false,
        finished: (() -> Unit)? = null
    ) {
        motionJob?.cancel()
        target = destination
        settled = null
        motionJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (snap || reduceMotion) {
                motion.snapTo(destination)
            } else if (motion.value != destination || velocity != 0f) {
                val tap = tween<Float>(
                    durationMillis = if (destination == 0f) PlayerExpandMath.COLLAPSE_MS else PlayerExpandMath.EXPAND_MS,
                    easing = Easing(PlayerExpandMath::tapEasing)
                )
                if (release || velocity != 0f) {
                    motion.animateTo(
                        destination,
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = PlayerExpandMath.RELEASE_STIFFNESS,
                            visibilityThreshold = .001f
                        ),
                        initialVelocity = if (release) {
                            PlayerExpandMath.boundReleaseVelocity(motion.value, destination, velocity)
                        } else velocity
                    )
                } else {
                    motion.animateTo(destination, tap, initialVelocity = velocity)
                }
                // An opposing fling can first hit the other bound. Continue from rest there.
                if (motion.value != destination) motion.animateTo(destination, tap, initialVelocity = 0f)
            }
            if (destination == 0f || destination == 1f) settled = destination
            ownsBack = destination > 0f
            if (destination == 0f) {
                val action = queuedNavigation
                queuedNavigation = null
                action?.invoke()
            }
            finished?.invoke()
        }
    }

    @Composable
    fun HandleBack(enabled: Boolean) {
        PredictiveBackHandler(enabled = enabled && ownsBack) { events ->
            if (lyricsVisible) {
                events.collect {}
                closeLyrics()
                return@PredictiveBackHandler
            }
            if (!ownsBack || queuedNavigation != null) {
                events.collect {}
                return@PredictiveBackHandler
            }
            val gesture = Any()
            val start = progress
            val resumeTarget = target
            val resumeVelocity = motion.velocity
            val wasAnimating = motionJob?.isActive == true && motion.isRunning
            val wasPending = pendingOpen
            val wasDragging = dragging
            var receivedProgress = false
            backGesture = gesture
            dragging = false
            pendingOpen = false
            settled = null
            motionJob?.cancel()
            try {
                events.collect { event ->
                    receivedProgress = true
                    if (backGesture === gesture && !reduceMotion) {
                        seek(PlayerExpandMath.backProgress(start, event.progress))
                    }
                }
                if (backGesture !== gesture) return@PredictiveBackHandler
                backGesture = null
                dismissLyrics()
                animate(0f, velocity = if (receivedProgress) 0f else resumeVelocity)
            } catch (cancelled: CancellationException) {
                if (backGesture === gesture) {
                    backGesture = null
                    if (!receivedProgress && wasAnimating) {
                        animate(resumeTarget, resumeVelocity)
                    } else {
                        animate(start) {
                            when {
                                wasPending -> open()
                                wasAnimating -> animate(resumeTarget, resumeVelocity)
                                wasDragging -> animate(PlayerExpandMath.settleTarget(start, 0f))
                            }
                        }
                    }
                }
                throw cancelled
            }
        }
    }

    private val lyrics = Animatable(0f)
    private var lyricsJob: Job? = null
    private var lyricsOpen by mutableStateOf(false)
    val lyricsProgress get() = lyrics.value
    val lyricsVisible by derivedStateOf { lyricsOpen || lyrics.value > 0f }

    fun openLyrics() {
        if (settled != 1f || backInProgress) return
        lyricsOpen = true
        animateLyrics(1f, VinylLyricsMath.ENTER_MS)
    }

    fun closeLyrics() {
        lyricsOpen = false
        animateLyrics(0f, VinylLyricsMath.EXIT_MS)
    }

    private fun dismissLyrics() {
        lyricsOpen = false
        lyricsJob?.cancel()
        lyricsJob = scope.launch(start = CoroutineStart.UNDISPATCHED) { lyrics.snapTo(0f) }
    }

    private fun animateLyrics(destination: Float, duration: Int) {
        lyricsJob?.cancel()
        lyricsJob = scope.launch {
            if (reduceMotion) lyrics.snapTo(destination)
            else lyrics.animateTo(destination, tween(duration, easing = FastOutSlowInEasing))
        }
    }
}

@Composable
internal fun rememberPlayerSheetState(): PlayerSheetState {
    val scope = rememberCoroutineScope()
    val reduceMotion = systemAnimationsDisabled()
    return remember(scope) { PlayerSheetState(scope) }.also { it.reduceMotion = reduceMotion }
}

@Composable
internal fun systemAnimationsDisabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f) == 0f
    }
}
