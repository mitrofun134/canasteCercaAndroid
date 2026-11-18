package ar.canastacerca.feature.location.impl.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.canastacerca.core.common.AppError
import ar.canastacerca.core.common.AppResult
import ar.canastacerca.feature.location.api.Address
import ar.canastacerca.feature.location.api.LocationRepository
import ar.canastacerca.feature.location.impl.data.GeocodingRepository
import ar.canastacerca.feature.location.impl.data.Zone
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class AddressPickerUiState(
    val query: String = "",
    val zone: Zone = Zone.Both,
    val currentAddress: Address? = null,
    val isSearching: Boolean = false,
    val results: List<Address> = emptyList(),
    /** Поиск по текущему запросу уже завершился (чтобы показать «не нашли»). */
    val searched: Boolean = false,
    val error: AppError? = null,
    /** Адрес сохранён — экран закрывается. */
    val done: Boolean = false,
) {
    val queryTooShort: Boolean get() = query.trim().length < MIN_QUERY

    companion object {
        const val MIN_QUERY = 3
    }
}

sealed interface AddressPickerAction {
    data class QueryChanged(val text: String) : AddressPickerAction
    data class ZoneChanged(val zone: Zone) : AddressPickerAction
    data class Select(val address: Address) : AddressPickerAction
    data object Retry : AddressPickerAction
}

@Inject
@ViewModelKey(AddressPickerViewModel::class)
@ContributesIntoMap(AppScope::class)
class AddressPickerViewModel(
    private val geocoding: GeocodingRepository,
    private val location: LocationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddressPickerUiState())
    val state: StateFlow<AddressPickerUiState> = _state.asStateFlow()

    private data class SearchRequest(val query: String, val zone: Zone, val attempt: Int = 0)

    private val requests = MutableStateFlow(SearchRequest("", Zone.Both))

    init {
        viewModelScope.launch {
            location.address.collect { address -> _state.update { it.copy(currentAddress = address) } }
        }
        observeSearch()
    }

    fun onAction(action: AddressPickerAction) {
        when (action) {
            is AddressPickerAction.QueryChanged -> {
                _state.update { it.copy(query = action.text, searched = false) }
                requests.update { it.copy(query = action.text) }
            }
            is AddressPickerAction.ZoneChanged -> {
                _state.update { it.copy(zone = action.zone, searched = false) }
                requests.update { it.copy(zone = action.zone) }
            }
            is AddressPickerAction.Select -> viewModelScope.launch {
                location.setAddress(action.address)
                _state.update { it.copy(done = true) }
            }
            AddressPickerAction.Retry -> requests.update { it.copy(attempt = it.attempt + 1) }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeSearch() {
        viewModelScope.launch {
            requests
                .debounce(SEARCH_DEBOUNCE_MS)
                .collectLatest { request -> search(request) }
        }
    }

    private suspend fun search(request: SearchRequest) {
        val query = request.query.trim()
        if (query.length < AddressPickerUiState.MIN_QUERY) {
            _state.update { it.copy(isSearching = false, results = emptyList(), error = null, searched = false) }
            return
        }
        _state.update { it.copy(isSearching = true, error = null) }
        when (val result = geocoding.search(query, request.zone)) {
            is AppResult.Success -> _state.update {
                it.copy(isSearching = false, results = result.value, searched = true)
            }
            is AppResult.Failure -> _state.update {
                it.copy(isSearching = false, error = result.error)
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 400L
    }
}
