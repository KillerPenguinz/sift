package com.ironclinicgym.sift.ui.board

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor
import kotlinx.coroutines.delay

/** Mutable holder so the SpeechRecognizer can be created lazily on first use. */
private class RecognizerHolder {
    var value: SpeechRecognizer? = null
}

/**
 * Inline mic button that drives Android's [SpeechRecognizer] directly, so voice input
 * streams into the target field in place rather than opening the full-screen Google
 * speech UI.
 *
 * [currentText] is read once, right when recording starts, to snapshot the field's
 * existing content (the "base text"). Every partial and final transcript then replaces
 * the field with `base + " " + transcript` via [onTextChange], so partial results never
 * get appended on top of each other (no "buy" then "buy buy milk" duplication).
 *
 * The recognizer itself is created lazily on the first tap, not on composition, so
 * mounting several MicButtons does not bind speech service connections that may never
 * be used. Hard recognition failures briefly tint the mic with the overdue token as a
 * quiet error signal; transient outcomes (no match, silence timeout) reset silently.
 *
 * Hides itself entirely (renders nothing) if [SpeechRecognizer.isRecognitionAvailable]
 * is false for this device.
 */
@Composable
fun MicButton(
    currentText: () -> String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    size: TextUnit = 20.sp,
) {
    val context = LocalContext.current
    val tokens = SiftTheme.tokens

    val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    if (!available) return

    var isListening by remember { mutableStateOf(false) }
    var hadError by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var baseText by remember { mutableStateOf("") }

    val currentTextState = rememberUpdatedState(currentText)
    val onTextChangeState = rememberUpdatedState(onTextChange)

    val holder = remember { RecognizerHolder() }

    DisposableEffect(Unit) {
        onDispose {
            holder.value?.destroy()
            holder.value = null
        }
    }

    LaunchedEffect(hadError) {
        if (hadError) {
            delay(1500)
            hadError = false
        }
    }

    fun applyTranscript(spoken: String) {
        val combined = if (baseText.isBlank()) spoken else "$baseText $spoken"
        onTextChangeState.value(combined)
    }

    fun startListening() {
        val recognizer = holder.value
            ?: SpeechRecognizer.createSpeechRecognizer(context).also { holder.value = it }
        baseText = currentTextState.value()
        hadError = false
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) {
                isListening = false
                when (error) {
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_AUDIO,
                    SpeechRecognizer.ERROR_CLIENT,
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                    -> hadError = true
                    else -> Unit // no match, silence timeout, busy: reset quietly
                }
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { applyTranscript(it) }
                isListening = false
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                partial?.firstOrNull()?.let { applyTranscript(it) }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer.startListening(intent)
        isListening = true
    }

    fun stopListening() {
        holder.value?.stopListening()
        isListening = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (granted) startListening()
    }

    val micColor = if (isListening || hadError) {
        tokens.overdueText.toColor()
    } else {
        tokens.neutrals.textTertiary.toColor()
    }

    val pulseModifier = if (isListening) {
        val transition = rememberInfiniteTransition(label = "mic_pulse")
        val scale by transition.animateFloat(
            1f, 1.2f,
            infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "pulse",
        )
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
    } else {
        modifier
    }

    MaterialSymbol(
        "mic", micColor, size = size, filled = isListening,
        modifier = pulseModifier.clickable {
            if (isListening) {
                stopListening()
            } else if (!hasPermission) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                startListening()
            }
        },
    )
}
