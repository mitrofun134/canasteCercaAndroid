package ar.canastacerca.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import java.util.concurrent.atomic.AtomicLong

/**
 * Одноразовое сообщение как часть состояния экрана (single state, без отдельного канала событий):
 * экран показывает его и сообщает ViewModel, что показал.
 */
@Immutable
data class UiMessage(
    @param:StringRes val textRes: Int,
    val formatArgs: List<Any> = emptyList(),
    val id: Long = nextId.incrementAndGet(),
) {
    private companion object {
        val nextId = AtomicLong()
    }
}

@Composable
fun MessageEffect(
    message: UiMessage?,
    snackbarHostState: SnackbarHostState,
    onShown: (id: Long) -> Unit,
) {
    val text = message?.let { stringResource(it.textRes, *it.formatArgs.toTypedArray()) }
    val currentOnShown by rememberUpdatedState(onShown)
    LaunchedEffect(message?.id) {
        if (message != null && text != null) {
            snackbarHostState.showSnackbar(text)
            currentOnShown(message.id)
        }
    }
}
