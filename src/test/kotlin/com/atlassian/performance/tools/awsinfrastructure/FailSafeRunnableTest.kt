package com.atlassian.performance.tools.awsinfrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class FailSafeRunnableTest {
    @Test
    fun shouldExecuteAllEvenIfFirstFails() {
        var executed1 = false
        var executed2 = false
        var executed3 = false
        val runnable = FailSafeRunnable(
            listOf(
                Runnable { executed1 = true; throw Exception("Fail 1") },
                Runnable { executed2 = true; throw Exception("Fail 2") },
                Runnable { executed3 = true; throw Exception("Fail 3") }
            )
        )

        try {
            runnable.run()
        } catch (e: Exception) {
            // Expected and ignored, so that we can go to asserts
        }

        assertThat(executed1).isTrue()
        assertThat(executed2).isTrue()
        assertThat(executed3).isTrue()
    }

    @Test
    fun shouldThrowAllFailures() {
        val runnable = FailSafeRunnable(
            listOf(
                Runnable { throw Exception("Banana") },
                Runnable { throw Exception("Apple") },
                Runnable { throw Exception("Pear") },
                Runnable { throw Exception("Peach") }
            )
        )

        val exception = try {
            runnable.run()
            null
        } catch (e: Exception) {
            e
        }

        val allExceptions = listOf(exception!!) + exception.suppressed.toList()
        val allMessages = allExceptions.map { it.message }
        assertThat(allMessages).contains("Banana", "Apple", "Pear", "Peach")
    }


    @Test
    fun shouldExecuteAll() {
        val allIndexes = Array(20) { it }
        val finishedIndexes = mutableListOf<Int>()
        val runnable = FailSafeRunnable(
            allIndexes.map { index -> Runnable { finishedIndexes.add(index) } }
        )

        runnable.run()

        assertThat(finishedIndexes).contains(*allIndexes)
    }
}