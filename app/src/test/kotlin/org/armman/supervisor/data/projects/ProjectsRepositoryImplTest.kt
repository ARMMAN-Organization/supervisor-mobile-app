package org.armman.supervisor.data.projects

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

private class FakeProjectsApi : ProjectsApi {
  var projects: List<ProjectDto> = listOf(ProjectDto("proj-1", "Test Project"))
  var sakhisByProject: Map<String, List<SakhiDto>> = mapOf(
    "proj-1" to listOf(
      SakhiDto("sakhi-1", "Sushil", "+911111111111", "proj-1", "sup-1"),
    ),
  )
  var failingProjectIds: Set<String> = emptySet()
  var getSakhisCallCount = 0

  override suspend fun getProjects(): Response<ProjectsEnvelopeDto> =
    Response.success(ProjectsEnvelopeDto(success = true, message = "OK", data = projects))

  override suspend fun getSakhis(projectId: String): Response<SakhisEnvelopeDto> {
    getSakhisCallCount++
    if (projectId in failingProjectIds) error("Simulated network failure for $projectId")
    return Response.success(SakhisEnvelopeDto(success = true, message = "OK", data = sakhisByProject[projectId].orEmpty()))
  }
}

class ProjectsRepositoryImplTest {
  private val api = FakeProjectsApi()
  private val repository = ProjectsRepositoryImpl(api)

  @Test
  fun `getProjects returns projects from the API`() = runTest {
    val projects = repository.getProjects()

    assertTrue(projects.isNotEmpty())
    assertTrue(projects.any { it.id == "proj-1" && it.name == "Test Project" })
  }

  @Test
  fun `getSakhis returns the roster for a known project`() = runTest {
    val sakhis = repository.getSakhis("proj-1")

    assertTrue(sakhis.isNotEmpty())
    assertTrue(sakhis.any { it.id == "sakhi-1" && it.name == "Sushil" })
  }

  @Test
  fun `getSakhis returns an empty list for an unknown project`() = runTest {
    assertTrue(repository.getSakhis("unknown-project").isEmpty())
  }

  @Test
  fun `getSakhiDetail returns the detail for a sakhi seen in a prior getSakhis call`() = runTest {
    repository.getSakhis("proj-1")

    val detail = repository.getSakhiDetail("sakhi-1")

    assertEquals("Sushil", detail.sakhiName)
    assertEquals("Test Project", detail.projectName)
    assertEquals("", detail.address)
  }

  @Test
  fun `getSakhiDetail fetches the roster on a cache miss instead of throwing`() = runTest {
    // Simulates process death: a fresh Singleton with an empty cache, detail requested directly.
    val detail = repository.getSakhiDetail("sakhi-1")

    assertEquals("Sushil", detail.sakhiName)
    assertEquals("Test Project", detail.projectName)
  }

  @Test(expected = IllegalStateException::class)
  fun `getSakhiDetail throws for a sakhi in no project's roster`() = runTest {
    repository.getSakhiDetail("unknown-sakhi")
  }

  @Test
  fun `getSakhiDetail does not re-fetch a roster already cached`() = runTest {
    repository.getSakhis("proj-1")
    val callsAfterWarmUp = api.getSakhisCallCount

    repository.getSakhiDetail("sakhi-1")

    assertEquals(callsAfterWarmUp, api.getSakhisCallCount)
  }

  @Test
  fun `getSakhiOption returns the option for a sakhi seen in a prior getSakhis call`() = runTest {
    repository.getSakhis("proj-1")

    val option = repository.getSakhiOption("sakhi-1")

    assertEquals("sakhi-1", option.id)
    assertEquals("Sushil", option.name)
  }

  @Test
  fun `getSakhiOption fetches the roster on a cache miss instead of throwing`() = runTest {
    val option = repository.getSakhiOption("sakhi-1")

    assertEquals("sakhi-1", option.id)
  }

  @Test(expected = IllegalStateException::class)
  fun `getSakhiOption throws for a sakhi in no project's roster`() = runTest {
    repository.getSakhiOption("unknown-sakhi")
  }

  @Test
  fun `clearCache forces getSakhiDetail to re-fetch instead of serving a stale entry`() = runTest {
    repository.getSakhis("proj-1")

    repository.clearCache()

    val callsBeforeDetail = api.getSakhisCallCount
    val detail = repository.getSakhiDetail("sakhi-1")

    assertEquals("Sushil", detail.sakhiName)
    assertTrue(api.getSakhisCallCount > callsBeforeDetail)
  }

  @Test
  fun `getSakhiDetail skips a project whose roster fails to load and keeps searching`() = runTest {
    api.projects = listOf(ProjectDto("proj-broken", "Broken Project"), ProjectDto("proj-1", "Test Project"))
    api.failingProjectIds = setOf("proj-broken")

    val detail = repository.getSakhiDetail("sakhi-1")

    assertEquals("Sushil", detail.sakhiName)
  }

  @Test(expected = IllegalStateException::class)
  fun `getSakhiDetail throws unknown-sakhi, not a network error, when every project fails`() = runTest {
    api.failingProjectIds = setOf("proj-1")

    repository.getSakhiDetail("sakhi-1")
  }

  @Test
  fun `getMySakhiIds returns only sakhis assigned to the given supervisor`() = runTest {
    api.sakhisByProject = mapOf(
      "proj-1" to listOf(
        SakhiDto("sakhi-1", "Sushil", "+911111111111", "proj-1", "sup-1"),
        SakhiDto("sakhi-2", "Priya", "+912222222222", "proj-1", "sup-2"),
      ),
    )

    val mySakhiIds = repository.getMySakhiIds("proj-1", "sup-1")

    assertEquals(setOf("sakhi-1"), mySakhiIds)
  }

  @Test
  fun `getMySakhiIds returns an empty set when the supervisor has no assigned sakhis`() = runTest {
    val mySakhiIds = repository.getMySakhiIds("proj-1", "sup-with-no-sakhis")

    assertTrue(mySakhiIds.isEmpty())
  }

  @Test(expected = IllegalStateException::class)
  fun `getMySakhiIds propagates a roster load failure`() = runTest {
    api.failingProjectIds = setOf("proj-1")

    repository.getMySakhiIds("proj-1", "sup-1")
  }

  @Test
  fun `getMySakhiIds populates the same roster cache as getSakhis, so a later lookup for that project does not re-fetch`() =
    runTest {
      repository.getMySakhiIds("proj-1", "sup-1")
      val callsAfterMySakhiIds = api.getSakhisCallCount

      val detail = repository.getSakhiDetail("sakhi-1")

      assertEquals("Sushil", detail.sakhiName)
      assertEquals(callsAfterMySakhiIds, api.getSakhisCallCount)
    }
}
