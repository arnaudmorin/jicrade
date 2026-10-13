package fr.amorin.jicrade.data.repo

import fr.amorin.jicrade.data.remote.dto.AlertDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappersTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    @Test
    fun parsesIsoOffsetsAndZulu() {
        assertEquals(1702653625000L, parseInstant("2023-12-15T15:20:25+00:00"))
        assertEquals(1702653625000L, parseInstant("2023-12-15T15:20:25Z"))
        assertEquals(1702653625123L, parseInstant("2023-12-15T15:20:25.123Z"))
        assertEquals(1702650025000L, parseInstant("2023-12-15T15:20:25+01:00"))
        assertNull(parseInstant(null))
        assertNull(parseInstant("not a date"))
    }

    @Test
    fun formatIsoRoundTrips() {
        val t = 1702653625000L
        assertEquals("2023-12-15T15:20:25+00:00", formatIso(t))
        assertEquals(t, parseInstant(formatIso(t)))
    }

    @Test
    fun mapsAlertDtoFromSpecExample() {
        val payload = """
        {
          "id": "f4b3a1b2-7c8d-4e5f-9a0b-1c2d3e4f5a6b",
          "tinyId": "42",
          "createdAt": "2023-12-15T15:20:25+00:00",
          "updatedAt": "2023-12-15T15:30:25+00:00",
          "message": "The CPU usage on Server XYZ has exceeded 80% for over 5 minutes.",
          "entity": "Server XYZ",
          "source": "monitoring",
          "status": "open",
          "alias": "cpu-xyz",
          "tags": ["Critical"],
          "extraProperties": {"backend": false, "browser": "Firefox 113.0", "nested": {"a": 1}},
          "description": "details",
          "acknowledged": false,
          "count": 3,
          "owner": "",
          "snoozed": false,
          "lastOccuredAt": "2023-12-15T15:25:25+00:00",
          "integrationType": "API",
          "integrationName": "Default API",
          "priority": "P1",
          "responders": [{"id": "4513b7ea-3b91-438f-b7e4-e3e54af9147c", "type": "team"}, {"id": "5b2b0e011b3a756623f4e25e", "type": "user"}],
          "actions": ["RestartServer"],
          "seen": false,
          "unknownFutureField": 1
        }
        """.trimIndent()
        val entity = json.decodeFromString(AlertDto.serializer(), payload).toEntity()
        assertEquals("42", entity.tinyId)
        assertEquals("P1", entity.priority)
        assertTrue(entity.isOpen)
        assertEquals(1702653625000L, entity.createdAt)
        assertNull(entity.owner)
        assertEquals("false", entity.extraProperties["backend"])
        assertEquals("Firefox 113.0", entity.extraProperties["browser"])
        assertEquals("""{"a":1}""", entity.extraProperties["nested"])
        assertEquals(listOf("team", "user"), entity.responders.map { it.type })
    }

    @Test
    fun statusFallsBackToAcknowledgedFlag() {
        val entity = json.decodeFromString(AlertDto.serializer(), """{"id":"x","acknowledged":true}""").toEntity()
        assertTrue(entity.isAcked)
    }
}
