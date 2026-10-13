package fr.amorin.jicrade.data.remote

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class OpsApiTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    private lateinit var api: OpsApi

    @Before
    fun setUp() {
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().addInterceptor(RetryInterceptor(maxRetries = 2)).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpsApi::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun listAlertsBuildsExpectedUrlAndParsesPage() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"values":[{"id":"a1","tinyId":"1","message":"m","status":"open","priority":"P2"}],"count":1,"links":{"next":"/v1/alerts?offset=100&size=100"}}"""
            )
        )
        val page = api.listAlerts("cloud-1", query = "teams: \"Ops\" AND NOT status: closed")
        val request = server.takeRequest()
        assertEquals("/jsm/ops/api/cloud-1/v1/alerts", request.requestUrl!!.encodedPath)
        assertEquals("teams: \"Ops\" AND NOT status: closed", request.requestUrl!!.queryParameter("query"))
        assertEquals("100", request.requestUrl!!.queryParameter("size"))
        assertEquals(1, page.values.size)
        assertEquals("/v1/alerts?offset=100&size=100", page.links?.next)
    }

    @Test
    fun timelineParsesFlattenedResponders() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"startDate":"2024-01-01T00:00:00Z","endDate":"2024-01-29T00:00:00Z","finalTimeline":{"rotations":[{"id":"r1","name":"Primary","order":1.0,"periods":[
                   {"startDate":"2024-01-01T09:00:00Z","endDate":"2024-01-02T09:00:00Z","type":"base","responder":{"id":"u1","type":"user"},"flattenedResponders":[{"id":"u1","type":"user"}]},
                   {"startDate":"2024-01-02T09:00:00Z","endDate":"2024-01-03T09:00:00Z","type":"override","responder":{"id":"u2","type":"user"},"flattenedResponders":[{"id":"u2","type":"user"}]}
                ]}]},"_expands":["base","override","forwarding"]}"""
            )
        )
        val tl = api.timeline("c", "s1", date = "2024-01-01T00:00:00Z")
        val periods = tl.finalTimeline!!.rotations.single().periods
        assertEquals(listOf("u1", "u2"), periods.map { it.flattenedResponders.single().id })
        assertEquals("override", periods[1].type)
    }

    @Test
    fun errorBodyIsMappedToApiException() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"errors":[{"title":"Sum of offset and size should be lower than 20K","code":"400"}]}"""))
        try {
            api(json, "alerts") { api.listAlerts("c", query = "x", offset = 20000) }
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(422, e.code)
            assertTrue(e.message!!.startsWith("alerts: "))
            assertTrue(e.message!!.contains("20K"))
        }
    }

    @Test
    fun arrayWhereObjectExpectedBecomesReadableError() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))
        try {
            api(json, "alert") { api.getAlert("c", "a1") }
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(0, e.code)
            assertTrue(e.message!!.startsWith("alert: unexpected response format"))
        }
    }

    @Test
    fun acknowledgeParsesFractionalTook() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"result":"Request will be processed","requestId":"r1","took":0.123}"""))
        val r = api.acknowledge("c", "a1")
        assertEquals("r1", r.requestId)
        assertEquals(0.123, r.took!!, 1e-9)
    }

    @Test
    fun retriesOn429UsingRetryAfter() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "1"))
        server.enqueue(MockResponse().setBody("""{"platformTeams":[{"teamId":"t1","teamName":"Ops"}]}"""))
        val teams = json.decodeTeams(api.teams("c"))
        assertEquals("Ops", teams.single().teamName)
        assertEquals(2, server.requestCount)
    }
}
