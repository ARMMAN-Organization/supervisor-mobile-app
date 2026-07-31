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

  override suspend fun getProjects(): Response<ProjectsEnvelopeDto> =
    Response.success(ProjectsEnvelopeDto(success = true, message = "OK", data = projects))

  override suspend fun getSakhis(projectId: String): Response<SakhisEnvelopeDto> =
    Response.success(SakhisEnvelopeDto(success = true, message = "OK", data = sakhisByProject[projectId].orEmpty()))
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

  @Test(expected = IllegalStateException::class)
  fun `getSakhiDetail throws for a sakhi never seen in a getSakhis call`() = runTest {
    repository.getSakhiDetail("unknown-sakhi")
  }
}
