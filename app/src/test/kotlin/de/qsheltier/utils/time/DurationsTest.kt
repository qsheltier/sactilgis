package de.qsheltier.utils.time

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

class DurationsTest {

	@Test
	fun `0L is displayed as '0s'`() {
		assertThat(0L.toDurationString(), equalTo("0s"))
	}

	@Test
	fun `values below 1000 are displayed as '0s'`() {
		assertThat(999L.toDurationString(), equalTo("0s"))
	}

	@Test
	fun `34567 is displayed as '34s'`() {
		assertThat(34567L.toDurationString(), equalTo("34s"))
	}

	@Test
	fun `98765 is displayed as '1m 38s'`() {
		assertThat(98765L.toDurationString(), equalTo("1m 38s"))
	}

	@Test
	fun `7425000 is displayed as '2h 3m'`() {
		assertThat(7425000L.toDurationString(), equalTo("2h 3m"))
	}

	@Test
	fun `100000000 is displayed as '1d '`() {
		assertThat(100000000L.toDurationString(), equalTo("1d 3h"))
	}

}
