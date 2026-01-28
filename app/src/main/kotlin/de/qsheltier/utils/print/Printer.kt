package de.qsheltier.utils.print

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Component that formats the output of Sactilgis a bit nicer, separating
 * different stages of the process into sections that are automatically
 * timed.
 *
 * ## Usage
 *
 * ```kotlin
 * val printTime = Printer()
 *
 * printTime("doing stuff") {
 *   doStuff()
 * }
 * ```
 *
 * This will result in output like `(doing stuff: …)` which the action is
 * still ongoing, and turn in into `(doing stuff: 1.421s)` when it’s done.
 *
 * It is also possible during the action to add output that is then prepended
 * to the status of the current action, i.e. `(doing stuff: …)` turns into
 * `(baked a caked)(doing stuff: …)` during the action when
 * `prepend("baked a cake")` is called.
 *
 * @param [clock] A [Clock] to use for measuring
 */
@OptIn(ExperimentalTime::class)
class Printer(private val clock: Clock = Clock.System) {

	/**
	 * Adds a [section] to the output, executes the [action], and updates the
	 * [section] output with the time taken by the [action].
	 *
	 * @param [section] The title of the section
	 * @param [action] The action to run and measure
	 */
	fun <R> printTime(section: String, action: Prepender.() -> R): R {
		print("($section: …)")
		val start = clock.now()
		try {
			return action(Prepender(section))
		} finally {
			val end = clock.now()
			print("\b\b${(end.toEpochMilliseconds() - start.toEpochMilliseconds()) / 1000.0}s)")
		}
	}

	/**
	 * Allows invoking [printTime] without actually naming the method.
	 *
	 * ## Usage
	 *
	 * ```kotlin
	 * val printTime = Printer()
	 * printTime("doing stuff") {
	 *   doStuff()
	 * }
	 * ```
	 *
	 * @see printTime
	 */
	operator fun <R> invoke(section: String, action: Prepender.() -> R): R = printTime(section, action)

	/**
	 * Component that can [prepend a section][prepend] to a [current section][currentSection].
	 */
	class Prepender(private val currentSection: String) {

		/**
		 * Prepends the given section to the current section.
		 *
		 * @param [section] The section to prepend
		 */
		fun prepend(section: String) {
			print("\b".repeat(currentSection.length + 4) + "$section)($currentSection: …)")
		}

	}

}
