package org.armman.supervisor.data.lookups

import javax.inject.Inject
import javax.inject.Singleton

/** Resolves a lookup category's `valueCode` (e.g. `"PENDING"`) to its opaque `id` (a UUID minted
 * per environment/seed) so callers never hardcode environment-specific lookup-value ids. Cached
 * for the process lifetime — [LookupsApi] is called at most once per category per session. */
@Singleton
class LookupsRepository @Inject constructor(
  private val api: LookupsApi,
) {
  @Volatile
  private var cache: Map<String, LookupCategoryDto>? = null

  /** Returns `categoryCode` -> (`valueCode` -> `id`) for one category, fetching and caching every
   * category the first time any category is requested. */
  suspend fun getValueIdsByCode(categoryCode: String): Map<String, String> {
    val category = (cache ?: fetchAll().also { cache = it })[categoryCode] ?: return emptyMap()
    return category.values.associate { it.valueCode to it.id }
  }

  private suspend fun fetchAll(): Map<String, LookupCategoryDto> {
    val response = api.getLookups()
    if (!response.isSuccessful) error("Failed to load lookups: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty lookups response")
    if (!body.success) error(body.message ?: "Failed to load lookups")
    return body.data.orEmpty().associateBy { it.categoryCode }
  }
}
