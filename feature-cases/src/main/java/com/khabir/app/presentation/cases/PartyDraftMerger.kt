package com.khabir.app.presentation.cases

internal object PartyDraftMerger {
    data class Result(val parties: List<PartyDraft>, val conflicts: List<Pair<PartyDraft, PartyDraft>>)
    private fun normalize(value: String) = value.replace(Regex("[\\u064B-\\u065F\\u0670ـ]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي').trim().replace(Regex("\\s+"), " ")
    fun merge(current: List<PartyDraft>, incoming: List<PartyDraft>): Result {
        val parties = current.toMutableList()
        val conflicts = mutableListOf<Pair<PartyDraft, PartyDraft>>()
        incoming.forEach { next ->
            val index = parties.indexOfFirst {
                normalize("${it.firstName} ${it.restName}") == normalize("${next.firstName} ${next.restName}") &&
                    it.role == next.role && it.claimKind == next.claimKind && it.withCapacity == next.withCapacity
            }
            if (index < 0) parties += next else {
                val existing = parties[index]
                when {
                    existing.address.isBlank() -> parties[index] = existing.copy(address = next.address)
                    next.address.isBlank() || normalize(existing.address) == normalize(next.address) -> Unit
                    else -> conflicts += existing to next
                }
            }
        }
        return Result(parties, conflicts.distinctBy { it.first.localId to normalize(it.second.address) })
    }
}
