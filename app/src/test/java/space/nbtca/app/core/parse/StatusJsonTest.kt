package space.nbtca.app.core.parse

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import space.nbtca.app.core.model.ServiceStatus

class StatusJsonTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    @Test
    fun parsesUpstreamStatusPayload() {
        val status = json.decodeFromString(ServiceStatus.serializer(), fixture("status.json"))
        assertEquals("major", status.state)
        assertTrue(status.groups.isNotEmpty())
        assertTrue(status.totalComponents > 0)
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "missing fixture $name" }
            .readText(Charsets.UTF_8)
}
