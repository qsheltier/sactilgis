package de.qsheltier.utils.action

import java.util.concurrent.atomic.AtomicBoolean
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test

class DelayedPeriodicActionTest {

	@Test
	fun `can create delayed periodic action`() {
		DelayedPeriodicAction(1, {})
	}

	@Test
	fun `new dpa does not execute action`() {
		val executed = AtomicBoolean(false)
		DelayedPeriodicAction(1, { executed.set(true) })
		MatcherAssert.assertThat(executed.get(), Matchers.equalTo(false))
	}

	@Test
	fun `new dpa does not execute action when invoked less often than the period`() {
		val executed = AtomicBoolean(false)
		val action = DelayedPeriodicAction(3, { executed.set(true) })
		repeat(2, { action() })
		MatcherAssert.assertThat(executed.get(), Matchers.equalTo(false))
	}

	@Test
	fun `new dpa executes action when invoked as often as the period`() {
		val executed = AtomicBoolean(false)
		val action = DelayedPeriodicAction(3, { executed.set(true) })
		repeat(3, { action() })
		MatcherAssert.assertThat(executed.get(), Matchers.equalTo(true))
	}

	@Test
	fun `new dpa does not executes action twice when invoked more often than the period`() {
		val executed = AtomicBoolean(false)
		val action = DelayedPeriodicAction(3, { executed.set(true) })
		repeat(3, { action() })
		executed.set(false)
		repeat(2, { action() })
		MatcherAssert.assertThat(executed.get(), Matchers.equalTo(false))
	}

	@Test
	fun `new dpa executes action twice when invoked twice as often as the period`() {
		val executed = AtomicBoolean(false)
		val action = DelayedPeriodicAction(3, { executed.set(true) })
		repeat(3, { action() })
		executed.set(false)
		repeat(3, { action() })
		MatcherAssert.assertThat(executed.get(), Matchers.equalTo(true))
	}

}
