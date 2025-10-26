package de.qsheltier.utils.action

/**
 * Executes an action every *nth* time [invoke()][invoke] is called.
 *
 * This object is not safe for usage from multiple threads.
 *
 * @param period The period for the invocation
 * @param action The action to perform
 */
class DelayedPeriodicAction(private val period: Int, private val action: () -> Unit) {

	/**
	 * Counts an invocation. Every [n][period] times [action][action] is executed.
	 */
	operator fun invoke() {
		++counter
		if (counter == period) {
			action()
			counter = 0
		}
	}

	private var counter: Int = 0

}
