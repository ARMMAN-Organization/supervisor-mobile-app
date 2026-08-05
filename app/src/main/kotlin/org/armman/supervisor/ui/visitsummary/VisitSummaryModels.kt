package org.armman.supervisor.ui.visitsummary

/** One village's visit counts under a Sakhi (Total / Due / Missed). */
data class VillageVisitRow(val villageName: String, val total: Int, val due: Int, val missed: Int)

/** One Sakhi's card on the Visit Summary screen — her name plus her villages' visit rows. */
data class SakhiVisitSummary(val sakhiName: String, val villages: List<VillageVisitRow>)
