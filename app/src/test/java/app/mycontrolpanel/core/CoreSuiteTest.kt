package app.mycontrolpanel.core

import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs the plain-Kotlin CoreSuite under JUnit in CI (the same suite also runs locally without JUnit). */
class CoreSuiteTest {
    @Test
    fun coreSuitePasses() {
        val failures = CoreSuite.runAll()
        assertTrue("Core failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
