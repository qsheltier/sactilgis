package de.qsheltier.sactilgis.helper

import java.io.File
import org.tmatesoft.svn.core.SVNDepth
import org.tmatesoft.svn.core.wc.SVNClientManager
import org.tmatesoft.svn.core.wc.SVNRevision
import org.tmatesoft.svn.core.wc.SVNStatus
import org.tmatesoft.svn.core.wc.SVNStatusType

fun SVNClientManager.revert(workDirectory: File, svnRevision: SVNRevision) {
	val statusLogs = mutableListOf<SVNStatus>()
	this.statusClient.doStatus(workDirectory, svnRevision, SVNDepth.INFINITY, false, true, false, false, statusLogs::add, null)
	statusLogs
		.filterNot { it.file == File(workDirectory, ".git") }
		.filter { it.isConflicted || (it.treeConflict != null) || (it.contentsStatus != SVNStatusType.STATUS_NORMAL) || (it.nodeStatus != SVNStatusType.STATUS_NORMAL) }
		.map(SVNStatus::getFile)
		.onEach(File::deleteRecursively)
	this.wcClient.doRevert(arrayOf(workDirectory), SVNDepth.INFINITY, null)
}
