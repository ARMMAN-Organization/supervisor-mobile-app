package org.armman.supervisor.data.projects

import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.SakhiDetail
import org.armman.supervisor.ui.assignitem.SakhiOption
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete [ProjectsRepository]. Backed by the auth-service project/Sakhi roster endpoints via
 * [ProjectsApi]. [getSakhiDetail] has no single-Sakhi endpoint yet, so it's served from the last
 * roster fetched for that Sakhi's project by [getSakhis] — the only path every caller (Dashboard,
 * Assign Item) uses before requesting a detail.
 */
@Singleton
class ProjectsRepositoryImpl @Inject constructor(
  private val api: ProjectsApi,
) : ProjectsRepository {

  private val sakhisBySakhiId = mutableMapOf<String, SakhiDto>()

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
    val sakhi = sakhisBySakhiId[sakhiId] ?: error("Unknown sakhi id: $sakhiId")
    val projectName = getProjects().firstOrNull { it.id == sakhi.primaryProjectId }?.name.orEmpty()
    // auth-service's Sakhi roster has no address field yet — left blank rather than
    // substituting an unrelated field (e.g. mobile number) under the "Address" label.
    return SakhiDetail(sakhiName = sakhi.displayName, projectName = projectName, address = "")
  }
}
