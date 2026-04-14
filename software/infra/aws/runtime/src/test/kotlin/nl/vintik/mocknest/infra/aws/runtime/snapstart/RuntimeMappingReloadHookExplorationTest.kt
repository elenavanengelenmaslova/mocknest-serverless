package nl.vintik.mocknest.infra.aws.runtime.snapstart

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Bug Condition Exploration Test — Property 1: Missing Post-Restore Reload
 *
 * On unfixed code, `Class.forName("...RuntimeMappingReloadHook")` throws
 * `ClassNotFoundException` because the class does not exist yet — the test FAILS,
 * proving the bug exists (no CRaC afterRestore hook to reload mappings).
 *
 * On fixed code, the class exists, implements `org.crac.Resource`, and declares
 * an `afterRestore` method — the test PASSES, verifying the fix.
 *
 * **Validates: Requirements 2.1, 2.2, 2.6**
 */
class RuntimeMappingReloadHookExplorationTest {

    @Test
    fun `Given fixed code When checking for RuntimeMappingReloadHook Then class exists with afterRestore method`() {
        val clazz = Class.forName(
            "nl.vintik.mocknest.infra.aws.runtime.snapstart.RuntimeMappingReloadHook"
        )
        assertTrue(
            clazz.declaredMethods.any { it.name == "afterRestore" },
            "RuntimeMappingReloadHook must have an afterRestore() method"
        )
    }

    @Test
    fun `Given fixed code When checking RuntimeMappingReloadHook Then it implements CRaC Resource`() {
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
