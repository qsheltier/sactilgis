package de.qsheltier.utils.time

import kotlin.time.Duration
import kotlin.time.DurationUnit.MILLISECONDS
import kotlin.time.DurationUnit.NANOSECONDS
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.time.toDuration
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProgressTimeTrackerTest {

	@Test
	fun `progress start at 0 percent`() {
		val progressTimeTracker = ProgressTimeTracker(1234)
		assertThat(progressTimeTracker.progress, equalTo(0.0))
	}

	@Test
	fun `progress cannot be created with size of 0`() {
		assertThrows<IllegalArgumentException> { ProgressTimeTracker(0) }
	}

	@Test
	fun `progress elapsed time starts at 0`() {
		val progressTimeTracker = ProgressTimeTracker(1234, timeSource = TestTimeSource(1000, listOf(1000)))
		assertThat(progressTimeTracker.elapsed.toLong(NANOSECONDS), equalTo(0L))
	}

	@Test
	fun `progress can be progressed`() {
		val progressTimeTracker = ProgressTimeTracker(1234)
		progressTimeTracker.trackProgress()
		assertThat(progressTimeTracker.progress, closeTo(1 / 1234.0, 0.001))
	}

	@Test
	fun `progress can be progressed with bigger increment`() {
		val progressTimeTracker = ProgressTimeTracker(1234)
		progressTimeTracker.trackProgress(47)
		assertThat(progressTimeTracker.progress, closeTo(47 / 1234.0, 0.001))
	}

	@Test
	fun `progress cannot be progressed with zero increment`() {
		val progressTimeTracker = ProgressTimeTracker(1234)
		assertThrows<IllegalArgumentException> { progressTimeTracker.trackProgress(0) }
	}

	@Test
	fun `progress cannot be progressed beyond completion`() {
		val progressTimeTracker = ProgressTimeTracker(2)
		repeat(3) { progressTimeTracker.trackProgress() }
		assertThat(progressTimeTracker.progress, equalTo(1.0))
	}

	@Test
	fun `progress cannot be progressed beyond completion with bigger increment`() {
		val progressTimeTracker = ProgressTimeTracker(2)
		progressTimeTracker.trackProgress(10)
		assertThat(progressTimeTracker.progress, equalTo(1.0))
	}

	@Test
	fun `tracker can be started with progress`() {
		val progressTimeTracker = ProgressTimeTracker(50, 20)
		assertThat(progressTimeTracker.progress, equalTo(0.4))
	}

	@Test
	fun `tracker cannot be started with negative progress`() {
		assertThrows<IllegalArgumentException> { ProgressTimeTracker(50, -20) }
	}

	@Test
	fun `tracker without progress has infinite estimated time`() {
		val progressTimeTracker = ProgressTimeTracker(100, timeSource = TestTimeSource(1000, listOf(2000)))
		assertThat(progressTimeTracker.estimated, equalTo(Duration.INFINITE))
	}

	@Test
	fun `tracker with quarter progress has four times as much estimated time as elapsed time`() {
		val progressTimeTracker = ProgressTimeTracker(100, timeSource = TestTimeSource(1000, listOf(2000)))
		progressTimeTracker.trackProgress(25)
		assertThat(progressTimeTracker.estimated, equalTo(4000.toDuration(MILLISECONDS)))
	}

	@Test
	fun `tracker with three fifths progress but initial progress of half will report estimated time based on actual progress`() {
		val progressTimeTracker = ProgressTimeTracker(100, 50, TestTimeSource(1000, listOf(2000)))
		progressTimeTracker.trackProgress(10)
		assertThat(progressTimeTracker.estimated, equalTo(5000.toDuration(MILLISECONDS)))
	}

	@Test
	fun `track method tracks progress automatically`() {
		val progressTimeTracker = ProgressTimeTracker(100)
		progressTimeTracker.track(0 until 63) {}
		assertThat(progressTimeTracker.progress, closeTo(0.63, 0.001))
	}

	@Test
	fun `track method calls action with correct values`() {
		val receivedValues = mutableListOf<Int>()
		val progressTimeTracker = ProgressTimeTracker(100)
		progressTimeTracker.track(0 until 37) { receivedValues += it }
		assertThat(receivedValues, contains(*(0 until 37).toList().toTypedArray()))
	}

}

private class TestTimeSource(private val start: Long, private val times: List<Long>) : TimeSource {
	override fun markNow(): TimeMark = TestMark(start, times)
}

private class TestMark(private val start: Long, private val times: List<Long>) : TimeMark {
	override fun elapsedNow() = (times[counter++ % times.size] - start).toDuration(MILLISECONDS)
	private var counter = 0;
}
