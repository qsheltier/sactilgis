package de.qsheltier.utils.git

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
