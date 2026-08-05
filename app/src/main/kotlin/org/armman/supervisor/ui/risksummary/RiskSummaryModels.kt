package org.armman.supervisor.ui.risksummary

/** One village's risk counts under a Sakhi (Mother / Child). Village name renders as a plain
 * table cell — no pill background, no link color — matching every other summary table. */
data class VillageRiskRow(val villageName: String, val motherCount: Int, val childCount: Int)

/** One Sakhi's card on the Risk Summary screen — her name plus her villages' risk rows. */
data class SakhiRiskSummary(val sakhiName: String, val villages: List<VillageRiskRow>)
