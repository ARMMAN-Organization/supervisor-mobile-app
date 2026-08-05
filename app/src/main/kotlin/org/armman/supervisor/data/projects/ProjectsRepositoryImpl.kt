package org.armman.supervisor.data.projects

import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.SakhiDetail
import org.armman.supervisor.ui.assignitem.SakhiOption
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete [ProjectsRepository]. Backed by the auth-service project/Sakhi roster endpoints via
 * [ProjectsApi]. [getSakhiDetail]/[getSakhiOption] have no single-Sakhi endpoint yet, so they're
 * served from the roster [getSakhis] last fetched for that Sakhi's project, fetching every
 * project's roster on a cache miss (e.g. after process death, since the cache is only
 * [Singleton]-scoped in-memory state).
 */
@Singleton
class ProjectsRepositoryImpl @Inject constructor(
  private val api: ProjectsApi,
) : ProjectsRepository {

  // ConcurrentHashMap, not mutableMapOf: getSakhis() writes here and clearCache() (called from
  // logout, on its own coroutine) can run concurrently with a load already in flight.
  private val sakhisBySakhiId = ConcurrentHashMap<String, SakhiDto>()

  override suspend fun getProjects(): List<LocationOption> {
    val response = api.getProjects()
    if (!response.isSuccessful) error("Failed to load projects: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty projects response")
    if (!body.success) error(body.message ?: "Failed to load projects")
    return body.data.orEmpty().map { LocationOption(it.projectId, it.projectName) }
  }

  override suspend fun getSakhis(projectId: String): List<SakhiOption> {
    val response = api.getSakhis(projectId)
    if (!response.isSuccessful) error("Failed to load Sakhis: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty Sakhis response")
    if (!body.success) error(body.message ?: "Failed to load Sakhis")
    val sakhis = body.data.orEmpty()
    sakhis.forEach { sakhisBySakhiId[it.sakhiId] = it }
    return sakhis.map { SakhiOption(it.sakhiId, it.displayName) }
  }

  override suspend fun getSakhiDetail(sakhiId: String): SakhiDetail {
    val sakhi = findSakhi(sakhiId)
    val projectName = getProjects().firstOrNull { it.id == sakhi.primaryProjectId }?.name.orEmpty()
    // auth-service's Sakhi roster has no address field yet — left blank rather than
    // substituting an unrelated field (e.g. mobile number) under the "Address" label.
    return SakhiDetail(sakhiName = sakhi.displayName, projectName = projectName, address = "")
  }

  override suspend fun getSakhiOption(sakhiId: String): SakhiOption {
    val sakhi = findSakhi(sakhiId)
    return SakhiOption(sakhi.sakhiId, sakhi.displayName)
  }

  override suspend fun getSakhiProjectId(sakhiId: String): String = findSakhi(sakhiId).primaryProjectId

  override fun clearCache() {
    sakhisBySakhiId.clear()
  }

  /** [sakhisBySakhiId] is only populated as a side effect of [getSakhis], so a caller that never
   * fetched this Sakhi's project roster in this process (e.g. a detail screen restored after
   * process death) would otherwise see a false "unknown sakhi" — fetch every project's roster
   * once before giving up. A single project's roster failing to load (network blip) must not
   * abort the search for a Sakhi that belongs to a different, reachable project, so failures are
   * swallowed here; if every project fails, the loop still ends in the same "unknown sakhi" error
   * as a Sakhi that genuinely doesn't exist. */
  private suspend fun findSakhi(sakhiId: String): SakhiDto {
    sakhisBySakhiId[sakhiId]?.let { return it }
    for (project in getProjects()) {
      runCatching { getSakhis(project.id) }
      sakhisBySakhiId[sakhiId]?.let { return it }
    }
    error("Unknown sakhi id: $sakhiId")
  }
}
