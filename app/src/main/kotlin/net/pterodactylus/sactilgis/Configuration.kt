package net.pterodactylus.sactilgis

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.util.StdConverter

data class Configuration(
	val general: General = General(),
	val branches: MutableList<Branch> = mutableListOf(),
	val committers: MutableList<Committer> = mutableListOf()
) {

	data class General(
		@JsonProperty("subversion-url")
		var subversionUrl: String? = null,
		@JsonProperty("subversion-auth")
		var subversionAuth: SubversionAuth? = null,
		@JsonProperty("committer")
		var committer: Committer? = null,
		@JsonProperty("target-directory")
		var targetDirectory: String? = null,
		@JsonProperty("use-commit-date-from-entry")
		var useCommitDateFromEntry: Boolean? = null,
		@JsonProperty("ignore-global-gitignore-file")
		var ignoreGlobalGitIgnoreFile: Boolean? = null,
		@JsonProperty("sign-commits")
		var signCommits: Boolean? = null
	)

	data class SubversionAuth(
		@JsonProperty("username")
		var username: String? = null,
		@JsonProperty("password")
		var password: String? = null
	)

	class Branch {

		var name: String = ""
		var origin: Origin? = null
		@JsonProperty("revision-paths")
		val revisionPaths: List<RevisionPath> = ArrayList()
		val merges: List<Merge> = ArrayList()
		val tags: List<Tag> = ArrayList()
		val fixes: List<Fix> = ArrayList()

		data class Origin(
			@JsonProperty("tag")
			var tag: String? = null,
			@JsonProperty("branch")
			var branch: String? = "",
			@JsonProperty("revision")
			var revision: Long? = 0,
		)

		class RevisionConverter : StdConverter<String?, Long?>() {

			override fun convert(value: String?): Long {
				if (value == "HEAD") {
					return -1L
				}
				return value!!.toLong()
			}
		}

		class RevisionPath {

			@JsonDeserialize(converter = RevisionConverter::class)
			var revision: Long = 0
			var path: String = ""

		}

		class Merge {

			var revision: Long = 0
			var branch: String = ""

		}

		class Tag {

			var revision: Long = 0
			var name: String = ""
			@JsonProperty("message-revision")
			var messageRevision: Long = 0

		}

		class Fix {

			var revision: Long = 0
			var message: String = ""

		}

	}

	data class Committer(
		@get:JsonProperty("id")
		var subversionId: String = "",
		var name: String = "",
		var email: String = ""
	)

	fun merge(configuration: Configuration): Configuration {
		val mergedConfiguration = Configuration()
		mergedConfiguration.general.subversionUrl = configuration.general.subversionUrl ?: general.subversionUrl
		mergedConfiguration.general.subversionAuth = configuration.general.subversionAuth ?: general.subversionAuth
		mergedConfiguration.general.committer = configuration.general.committer ?: general.committer
		mergedConfiguration.general.targetDirectory = configuration.general.targetDirectory ?: general.targetDirectory
		mergedConfiguration.general.useCommitDateFromEntry = configuration.general.useCommitDateFromEntry ?: general.useCommitDateFromEntry
		mergedConfiguration.general.ignoreGlobalGitIgnoreFile = configuration.general.ignoreGlobalGitIgnoreFile ?: general.ignoreGlobalGitIgnoreFile
		mergedConfiguration.general.signCommits = configuration.general.signCommits ?: general.signCommits
		mergedConfiguration.committers += committers.filterNot { it.subversionId in configuration.committers.map(Committer::subversionId) } + configuration.committers
		mergedConfiguration.branches += configuration.branches.takeIf(List<*>::isNotEmpty) ?: branches
		return mergedConfiguration
	}

}
