package com.sosauce.chocola.feature.splash

/**
 * Pure decision rules for the in-app brand loading page.
 *
 * This is not the Android 12+ system SplashScreen. The system first frame is
 * a black field plus a small static mark; this gate only decides whether the
 * subsequent in-app vinyl page may appear.
 */
enum class BrandSplashPhase {
    /** Overlay is not shown; app or setup content is visible. */
    Hidden,
    /** Overlay is showing while necessary startup work is still running. */
    Visible,
    /** Overlay has been dismissed for this process session. */
    Finished,
}

enum class BrandSplashReason {
    NoPermission,
    SessionAlreadyCompleted,
    AlreadyReady,
    ColdStart,
    InitReady,
    InitFailed,
    InitTimedOut,
}

enum class BrandSplashSettle {
    Ready,
    TimedOut,
    Failed,
    Cancelled,
}

data class BrandSplashDecision(
    val phase: BrandSplashPhase,
    val reason: BrandSplashReason,
    val markSessionComplete: Boolean,
)

object BrandSplashGate {

    fun decideInitial(
        hasMusicPermission: Boolean,
        sessionAlreadyCompleted: Boolean,
        initReady: Boolean,
    ): BrandSplashDecision {
        if (!hasMusicPermission) {
            return BrandSplashDecision(
                phase = BrandSplashPhase.Hidden,
                reason = BrandSplashReason.NoPermission,
                markSessionComplete = false,
            )
        }
        if (sessionAlreadyCompleted) {
            return BrandSplashDecision(
                phase = BrandSplashPhase.Hidden,
                reason = BrandSplashReason.SessionAlreadyCompleted,
                markSessionComplete = false,
            )
        }
        if (initReady) {
            return BrandSplashDecision(
                phase = BrandSplashPhase.Hidden,
                reason = BrandSplashReason.AlreadyReady,
                markSessionComplete = true,
            )
        }
        return BrandSplashDecision(
            phase = BrandSplashPhase.Visible,
            reason = BrandSplashReason.ColdStart,
            markSessionComplete = false,
        )
    }

    fun decideAfterSettle(settle: BrandSplashSettle): BrandSplashDecision {
        return when (settle) {
            BrandSplashSettle.Ready -> BrandSplashDecision(
                phase = BrandSplashPhase.Finished,
                reason = BrandSplashReason.InitReady,
                markSessionComplete = true,
            )
            BrandSplashSettle.TimedOut -> BrandSplashDecision(
                phase = BrandSplashPhase.Finished,
                reason = BrandSplashReason.InitTimedOut,
                markSessionComplete = true,
            )
            BrandSplashSettle.Failed -> BrandSplashDecision(
                phase = BrandSplashPhase.Finished,
                reason = BrandSplashReason.InitFailed,
                markSessionComplete = true,
            )
            BrandSplashSettle.Cancelled -> BrandSplashDecision(
                phase = BrandSplashPhase.Visible,
                reason = BrandSplashReason.ColdStart,
                markSessionComplete = false,
            )
        }
    }

    fun shouldAnimateRing(animatorDurationScale: Float): Boolean = animatorDurationScale > 0f
}
