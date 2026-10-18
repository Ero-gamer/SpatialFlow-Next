package com.codetrio.spatialflow.ui.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class BackSwipeEdge {
    LEFT,
    RIGHT
}

object BackGestureTracker {
    var lastEdge by mutableStateOf(BackSwipeEdge.LEFT)
}

// Curva Emphasized oficial de Material 3 (la que usan los Pixel contemporáneos)
val M3EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
const val AOSP_TRANSITION_DURATION = 350

// Equivale a sud_slide_next_in
fun aospSharedAxisEnter(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { it / 3 }, // Arranca desde 1/3 de la pantalla hacia la derecha
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    ) + fadeIn(
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    )
}

// Equivale a sud_slide_next_out
fun aospSharedAxisExit(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { -it / 3 }, // Se desplaza 1/3 de la pantalla hacia la izquierda
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    ) + scaleOut(
        targetScale = 0.92f, // Escala hacia abajo para dar efecto de profundidad sin desaparecer por completo
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    )
}

// Equivale a sud_slide_back_in (cuando volvés atrás, la pantalla previa reaparece desde la dirección del gesto)
fun aospSharedAxisPopEnter(edge: BackSwipeEdge = BackGestureTracker.lastEdge): EnterTransition {
    val isLeft = edge == BackSwipeEdge.LEFT
    return slideInHorizontally(
        initialOffsetX = { if (isLeft) -it / 3 else it / 3 }, // Reaparece desde el lado del gesto
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    ) + fadeIn(
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    )
}

// Equivale a sud_slide_back_out (la pantalla secundaria se achica y desliza según el borde del gesto)
fun aospSharedAxisPopExit(edge: BackSwipeEdge = BackGestureTracker.lastEdge): ExitTransition {
    val isLeft = edge == BackSwipeEdge.LEFT
    return slideOutHorizontally(
        targetOffsetX = { if (isLeft) it else -it }, // Se desliza hacia la derecha si el gesto vino de la izquierda, o hacia la izquierda si vino de la derecha
        animationSpec = tween(
            durationMillis = AOSP_TRANSITION_DURATION,
            easing = M3EmphasizedEasing // Acompaña el gesto de predictive back
        )
    ) + scaleOut(
        targetScale = 0.85f, // Se achica para dar efecto de profundidad
        transformOrigin = TransformOrigin(if (isLeft) 0f else 1f, 0.5f), // Anclado al borde desde el que el usuario deslizó
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    )
}

// MD3 Expressive – Emphasized easing (matches Material Motion spec)
// cubic-bezier(0.2, 0, 0, 1.0) — fast start, smooth settle
val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

// Decelerate for elements entering the screen — mirror of EmphasizedAccelerateEasing
// so the entry feels as weighty/grounded as the pop-exit
val EmphasizedDecelerateEasing = CubicBezierEasing(0.2f, 0.85f, 0.7f, 1f)

// Accelerate for elements leaving the screen
val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

// Base duration designed for 1x animation scale — looks good at full speed,
// still smooth at 0.5x (system halves it to ~225 ms).
const val TRANSITION_DURATION = 450

// Push: Enter from Right — slides in 50% of screen width + slight scale up (mirrors popExit's weight)
// Fade uses an accelerate-style curve so alpha stays low while slide does its work,
// then catches up at the end — prevents the "fade arrives before slide" perceptual mismatch.
fun enterTransition(): EnterTransition = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialOffsetX = { (it * 0.5f).toInt() }
) + scaleIn(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialScale = 0.92f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeIn(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedAccelerateEasing)
)

// Push: Exit to Left — recedes 25% (parallax, barely moves)
fun exitTransition(): ExitTransition = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedAccelerateEasing),
    targetOffsetX = { -(it * 0.25f).toInt() }
) + fadeOut(
    animationSpec = tween(TRANSITION_DURATION / 2, easing = EmphasizedAccelerateEasing)
)

// Pop: Enter from Left or Right depending on gesture edge
fun popEnterTransition(edge: BackSwipeEdge = BackGestureTracker.lastEdge): EnterTransition {
    val isLeft = edge == BackSwipeEdge.LEFT
    return slideInHorizontally(
        animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
        initialOffsetX = { if (isLeft) -(it * 0.25f).toInt() else (it * 0.25f).toInt() }
    ) + scaleIn(
        animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
        initialScale = 0.95f
    ) + fadeIn(
        animationSpec = tween(TRANSITION_DURATION / 2, easing = EmphasizedDecelerateEasing)
    )
}

// Pop: Exit to Right or Left depending on gesture edge
fun popExitTransition(edge: BackSwipeEdge = BackGestureTracker.lastEdge): ExitTransition {
    val isLeft = edge == BackSwipeEdge.LEFT
    return slideOutHorizontally(
        animationSpec = tween(TRANSITION_DURATION, easing = M3EmphasizedEasing),
        targetOffsetX = { if (isLeft) it else -it }
    ) + scaleOut(
        animationSpec = tween(TRANSITION_DURATION, easing = M3EmphasizedEasing),
        targetScale = 0.88f,
        transformOrigin = TransformOrigin(if (isLeft) 0f else 1f, 0.5f)
    )
}

enum class MainRootDirection {
    FORWARD,
    BACKWARD
}

// Base duration for bottom-nav switches at 1x — at 0.5x system scale = ~190 ms.
const val BOTTOM_NAV_TRANSITION_DURATION = 380

// MD3 Expressive easing for bottom-nav switches
val BottomNavEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

val MAIN_ROOT_TRANSITION_SPEC =
    tween<IntOffset>(durationMillis = BOTTOM_NAV_TRANSITION_DURATION, easing = BottomNavEasing)

val MAIN_ROOT_FADE_SPEC =
    tween<Float>(durationMillis = BOTTOM_NAV_TRANSITION_DURATION / 2, easing = BottomNavEasing)

fun isMainRootRoute(route: String?): Boolean = when (route) {
    "explore",
    "library",
    "effects",
    "settings" -> true
    else -> false
}

fun mainRootRouteIndex(route: String?): Int? = when (route) {
    "explore" -> 0
    "library" -> 1
    "effects" -> 2
    "settings" -> 3
    else -> null
}

fun mainRootDirection(
    fromRoute: String?,
    toRoute: String?
): MainRootDirection? {
    val fromIndex = mainRootRouteIndex(fromRoute) ?: return null
    val toIndex = mainRootRouteIndex(toRoute) ?: return null
    if (fromIndex == toIndex) return null
    return if (toIndex > fromIndex) MainRootDirection.FORWARD else MainRootDirection.BACKWARD
}

fun mainRootEnterTransition(
    fromRoute: String?,
    toRoute: String?,
    fallback: EnterTransition = aospSharedAxisEnter()
): EnterTransition = when (mainRootDirection(fromRoute, toRoute)) {
    MainRootDirection.FORWARD -> {
        slideInHorizontally(
            animationSpec = MAIN_ROOT_TRANSITION_SPEC,
            initialOffsetX = { (it * 0.5f).toInt() }
        ) + fadeIn(animationSpec = MAIN_ROOT_FADE_SPEC)
    }
    MainRootDirection.BACKWARD -> {
        slideInHorizontally(
            animationSpec = MAIN_ROOT_TRANSITION_SPEC,
            initialOffsetX = { -(it * 0.5f).toInt() }
        ) + fadeIn(animationSpec = MAIN_ROOT_FADE_SPEC)
    }
    null -> fallback
}

fun mainRootExitTransition(
    fromRoute: String?,
    toRoute: String?,
    fallback: ExitTransition = aospSharedAxisExit()
): ExitTransition = when (mainRootDirection(fromRoute, toRoute)) {
    MainRootDirection.FORWARD -> {
        slideOutHorizontally(
            animationSpec = MAIN_ROOT_TRANSITION_SPEC,
            targetOffsetX = { -(it * 0.5f).toInt() }
        ) + fadeOut(animationSpec = MAIN_ROOT_FADE_SPEC)
    }
    MainRootDirection.BACKWARD -> {
        slideOutHorizontally(
            animationSpec = MAIN_ROOT_TRANSITION_SPEC,
            targetOffsetX = { (it * 0.5f).toInt() }
        ) + fadeOut(animationSpec = MAIN_ROOT_FADE_SPEC)
    }
    null -> fallback
}
