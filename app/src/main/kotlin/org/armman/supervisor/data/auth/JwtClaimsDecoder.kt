package org.armman.supervisor.data.auth

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** The access token isn't a well-formed JWT (wrong segment count) — can't extract claims. */
class JwtDecodeException(message: String) : Exception(message)

/** The only claim this app reads out of the access token payload — the subject id. `roles`,
 * `projectId`, `geographyUnitId`, and expiry all come directly from the login response body,
 * so they don't need to be decoded here (unlike sakhi's Sakhi app, which had no such fields in
 * its response and had to decode the token for them). */
data class JwtClaims(
  val subjectId: String,
)

/** Mirrors the raw wire shape with every field nullable. Gson instantiates Kotlin data classes
 * via reflection (bypassing the constructor), so a missing `sub` key would silently become
 * `null` at runtime despite the type system, unless this field is nullable and
 * [JwtClaimsDecoder] coerces explicitly. Never use a non-null-with-defaults data class as a
 * direct Gson target. */
private data class RawJwtClaims(
  @SerializedName("sub") val subjectId: String?,
)

/**
 * Reads the subject id out of an access token's payload segment for local session use.
 *
 * **This deliberately does not verify the RS256 signature** — that requires the server's
 * public key, which the app doesn't have and shouldn't need. Signature verification is the
 * server's job on every subsequent authenticated request; this decoder only exists so the app
 * can record which subject a session belongs to, never as an auth boundary.
 */
@Singleton
class JwtClaimsDecoder @Inject constructor() {
  private val gson = Gson()

  fun decode(accessToken: String): JwtClaims {
    val segments = accessToken.split(".")
    if (segments.size != 3) {
      throw JwtDecodeException("Access token has ${segments.size} segments, expected 3.")
    }
    val payloadJson = try {
      String(Base64.getUrlDecoder().decode(padBase64Url(segments[1])))
    } catch (e: IllegalArgumentException) {
      throw JwtDecodeException("Access token payload is not valid base64url.")
    }
    val raw = try {
      gson.fromJson(payloadJson, RawJwtClaims::class.java)
        ?: throw JwtDecodeException("Access token payload decoded to null.")
    } catch (e: com.google.gson.JsonSyntaxException) {
      throw JwtDecodeException("Access token payload is not valid JSON.")
    }
    return JwtClaims(subjectId = raw.subjectId.orEmpty())
  }

  /** `Base64.getUrlDecoder()` requires padding; JWT segments omit it per RFC 7515. A remainder
   * of 1 is not a valid base64 length (each char encodes 6 bits, so 1 leftover char can't
   * complete a byte) — reject it here instead of padding garbage into "decodable" input. */
  private fun padBase64Url(segment: String): String {
    val remainder = segment.length % 4
    if (remainder == 1) {
      throw IllegalArgumentException("Invalid base64url length: ${segment.length}.")
    }
    return if (remainder == 0) segment else segment + "=".repeat(4 - remainder)
  }
}
