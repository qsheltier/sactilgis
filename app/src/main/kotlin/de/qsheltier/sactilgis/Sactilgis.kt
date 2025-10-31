package de.qsheltier.sactilgis

import com.sun.jna.Platform
import de.qsheltier.sactilgis.Configuration.Filter
import de.qsheltier.utils.action.DelayedPeriodicAction
import de.qsheltier.utils.git.createCommit
import de.qsheltier.utils.git.createTag
import de.qsheltier.utils.git.readCommitCache
import de.qsheltier.utils.git.storeCommitInCache
import de.qsheltier.utils.svn.RepositoryScanner
import de.qsheltier.utils.svn.SimpleSVN
import de.qsheltier.utils.time.toDurationString
import java.io.File
import java.util.TimeZone
import java.util.logging.FileHandler
import java.util.logging.Logger
import java.util.logging.SimpleFormatter
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Ref
import org.tmatesoft.svn.core.SVNDepth
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager
import org.tmatesoft.svn.core.io.SVNRepositoryFactory
import org.tmatesoft.svn.core.wc.SVNClientManager
import org.tmatesoft.svn.core.wc.SVNRevision
import org.tmatesoft.svn.core.wc.SVNStatus
import org.tmatesoft.svn.core.wc.SVNStatusType.STATUS_NORMAL
import tools.jackson.dataformat.xml.XmlMapper
import tools.jackson.module.kotlin.kotlinModule

fun main(vararg arguments: String) {

	val configuration = readConfigurationFiles(*arguments).merge()
	configuration.verify()

	val zoneId = (configuration.general.timezone?.let(TimeZone::getTimeZone) ?: TimeZone.getDefault()).toZoneId()
	val svnUrl = SVNURL.parseURIEncoded(configuration.general.subversionUrl ?: throw IllegalStateException("Subversion URL not set."))
	val svnRepository = SVNRepositoryFactory.create(svnUrl)
	configuration.general.subversionAuth?.let { subversionAuth ->
		subversionAuth.username?.let { username ->
			subversionAuth.password?.let { password ->
				svnRepository.authenticationManager = BasicAuthenticationManager.newInstance(username, password.toCharArray())
			}
		} ?: throw IllegalStateException("Username and Password not given.")
	}
	val repositoryScanner = RepositoryScanner(svnRepository)
	val configuredBranches = configureBranches(configuration, repositoryScanner) { revision -> print("(@$revision)\r") }
	val fileFilters = configuration.filters.map(stringToFilter)
	val branchFilters = configuration.branches.associate { it.name to it.filters.map(stringToFilter) }

	val committers = configuration.committers
		.associate { it.subversionId to PersonIdent(it.name, it.email) }
		.withDefault { PersonIdent(it, "$it@svn") }
	val committer = configuration.general.committer?.let { PersonIdent(it.name, it.email) }

	val worklist = Worklist(
		configuredBranches.map { (name, branch) -> name to branch.revisions }.toMap(),
		configuredBranches
			.map { (name, branch) -> name to branch.origin }
			.filter { (_, origin) -> origin != null }
			.associate { (name, origin) -> name to (origin!!.branchName to origin.revision) },
		configuredBranches
			.map { (name, branch) -> name to branch.merges.mapValues { it.value.branch to it.value.revision }.toMap() }
			.toMap()
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
		val revisionCommits = gitRepository.readCommitCache().toMutableMap()
		var currentBranch = gitRepository.repository.branch
		var currentPath = "/"

		val periodicGarbageCollection = DelayedPeriodicAction(100) {
			printTime("gc") {
				gitRepository.gc().call()
			}
		}
		val plan = worklist.createPlan()
			.filter { (_, revision) -> revision <= (configuration.general.lastRevision ?: revision) }
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
			val configuredBranch = configuredBranches[branch]!!
			val svnRevision = SVNRevision.create(revision)
			if (currentBranch != branch) {
				if (gitRepository.branchDoesNotExist(branch)) {
					if (configuredBranch.origin != null) {
						print("(from ${configuredBranch.origin.branchName} @ ${configuredBranch.origin.revision})")
						printTime("create") {
							gitRepository.checkout().setCreateBranch(true).setName(branch).setStartPoint(revisionCommits[configuredBranch.origin.revision to configuredBranch.origin.branchName]!!.name).call()
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
			val path = configuredBranch.getPathAt(revision)!!
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
					status.added + status.changed + status.modified + status.missing + status.removed + status.untracked + status.ignoredNotInIndex
				}.filterNot { it.startsWith(".svn") }
					.filterNot { file -> fileFilters.any { it(file) } }
					.filterNot { file -> branchFilters[branch]!!.any { it(file) } }
			}.also { logger.info("Files to update in Git: $it") }
			filePatterns.takeIf { it.isNotEmpty() }?.let {
				printTime("add") {
					gitRepository.add().apply { it.forEach(this::addFilepattern) }.setUpdate(true).call()
					gitRepository.add().apply { it.forEach(this::addFilepattern) }.setUpdate(false).call()
				}
			}
			printTime("commit") {
				val logEntry = simpleSvn.getLogEntry(path, revision)!!
				val commitMessage = (configuredBranch.getFixAt(revision)?.message?.replaceLineBreaks() ?: logEntry.message) +
						"\n\nSubversion-Original-Commit: $svnUrl$path@$revision\nSubversion-Original-Author: ${logEntry.author}"
				val commitAuthor = committers.getValue(logEntry.author)
				configuredBranch.getMergeAt(revision)?.let { merge ->
					print("(merge ${merge.branch} @ ${merge.revision})")
					gitRepository.repository.writeMergeHeads(listOf(revisionCommits[merge.revision to merge.branch]))
				}
				val committerAndTime = (committer ?: commitAuthor).let { if (configuration.general.useCommitDateFromEntry != false) PersonIdent(it, logEntry.date.toInstant(), zoneId) else it }
				val commit = gitRepository.createCommit(PersonIdent(commitAuthor, logEntry.date.toInstant(), zoneId), committerAndTime, commitMessage)
				revisionCommits[revision to branch] = commit
				gitRepository.storeCommitInCache(revision, branch, commit)
			}
			configuredBranch.getTagAt(revision)?.let { tag ->
				val tagLogEntry = simpleSvn.getLogEntry("/", tag.messageRevision)!!
				printTime("tag ${tag.name}") {
					val tagger = PersonIdent(committer ?: committers.getValue(tagLogEntry.author), tagLogEntry.date.toInstant(), zoneId)
					gitRepository.createTag(revisionCommits[revision to branch]!!, tag.name, tagLogEntry.message, tagger)
				}
			}
			periodicGarbageCollection()
			println()
		}
	}
}

private val stringToFilter = { filter: Filter -> filter.path.let(::Regex).let { regex -> { file: String -> regex.containsMatchIn(file) } } }

private fun readConfigurationFiles(vararg arguments: String) =
	arguments.map(::File)
		.filter(File::exists)
		.map { xmlMapper.readValue(it, Configuration::class.java) }

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

private val xmlMapper = XmlMapper.builder().addModule(kotlinModule()).build()
private val logger = Logger.getLogger("de.qsheltier.sactilgis.Sactilgis").apply {
	System.setProperty("java.util.logging.SimpleFormatter.format", "%tF %<tT %5\$s%n")
	addHandler(FileHandler("./sactilgis.log").apply { this.formatter = SimpleFormatter() })
	useParentHandlers = false
}
