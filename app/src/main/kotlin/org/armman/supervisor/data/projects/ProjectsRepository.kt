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
   * served from the roster [getSakhis] last fetched for [sakhiId]'s project, fetching that
   * roster first on a cache miss (e.g. after process death) — throws only if [sakhiId] is in
   * no project's roster. */
  suspend fun getSakhiDetail(sakhiId: String): SakhiDetail

  /** Looks up one Sakhi's [SakhiOption] across all projects, same cache-then-fetch-all behavior
   * as [getSakhiDetail]. */
  suspend fun getSakhiOption(sakhiId: String): SakhiOption

  /** Looks up the id of the project [sakhiId] primarily belongs to, same cache-then-fetch-all
   * behavior as [getSakhiDetail]. Needed to submit a call log (FR-SV-3.1/3.2), which is scoped to
   * a project. */
  suspend fun getSakhiProjectId(sakhiId: String): String

  /** The Sakhi ids assigned to [supervisorUserId] within [projectId] — i.e. [projectId]'s roster
   * filtered to `supervisorId == supervisorUserId`. Used to scope a Supervisor-facing list (e.g.
   * Quick Response) to only their own Sakhis when the backend endpoint providing that list
   * doesn't already enforce that scoping itself. */
  suspend fun getMySakhiIds(projectId: String, supervisorUserId: String): Set<String>

  /** Drops all cached project/Sakhi roster data. Call on sign-out so a subsequent login (as
   * possibly a different Supervisor on a shared device) never serves another account's roster. */
  fun clearCache()
}
