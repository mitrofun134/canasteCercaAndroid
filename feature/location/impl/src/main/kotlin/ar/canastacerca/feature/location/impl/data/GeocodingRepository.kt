package ar.canastacerca.feature.location.impl.data

import ar.canastacerca.core.common.AppDispatchers
import ar.canastacerca.core.common.AppResult
import ar.canastacerca.core.common.map
import ar.canastacerca.core.network.safeApiCall
import ar.canastacerca.feature.location.api.Address
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** Где искать адрес: Georef ищет по провинциям (lib/geo.js). */
enum class Zone(val wire: String) { Both("ambos"), Caba("caba"), Gba("gba") }

/** Адрес → координаты: GET /api/geocode (Georef, при неудаче — Nominatim). */
interface GeocodingRepository {
    suspend fun search(query: String, zone: Zone): AppResult<List<Address>>
}

@Inject
@ContributesBinding(AppScope::class)
class DefaultGeocodingRepository(
    private val client: HttpClient,
    private val dispatchers: AppDispatchers,
) : GeocodingRepository {

    override suspend fun search(query: String, zone: Zone): AppResult<List<Address>> = withContext(dispatchers.io) {
        safeApiCall {
            client.get("api/geocode") {
                parameter("q", query)
                parameter("zona", zone.wire)
            }.body<GeocodeResponseDto>()
        }.map { response ->
            response.results
                .filter { it.lat != null && it.lng != null }
                .map { Address(label = it.label, lat = it.lat!!, lng = it.lng!!) }
                .distinctBy { it.label }
        }
    }
}

@Serializable
private data class GeocodeResponseDto(val results: List<GeocodeResultDto> = emptyList())

@Serializable
private data class GeocodeResultDto(
    val label: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val source: String? = null,
)
