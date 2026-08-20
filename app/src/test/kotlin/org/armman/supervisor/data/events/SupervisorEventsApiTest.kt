package org.armman.supervisor.data.events

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression test for "Expected a string but was BEGIN_OBJECT ... path $.data[N].topicsJson" —
 * seen live against api.armman.org, where some `GET /supervisor-events` records return
 * `topicsJson` as a JSON-encoded string and others as a raw JSON object. Uses a plain [Gson]
 * instance (matching [org.armman.supervisor.di.NetworkModule.provideRetrofit]'s
 * `GsonConverterFactory.create()`, which has no custom global config) so this exercises the exact
 * same deserialization path Retrofit uses in the app. */
class SupervisorEventsApiTest {
  private val gson = Gson()

  @Test
  fun `topicsJson deserializes correctly when the server sends a JSON-encoded string`() {
    val json = """{"id":"e1","projectId":"p1","supervisorId":"s1","eventType":"MEETING",""" +
      """"eventDate":"2026-08-19T00:00:00.000Z","topicsJson":"{}","remarks":null,""" +
      """"status":"SCHEDULED","photoMediaId":null,"createdAt":"2026-08-18T00:00:00.000Z",""" +
      """"updatedAt":"2026-08-18T00:00:00.000Z"}"""

    val dto = gson.fromJson(json, SupervisorEventDto::class.java)

    assertEquals("{}", dto.topicsJson)
  }

  @Test
  fun `topicsJson deserializes correctly when the server sends a raw JSON object`() {
    val json = """{"id":"e1","projectId":"p1","supervisorId":"s1","eventType":"TRAINING",""" +
      """"eventDate":"2026-08-19T00:00:00.000Z","topicsJson":{"topics":["topic-a","topic-b"]},""" +
      """"remarks":null,"status":"SCHEDULED","photoMediaId":null,""" +
      """"createdAt":"2026-08-18T00:00:00.000Z","updatedAt":"2026-08-18T00:00:00.000Z"}"""

    val dto = gson.fromJson(json, SupervisorEventDto::class.java)

    assertEquals("""{"topics":["topic-a","topic-b"]}""", dto.topicsJson)
  }

  @Test
  fun `a full events-list envelope with mixed topicsJson shapes across records parses without throwing`() {
    val json = """{"success":true,"message":"OK","data":[""" +
      """{"id":"e1","projectId":"p1","supervisorId":"s1","eventType":"MEETING",""" +
      """"eventDate":"2026-08-19T00:00:00.000Z","topicsJson":"{}","remarks":null,""" +
      """"status":"SCHEDULED","photoMediaId":null,"createdAt":"2026-08-18T00:00:00.000Z",""" +
      """"updatedAt":"2026-08-18T00:00:00.000Z"},""" +
      """{"id":"e2","projectId":"p1","supervisorId":"s1","eventType":"TRAINING",""" +
      """"eventDate":"2026-08-19T00:00:00.000Z","topicsJson":{"topics":["topic-a"]},""" +
      """"remarks":null,"status":"SCHEDULED","photoMediaId":null,""" +
      """"createdAt":"2026-08-18T00:00:00.000Z","updatedAt":"2026-08-18T00:00:00.000Z"}""" +
      """]}"""

    val envelope = gson.fromJson(json, SupervisorEventsEnvelopeDto::class.java)

    assertEquals(2, envelope.data?.size)
    assertEquals("{}", envelope.data?.get(0)?.topicsJson)
    assertEquals("""{"topics":["topic-a"]}""", envelope.data?.get(1)?.topicsJson)
  }
}
