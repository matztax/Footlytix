package de.tactics.app.ui

import de.tactics.app.data.Analysis
import de.tactics.app.data.AttributeDef
import de.tactics.app.data.AttributeLink
import de.tactics.app.data.Club
import de.tactics.app.data.Player

/** Fasst Verein, Attribute, Analysen und ausgewählte Spieler zu einem Gegnerbericht zusammen. */
fun buildOpponentReport(
    club: Club,
    players: List<Player>,
    defs: List<AttributeDef>,
    links: List<AttributeLink>,
    analyses: List<Analysis>
): String {
    fun attributeLine(type: String, id: Long, positive: Boolean): String =
        links.filter { it.targetType == type && it.targetId == id }
            .mapNotNull { l -> defs.firstOrNull { it.id == l.attributeId && it.positive == positive }?.let { "${it.name} (${l.level}/5)" } }
            .joinToString(", ")

    return buildString {
        append("Gegner: ").append(club.name).append("\n")
        if (club.system.isNotBlank()) append("System: ").append(club.system).append("\n")
        val pos = attributeLine("club", club.id, true)
        val neg = attributeLine("club", club.id, false)
        if (pos.isNotBlank()) append("Stärken: ").append(pos).append("\n")
        if (neg.isNotBlank()) append("Schwächen: ").append(neg).append("\n")
        if (club.notes.isNotBlank()) append("Notizen: ").append(club.notes).append("\n")

        val clubAnalyses = analyses.filter { it.targetType == "club" && it.targetId == club.id }
        if (clubAnalyses.isNotEmpty()) {
            append("\nAnalysen zum Team:\n")
            clubAnalyses.forEach { append("• ").append(it.title).append(": ").append(it.text).append("\n") }
        }

        if (players.isNotEmpty()) {
            append("\nSpieler:\n")
            players.sortedBy { it.number }.forEach { p ->
                append("#").append(p.number).append(" ").append(p.name)
                if (p.position.isNotBlank()) append(" (").append(p.position).append(")")
                append("\n")
                val pp = attributeLine("player", p.id, true)
                val pn = attributeLine("player", p.id, false)
                if (pp.isNotBlank()) append("   + ").append(pp).append("\n")
                if (pn.isNotBlank()) append("   − ").append(pn).append("\n")
                analyses.filter { it.targetType == "player" && it.targetId == p.id }.forEach {
                    append("   Analyse: ").append(it.title).append(" – ").append(it.text).append("\n")
                }
            }
        }
    }.trim()
}
