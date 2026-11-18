package ar.canastacerca.feature.payments.impl.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.canastacerca.core.designsystem.component.CanastaTopBar
import ar.canastacerca.core.designsystem.component.EmptyState
import ar.canastacerca.core.designsystem.component.ErrorState
import ar.canastacerca.core.designsystem.component.LoadingState
import ar.canastacerca.feature.payments.impl.R
import ar.canastacerca.feature.payments.impl.data.PaymentKind
import ar.canastacerca.feature.payments.impl.data.PaymentMethod
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun PaymentsRoute(
    onDone: () -> Unit,
    viewModel: PaymentsViewModel = metroViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PaymentsScreen(state = state, onAction = viewModel::onAction, onDone = onDone)
}

@Composable
fun PaymentsScreen(
    state: PaymentsUiState,
    onAction: (PaymentsAction) -> Unit,
    onDone: () -> Unit,
) {
    Scaffold(
        topBar = {
            CanastaTopBar(
                title = stringResource(R.string.payments_title),
                onBack = onDone,
                actions = {
                    TextButton(onClick = onDone) { Text(stringResource(R.string.payments_done)) }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier
            .padding(padding)
            .fillMaxSize()
        when {
            state.isLoading && state.groups.isEmpty() -> LoadingState(modifier)
            state.error != null && state.groups.isEmpty() ->
                ErrorState(state.error, onRetry = { onAction(PaymentsAction.Retry) }, modifier = modifier)
            state.groups.isEmpty() -> EmptyState(title = stringResource(R.string.payments_empty), modifier = modifier)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
            ) {
                item(key = "intro") {
                    Column {
                        Text(
                            text = stringResource(R.string.payments_intro),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.selected.isNotEmpty()) {
                            TextButton(
                                onClick = { onAction(PaymentsAction.ClearAll) },
                                modifier = Modifier.padding(horizontal = 4.dp),
                            ) { Text(stringResource(R.string.payments_clear)) }
                        }
                    }
                }
                state.groups.forEach { group ->
                    item(key = "group-${group.kind}") {
                        Text(
                            text = group.kind.title(),
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(group.methods, key = { it.id }) { method ->
                        PaymentRow(
                            method = method,
                            checked = method.id in state.selected,
                            onToggle = { onAction(PaymentsAction.Toggle(method.id)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentRow(method: PaymentMethod, checked: Boolean, onToggle: () -> Unit) {
    val via = method.via
    ListItem(
        headlineContent = { Text(method.name) },
        supportingContent = if (via != null) {
            { Text(via) }
        } else {
            null
        },
        trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
    )
}

@Composable
private fun PaymentKind.title(): String = when (this) {
    PaymentKind.Wallet -> stringResource(R.string.payments_group_wallet)
    PaymentKind.Bank -> stringResource(R.string.payments_group_bank)
    PaymentKind.Card -> stringResource(R.string.payments_group_card)
    PaymentKind.Store -> stringResource(R.string.payments_group_store)
    PaymentKind.Other -> stringResource(R.string.payments_group_other)
}
