package de.qsheltier.sactilgis

import de.qsheltier.utils.svn.BranchDefinition
import de.qsheltier.utils.svn.RepositoryInformation
import de.qsheltier.utils.svn.RepositoryScanner
import java.util.SortedMap
import java.util.SortedSet
import java.util.TreeMap

/**
 * Uses the user-supplied [configuration][Configuration] and the result
 * of the [RepositoryScanner] to create [ConfiguredBranch] objects that
 * contain all relevant information about a branch.
 *
 * This method will call [RepositoryScanner.identifyBranches] to collect
 * information about the branches from the repository after
 * [adding][RepositoryScanner.addBranch] all branches from
 * [configuration] to it.
 *
 * @param [configuration] The user-supplied configuration
 * @param [repositoryScanner] A repository scanner
 * @param [progressHandler] A progress handler for
 * [RepositoryScanner.identifyBranches]
 */
fun configureBranches(configuration: Configuration, repositoryScanner: RepositoryScanner, progressHandler: (Long) -> Unit = {}): Map<String, ConfiguredBranch> {
	configuration.branches.forEach { branch ->
		repositoryScanner.addBranch(branch.name, BranchDefinition(branch.revisionPaths.map { it.revision to it.path }))
	}
	val repositoryInformation = repositoryScanner.identifyBranches(progressHandler)
	val scannedBranches = repositoryInformation.branchRevisions
	return scannedBranches.map { (branch, revisions) ->
		val configurationBranch = configuration.getBranch(branch)
		ConfiguredBranch(
			branch,
			revisions,
			determineBranchOrigin(configurationBranch, configuration, scannedBranches, repositoryInformation, branch),
			configurationBranch.tags.associate { tag ->
				scannedBranches[branch]!!.sameOrNextSmaller(tag.revision) to BranchTag(tag.name, tag.messageRevision)
			},
			determineMergesIntoThisBranch(configurationBranch, configuration, scannedBranches),
			configurationBranch.revisionPaths.associate { it.revision to it.path }.let(::TreeMap),
			configurationBranch.fixes.associate { it.revision to BranchFix(it.message) }
		)
	}
		.associateBy { it.name }
}

/**
 * Contains information about special points in a branch’s history, such
 * as its creation point, merge points, tags, paths, and other attributes.
 *
 * The information in the origin, tag, and merges is already corrected with
 * actual data from the repository, so if a branch and a revision are paired
 * together, the given revision is guaranteed to exist on the branch.
 *
 * @param [name] The name of the branch
 * @param [revisions] All revisions that are a part of this branch
 * @param [origin] The origin of a branch, or `null`
 * @param [tags] All tags that have been created from this branch
 * @param [merges] All merges of other branches into this one
 * @param [revisionPaths] The revisions at which the path of this branch
 * changes in the Subversion repository
 * @param [fixes] Commit message fixes for this branch
 */
class ConfiguredBranch(val name: String, val revisions: SortedSet<Long>, val origin: BranchOrigin? = null, val tags: Map<Long, BranchTag> = emptyMap(), val merges: Map<Long, BranchMerge> = emptyMap(), val revisionPaths: SortedMap<Long, String> = TreeMap(), val fixes: Map<Long, BranchFix> = emptyMap()) {

	/**
	 * Returns the [tag][BranchTag] at the given [revision].
	 *
	 * @param [revision] The revision for which to return the [tag][BranchTag]
	 * @return The [tag][BranchTag] at [revision], or `null` if there is none
	 */
	fun getTagAt(revision: Long) = tags[revision]

	/**
	 * Returs the [merge][BranchMerge] at the given [revision].
	 *
	 * @param [revision] The revision for which to return the [merge][BranchMerge]
	 * @return The [merge][BranchMerge] at [revision], or `null` if there is none
	 */
	fun getMergeAt(revision: Long) = merges[revision]

	/**
	 * Returns the [fix][BranchFix] at the given [revision].
	 *
	 * @param [revision] The revision for which to return the [fix][BranchFix]
	 * @return The [fix][BranchFix] at [revision], or `null` if there is none
	 */
	fun getFixAt(revision: Long) = fixes[revision]

	/**
	 * Returns the path at which this branch can be found in the Subversion
	 * repository at the given [revision].
	 *
	 * @param [revision] The revision for which to return the path
	 * @return The path of this branch in the subversion repository, or
	 * `null` if this path doesn’t exist at [revision].
	 */
	fun getPathAt(revision: Long) = revisionPaths.headMap(revision + 1).entries.lastOrNull()?.value

}

/**
 * Represents the origin of a branch, expressed as the name and revision
 * of the branch this branch was created from.
 *
 * @param [branchName] The name of the origin branch
 * @param [revision] The origin revision
 */
data class BranchOrigin(val branchName: String, val revision: Long)

/**
 * Represents a tag created from a revision of this branch.
 *
 * @param [name] The name of the tag
 * @param [messageRevision] The revision whose commit message contains the
 * message for the tag
 */
data class BranchTag(val name: String, val messageRevision: Long)

/**
 * Represents a merge of another branch into the current branch.
 *
 * @param [branch] The name of the branch to merge
 * @param [revision] The revision of the branch to merge
 */
data class BranchMerge(val branch: String, val revision: Long)

/**
 * Represents a fixed commit message.
 *
 * @param [message] The new commit message
 */
data class BranchFix(val message: String)

private fun SortedSet<Long>.sameOrNextSmaller(n: Long) = headSet(n + 1).last()

private fun determineMergesIntoThisBranch(configurationBranch: Configuration.Branch, configuration: Configuration, scannedBranches: Map<String, SortedSet<Long>>): Map<Long, BranchMerge> = configurationBranch.merges.associate { merge ->
	if (merge.tag != null) {
		configuration.getTag(merge.tag!!).let { tag ->
			configuration.getBranchForTag(merge.tag!!).let { taggedBranch ->
				val revision = scannedBranches[taggedBranch.name]!!.sameOrNextSmaller(tag.revision)
				merge.revision to BranchMerge(taggedBranch.name, revision)
			}
		}
	} else {
		val revision = scannedBranches[merge.branch!!]!!.sameOrNextSmaller(merge.revision!!)
		merge.revision to BranchMerge(merge.branch!!, revision)
	}
}

private fun determineBranchOrigin(configurationBranch: Configuration.Branch, configuration: Configuration, scannedBranches: Map<String, SortedSet<Long>>, repositoryInformation: RepositoryInformation, branch: String): BranchOrigin? {
	return configurationBranch.origin?.let { origin ->
		if (origin.tag != null) {
			configuration.getBranchForTag(origin.tag!!).let { taggedBranch ->
				configuration.getTag(origin.tag!!).let { tag ->
					val revision = scannedBranches[taggedBranch.name]!!.sameOrNextSmaller(tag.revision)
					BranchOrigin(taggedBranch.name, revision)
				}
			}
		} else {
			val revision = scannedBranches[origin.branch!!]!!.sameOrNextSmaller(origin.revision!!)
			BranchOrigin(origin.branch!!, revision)
		}
	} ?: repositoryInformation.branchCreationPoints[branch]?.let { branchCreationPoint ->
		val branch = configuration.findBranch(branchCreationPoint.first, branchCreationPoint.second).name
		val revision = scannedBranches[branch]!!.sameOrNextSmaller(branchCreationPoint.second)
		BranchOrigin(branch, revision)
	}
}

private fun Configuration.getBranch(branchName: String): Configuration.Branch =
	branches.single { it.name == branchName }

private fun Configuration.getBranchForTag(tagName: String): Configuration.Branch =
	branches.single { it.tags.any { it.name == tagName } }

private fun Configuration.getTag(tagName: String): Configuration.Branch.Tag =
	branches.flatMap { it.tags }.single { it.name == tagName }

private fun Configuration.findBranch(path: String, revision: Long): Configuration.Branch =
	branches
		.flatMap { branch -> branch.revisionPaths.map { branch to it } }
		.filter { (_, revisionPath) -> revisionPath.revision <= revision }
		.filter { (_, revisionPath) -> (revisionPath.path == path) || ("$path/".startsWith("${revisionPath.path}/")) }
		.maxByOrNull { (_, revisionPath) -> revisionPath.revision }!!
		.first
