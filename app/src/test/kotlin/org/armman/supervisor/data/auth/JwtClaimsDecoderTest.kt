package org.armman.supervisor.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64

class JwtClaimsDecoderTest {
  private val decoder = JwtClaimsDecoder()

  private fun buildToken(payloadJson: String): String {
    val header = encodeSegment("""{"alg":"RS256","typ":"JWT"}""")
    val payload = encodeSegment(payloadJson)
    return "$header.$payload.fakesignature"
  }

  private fun encodeSegment(json: String): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())

  @Test
  fun `decodes sub from a valid JWT payload`() {
    val token = buildToken(
      """{"sub":"11111111-1111-1111-1111-111111111111","roles":["SUPERVISOR"],"iat":1,"exp":2}""",
    )

    val claims = decoder.decode(token)

    assertEquals("11111111-1111-1111-1111-111111111111", claims.subjectId)
  }

  @Test
  fun `missing sub decodes to empty string, no crash`() {
    val token = buildToken("""{"roles":["SUPERVISOR"]}""")

    val claims = decoder.decode(token)

    assertEquals("", claims.subjectId)
  }

  @Test
  fun `malformed token with wrong segment count throws JwtDecodeException`() {
    assertThrows(JwtDecodeException::class.java) {
      decoder.decode("not-a-jwt")
    }
  }

  @Test
  fun `token with invalid base64url payload throws JwtDecodeException`() {
    assertThrows(JwtDecodeException::class.java) {
      decoder.decode("header.!!!not-base64!!!.signature")
    }
  }

  @Test
  fun `token with non-JSON payload throws JwtDecodeException`() {
    val badPayload = encodeSegment("not json at all")
    assertThrows(JwtDecodeException::class.java) {
      decoder.decode("header.$badPayload.signature")
    }
  }

  @Test
  fun `decoder needs no network dependency`() {
    // Sanity: constructing JwtClaimsDecoder takes no repository/network args.
    JwtClaimsDecoder()
  }
}
