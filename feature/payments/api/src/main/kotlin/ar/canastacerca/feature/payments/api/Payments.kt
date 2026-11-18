package ar.canastacerca.feature.payments.api

import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** Экран выбора способов оплаты (банки через MODO, Mercado Pago, карты магазинов). */
@Serializable
data object PaymentsKey : NavKey

/** Какие способы оплаты есть у пользователя. Корзина отправляет их id на сервер для расчёта скидок. */
interface PaymentsRepository {
    val selected: Flow<Set<String>>

    suspend fun setSelected(ids: Set<String>)
    suspend fun toggle(id: String)
}
