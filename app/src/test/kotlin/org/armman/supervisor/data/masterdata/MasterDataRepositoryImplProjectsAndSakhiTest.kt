package org.armman.supervisor.data.masterdata

import kotlinx.coroutines.test.runTest
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.SakhiOption
import org.armman.supervisor.ui.masterdata.MasterDataEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterDataRepositoryImplProjectsAndSakhiTest {
  private val projectsRepository = FakeProjectsRepository()
  private val geographyApi = FakeGeographyApi()

  private fun repository(mockUnreadyEntities: Boolean = false) =
    testMasterDataRepository(
      projectsRepository = projectsRepository,
      geographyApi = geographyApi,
      mockUnreadyEntities = mockUnreadyEntities,
    )

  @Test
  fun `downloading Project Geography sums links across all projects`() = runTest {
    projectsRepository.projects = listOf(LocationOption("proj-1", "P1"), LocationOption("proj-2", "P2"))
    geographyApi.projectGeographyByProject = mapOf(
      "proj-1" to listOf(ProjectGeographyLinkDto("link-1", "proj-1", "state-1", "2026-01-01T00:00:00.000Z", null)),
      "proj-2" to listOf(
        ProjectGeographyLinkDto("link-2", "proj-2", "state-1", "2026-01-01T00:00:00.000Z", null),
        ProjectGeographyLinkDto("link-3", "proj-2", "state-2", "2026-01-01T00:00:00.000Z", null),
      ),
    )

    val result = repository().download(MasterDataEntity.PROJECT_GEOGRAPHY)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(3, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Projects returns Success with the project count`() = runTest {
    val result = repository().download(MasterDataEntity.PROJECTS)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(1, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Projects with an empty roster returns Empty`() = runTest {
    projectsRepository.projects = emptyList()

    val result = repository().download(MasterDataEntity.PROJECTS)

    assertEquals(MasterDataResult.Empty, result)
  }

  @Test
  fun `downloading Sakhi sums rosters across all projects`() = runTest {
    projectsRepository.projects = listOf(LocationOption("proj-1", "P1"), LocationOption("proj-2", "P2"))
    projectsRepository.sakhisByProject = mapOf(
      "proj-1" to listOf(SakhiOption("s1", "A"), SakhiOption("s2", "B")),
      "proj-2" to listOf(SakhiOption("s3", "C")),
    )

    val result = repository().download(MasterDataEntity.SAKHI)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(3, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `a Projects failure is surfaced as Failure, not thrown`() = runTest {
    projectsRepository.failingProjects = true

    val result = repository().download(MasterDataEntity.PROJECTS)

    assertTrue(result is MasterDataResult.Failure)
  }

  @Test
  fun `a Sakhi-roster-specific failure is surfaced as Failure even when Projects itself succeeds`() = runTest {
    projectsRepository.failingSakhis = true

    val result = repository().download(MasterDataEntity.SAKHI)

    assertTrue(result is MasterDataResult.Failure)
  }
}
