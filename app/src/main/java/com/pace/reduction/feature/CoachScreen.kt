package com.pace.reduction.feature

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import com.pace.reduction.CoachUiState
import com.pace.reduction.PaceUiState
import com.pace.reduction.PaceViewModel
import com.pace.reduction.R
import com.pace.reduction.domain.CoachImageMemory
import com.pace.reduction.domain.model.CoachMessage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CoachScreen(
    uiState: PaceUiState,
    viewModel: PaceViewModel,
    onOpenSettings: () -> Unit,
    onStartCall: () -> Unit,
) {
    val coach by viewModel.coachState.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    var showBehaviourSheet by rememberSaveable { mutableStateOf(false) }
    var pendingImage by remember { mutableStateOf<ByteArray?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                pendingImage = readImageBytes(context, uri)
            }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val file = cameraFile
        if (captured && file != null) {
            scope.launch {
                try {
                    pendingImage = readImageBytes(context, Uri.fromFile(file))
                } finally {
                    withContext(Dispatchers.IO) { file.delete() }
                    if (cameraFile == file) cameraFile = null
                }
            }
        } else {
            file?.delete()
            cameraFile = null
        }
    }
    DisposableEffect(cameraFile) {
        val transientFile = cameraFile
        onDispose { transientFile?.delete() }
    }
    val listState = rememberLazyListState()
    val messages = uiState.coachMessages

    LaunchedEffect(messages.size, coach.streamingReply) {
        val target = messages.size + if (coach.busy) 1 else 0
        if (target > 0) listState.animateScrollToItem(target)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PaceTopBar(
            title = stringResource(R.string.coach_title),
            listState = listState,
            actions = {
                IconButton(onClick = onStartCall, enabled = uiState.ai.isReady) {
                    Icon(Icons.Outlined.PhoneInTalk, contentDescription = stringResource(R.string.coach_call))
                }
                if (messages.isNotEmpty()) {
                    IconButton(onClick = {
                        pendingImage = null
                        cameraFile?.delete()
                        cameraFile = null
                        viewModel.clearCoach()
                    }) {
                        Icon(
                            Icons.Outlined.DeleteSweep,
                            contentDescription = stringResource(R.string.coach_clear),
                        )
                    }
                }
                IconButton(onClick = { showBehaviourSheet = true }) {
                    Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.coach_settings))
                }
            },
        )

        if (showBehaviourSheet) {
            CoachSettingsSheet(
                ai = uiState.ai,
                viewModel = viewModel,
                onDismiss = { showBehaviourSheet = false },
            )
        }

        if (!uiState.ai.isReady) {
            CoachSetupPrompt(onOpenSettings)
            return@Column
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty() && !coach.busy) {
                item { CoachEmptyState(onStarter = { draft = it }) }
            }
            items(messages, key = CoachMessage::id) { message ->
                ChatBubble(
                    text = CoachImageMemory.displayContent(message.content),
                    fromUser = message.isUser,
                    hasImage = message.isUser && CoachImageMemory.hasImage(message.content),
                )
            }
            if (coach.busy) {
                item {
                    if (coach.streamingReply.isBlank()) {
                        ThinkingBubble()
                    } else {
                        ChatBubble(text = coach.streamingReply, fromUser = false)
                    }
                }
            }
            coach.error?.let { error ->
                item { ErrorBubble(error, onDismiss = viewModel::dismissCoachError) }
            }
        }

        CoachComposer(
            draft = draft,
            coach = coach,
            onDraftChange = { draft = it },
            onSend = {
                viewModel.sendCoachMessage(draft, pendingImage)
                draft = ""
                pendingImage = null
            },
            onStop = viewModel::stopCoach,
            onRiddle = viewModel::requestRiddle,
            imageAttached = pendingImage != null,
            onChoosePhoto = {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onTakePhoto = {
                cameraFile?.delete()
                val directory = File(context.cacheDir, "coach-images").apply { mkdirs() }
                val file = File(directory, "coach-${System.currentTimeMillis()}.jpg")
                cameraFile = file
                camera.launch(
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
                )
            },
            onRemovePhoto = { pendingImage = null },
        )
    }
}

@Composable
private fun CoachSetupPrompt(onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(36.dp))
                Text(stringResource(R.string.coach_setup_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.coach_setup_body),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onOpenSettings) { Text(stringResource(R.string.coach_open_settings)) }
            }
        }
    }
}

@Composable
private fun CoachEmptyState(onStarter: (String) -> Unit) {
    val starters = listOf(
        stringResource(R.string.coach_starter_urge),
        stringResource(R.string.coach_starter_bored),
        stringResource(R.string.coach_starter_why),
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            Icons.Outlined.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(stringResource(R.string.coach_empty_title), style = MaterialTheme.typography.titleLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            starters.forEach { starter ->
                AssistChip(onClick = { onStarter(starter) }, label = { Text(starter) })
            }
        }
    }
}

@Composable
private fun ChatBubble(text: String, fromUser: Boolean, hasImage: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (fromUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (fromUser) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (fromUser) 18.dp else 4.dp,
                bottomEnd = if (fromUser) 4.dp else 18.dp,
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (hasImage) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Outlined.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(stringResource(R.string.coach_photo), style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (text.isNotBlank()) Text(text = text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            stringResource(R.string.coach_thinking),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorBubble(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Stop, contentDescription = stringResource(R.string.dismiss))
            }
        }
    }
}

@Composable
private fun CoachComposer(
    draft: String,
    coach: CoachUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onRiddle: () -> Unit,
    imageAttached: Boolean,
    onChoosePhoto: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The app shell already keeps this clear of the navigation bar; only the keyboard
                // is left to make room for.
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (imageAttached) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                        Text(
                            stringResource(R.string.coach_photo_attached),
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        IconButton(onClick = onRemovePhoto) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.coach_remove_photo),
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { onDraftChange(it.take(500)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.coach_input_hint)) },
                minLines = 1,
                maxLines = 4,
                shape = RoundedCornerShape(18.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onChoosePhoto, enabled = !coach.busy) {
                    Icon(
                        Icons.Outlined.AddPhotoAlternate,
                        contentDescription = stringResource(R.string.coach_choose_photo),
                    )
                }
                FilledTonalIconButton(onClick = onTakePhoto, enabled = !coach.busy) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = stringResource(R.string.coach_take_photo))
                }
                TextButton(onClick = onRiddle, enabled = !coach.busy) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                    Text(stringResource(R.string.coach_riddle))
                }
                Spacer(Modifier.weight(1f))
                FilledIconButton(
                    onClick = if (coach.busy) onStop else onSend,
                    enabled = coach.busy || draft.isNotBlank() || imageAttached,
                ) {
                    Icon(
                        imageVector = if (coach.busy) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.Send,
                        contentDescription = stringResource(
                            if (coach.busy) R.string.coach_stop else R.string.coach_send,
                        ),
                    )
                }
            }
        }
    }
}

private suspend fun readImageBytes(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        stream.readUpTo(MAX_PICKED_IMAGE_BYTES + 1)
    }
}

private fun InputStream.readUpTo(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    val buffer = ByteArray(8 * 1024)
    var remaining = maxBytes
    while (remaining > 0) {
        val read = read(buffer, 0, minOf(buffer.size, remaining))
        if (read < 0) break
        output.write(buffer, 0, read)
        remaining -= read
    }
    return output.toByteArray()
}

private const val MAX_PICKED_IMAGE_BYTES = 20 * 1024 * 1024
