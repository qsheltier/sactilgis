package de.qsheltier.utils.time

import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * A tracker for both progress of and time taken by a task.
 *
 * ## Usage
 *
 * Initialize the tracker with the expected number of progress updates it
 * will take to complete the task, any progress that has already been done,
 * and a [TimeSource] (if [TimeSource.Monotonic] isn’t good enough, which
 * it should be).
 *
 * ```kotlin
 * val tracker = ProgressTimeTracker(719, 38)
 * ```
 *
 * Then, each time your task makes progress, update the tracker.
 *
 * ```kotlin
 * tracker.trackProgress()
 * ```
 *
 * When displaying the progress of your task, you have the following
 * options available:
 *
 * 1. Show a fractional progress; [progress] returns the progress of the
 * task with a range from `0.0` to `1.0`.
 * 2. Show the [elapsed] time.
 * 3. Show the [estimated total][estimated] time.
 *
 */
class ProgressTimeTracker(private val size: Int, private val initialProgress: Int = 0, timeSource: TimeSource = TimeSource.Monotonic) {

	/**
	 * Increments the progress of this tracker. The progress cannot be
	 * increased beyond the initially given size, and will be capped to that
	 * size.
	 *
	 * @param[increment] The amount by which to increase the progress of
	 * this tracker; must be >= 1, default is 1
	 */
	fun trackProgress(increment: Int = 1) {
		require(increment >= 1) { "increment must be >= 1" }
		completed = min(size, completed + increment)
	}

	/**
	 * Returns the progress of this tracker, in a fractional value from
	 * `0.0` (new tracker without progress) and `1.0` (completed progress).
	 *
	 * @return The progress of this tracker, from `0.0` to `1.0`
	 */
	val progress: Double get() = completed.toDouble() / size

	/**
	 * Returns the duration of time that has elapsed since the tracker was
	 * created.
	 *
	 * @return The duration since the tracker was created
	 */
	val elapsed: Duration get() = startMark.elapsedNow()

	/**
	 * Returns the estimated duration of the tracker’s completion. This
	 * value is calculated from any progress made beyond the initial
	 * progress the tracker was created with.
	 *
	 * @return The estimated duration of this tracker’s completion
	 */
	val estimated: Duration get() = elapsed.div((completed - initialProgress) / (size.toDouble() - initialProgress))

	init {
		require(size > 0) { "size must be greater than 0" }
		require(initialProgress >= 0) { "initialProgress must be greater than or equal to 0" }
	}

	private var completed = initialProgress
	private val startMark = timeSource.markNow()

}

/**
 * Iterates over the given items, increasing the progress of this progress
 * tracker after each item. This progress tracker is set as receiver on the
 * invoked action, making it easier to access the
 * [current progress][ProgressTimeTracker.progress] and
 * [duration][ProgressTimeTracker.elapsed].
 *
 * ## Usage
 *
 * ```kotlin
 * tracker.track(items) { item ->
 *   println("progress: ${progress * 100}%")
 *   item.doStuff()
 * }
 * ```
 *
 * @param[items] The items to iterate over
 * @param[action] The action to perform on each item
 */
fun <T> ProgressTimeTracker.track(items: Iterable<T>, action: ProgressTimeTracker.(T) -> Unit) {
	items.forEach { item ->
		action(this, item)
		trackProgress()
	}
}
