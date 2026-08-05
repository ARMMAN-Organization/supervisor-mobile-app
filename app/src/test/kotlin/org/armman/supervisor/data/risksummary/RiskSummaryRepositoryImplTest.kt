package org.armman.supervisor.data.risksummary

import kotlinx.coroutines.test.runTest
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.SakhiDetail
import org.armman.supervisor.ui.assignitem.SakhiOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

private class FakeProjectsRepository : ProjectsRepository {
  var failGetProjects: Boolean = false
  var getProjectsCallCount = 0
    private set

  override suspend fun getProjects(): List<LocationOption> {
    getProjectsCallCount++
    if (failGetProjects) error("Simulated failure fetching projects")
    return listOf(LocationOption("loc-1", "Unrestricted Armman"), LocationOption("loc-2", "Wardha - Zone A"))
  }

  override suspend fun getSakhis(projectId: String): List<SakhiOption> = error("not used")

  override suspend fun getSakhiDetail(sakhiId: String): SakhiDetail = error("not used")

  override suspend fun getSakhiOption(sakhiId: String): SakhiOption = error("not used")

  override suspend fun getSakhiProjectId(sakhiId: String): String = error("not used")

  override fun clearCache() = Unit
}

class RiskSummaryRepositoryImplTest {
  private val projectsRepository = FakeProjectsRepository()
  private val repository = RiskSummaryRepositoryImpl(projectsRepository)

  @Test
  fun `getLocations delegates to and returns exactly what ProjectsRepository returns`() = runTest {
    assertEquals(listOf(LocationOption("loc-1", "Unrestricted Armman"), LocationOption("loc-2", "Wardha - Zone A")), repository.getLocations())
    assertEquals(1, projectsRepository.getProjectsCallCount)
  }

  @Test
  fun `getLocations propagates a ProjectsRepository failure instead of swallowing it`() = runTest {
    projectsRepository.failGetProjects = true

    assertThrows(IllegalStateException::class.java) { runTest { repository.getLocations() } }
  }

  @Test
  fun `getRiskSummary returns a Sakhi card per seeded Sakhi with her villages`() = runTest {
    val summaries = repository.getRiskSummary("loc-1")

    assertEquals(2, summaries.size)
    assertEquals("SakhiKomal", summaries[0].sakhiName)
    assertEquals("SakhiMeera", summaries[1].sakhiName)
    assertEquals("SushilTest", summaries[0].villages.single().villageName)
    assertEquals("SushilTest1", summaries[1].villages.single().villageName)
  }

  @Test
  fun `getRiskSummary counts are seeded from the locationId hash so the same location repeats`() = runTest {
    val first = repository.getRiskSummary("loc-1")
    val second = repository.getRiskSummary("loc-1")

    assertEquals(first, second)
  }

  @Test
  fun `getRiskSummary counts vary with a different locationId`() = runTest {
    val locOne = repository.getRiskSummary("loc-1")
    val locTwo = repository.getRiskSummary("loc-2")

    val seedOne = "loc-1".hashCode().mod(10)
    val seedTwo = "loc-2".hashCode().mod(10)
    assertEquals(1 + seedOne, locOne[0].villages.single().motherCount)
    assertEquals(1 + seedTwo, locTwo[0].villages.single().motherCount)
  }

  @Test
  fun `getRiskSummary with a null locationId seeds with zero`() = runTest {
    val summaries = repository.getRiskSummary(null)

    assertEquals(1, summaries[0].villages.single().motherCount)
    assertEquals(1, summaries[1].villages.single().motherCount)
  }
}
