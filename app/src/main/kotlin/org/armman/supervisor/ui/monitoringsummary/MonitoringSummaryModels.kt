package org.armman.supervisor.ui.monitoringsummary

/** One village's monitoring counts under a Sakhi (Mother / Child). */
data class VillageMonitoringRow(val villageName: String, val motherCount: Int, val childCount: Int)

/** One Sakhi's card on the Monitoring Summary screen — her name plus her villages' rows. */
data class SakhiMonitoringSummary(val sakhiName: String, val villages: List<VillageMonitoringRow>)
