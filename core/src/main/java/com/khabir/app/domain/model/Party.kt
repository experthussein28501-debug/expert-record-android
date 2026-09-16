package com.khabir.app.domain.model

enum class PartyRole(
    val arabicLabel: String,
    val side: PartySide
) {
    PLAINTIFF("مدعي", PartySide.PLAINTIFF),
    DEFENDANT("مدعى عليه", PartySide.DEFENDANT),
    OTHER("غيره", PartySide.OTHER);

    val isPlaintiff: Boolean get() = side == PartySide.PLAINTIFF
    val isDefendant: Boolean get() = side == PartySide.DEFENDANT

    companion object {
        fun fromArabicLabel(label: String): PartyRole {
            val normalized = label.trim()
            return when {
                normalized.startsWith("مدعي") || normalized.startsWith("مدعى") && !normalized.contains("عليه") -> PLAINTIFF
                normalized.contains("مدعى عليه") || normalized.contains("مدعي عليه") -> DEFENDANT
                else -> OTHER
            }
        }

        /** Reads both the new stored form ("مدعي|بصفته") and old composite labels. */
        fun parseStored(value: String): Pair<PartyRole, Boolean> {
            val normalized = value.trim()
            val capacity = normalized.contains("بصفته")
            val role = when {
                normalized.contains("مدعى عليه") || normalized.contains("مدعي عليه") -> DEFENDANT
                normalized.contains("مدعي") || (normalized.contains("مدعى") && !normalized.contains("عليه")) -> PLAINTIFF
                else -> OTHER
            }
            return role to capacity
        }
    }
}

enum class PartySide { PLAINTIFF, DEFENDANT, OTHER }

data class Party(
    val id: Long = 0L,
    val firstName: String,
    val restName: String,
    val role: PartyRole,
    val address: String,
    val orderIndex: Int,
    val withCapacity: Boolean = false,
    val claimKind: String = "أصلية"
) {
    val fullName: String get() = "$firstName $restName".trim()

    /** "بصفته" حالة للشخص وليست جهة خصومة مستقلة. */
    val reportDisplayName: String
        get() = if (withCapacity && fullName.isNotBlank()) "$fullName بصفته" else fullName

    val storedRoleValue: String
        get() = (if (withCapacity) "${role.arabicLabel}|بصفته" else role.arabicLabel) + (if (claimKind == "أصلية") "" else "|دعوى:$claimKind")
}


