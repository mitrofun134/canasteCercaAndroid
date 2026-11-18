package ar.canastacerca.feature.location.impl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.canastacerca.core.designsystem.component.CanastaTopBar
import ar.canastacerca.core.designsystem.component.InlineError
import ar.canastacerca.feature.location.api.Address
import ar.canastacerca.feature.location.impl.R
import ar.canastacerca.feature.location.impl.data.Zone
import dev.zacsweers.metrox.viewmodel.metroViewModel
import ar.canastacerca.core.designsystem.R as DesignR

@Composable
fun AddressPickerRoute(
    onBack: () -> Unit,
    viewModel: AddressPickerViewModel = metroViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) {
        if (state.done) onBack()
    }
    AddressPickerScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

@Composable
fun AddressPickerScreen(
    state: AddressPickerUiState,
    onAction: (AddressPickerAction) -> Unit,
    onBack: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = { CanastaTopBar(title = stringResource(R.string.location_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.currentAddress?.let {
                    Text(
                        stringResource(R.string.location_current, it.label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { onAction(AddressPickerAction.QueryChanged(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text(stringResource(R.string.location_hint)) },
                    leadingIcon = { Icon(painterResource(DesignR.drawable.ic_search), contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Search,
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Zone.entries.forEach { zone ->
                        FilterChip(
                            selected = state.zone == zone,
                            onClick = { onAction(AddressPickerAction.ZoneChanged(zone)) },
                            label = { Text(zone.label()) },
                        )
                    }
                }
                if (state.isSearching) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                state.error?.let { error ->
                    InlineError(error, onRetry = { onAction(AddressPickerAction.Retry) })
                }
                when {
                    state.query.isNotEmpty() && state.queryTooShort -> HintText(stringResource(R.string.location_min_chars))
                    state.searched && state.results.isEmpty() && state.error == null ->
                        HintText(stringResource(R.string.location_no_results))
                }
            }

            LazyColumn(Modifier.fillMaxSize()) {
                items(state.results, key = { "${it.label}|${it.lat}|${it.lng}" }) { address ->
                    AddressRow(address, onClick = { onAction(AddressPickerAction.Select(address)) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun AddressRow(address: Address, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(address.label) },
        leadingContent = { Icon(painterResource(DesignR.drawable.ic_location), contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Zone.label(): String = when (this) {
    Zone.Both -> stringResource(R.string.location_zone_both)
    Zone.Caba -> stringResource(R.string.location_zone_caba)
    Zone.Gba -> stringResource(R.string.location_zone_gba)
}
