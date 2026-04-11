package nl.vintik.mocknest.application.core

import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import java.time.Instant
import kotlin.test.assertEquals

private data class HttpMethodWrapper(val method: HttpMethod)
private data class InstantWrapper(val timestamp: Instant)

@Tag("unit")
class JsonMapperTest {

    @Test
    fun `Given HttpMethod GET When serializing Then should produce string GET`() {
        val wrapper = HttpMethodWrapper(HttpMethod.GET)

        val json = mapper.writeValueAsString(wrapper)

        assertEquals("""{"method":"GET"}""", json)
    }

    @Test
    fun `Given HttpMethod POST When serializing Then should produce string POST`() {
        val wrapper = HttpMethodWrapper(HttpMethod.POST)

        val json = mapper.writeValueAsString(wrapper)

        assertEquals("""{"method":"POST"}""", json)
    }

    @Test
    fun `Given JSON with method POST When deserializing Then should produce HttpMethod POST`() {
        val json = """{"method":"POST"}"""

        val wrapper = mapper.readValue<HttpMethodWrapper>(json)

        assertEquals(HttpMethod.POST, wrapper.method)
    }

    @Test
    fun `Given JSON with lowercase method When deserializing Then should normalise to correct HttpMethod`() {
        val json = """{"method":"delete"}"""

        val wrapper = mapper.readValue<HttpMethodWrapper>(json)

        assertEquals(HttpMethod.DELETE, wrapper.method)
    }

    @Test
    fun `Given Instant value When serializing and deserializing Then should round-trip correctly`() {
        val instant = Instant.parse("2024-01-15T10:30:00Z")
        val wrapper = InstantWrapper(instant)

        val json = mapper.writeValueAsString(wrapper)
        val deserialized = mapper.readValue<InstantWrapper>(json)

        assertEquals(instant, deserialized.timestamp)
    }
}
