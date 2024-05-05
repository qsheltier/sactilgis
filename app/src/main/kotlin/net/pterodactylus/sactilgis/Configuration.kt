package net.pterodactylus.sactilgis

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.util.StdConverter

class Configuration {

	class Subversion {

		var url: String = ""

	}

	class Branch {

		var name: String = ""

		@JsonProperty("revision-paths")
		val revisionPaths: List<RevisionPath> = ArrayList()
		val merges: List<Merge> = ArrayList()
		val tags: List<Tag> = ArrayList()
		val fixes: List<Fix> = ArrayList()

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
			var messageRevision: Long = 0

		}

		class Fix {

			var revision: Long = 0
			var message: String = ""

		}

	}

	class Committer {

		@get:JsonProperty("id")
		var subversionId: String = ""
		var name: String = ""
		var email: String = ""

	}

	val subversion: Subversion = Subversion()
	val branches: List<Branch> = ArrayList()
	val committers: List<Committer> = ArrayList()

}
