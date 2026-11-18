package ar.canastacerca.core.model

/** Сеть супермаркетов или аптек — те же ключи, что в lib/vtex.js сервера. */
data class Chain(
    val key: String,
    val name: String,
    val kind: ChainKind,
    /** Цвет кружка-метки сети в интерфейсе (ARGB). */
    val color: Long,
)

enum class ChainKind { Supermarket, Pharmacy }

object Chains {
    val all: List<Chain> = listOf(
        Chain("jumbo", "Jumbo", ChainKind.Supermarket, 0xFF1B9E4B),
        Chain("disco", "Disco", ChainKind.Supermarket, 0xFFD7262C),
        Chain("vea", "Vea", ChainKind.Supermarket, 0xFFE4002B),
        Chain("carrefour", "Carrefour", ChainKind.Supermarket, 0xFF1E4FA3),
        Chain("dia", "Día", ChainKind.Supermarket, 0xFFE2001A),
        Chain("changomas", "ChangoMás", ChainKind.Supermarket, 0xFF00843D),
        Chain("coto", "Coto", ChainKind.Supermarket, 0xFFC8102E),
        Chain("farmacity", "Farmacity", ChainKind.Pharmacy, 0xFF0072BC),
        Chain("farmaciasdelpueblo", "Farmacias del Pueblo", ChainKind.Pharmacy, 0xFF2E7D32),
        Chain("farmaplus", "Farmaplus", ChainKind.Pharmacy, 0xFF6A1B9A),
    )

    private val byKey = all.associateBy { it.key }

    fun byKey(key: String): Chain? = byKey[key]

    fun nameOf(key: String): String = byKey[key]?.name ?: key.replaceFirstChar { it.uppercase() }

    /** Цвет для сети, которой нет в списке (например, филиал SEPA с неизвестной сетью). */
    const val FALLBACK_COLOR: Long = 0xFF5B6B80

    fun colorOf(key: String?): Long = key?.let { byKey[it]?.color } ?: FALLBACK_COLOR

    /** Филиалы из SEPA приходят с названием сети («Día», «Jumbo»), а не с ключом. */
    fun keyForName(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val n = name.lowercase()
        return all.firstOrNull { n.contains(it.name.lowercase()) || n.contains(it.key) }?.key
    }
}
