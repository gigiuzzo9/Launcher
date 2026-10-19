package com.simplylauncher

/**
 * Rappresenta un'icona posizionata nella griglia della home.
 *
 * @param packageName package dell'app
 * @param row riga nella griglia (0-based)
 * @param col colonna nella griglia (0-based)
 */
data class HomeSlot(
    val packageName: String,
    val row: Int,
    val col: Int
) {
    companion object {
        /**
         * Serializza una lista di HomeSlot in formato stringa.
         * Formato: "pkg:row:col|pkg:row:col|..."
         * Zero dipendenze, zero JSON.
         */
        fun listToString(slots: List<HomeSlot>): String {
            return slots.joinToString("|") { "${it.packageName}:${it.row}:${it.col}" }
        }

        /**
         * Deserializza una stringa in lista di HomeSlot.
         * Ignora entry malformate (sicurezza).
         */
        fun stringToList(raw: String): List<HomeSlot> {
            if (raw.isBlank()) return emptyList()
            return raw.split("|").mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size != 3) return@mapNotNull null
                val pkg = parts[0]
                val row = parts[1].toIntOrNull() ?: return@mapNotNull null
                val col = parts[2].toIntOrNull() ?: return@mapNotNull null
                HomeSlot(pkg, row, col)
            }
        }

        /**
         * Migra una vecchia lista lineare (List<String>) in HomeSlot
         * assegnando posizioni progressive in una griglia con [cols] colonne.
         */
        fun migrateFromLinear(items: List<String>, cols: Int): List<HomeSlot> {
            return items.mapIndexed { index, pkg ->
                HomeSlot(
                    packageName = pkg,
                    row = index / cols,
                    col = index % cols
                )
            }
        }
    }
}
