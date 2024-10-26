package de.qsheltier.sactilgis

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.sun.jna.Platform
import de.qsheltier.sactilgis.Configuration.Branch
import de.qsheltier.sactilgis.Configuration.Filter
import de.qsheltier.utils.svn.BranchDefinition
import de.qsheltier.utils.svn.RepositoryScanner
import de.qsheltier.utils.svn.SimpleSVN
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.logging.FileHandler
import java.util.logging.Logger
import java.util.logging.SimpleFormatter
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.tmatesoft.svn.core.SVNDepth
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager
import org.tmatesoft.svn.core.io.SVNRepositoryFactory
import org.tmatesoft.svn.core.wc.SVNClientManager
import org.tmatesoft.svn.core.wc.SVNRevision
import org.tmatesoft.svn.core.wc.SVNStatus
import org.tmatesoft.svn.core.wc.SVNStatusType.STATUS_NORMAL

fun main(vararg arguments: String) {

	val configuration = arguments.map(::File).filter(File::exists)
		.map { xmlMapper.readValue(it, Configuration::class.java) }
		.reduceOrNull { mergedConfiguration, configuration -> mergedConfiguration.merge(configuration) }
		?: throw IllegalStateException("No configuration(s) given.")
	configuration.verify()

	val svnUrl = SVNURL.parseURIDecoded(configuration.general.subversionUrl ?: throw IllegalStateException("Subversion URL not set."))
	val svnRepository = SVNRepositoryFactory.create(svnUrl)
	configuration.general.subversionAuth?.let { subversionAuth ->
		subversionAuth.username?.let { username ->
			subversionAuth.password?.let { password ->
				svnRepository.authenticationManager = BasicAuthenticationManager.newInstance(username, password.toCharArray())
			}
		} ?: throw IllegalStateException("Username and Password not given.")
	}
	val branchDefinitions = configuration.branches.associate { branch -> branch.name to BranchDefinition(*branch.revisionPaths.map { it.revision to it.path }.toTypedArray()) }
	val repositoryScanner = RepositoryScanner(svnRepository)
	branchDefinitions.forEach(repositoryScanner::addBranch)
	val repositoryInformation = repositoryScanner.identifyBranches()
	val fileFilters = configuration.filters.map(Filter::path).map(::Regex).map { regex -> { file: String -> regex.containsMatchIn(file) } }
	logger.info("RepositoryInformation: $repositoryInformation")

	val committers = configuration.committers
		.associate { it.subversionId to PersonIdent(it.name, it.email) }
		.withDefault { PersonIdent(it, "$it@svn") }
	val committer = configuration.general.committer?.let { PersonIdent(it.name, it.email) }

	fun findActualRevision(branch: String, revision: Long) =
		repositoryInformation.brachRevisions[branch]!!.headSet(revision + 1).last()

	val branchRevisionsByTag = configuration.branches.flatMap { branch -> branch.tags.map { it.name to (branch.name to it.revision) } }.toMap()

	fun findActualRevision(tag: String) =
		branchRevisionsByTag[tag]?.let { findActualRevision(it.first, it.second) }

	fun findBranchByPathAndRevision(path: String, revision: Long) =
		branchDefinitions.entries.single { entry ->
			(entry.value.pathAt(revision) == path) || entry.value.pathAt(revision)?.startsWith("$path/") ?: false || (entry.value.pathAt(revision)?.let { path.startsWith("$it/") } ?: false)
		}.key

	val mergeRevisionsByBranch = configuration.branches.associate { it.name to it.merges.associateBy { it.revision } }
	val tagRevisionsByBranch = configuration.branches.associate { branch -> branch.name to branch.tags.associateBy { findActualRevision(branch.name, it.revision) } }
	val fixRevisionsByBranch = configuration.branches.associate { branch -> branch.name to branch.fixes.associateBy { it.revision } }
	val branchOrigins = configuration.branches.associate { branch -> branch.name to branch.origin }.filterValues { it != null }

	(configuration.branches.map(Branch::name) - repositoryInformation.branchCreationPoints.keys - branchOrigins.keys).takeIf { it.isNotEmpty() }?.also {
		println("Branches without creation points: " + it.map { it to branchDefinitions[it]!!.earliestRevision })
	}

	val worklist = Worklist(
		repositoryInformation.brachRevisions,
		repositoryInformation.branchCreationPoints.mapValues { entry -> findBranchByPathAndRevision(entry.value.first, entry.value.second) to entry.value.second } +
				branchOrigins.mapValues { it.value!!.tag?.let { branchRevisionsByTag[it]!! } ?: (it.value!!.branch!! to it.value!!.revision!!) },
		mergeRevisionsByBranch.mapValues { it.value.mapValues { it.value.tag?.let { branchRevisionsByTag[it]!! } ?: (it.value.branch!! to it.value.revision) } }
	)

	val workDirectory = File(configuration.general.targetDirectory ?: throw IllegalStateException("No target directory given."))
	val simpleSvn = SimpleSVN(svnRepository)
	val svnClientManager = SVNClientManager.newInstance()
	svnClientManager.setAuthenticationManager(svnRepository.authenticationManager)
	try {
		Git.open(workDirectory).also {
			printTime("cleanup") {
				svnClientManager.wcClient.doCleanup(workDirectory, true, true, true, false, true, false)
			}
		}
	} catch (e: RepositoryNotFoundException) {
		/* no existing repo found, creating new one. */
		workDirectory.deleteRecursively()
		workDirectory.mkdirs()
		printTime("update") {
			svnClientManager.updateClient.doCheckout(svnUrl, workDirectory, SVNRevision.create(1), SVNRevision.create(1), SVNDepth.EMPTY, false)
		}
		Git.init().setBare(false).setDirectory(workDirectory).setInitialBranch("main").call()
	}.use { gitRepository ->
		if (configuration.general.ignoreGlobalGitIgnoreFile != false) {
			gitRepository.repository.config.setString("core", null, "excludesFile", if (Platform.isWindows()) "NUL" else "/dev/null")
		}
		val stateDirectory = File(workDirectory, ".git/sactilgis")
		stateDirectory.mkdirs()
		val revisionCommits = readCacheFromStateDir(stateDirectory, gitRepository.repository).toMutableMap()
		var currentBranch = gitRepository.repository.branch
		var currentPath = "/"

		var processedRevisionCount = 0
		val plan = worklist.createPlan()
			.filter { (branch, revision) -> (revision to branch) !in revisionCommits }
			.also { logger.info("Plan: $it") }
		val startTime = System.currentTimeMillis()
		plan.forEachIndexed { index, (branch, revision) ->
			logger.info("Processing $branch at Revision $revision")
			print("${"%tT.%<tL".format(System.currentTimeMillis())} ")
			print("(@$revision)")
			print("(${"%.1f".format(100.0 * index / (plan.size - 1))}%)")
			print("(eta: ${(System.currentTimeMillis() - startTime).let { elapsedTime -> ((elapsedTime / ((index + 1.0) / plan.size)) - elapsedTime).toLong().toDurationString() }})")
			print("($branch)")
			val svnRevision = SVNRevision.create(revision)
			if (currentBranch != branch) {
				if (gitRepository.branchDoesNotExist(branch)) {
					val originalRevision = repositoryInformation.branchCreationPoints[branch]?.second
					val startPoint = originalRevision
						?: branchOrigins[branch]?.tag?.let(::findActualRevision)
						?: branchOrigins[branch]?.let { findActualRevision(it.branch!!, it.revision!!) }
					if (startPoint != null) {
							val startBranch = branchOrigins[branch]?.tag?.let { branchRevisionsByTag[it]!!.first }
								?: branchOrigins[branch]?.branch
								?: repositoryInformation.branchCreationPoints[branch]?.let { findBranchByPathAndRevision(it.first, it.second) }
						print("(from $startBranch @ $startPoint)")
						printTime("create") {
							gitRepository.checkout().setCreateBranch(true).setName(branch).setStartPoint(revisionCommits[startPoint to startBranch]!!.name).call()
						}
					} else {
						printTime("orphan") {
							gitRepository.checkout().setName(branch).setOrphan(true).call()
						}
					}
				} else {
					printTime("checkout") {
						gitRepository.checkout().setName(branch).call()
					}
				}
				currentBranch = branch
				revert(svnClientManager, workDirectory, svnRevision)
			}
			val path = branchDefinitions[branch]!!.pathAt(revision)!!
			print("($path)")
			if (path != currentPath) {
				currentPath = path
				printTime("switch") {
					svnClientManager.updateClient.doSwitch(workDirectory, svnUrl.appendPath(path, false), svnRevision, svnRevision, SVNDepth.INFINITY, false, true)
				}
				revert(svnClientManager, workDirectory, svnRevision)
			} else {
				printTime("update") {
					svnClientManager.updateClient.doUpdate(workDirectory, svnRevision, SVNDepth.INFINITY, false, true)
				}
			}
			val filePatterns = printTime("status") {
				gitRepository.status().call().let { status ->
					status.added + status.changed + status.modified + status.missing + status.removed + status.untracked + status.untrackedFolders + status.ignoredNotInIndex
				}.filterNot { it.startsWith(".svn") }
					.filterNot { file -> fileFilters.any { it(file) } }
			}.also { logger.info("Files to update in Git: $it") }
			filePatterns.takeIf { it.isNotEmpty() }?.let {
				printTime("add") {
					gitRepository.add().apply { it.forEach(this::addFilepattern) }.setUpdate(true).call()
					gitRepository.add().apply { it.forEach(this::addFilepattern) }.setUpdate(false).call()
				}
			}
			printTime("commit") {
				val logEntry = simpleSvn.getLogEntry(path, revision)!!
				val commitMessage = (fixRevisionsByBranch[branch]!![revision]?.message?.replaceLineBreaks() ?: logEntry.message) +
						"\n\nSubversion-Original-Commit: $svnUrl$path@$revision\nSubversion-Original-Author: ${logEntry.author}"
				val commitAuthor = committers.getValue(logEntry.author)
				mergeRevisionsByBranch[branch]!![revision]?.let { merge ->
					val commitId: RevCommit = (merge.tag?.let { branchRevisionsByTag[it]!! }
						?: run { merge.branch!! to merge.revision })
						.let { (branch, revision) -> branch to findActualRevision(branch, revision) }
						.also { (branch, revision) -> print("(merge $branch @ $revision)") }
						.let { (branch, revision) -> revisionCommits[revision to branch]!! }
					gitRepository.repository.writeMergeHeads(listOf(commitId))
				}
				val commit = gitRepository.commit()
					.setAllowEmpty(true)
					.setAuthor(PersonIdent(commitAuthor, logEntry.date))
					.setCommitter((committer ?: commitAuthor).let { if (configuration.general.useCommitDateFromEntry != false) PersonIdent(it, logEntry.date) else it })
					.setMessage(commitMessage)
					.setSign(configuration.general.signCommits == true)
					.call()
				revisionCommits[revision to branch] = commit
				storeCommitInState(stateDirectory, revision, branch, commit)
			}
			tagRevisionsByBranch[branch]!![revision]?.let { tag ->
				val tagLogEntry = simpleSvn.getLogEntry("/", tag.messageRevision)!!
				printTime("tag ${tag.name}") {
					gitRepository.tag()
						.setObjectId(revisionCommits[revision to branch])
						.setName(tag.name)
						.setMessage(tagLogEntry.message)
						.setTagger(PersonIdent(committer ?: committers.getValue(tagLogEntry.author), tagLogEntry.date))
						.setAnnotated(true).setSigned(configuration.general.signCommits == true).call()
				}
			}
			processedRevisionCount++
			if (processedRevisionCount.rem(100) == 0) {
				printTime("gc") {
					gitRepository.gc().call()
				}
			}
			println()
		}
	}
}

private fun readCacheFromStateDir(stateDir: File, repository: Repository): Map<Pair<Long, String>, RevCommit> =
	File(stateDir, "commit-cache.txt").let { file ->
		if (file.exists()) {
			file.useLines { lines ->
				lines.map { it.split(",") }
					.map { (it[0].toLong() to it[1]) to it[2] }
					.map { it.first to repository.resolve(it.second)!! }
					.map { it.first to repository.parseCommit(it.second)!! }
					.toMap()
			}
		} else {
			mutableMapOf()
		}
	}

private fun storeCommitInState(stateDir: File, revision: Long, branch: String, commit: RevCommit) {
	File(stateDir, "commit-cache.txt").appendText("$revision,$branch,${commit.name}\n")
}

private fun revert(svnClientManager: SVNClientManager, workDirectory: File, svnRevision: SVNRevision) {
	printTime("revert") {
		val statusLogs = mutableListOf<SVNStatus>()
		svnClientManager.statusClient.doStatus(workDirectory, svnRevision, SVNDepth.INFINITY, false, true, false, false, statusLogs::add, null)
		statusLogs
			.filterNot { it.file == File(workDirectory, ".git") }
			.filter { it.isConflicted || (it.treeConflict != null) || (it.contentsStatus != STATUS_NORMAL) || (it.nodeStatus != STATUS_NORMAL) }
			.map(SVNStatus::getFile)
			.onEach(File::deleteRecursively)
		svnClientManager.wcClient.doRevert(arrayOf(workDirectory), SVNDepth.INFINITY, null)
	}
}

private fun Long.toDurationString() = listOf(TimeUnit.DAYS to Int.MAX_VALUE, TimeUnit.HOURS to 24, TimeUnit.MINUTES to 60, TimeUnit.SECONDS to 60)
	.map { (this / it.first.toMillis(1)) % it.second }
	.dropWhile { it == 0L }
	.ifEmpty { listOf(0L) }
	.let { if (it.size < 2) (listOf(0L) + it) else it }
	.joinToString(separator = ":") { "%02d".format(it) }

private fun String.replaceLineBreaks() = replace(Regex("\\\\n"), "\n")

private fun <T : Any> printTime(text: String, action: () -> T): T {
	val timeBefore = System.currentTimeMillis()
	try {
		return action()
	} finally {
		val timeAfter = System.currentTimeMillis()
		print("($text: ${(timeAfter - timeBefore) / 1000.0}s)")
	}
}

private fun Git.branchDoesNotExist(branch: String) =
	"refs/heads/$branch" !in branchList().call().map(Ref::getName)

private val xmlMapper = XmlMapper()
private val logger = Logger.getLogger("de.qsheltier.sactilgis.Sactilgis").apply {
	System.setProperty("java.util.logging.SimpleFormatter.format", "%tF %<tT %5\$s%n")
	addHandler(FileHandler("./sactilgis.log").apply { this.formatter = SimpleFormatter() })
	useParentHandlers = false
}
