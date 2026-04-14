package nl.vintik.mocknest.infra.aws.runtime.snapstart

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Bug Condition Exploration Test — Property 1: Missing Post-Restore Reload
 *
 * This test PROVES the bug exists on UNFIXED code by verifying that
 * RuntimeMappingReloadHook does not exist yet.
 *
 * EXPECTED TO FAIL on unfixed code — the class does not exist, so the
 * Class.forName call will throw ClassNotFoundException.
 *
 * DO NOT fix production code to make this pass.
 * DO NOT fix this test when it fails.
 *
 * **Validates: Requirements 2.1, 2.2, 2.6**
 */
class RuntimeMappingReloadHookExplorationTest {

    @Test
    fun `Given unfixed code When checking for RuntimeMappingReloadHook Then class exists`() {
        // This will throw ClassNotFoundException on unfixed code because
        // RuntimeMappingReloadHook has not been created yet — proving the bug exists.
        val clazz = Class.forName(
            "nl.vintik.mocknest.infra.aws.runtime.snapstart.RuntimeMappingReloadHook"
        )
        assertTrue(
            clazz.declaredMethods.any { it.name == "afterRestore" },
            "RuntimeMappingReloadHook must have an afterRestore() method that calls wireMockServer.resetMappings()"
        )
    }

    @Test
    fun `Given unfixed code When checking RuntimeMappingReloadHook Then it implements CRaC Resource`() {
        val clazz = Class.forName(
            "nl.vintik.mocknest.infra.aws.runtime.snapstart.RuntimeMappingReloadHook"
        )
        val implementsCrac = clazz.interfaces.any { it.name == "org.crac.Resource" }
        assertTrue(
            implementsCrac,
            "RuntimeMappingReloadHook must implement org.crac.Resource for SnapStart afterRestore lifecycle"
        )
    }
}
