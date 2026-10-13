package fr.amorin.jicrade.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LenientTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    @Test
    fun teamsFromWrappedObject() {
        val teams = json.decodeTeams(json.parseToJsonElement("""{"platformTeams":[{"teamId":"t1","teamName":"Red Team"}]}"""))
        assertEquals("Red Team", teams.single().teamName)
    }

    @Test
    fun teamsFromBareArray() {
        val teams = json.decodeTeams(json.parseToJsonElement("""[{"teamId":"t1","teamName":"Red Team"},{"teamId":"t2","teamName":"Blue"}]"""))
        assertEquals(listOf("t1", "t2"), teams.map { it.teamId })
    }

    @Test
    fun usersFromArrayOrPage() {
        val a = json.decodeUsers(json.parseToJsonElement("""[{"accountId":"u1","displayName":"Ann"}]"""))
        val b = json.decodeUsers(json.parseToJsonElement("""{"values":[{"accountId":"u1","displayName":"Ann"}],"total":1}"""))
        assertEquals(a, b)
        assertTrue(json.decodeUsers(json.parseToJsonElement("""{"message":"nope"}""")).isEmpty())
    }
}
