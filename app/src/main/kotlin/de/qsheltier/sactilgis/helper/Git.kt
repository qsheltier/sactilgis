package de.qsheltier.sactilgis.helper

import de.qsheltier.sactilgis.ConfiguredBranch
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.useLines
import kotlin.io.path.writeText
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.revwalk.RevCommit

fun Git.createTag(commit: RevCommit, tagName: String, tagMessage: String, tagger: PersonIdent): Ref =
	tag()
		.setObjectId(commit)
		.setName(tagName)
		.setMessage(tagMessage)
		.setTagger(tagger)
		.setAnnotated(true)
		.setSigned(false)
		.call()

fun Git.createCommit(author: PersonIdent, committer: PersonIdent, message: String): RevCommit =
	commit()
		.setAllowEmpty(true)
		.setAuthor(author)
		.setCommitter(committer)
		.setMessage(message)
		.setSign(false)
		.call()

fun Git.readCommitCache(): Map<Pair<Long, String>, RevCommit> =
	repository.directory.toPath().resolve("sactilgis").resolve("commit-cache.txt").let { commitCacheFile ->
		if (commitCacheFile.exists()) {
			commitCacheFile.useLines { lines ->
				lines.map { it.split(",", limit = 3) }
					.map { (it[0].toLong() to it[2]) to it[1] }
					.map { it.first to repository.resolve(it.second)!! }
					.map { it.first to repository.parseCommit(it.second)!! }
					.toMap()
			}
		} else {
			mutableMapOf()
		}
	}

fun Git.storeCommitInCache(revision: Long, branch: String, commit: RevCommit) =
	with(repository.directory.toPath().resolve("sactilgis")) {
		createDirectories()
		resolve("commit-cache.txt")
			.writeText("$revision,${commit.name},$branch\n", options = arrayOf(StandardOpenOption.CREATE, StandardOpenOption.APPEND))
	}

fun Git.branchDoesNotExist(branch: String) =
	"refs/heads/$branch" !in branchList().call().map(Ref::getName)

fun Git.switchBranch(branch: String, configuredBranch: ConfiguredBranch, revisionCommits: MutableMap<Pair<Long, String>, RevCommit>, printTime: (String, action: () -> Unit) -> Unit) {
	if (branchDoesNotExist(branch)) {
		if (configuredBranch.origin != null) {
			print("(from ${configuredBranch.origin.branchName} @ ${configuredBranch.origin.revision})")
			printTime("create") {
				checkout().setCreateBranch(true).setName(branch).setStartPoint(revisionCommits[configuredBranch.origin.revision to configuredBranch.origin.branchName]!!.name).call()
			}
		} else {
			printTime("orphan") {
				checkout().setName(branch).setOrphan(true).call()
			}
		}
	} else {
		printTime("checkout") {
			checkout().setName(branch).call()
		}
	}
}
