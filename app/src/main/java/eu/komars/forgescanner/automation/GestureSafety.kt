package eu.komars.forgescanner.automation

import java.util.UUID
import kotlin.math.abs

/** An opaque grant for one user-started scan, expiring after inactivity. It is never persisted. */
data class GestureSession internal constructor(internal val token: String, val width: Int, val height: Int)

/** Pure bounds/lifetime checks shared by every injected action. */
internal class GestureSafety {
    private var active: GestureSession? = null
    private var lastActionAt = 0L
    private var validatedAt = 0L
    private var actions = 0

    fun arm(width: Int, height: Int, now: Long): GestureSession {
        require(width in 100..10000 && height in 100..16000 && now >= 0) { "Nieprawidłowy rozmiar ekranu." }
        return GestureSession(UUID.randomUUID().toString(), width, height).also {
            active = it; lastActionAt = now; validatedAt = now; actions = 0
        }
    }

    fun validate(session: GestureSession, now: Long, vararg coordinates: Float) {
        require(active === session && now >= lastActionAt && now - lastActionAt <= 5 * 60_000 && actions < 2000) {
            "Sesja sterowania została zakończona lub przez 5 minut nie było gestów. Ponów skan; nie trzeba restartować gry."
        }
        require(coordinates.size % 2 == 0 && coordinates.all(Float::isFinite)) { "Nieprawidłowy punkt gestu." }
        coordinates.toList().chunked(2).forEach { (x, y) ->
            require(x >= 1 && x < session.width - 1 && y >= 1 && y < session.height - 1) {
                "Punkt gestu wychodzi poza skonfigurowany ekran."
            }
        }
        validatedAt=now
    }

    fun drag(session: GestureSession, fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long, now: Long) {
        validate(session, now, fromX, fromY, toX, toY)
        require(durationMs in 80..1000 && abs(toY - fromY) <= session.height * .35f &&
            abs(toX - fromX) <= session.width * .15f) { "Gest przewijania jest zbyt długi lub zbyt szybki." }
    }

    fun finishHold(session: GestureSession, durationMs: Long, now: Long) {
        validate(session, now)
        require(durationMs in 80..500) { "Nieprawidłowy czas zakończenia gestu." }
    }

    fun actionStarted() { actions++;lastActionAt=validatedAt }
    fun revoke() { active = null }
}
