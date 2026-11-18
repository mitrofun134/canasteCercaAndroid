package ar.canastacerca.feature.recipes.impl.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.canastacerca.core.common.AppError
import ar.canastacerca.core.common.AppResult
import ar.canastacerca.core.designsystem.component.UiMessage
import ar.canastacerca.core.model.ItemScope
import ar.canastacerca.feature.basket.api.BasketItem
import ar.canastacerca.feature.basket.api.BasketRepository
import ar.canastacerca.feature.recipes.api.RecipeKey
import ar.canastacerca.feature.recipes.impl.R
import ar.canastacerca.feature.recipes.impl.data.Dish
import ar.canastacerca.feature.recipes.impl.data.DishOption
import ar.canastacerca.feature.recipes.impl.data.RecipesRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class RecipeUiState(
    val title: String = "",
    val isLoading: Boolean = true,
    val dish: Dish? = null,
    val selectedChain: String? = null,
    val notFound: Boolean = false,
    val isAdding: Boolean = false,
    val error: AppError? = null,
    val message: UiMessage? = null,
) {
    val selectedOption: DishOption?
        get() = dish?.options?.firstOrNull { it.chainKey == selectedChain } ?: dish?.best
}

sealed interface RecipeAction {
    data class SelectChain(val chainKey: String) : RecipeAction
    data object AddToBasket : RecipeAction
    data object Retry : RecipeAction
    data class MessageShown(val id: Long) : RecipeAction
}

@AssistedInject
class RecipeViewModel(
    @Assisted private val key: RecipeKey,
    private val repository: RecipesRepository,
    private val basketRepository: BasketRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RecipeUiState(title = key.title))
    val state: StateFlow<RecipeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onAction(action: RecipeAction) {
        when (action) {
            is RecipeAction.SelectChain -> _state.update { it.copy(selectedChain = action.chainKey) }
            RecipeAction.AddToBasket -> addToBasket()
            RecipeAction.Retry -> load()
            is RecipeAction.MessageShown -> _state.update {
                if (it.message?.id == action.id) it.copy(message = null) else it
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.dish(key.dishId)) {
                is AppResult.Success -> _state.update {
                    val dish = result.value
                    it.copy(
                        isLoading = false,
                        dish = dish,
                        title = dish?.name ?: it.title,
                        notFound = dish == null,
                        selectedChain = it.selectedChain ?: dish?.best?.chainKey,
                    )
                }
                is AppResult.Failure -> _state.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }

    /** Кладём все ингредиенты (gen:…): корзина сама сравнит их цену во всех магазинах рядом. */
    private fun addToBasket() {
        val option = _state.value.selectedOption ?: return
        viewModelScope.launch {
            _state.update { it.copy(isAdding = true) }
            val added = basketRepository.addAll(
                option.ingredients.map { ingredient ->
                    BasketItem(
                        id = ingredient.id,
                        name = ingredient.name,
                        imageUrl = ingredient.imageUrl,
                        quantity = 1,
                        scope = ItemScope.Super,
                    )
                },
            )
            val message = if (added > 0) {
                UiMessage(R.string.recipes_added, listOf(added))
            } else {
                UiMessage(R.string.recipes_already)
            }
            _state.update { it.copy(isAdding = false, message = message) }
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(key: RecipeKey): RecipeViewModel
    }
}
