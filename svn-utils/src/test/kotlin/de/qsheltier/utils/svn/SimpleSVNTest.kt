package de.qsheltier.utils.svn

import java.io.File
import org.junit.jupiter.api.Test
import org.tmatesoft.svn.core.SVNURL

class SimpleSVNTest {

	@Test
	fun `SimpleSVN can be created`() {
		SimpleSVN(SVNURL.fromFile(File(".")))
	}

}
