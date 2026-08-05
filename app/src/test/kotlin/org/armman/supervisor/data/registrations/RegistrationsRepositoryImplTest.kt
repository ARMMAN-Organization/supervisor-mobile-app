package org.armman.supervisor.data.registrations

import kotlinx.coroutines.test.runTest
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.SakhiDetail
import org.armman.supervisor.ui.assignitem.SakhiOption
import org.armman.supervisor.ui.registrations.VillageRegistrationRow
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

class RegistrationsRepositoryImplTest {
  private val projectsRepository = FakeProjectsRepository()
  private val repository = RegistrationsRepositoryImpl(projectsRepository)

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
  fun `getRegistrations returns a Sakhi card per seeded Sakhi with her villages`() = runTest {
    val summaries = repository.getRegistrations("loc-1")

    assertEquals(2, summaries.size)
    assertEquals("SakhiKomal", summaries[0].sakhiName)
    assertEquals(listOf(VillageRegistrationRow("SushilTest", motherCount = 1, childCount = 1)), summaries[0].villages)
    assertEquals("SakhiMeera", summaries[1].sakhiName)
    assertEquals(listOf(VillageRegistrationRow("SushilTest1", motherCount = 1, childCount = 0)), summaries[1].villages)
  }

  @Test
  fun `getRegistrations badge counts are seeded from the locationId hash so the same location repeats`() = runTest {
    val first = repository.getRegistrations("loc-1")
    val second = repository.getRegistrations("loc-1")

    assertEquals(first, second)
  }

  @Test
  fun `getRegistrations badge counts vary with a different locationId`() = runTest {
    val locOne = repository.getRegistrations("loc-1")
    val locTwo = repository.getRegistrations("loc-2")

    val seedOne = "loc-1".hashCode().mod(10)
    val seedTwo = "loc-2".hashCode().mod(10)
    assertEquals(2 + seedOne, locOne[0].badgeCount)
    assertEquals(2 + seedTwo, locTwo[0].badgeCount)
  }

  @Test
  fun `getRegistrations with a null locationId seeds with zero`() = runTest {
    val summaries = repository.getRegistrations(null)

    assertEquals(2, summaries[0].badgeCount)
    assertEquals(1, summaries[1].badgeCount)
  }
}
