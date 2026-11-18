package ar.canastacerca.feature.payments.impl.data

import ar.canastacerca.core.common.AppDispatchers
import ar.canastacerca.core.common.AppResult
import ar.canastacerca.core.common.map
import ar.canastacerca.core.network.safeApiCall
import ar.canastacerca.core.storage.KeyValueStore
import ar.canastacerca.feature.payments.api.PaymentsRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Способ оплаты, для которого у сервера есть хотя бы одна проверенная скидка. */
data class PaymentMethod(
    val id: String,
    val name: String,
    val via: String?,
    val kind: PaymentKind,
)

enum class PaymentKind { Wallet, Bank, Card, Store, Other }

/** Список способов оплаты: GET /api/promos → payments. */
interface PaymentMethodsRepository {
    suspend fun methods(): AppResult<List<PaymentMethod>>
}

@Inject
@ContributesBinding(AppScope::class)
class DefaultPaymentMethodsRepository(
    private val client: HttpClient,
    private val dispatchers: AppDispatchers,
) : PaymentMethodsRepository {

    override suspend fun methods(): AppResult<List<PaymentMethod>> = withContext(dispatchers.io) {
        safeApiCall { client.get("api/promos").body<PromosDto>() }
            .map { dto ->
                dto.payments.distinctBy { it.id }.map { p ->
                    PaymentMethod(
                        id = p.id,
                        name = p.name ?: p.short ?: p.id,
                        via = p.via,
                        kind = when (p.kind) {
                            "wallet" -> PaymentKind.Wallet
                            "bank" -> PaymentKind.Bank
                            "card" -> PaymentKind.Card
                            "store" -> PaymentKind.Store
                            else -> PaymentKind.Other
                        },
                    )
                }
            }
    }
}

@Serializable
private data class PromosDto(val payments: List<PaymentDto> = emptyList())

@Serializable
private data class PaymentDto(
    val id: String,
    val name: String? = null,
    val short: String? = null,
    val via: String? = null,
    val kind: String? = null,
)

/** Выбранные способы оплаты: хранятся локально, сервер получает их id вместе с корзиной. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultPaymentsRepository(
    private val store: KeyValueStore,
) : PaymentsRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(String.serializer())

    override val selected: Flow<Set<String>> = store.observe(KEY).map(::decode)

    override suspend fun setSelected(ids: Set<String>) {
        store.put(KEY, json.encodeToString(serializer, ids.sorted()))
    }

    override suspend fun toggle(id: String) {
        store.update(KEY) { raw ->
            val current = decode(raw)
            val next = if (id in current) current - id else current + id
            json.encodeToString(serializer, next.sorted())
        }
    }

    private fun decode(raw: String?): Set<String> =
        raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }?.toSet().orEmpty()

    private companion object {
        const val KEY = "payments.selected"
    }
}
