package de.qsheltier.sactilgis

import de.qsheltier.utils.svn.RepositoryInformation
import de.qsheltier.utils.svn.RepositoryScanner
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ConfiguredBranchesTest {

	@Test
	fun `configured branches contain correct revisions`() {
		assertThat(configuredBranches["main"]!!.revisions, contains(1L, 6L, 9L, 10L))
		assertThat(configuredBranches["next"]!!.revisions, contains(2L, 3L, 4L, 7L, 8L))
		assertThat(configuredBranches["three"]!!.revisions, contains(5L))
	}

	@Test
	fun `configured branches have the correct origins`() {
		assertThat(configuredBranches["main"]!!.origin, nullValue())
		assertThat(configuredBranches["next"]!!.origin, equalTo(BranchOrigin("main", 1)))
		assertThat(configuredBranches["three"]!!.origin, equalTo(BranchOrigin("next", 4)))
	}

	@Test
	fun `configured branches have tags at the correct revisions`() {
		assertThat(configuredBranches["main"]!!.getTagAt(1), equalTo(BranchTag("v1", 2)))
		assertThat(configuredBranches["next"]!!.getTagAt(4), equalTo(BranchTag("v2", 4)))
	}

	@Test
	fun `configured branches have tags only at the correct revisions`() {
		testPropertyIsNullAtAllRevisionsWithExceptions(ConfiguredBranch::getTagAt, mapOf("main" to setOf(1L), "next" to setOf(4L), "three" to emptySet()))
	}

	@Test
	fun `configured branches have merges at the correct revisions`() {
		assertThat(configuredBranches["main"]!!.getMergeAt(6), equalTo(BranchMerge("next", 4)))
		assertThat(configuredBranches["next"]!!.getMergeAt(7), equalTo(BranchMerge("three", 5)))
	}

	@Test
	fun `configured branches have merges only at the correct revisions`() {
		testPropertyIsNullAtAllRevisionsWithExceptions(ConfiguredBranch::getMergeAt, mapOf("main" to setOf(6L), "next" to setOf(7L), "three" to emptySet()))
	}

	@Test
	fun `configured branches have fixes at the correct revisions`() {
		assertThat(configuredBranches["main"]!!.getFixAt(6), equalTo(BranchFix("fix main")))
		assertThat(configuredBranches["three"]!!.getFixAt(5), equalTo(BranchFix("fix three")))
	}

	@Test
	fun `configured branches have fixes only at the correct revisions`() {
		testPropertyIsNullAtAllRevisionsWithExceptions(ConfiguredBranch::getFixAt, mapOf("main" to setOf(6L), "next" to emptySet(), "three" to setOf(5L)))
	}

	private fun <T> testPropertyIsNullAtAllRevisionsWithExceptions(property: ConfiguredBranch.(Long) -> T, exceptedRevisions: Map<String, Set<Long>>) {
		(0L..10).forEach { revision ->
			exceptedRevisions.forEach { (branch, exceptions) ->
				if (revision !in exceptions) {
					assertThat(property(configuredBranches[branch]!!, revision), nullValue())
				}
			}
		}
	}

	@BeforeEach
	fun `create configuration`() {
		val configuration = Configuration().apply {
			branches += Configuration.Branch("main").apply {
				revisionPaths += Configuration.Branch.RevisionPath(1, "/main")
				revisionPaths += Configuration.Branch.RevisionPath(10, "/main2")
				tags += Configuration.Branch.Tag(2, "v1", 2)
				merges += Configuration.Branch.Merge(6, tag = "v2")
				fixes += Configuration.Branch.Fix(6, "fix main")
			}
			branches += Configuration.Branch("next").apply {
				revisionPaths += Configuration.Branch.RevisionPath(2, "/next")
				tags += Configuration.Branch.Tag(5, "v2", 4)
				merges += Configuration.Branch.Merge(7, branch = "three")
			}
			branches += Configuration.Branch("three", Configuration.Branch.Origin("v2")).apply {
				revisionPaths += Configuration.Branch.RevisionPath(5, "/three")
				fixes += Configuration.Branch.Fix(5, "fix three")
			}
		}
		val repositoryScanner = mock<RepositoryScanner>()
		whenever(repositoryScanner.identifyBranches(any())).thenReturn(RepositoryInformation(10,
			mapOf(
				"main" to sortedSetOf(1, 6, 9, 10), "next" to sortedSetOf(2, 3, 4, 7, 8), "three" to sortedSetOf(5),
			), mapOf(
				"next" to ("/main" to 1)
			)))
		configuredBranches = configureBranches(configuration, repositoryScanner)
	}

	private lateinit var configuredBranches: Map<String, ConfiguredBranch>

}
