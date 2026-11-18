package ar.canastacerca.feature.location.api

import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** Экран выбора адреса. После выбора возвращается назад. */
@Serializable
data object AddressPickerKey : NavKey

data class Address(
    val label: String,
    val lat: Double,
    val lng: Double,
)

/** Адрес и радиус поиска магазинов. Хранится между запусками. */
interface LocationRepository {
    val address: Flow<Address?>
    val radiusKm: Flow<Double>

    suspend fun setAddress(address: Address)
    suspend fun setRadius(km: Double)

    companion object {
        const val DEFAULT_RADIUS_KM: Double = 2.0
        val RADIUS_OPTIONS_KM: List<Double> = listOf(1.0, 2.0, 3.0, 5.0)
    }
}
