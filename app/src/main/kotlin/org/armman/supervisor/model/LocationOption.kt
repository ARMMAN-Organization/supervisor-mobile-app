package org.armman.supervisor.model

/**
 * A selectable geography/scope filter (e.g. village, PHC, zone). Shared across features —
 * any screen that scopes its data by location reuses this instead of a feature-local type.
 */
data class LocationOption(val id: String, val name: String)
