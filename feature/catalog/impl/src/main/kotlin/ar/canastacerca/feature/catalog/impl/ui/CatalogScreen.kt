package ar.canastacerca.feature.catalog.impl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.canastacerca.core.designsystem.component.CanastaTopBar
import ar.canastacerca.core.designsystem.component.ErrorState
import ar.canastacerca.core.designsystem.component.LoadingState
import ar.canastacerca.feature.catalog.api.CategoryKey
import ar.canastacerca.feature.catalog.impl.R
import ar.canastacerca.feature.catalog.impl.data.CatalogGroup
import dev.zacsweers.metrox.viewmodel.metroViewModel
import ar.canastacerca.core.designsystem.R as DesignR

@Composable
fun CatalogRoute(
    onOpenCategory: (CategoryKey) -> Unit,
    viewModel: CatalogViewModel = metroViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CatalogScreen(state = state, onAction = viewModel::onAction, onOpenCategory = onOpenCategory)
}

@Composable
fun CatalogScreen(
    state: CatalogUiState,
    onAction: (CatalogAction) -> Unit,
    onOpenCategory: (CategoryKey) -> Unit,
) {
    Scaffold(
        topBar = { CanastaTopBar(title = stringResource(R.string.catalog_title)) },
    ) { padding ->
        val modifier = Modifier
            .padding(padding)
            .fillMaxSize()
        when {
            state.isLoading && state.groups.isEmpty() -> LoadingState(modifier)
            state.error != null && state.groups.isEmpty() ->
                ErrorState(state.error, onRetry = { onAction(CatalogAction.Retry) }, modifier = modifier)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "all") {
                    val title = stringResource(R.string.catalog_all)
                    AllCard(onClick = { onOpenCategory(CategoryKey(CategoryKey.ALL, title)) })
                }
                items(state.groups, key = { it.id }) { group ->
                    GroupCard(group = group, onOpenCategory = onOpenCategory)
                }
            }
        }
    }
}

@Composable
private fun AllCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.catalog_all), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.catalog_all_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Icon(painterResource(DesignR.drawable.ic_chevron_right), contentDescription = null)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupCard(group: CatalogGroup, onOpenCategory: (CategoryKey) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                group.icon?.let {
                    Text(it, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(8.dp))
                }
                Text(group.name, style = MaterialTheme.typography.titleMedium)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                group.categories.forEach { category ->
                    SuggestionChip(
                        onClick = { onOpenCategory(CategoryKey(category.id, category.name)) },
                        label = { Text(category.name) },
                    )
                }
            }
        }
    }
}
