package org.armman.supervisor.data.beneficiaries

import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.ui.beneficiaries.BeneficiaryListRepository
import org.armman.supervisor.ui.beneficiaries.SakhiBeneficiaryList
import javax.inject.Inject

/** Concrete [BeneficiaryListRepository]. Backed by beneficiary-service's list endpoint via
 * [BeneficiaryListApi]. There is no dedicated "address" field on a beneficiary case — the
 * closest real equivalent, `villageName`, is used instead. [sakhiName]/[projectName] are read off
 * the first returned case (already enriched server-side); a Sakhi with zero beneficiaries has no
 * such case to read them from, so [ProjectsRepository] is used as the fallback for that name. */
class BeneficiaryListRepositoryImpl @Inject constructor(
  private val api: BeneficiaryListApi,
  private val projectsRepository: ProjectsRepository,
) : BeneficiaryListRepository {

  override suspend fun getBeneficiaries(sakhiId: String): SakhiBeneficiaryList {
    val items = fetchAllBeneficiaryPages { cursor -> api.getBeneficiaries(sakhiId, cursor) }
    val first = items.firstOrNull()

    val sakhiName = first?.sakhiName
      ?: runCatching { projectsRepository.getSakhiOption(sakhiId).name }.getOrDefault("")
    val projectName = first?.projectName
      ?: runCatching { projectsRepository.getSakhiDetail(sakhiId).projectName }.getOrDefault("")

    return SakhiBeneficiaryList(
      sakhiName = sakhiName,
      projectName = projectName,
      address = first?.villageName.orEmpty(),
      beneficiaries = items.map { it.toBeneficiaryDetail() },
    )
  }
}
