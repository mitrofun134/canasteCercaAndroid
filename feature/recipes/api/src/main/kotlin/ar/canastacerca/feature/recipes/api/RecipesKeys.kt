package ar.canastacerca.feature.recipes.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Вкладка «Рецепты из сегодняшних скидок». */
@Serializable
data object RecipesKey : NavKey

@Serializable
data class RecipeKey(val dishId: String, val title: String) : NavKey
