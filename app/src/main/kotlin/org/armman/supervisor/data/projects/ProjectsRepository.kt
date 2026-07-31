package org.armman.supervisor.data.projects

import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.SakhiDetail
import org.armman.supervisor.ui.assignitem.SakhiOption

/** Real project/Sakhi master data, shared by Dashboard and Assign Item so both features read the
 * same live projects and rosters instead of each keeping their own sample data. */
interface ProjectsRepository {
  suspend fun getProjects(): List<LocationOption>

  suspend fun getSakhis(projectId: String): List<SakhiOption>

  /** Looks up one Sakhi's display details. No single-Sakhi endpoint exists yet, so this is
   * served from the roster [getSakhis] last fetched for [sakhiId]'s project — throws if
   * [sakhiId] was never seen in a roster this repository has already loaded. */
  suspend fun getSakhiDetail(sakhiId: String): SakhiDetail
}
