package de.qsheltier.utils.svn

import java.util.TreeMap

data class BranchDefinition(private val definedRevisionPaths: Collection<Pair<Long, String>>) {

	private val revisionPaths = TreeMap(definedRevisionPaths.toMap())

	fun pathAt(revision: Long) = revisionPaths.floorEntry(revision)?.value
	val earliestRevision: Long = this.revisionPaths.firstEntry().key

}

/**
 * Convenience method to instantiate a [BranchDefinition][BranchDefinition]
 * with a vararg of [pairs][Pair].
 *
 * @param [revisionPaths] The revisions and paths that make up a branch
 * @return A [][BranchDefinition] for the given revisions and paths
 * @throws IllegalArgumentException if [revisionPaths] is empty
 */
fun defineBranch(vararg revisionPaths: Pair<Long, String>) =
	BranchDefinition(revisionPaths.ifEmpty { throw IllegalArgumentException("No path revisions given") }.toList())
