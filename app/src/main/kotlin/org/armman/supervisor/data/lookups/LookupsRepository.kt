package org.armman.supervisor.data.lookups

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Resolves a lookup category's `valueCode` (e.g. `"PENDING"`) to its opaque `id` (a UUID minted
 * per environment/seed) so callers never hardcode environment-specific lookup-value ids. Cached
 * for the process lifetime — [LookupsApi] is called at most once per category per session. */
@Singleton
class LookupsRepository @Inject constructor(
  private val api: LookupsApi,
) {
  private var cache: Map<String, LookupCategoryDto>? = null

  /** Guards the check-then-fetch in [cacheOrFetch] so concurrent callers (e.g. several Quick
   * Response cards resolving a lookup label at once) single-flight one `GET /lookups` call
   * instead of each racing to populate [cache] independently. */
  private val cacheMutex = Mutex()

  /** Returns `categoryCode` -> (`valueCode` -> `id`) for one category, fetching and caching every
   * category the first time any category is requested. */
  suspend fun getValueIdsByCode(categoryCode: String): Map<String, String> {
    val category = cacheOrFetch()[categoryCode] ?: return emptyMap()
    return category.values.associate { it.valueCode to it.id }
  }

  /** Resolves a single lookup-value [id] to its display [LookupValueDto.valueLabel] within
   * [categoryCode], or `null` if the id isn't found in that category. */
  suspend fun getValueLabelById(categoryCode: String, id: String): String? {
    val category = cacheOrFetch()[categoryCode] ?: return null
    return category.values.firstOrNull { it.id == id }?.valueLabel
  }

  private suspend fun cacheOrFetch(): Map<String, LookupCategoryDto> {
    cache?.let { return it }
    return cacheMutex.withLock { cache ?: fetchAll().also { cache = it } }
  }

  private suspend fun fetchAll(): Map<String, LookupCategoryDto> {
    val response = api.getLookups()
    if (!response.isSuccessful) error("Failed to load lookups: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty lookups response")
    if (!body.success) error(body.message ?: "Failed to load lookups")
    return body.data.orEmpty().associateBy { it.categoryCode }
  }
}
