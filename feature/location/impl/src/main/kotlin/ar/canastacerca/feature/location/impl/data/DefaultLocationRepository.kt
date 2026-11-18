package ar.canastacerca.feature.location.impl.data

import ar.canastacerca.core.storage.KeyValueStore
import ar.canastacerca.feature.location.api.Address
import ar.canastacerca.feature.location.api.LocationRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultLocationRepository(
    private val store: KeyValueStore,
) : LocationRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override val address: Flow<Address?> = store.observe(ADDRESS_KEY).map { raw ->
        raw?.let { runCatching { json.decodeFromString(StoredAddress.serializer(), it) }.getOrNull() }
            ?.let { Address(it.label, it.lat, it.lng) }
    }

    override val radiusKm: Flow<Double> = store.observe(RADIUS_KEY).map { raw ->
        raw?.toDoubleOrNull() ?: LocationRepository.DEFAULT_RADIUS_KM
    }

    override suspend fun setAddress(address: Address) {
        store.put(ADDRESS_KEY, json.encodeToString(StoredAddress.serializer(), StoredAddress(address.label, address.lat, address.lng)))
    }

    override suspend fun setRadius(km: Double) {
        store.put(RADIUS_KEY, km.toString())
    }

    @Serializable
    private data class StoredAddress(val label: String, val lat: Double, val lng: Double)

    private companion object {
        const val ADDRESS_KEY = "location.address"
        const val RADIUS_KEY = "location.radiusKm"
    }
}
