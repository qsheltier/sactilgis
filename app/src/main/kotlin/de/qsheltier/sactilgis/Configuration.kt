package de.qsheltier.sactilgis

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import de.qsheltier.sactilgis.Configuration.Branch.Merge
import de.qsheltier.sactilgis.Configuration.Branch.Origin
import de.qsheltier.sactilgis.Configuration.Branch.Tag
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.util.StdConverter

data class Configuration(
	val general: General = General(),
	val branches: MutableList<Branch> = mutableListOf(),
	val committers: MutableList<Committer> = mutableListOf(),
	val filters: MutableList<Filter> = mutableListOf()
) {

	data class General(
		@param:JsonProperty("subversion-url")
		var subversionUrl: String? = null,
		@param:JsonProperty("subversion-auth")
		var subversionAuth: SubversionAuth? = null,
		var committer: Committer? = null,
		@param:JsonProperty("target-directory")
		var targetDirectory: String? = null,
		@param:JsonProperty("use-commit-date-from-entry")
		var useCommitDateFromEntry: Boolean? = null,
		@param:JsonProperty("ignore-global-gitignore-file")
		var ignoreGlobalGitIgnoreFile: Boolean? = null,
		@param:JsonProperty("sign-commits")
		var signCommits: Boolean? = null,
		@param:JsonProperty("last-revision")
		var lastRevision: Long? = null,
	)

	data class SubversionAuth(
		var username: String? = null,
		var password: String? = null
	)

	data class Branch(
		var name: String = "",
		var origin: Origin? = null,
		@param:JsonProperty("revision-paths")
		val revisionPaths: MutableList<RevisionPath> = mutableListOf(),
		val merges: MutableList<Merge> = mutableListOf(),
		val tags: MutableList<Tag> = mutableListOf(),
		val fixes: MutableList<Fix> = mutableListOf()
	) {

		data class Origin(
			var tag: String? = null,
			var branch: String? = null,
			var revision: Long? = null,
		)

		class RevisionConverter : StdConverter<String?, Long?>() {

			override fun convert(value: String?): Long {
				if (value == "HEAD") {
					return -1L
				}
				return value!!.toLong()
			}

		}

		data class RevisionPath(
			@param:JsonDeserialize(converter = RevisionConverter::class)
			var revision: Long = 0,
			var path: String = ""
		)

		data class Merge(
			var revision: Long = 0,
			var branch: String? = null,
			var tag: String? = null
		)

		data class Tag(
			var revision: Long = 0,
			var name: String = "",
			@param:JsonProperty("message-revision")
			var messageRevision: Long = 0
		)

		data class Fix(
			var revision: Long = 0,
			var message: String = ""
		)

	}

	data class Committer(
		@param:JsonProperty("id")
		var subversionId: String = "",
		var name: String = "",
		var email: String = ""
	)

	data class Filter(@JsonValue var path: String = "")

	fun merge(configuration: Configuration): Configuration {
		val mergedConfiguration = Configuration()
		mergedConfiguration.general.subversionUrl = configuration.general.subversionUrl ?: general.subversionUrl
		mergedConfiguration.general.subversionAuth = configuration.general.subversionAuth ?: general.subversionAuth
		mergedConfiguration.general.committer = configuration.general.committer ?: general.committer
		mergedConfiguration.general.targetDirectory = configuration.general.targetDirectory ?: general.targetDirectory
		mergedConfiguration.general.useCommitDateFromEntry = configuration.general.useCommitDateFromEntry ?: general.useCommitDateFromEntry
		mergedConfiguration.general.ignoreGlobalGitIgnoreFile = configuration.general.ignoreGlobalGitIgnoreFile ?: general.ignoreGlobalGitIgnoreFile
		mergedConfiguration.general.signCommits = configuration.general.signCommits ?: general.signCommits
		mergedConfiguration.general.lastRevision = configuration.general.lastRevision ?: general.lastRevision
		mergedConfiguration.committers += committers.filterNot { it.subversionId in configuration.committers.map(Committer::subversionId) } + configuration.committers
		mergedConfiguration.branches += configuration.branches.takeIf(List<*>::isNotEmpty) ?: branches
		mergedConfiguration.filters += filters + configuration.filters
		return mergedConfiguration
	}

	fun verify() {
		branches.filter { isInvalidRefName(it.name) }.onNotEmpty { throw IllegalStateException("Invalid branch names: ${it.map(Branch::name)}") }
		branches.filter { it.revisionPaths.isEmpty() }.onNotEmpty { throw IllegalStateException("Empty revision paths: $it") }

		val allDefinedBranches = branches.map(Branch::name)
		if (allDefinedBranches.size != allDefinedBranches.distinct().size) {
			throw IllegalStateException("Duplicate branches found: ${allDefinedBranches.groupBy { it }.filterValues { it.size > 1 }.keys}")
		}

		val allDefinedTags = branches.flatMap { it.tags }.map(Tag::name)
		if (allDefinedTags.size != allDefinedTags.distinct().size) {
			throw IllegalStateException("Duplicate tags found: ${allDefinedTags.groupBy { it }.filterValues { it.size > 1 }.keys}")
		}
		allDefinedTags.filter(::isInvalidRefName).onNotEmpty { throw IllegalStateException("Invalid tag names: ${it}") }

		val allOriginTags = branches.mapNotNull(Branch::origin).mapNotNull(Origin::tag)
		if (!allDefinedTags.containsAll(allOriginTags)) {
			throw IllegalStateException("Missing origin tag found: ${allOriginTags - allDefinedTags}")
		}

		val allOriginBranches = branches.mapNotNull(Branch::origin).mapNotNull(Origin::branch)
		if (allOriginBranches.any { it !in allDefinedBranches }) {
			throw IllegalStateException("Missing origin branch found: ${allOriginBranches.filter { it !in allDefinedBranches }}")
		}

		val allMerges = branches.flatMap(Branch::merges)
		if (allMerges.any { (it.branch != null) && (it.branch !in allDefinedBranches) }) {
			throw IllegalStateException("Missing branch to merge found: ${allMerges.filter { (it.branch != null) && (it.branch !in allDefinedBranches) }.map(Merge::branch)}")
		}
		if (allMerges.any { (it.tag != null) && (it.tag !in allDefinedTags) }) {
			throw IllegalStateException("Missing tag to merge found: ${allMerges.filter { (it.tag != null) && (it.tag !in allDefinedTags) }.map(Merge::tag)}")
		}
		if (allMerges.any { it.revision == 0L }) {
			throw IllegalStateException("Missing revision of merge found: ${allMerges.filter { it.revision == 0L }.map { "${it.tag}/${it.branch}" }}")
		}
	}

}

private fun <T> Collection<T>.onNotEmpty(action: (Collection<T>) -> Unit) = run {
	if (isNotEmpty()) {
		action(this)
	}
}

private fun isInvalidRefName(ref: String) =
	ref.split("/").any { it.startsWith(".") || it.endsWith(".lock") } ||
			ref.contains("..") ||
			ref.any { it in setOf(' ', '~', '^', ':', '?', '*', '[') || (it < ' ') || (it == '\u007f') } ||
			ref.startsWith("/") || ref.endsWith("/") || "//" in ref ||
			ref.endsWith(".") ||
			"@{" in ref ||
			ref == "@" ||
			"\\" in ref
