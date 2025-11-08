package de.qsheltier.sactilgis

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.greaterThan
import org.junit.jupiter.api.Test

class WorklistTest {

	@Test
	fun `worklist for single branch contains all revisions of branch in order`() {
		val worklist = createWorklist(mapOf("main" to ConfiguredBranch("main", sortedSetOf(1L, 2, 3))))
		assertThat(worklist.createPlan(), contains("main" to 1L, "main" to 2L, "main" to 3L))
	}

	@Test
	fun `worklist for branch created from other branch lists first branch first, then second`() {
		val worklist = createWorklist(mapOf(
			"main" to ConfiguredBranch("main", sortedSetOf<Long>(1, 2, 3, 4, 8, 9)),
			"second" to ConfiguredBranch("second", sortedSetOf<Long>(5, 6, 7), BranchOrigin("main", 3))
		))
		assertThat(
			worklist.createPlan(), contains(
				"main" to 1L,
				"main" to 2L,
				"main" to 3L,
				"main" to 4L,
				"main" to 8L,
				"main" to 9L,
				"second" to 5L,
				"second" to 6L,
				"second" to 7L,
			)
		)
	}

	@Test
	fun `worklist for two merged branches lists first branch, second branch, first branch again`() {
		val worklist = createWorklist(mapOf(
			"main" to ConfiguredBranch("main", sortedSetOf<Long>(1, 2, 3, 4, 6, 8, 9), merges = merge(8L to "second" to 7L)),
			"second" to ConfiguredBranch("second", sortedSetOf<Long>(5, 7), BranchOrigin("main", 3))
		))
		assertThat(
			worklist.createPlan(), contains(
				"main" to 1L,
				"main" to 2L,
				"main" to 3L,
				"main" to 4L,
				"main" to 6L,
				"second" to 5L,
				"second" to 7L,
				"main" to 8L,
				"main" to 9L,
			)
		)
	}

	@Test
	fun `worklist for mutliple back-and-forth merges lists branches in correct order`() {
		val worklist = createWorklist(mapOf(
			"main" to ConfiguredBranch("main", sortedSetOf<Long>(1, 2, 3, 4, 6, 8, 9, 13, 14), merges = merge(8L to "second" to 7L, 13L to "second" to 12L)),
			"second" to ConfiguredBranch("second", sortedSetOf<Long>(5, 7, 10, 11, 12, 15, 16), BranchOrigin("main", 3), merges = merge(12L to "main" to 11L))
		))
		assertThat(
			worklist.createPlan(), contains(
				"main" to 1L,
				"main" to 2L,
				"main" to 3L,
				"main" to 4L,
				"main" to 6L,
				"second" to 5L,
				"second" to 7L,
				"main" to 8L,
				"main" to 9L,
				"second" to 10L,
				"second" to 11L,
				"second" to 12L,
				"main" to 13L,
				"main" to 14L,
				"second" to 15L,
				"second" to 16L,
			)
		)
	}

	@Test
	fun `worklist for first merge on-non main branch lists branches in correct order`() {
		val worklist = createWorklist(mapOf(
			"main" to ConfiguredBranch("main", sortedSetOf<Long>(1, 2, 3, 4)),
			"second" to ConfiguredBranch("second", sortedSetOf<Long>(5, 6), BranchOrigin("main", 1), merges = merge(6L to "main" to 4L))
		))
		val plan = worklist.createPlan()
		assertThat(
			plan, contains(
				"main" to 1L,
				"main" to 2L,
				"main" to 3L,
				"main" to 4L,
				"second" to 5L,
				"second" to 6L,
			)
		)
	}

	@Test
	fun `worklist for merges with three branches lists branches in correct order`() {
		val worklist = createWorklist(mapOf(
			"main" to ConfiguredBranch("main", sortedSetOf<Long>(1, 2, 3, 4, 9, 13, 14, 17, 18), merges = merge(9L to "third" to 8L, 14L to "second" to 13L, 17L to "third" to 16L, 18L to "second" to 17L)),
			"second" to ConfiguredBranch("second", sortedSetOf<Long>(5, 6, 10, 15, 16), BranchOrigin("main", 1), merges = merge(10L to "main" to 9L, 15L to "third" to 14L, 16L to "main" to 15L)),
			"third" to ConfiguredBranch("third", sortedSetOf<Long>(7, 8, 11, 12), BranchOrigin("main", 2), merges = merge(8L to "main" to 4L, 12L to "second" to 11L))
		))
		val plan = worklist.createPlan()
		assertThat(
			plan, contains(
				"main" to 1L,
				"main" to 2L,
				"main" to 3L,
				"main" to 4L,
				"third" to 7L,
				"third" to 8L,
				"main" to 9L,
				"main" to 13L,
				"second" to 5L,
				"second" to 6L,
				"second" to 10L,
				"main" to 14L,
				"third" to 11L,
				"third" to 12L,
				"main" to 17L,
				"second" to 15L,
				"second" to 16L,
				"main" to 18L,
			)
		)
	}

	@Test
	fun `worklist lists orphan branches with branch creation points after their origin branches`() {
		val worklist = createWorklist(mapOf(
			"main" to ConfiguredBranch("main", sortedSetOf<Long>(1, 2, 3)),
			"third" to ConfiguredBranch("third", sortedSetOf<Long>(4, 6, 8)),
			"first" to ConfiguredBranch("first", sortedSetOf<Long>(5, 9, 10), BranchOrigin("main", 2)),
			"second" to ConfiguredBranch("second", sortedSetOf<Long>(7, 11))
		))
		val plan = worklist.createPlan()
		assertThat(plan.indexOf("first" to 5L), greaterThan(plan.indexOf("main" to 2L)))
	}

	@Test
	fun `worklist for revisions existing on multiple branches lists revisions multiple times`() {
		val worklist = createWorklist(mapOf(
			"main" to ConfiguredBranch("main", sortedSetOf<Long>(1, 2, 3)),
			"second" to ConfiguredBranch("second", sortedSetOf<Long>(2, 4))
		))
		val plan = worklist.createPlan()
		assertThat(plan, contains(
			"main" to 1L,
			"main" to 2L,
			"main" to 3L,
			"second" to 2L,
			"second" to 4L
		))
	}

}

private fun merge(vararg merges: Pair<Pair<Long, String>, Long>) =
	merges.associate { it.first.first to BranchMerge(it.first.second, it.second) }
