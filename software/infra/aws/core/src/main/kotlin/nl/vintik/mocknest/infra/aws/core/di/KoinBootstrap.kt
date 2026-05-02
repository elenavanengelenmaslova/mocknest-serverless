package nl.vintik.mocknest.infra.aws.core.di

import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.module.Module

/**
 * Idempotent Koin bootstrap — safe to call from multiple Lambda handlers
 * or test classes sharing the same JVM.
 *
 * `startKoin` throws if called twice in the same JVM, so we use
 * double-checked locking to guarantee exactly-once initialization.
 */
object KoinBootstrap {

    @Volatile
    private var initialized = false

    /**
     * Starts Koin with the given [modules] if it has not been started yet.
     * Subsequent calls are no-ops.
     *
     * `allowOverride(false)` catches accidental duplicate bean definitions
     * (e.g. S3Client declared in both coreModule and runtimeModule).
     */
    fun init(modules: List<Module>) {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    startKoin {
                        allowOverride(false)
                        modules(modules)
                    }
                    initialized = true
                }
            }
        }
    }

    /**
     * Returns the global Koin instance.
     * Used by Lambda handlers for priming and CRaC hook registration.
     */
    fun getKoin(): Koin = GlobalContext.get()

    /**
     * Resets the bootstrap state. Intended for test teardown only —
     * call [org.koin.core.context.stopKoin] before calling this to fully clean up.
     */
    fun reset() {
        synchronized(this) {
            initialized = false
        }
    }
}
