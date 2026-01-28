package de.qsheltier.utils.print

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.text.Charsets.UTF_8
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTime::class, ExperimentalAtomicApi::class)
class PrinterTest {

	@Test
	fun `printer prints action and time`() {
		captureOutput { output ->
			Printer(clock).printTime("foo") {
				time.store(time.load().plus(123.milliseconds))
			}
			assertThat(output(), equalTo("(foo: …)\b\b0.123s)"))
		}
	}

	@Test
	fun `printer prints action and time using invoke method`() {
		captureOutput { output ->
			Printer(clock)("foo") {
				time.store(time.load().plus(123.milliseconds))
			}
			assertThat(output(), equalTo("(foo: …)\b\b0.123s)"))
		}
	}

	@Test
	fun `printer returns value from action`() {
		captureOutput { _ ->
			val returnValue = Printer(clock).printTime("foo") {
				time.store(time.load().plus(123.milliseconds))
				17
			}
			assertThat(returnValue, equalTo(17))
		}
	}

	@Test
	fun `printer can move secondary section before current`() {
		captureOutput { output ->
			Printer(clock).printTime("foo") {
				prepend("prev")
				time.store(time.load().plus(123.milliseconds))
			}
			assertThat(output(), equalTo("(foo: …)\b\b\b\b\b\b\bprev)(foo: …)\b\b0.123s)"))
		}
	}

	private fun captureOutput(output: (() -> String) -> Unit) {
		val previousOutputStream = System.out
		val outputBytes = ByteArrayOutputStream()
		val outputStream = PrintStream(outputBytes)
		System.setOut(outputStream)
		try {
			output.invoke { outputBytes.toString(UTF_8) }
		} finally {
			System.setOut(previousOutputStream)
		}
	}

	private val time = AtomicReference(Clock.System.now())
	private val clock: Clock = object : Clock {
		override fun now() = time.load()
	}

}
