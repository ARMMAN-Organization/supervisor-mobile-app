package org.armman.supervisor.ui.registrations

/** One village's registration counts under a Sakhi (Mother / Child). */
data class VillageRegistrationRow(val villageName: String, val motherCount: Int, val childCount: Int)

/** One Sakhi's card on the Registrations screen: name, badge total, mother/child targets, and
 * her villages' registration rows. */
data class SakhiRegistrationSummary(
  val sakhiName: String,
  val badgeCount: Int,
  val motherTarget: Int,
  val childTarget: Int,
  val villages: List<VillageRegistrationRow>,
)
