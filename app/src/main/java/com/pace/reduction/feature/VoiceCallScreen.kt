package com.pace.reduction.feature

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pace.reduction.PaceViewModel
import com.pace.reduction.R
import com.pace.reduction.core.designsystem.LocalMotion
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoiceCallScreen(
    viewModel: PaceViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val voice by viewModel.voiceCallState.collectAsStateWithLifecycle()
    val microphonePermissionDenied = stringResource(R.string.call_microphone_permission_denied)
    val speechUnavailable = stringResource(R.string.call_speech_unavailable)
    val didNotCatch = stringResource(R.string.call_didnt_catch)
    val recognitionNetworkError = stringResource(R.string.call_recognition_network_error)
    val recognitionError = stringResource(R.string.call_recognition_error)
    val ttsError = stringResource(R.string.call_tts_error)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var listening by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var deferredStart by remember { mutableStateOf(false) }
    var startRequest by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (granted && deferredStart) startRequest++
        if (!granted) localError = microphonePermissionDenied
        deferredStart = false
    }

    DisposableEffect(
        context,
        speechUnavailable,
        didNotCatch,
        microphonePermissionDenied,
        recognitionNetworkError,
        recognitionError,
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            localError = speechUnavailable
            onDispose { }
        } else {
            val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
                    localError = null
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { listening = false }

                override fun onError(error: Int) {
                    listening = false
                    localError = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                        -> didNotCatch
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            microphonePermissionDenied
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                        -> recognitionNetworkError
                        else -> recognitionError
                    }
                }

                override fun onResults(results: Bundle?) {
                    listening = false
                    val transcript = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (transcript.isBlank()) {
                        localError = didNotCatch
                    } else {
                        localError = null
                        viewModel.sendVoiceMessage(transcript)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            recognizer = speechRecognizer
            onDispose {
                speechRecognizer.cancel()
                speechRecognizer.destroy()
                recognizer = null
            }
        }
    }

    DisposableEffect(context, ttsError) {
        val engine = TextToSpeech(context) { status ->
            mainHandler.post {
                ttsReady = status == TextToSpeech.SUCCESS
                if (!ttsReady) localError = ttsError
            }
        }
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                mainHandler.post { speaking = true }
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post { speaking = false }
            }

            @Deprecated("Deprecated by Android")
            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    speaking = false
                    localError = ttsError
                }
            }
        })
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            tts = null
        }
    }

    DisposableEffect(viewModel) {
        onDispose { viewModel.stopVoiceCall() }
    }

    fun recognitionIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    LaunchedEffect(startRequest) {
        if (startRequest > 0 && !voice.thinking && !speaking) {
            runCatching {
                localError = null
                listening = true
                recognizer?.startListening(recognitionIntent())
            }.onFailure {
                listening = false
                localError = speechUnavailable
            }
        }
    }

    LaunchedEffect(voice.replyId, ttsReady) {
        val reply = voice.replyToSpeak
        val engine = tts
        if (reply.isNotBlank() && ttsReady && engine != null) {
            engine.language = Locale.getDefault()
            speaking = true
            val result = engine.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "pace-${voice.replyId}")
            if (result == TextToSpeech.SUCCESS) {
                viewModel.consumeVoiceReply()
            } else {
                speaking = false
                localError = ttsError
            }
        }
    }

    val phase = when {
        listening -> CallPhase.LISTENING
        voice.thinking -> CallPhase.THINKING
        speaking -> CallPhase.SPEAKING
        else -> CallPhase.IDLE
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.call_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            VoiceOrb(phase)
            Spacer(Modifier.size(32.dp))
            Text(
                stringResource(
                    when (phase) {
                        CallPhase.IDLE -> R.string.call_tap_to_speak
                        CallPhase.LISTENING -> R.string.call_listening
                        CallPhase.THINKING -> R.string.call_thinking
                        CallPhase.SPEAKING -> R.string.call_speaking
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.call_no_transcript),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            val error = voice.error ?: localError
            if (error != null) {
                Card(
                    modifier = Modifier.padding(top = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    onClick = {
                        localError = null
                        viewModel.dismissVoiceError()
                    },
                ) {
                    Text(
                        error,
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.size(34.dp))
            IconButton(
                onClick = {
                    when {
                        listening -> recognizer?.stopListening()
                        !permissionGranted -> {
                            deferredStart = true
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        else -> startRequest++
                    }
                },
                enabled = !voice.thinking && !speaking && recognizer != null,
                modifier = Modifier
                    .size(78.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            ) {
                Icon(
                    if (listening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                    contentDescription = stringResource(
                        if (listening) R.string.call_stop_listening else R.string.call_start_listening,
                    ),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }
}

@Composable
private fun VoiceOrb(phase: CallPhase) {
    val active = phase != CallPhase.IDLE
    val motion = LocalMotion.current
    val transition = rememberInfiniteTransition(label = "voiceOrb")
    val pulse by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1_250), RepeatMode.Reverse),
        label = "voicePulse",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(tween(1_250), RepeatMode.Reverse),
        label = "voiceAlpha",
    )
    val accent = when (phase) {
        CallPhase.IDLE -> MaterialTheme.colorScheme.secondary
        CallPhase.LISTENING -> MaterialTheme.colorScheme.primary
        CallPhase.THINKING -> MaterialTheme.colorScheme.tertiary
        CallPhase.SPEAKING -> MaterialTheme.colorScheme.primary
    }
    Box(modifier = Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier
                .size(205.dp)
                .scale(if (active && motion.enabled) pulse else 1f)
                .alpha(if (active) alpha else 0.12f),
            shape = CircleShape,
            color = accent,
            content = {},
        )
        Surface(
            modifier = Modifier.size(142.dp),
            shape = CircleShape,
            color = accent.copy(alpha = 0.18f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (phase == CallPhase.IDLE) Icons.Outlined.MicNone else Icons.Outlined.Mic,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
    }
}

private enum class CallPhase { IDLE, LISTENING, THINKING, SPEAKING }
