package de.qsheltier.sactilgis

import de.qsheltier.sactilgis.Configuration.Branch
import de.qsheltier.sactilgis.Configuration.Branch.Fix
import de.qsheltier.sactilgis.Configuration.Branch.Merge
import de.qsheltier.sactilgis.Configuration.Branch.Origin
import de.qsheltier.sactilgis.Configuration.Branch.RevisionPath
import de.qsheltier.sactilgis.Configuration.Branch.Tag
import de.qsheltier.sactilgis.Configuration.Committer
import de.qsheltier.sactilgis.Configuration.Filter
import de.qsheltier.sactilgis.Configuration.SubversionAuth
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.Test
import tools.jackson.dataformat.xml.XmlMapper
import tools.jackson.module.kotlin.kotlinModule

class ConfigurationParserTest {

	@Test
	fun `empty XML file can be parsed`() {
		val configuration = xmlMapper.readValue("""<?xml version="1.0" encoding="UTF-8"?>
			<configuration></configuration>
			""", Configuration::class.java)
		assertThat(configuration, notNullValue())
	}

	@Test
	fun `subversion URL can be parsed`() {
		val configuration = xmlMapper.readValue("""<?xml version="1.0" encoding="utf-8"?>
			<configuration>
				<general>
					<subversion-url>svn://url</subversion-url>
				</general>
			</configuration>
			""", Configuration::class.java)
		assertThat(configuration.general.subversionUrl, equalTo("svn://url"))
	}

	@Test
	fun `subversion auth can be parsed`() {
		val configuration = xmlMapper.readValue("""<?xml version="1.0" encoding="utf-8"?>
			<configuration>
				<general>
					<subversion-auth>
						<username>user</username>
						<password>pass</password>
					</subversion-auth>
				</general>
			</configuration>
			""", Configuration::class.java)
		assertThat(configuration.general.subversionAuth, equalTo(SubversionAuth("user", "pass")))
	}

	@Test
	fun `committer in general can be parsed`() {
		val configuration = xmlMapper.readValue("""<?xml version="1.0" encoding="utf-8"?>
			<configuration>
				<general>
					<committer>
						<id>com</id>
						<name>C. O. Mitter</name>
						<email>co@mitt.er</email>
					</committer>
				</general>
			</configuration>
			""", Configuration::class.java)
		assertThat(configuration.general.committer, equalTo(Committer("com", "C. O. Mitter", "co@mitt.er")))
	}

	@Test
	fun `target directory can be parsed`() {
		val configuration = xmlMapper.readValue("""<?xml version="1.0" encoding="utf-8"?>
			<configuration>
				<general>
					<target-directory>/target/path</target-directory>
				</general>
			</configuration>
			""", Configuration::class.java)
		assertThat(configuration.general.targetDirectory, equalTo("/target/path"))
	}

	@Test
	fun `use-commit-date-from-entry can be parsed`() {
		val configuration = xmlMapper.readValue("""<?xml version="1.0" encoding="utf-8"?>
			<configuration>
				<general>
					<use-commit-date-from-entry>true</use-commit-date-from-entry>
				</general>
			</configuration>
			""", Configuration::class.java)
		assertThat(configuration.general.useCommitDateFromEntry, equalTo(true))
	}

	@Test
	fun `timezone can be parsed`() {
		val configuration = xmlMapper.readValue("""<?xml version="1.0" encoding="utf-8"?>
			<configuration>
				<general>
					<timezone>World/Test</timezone>
				</general>
			</configuration>
			""", Configuration::class.java)
		assertThat(configuration.general.timezone, equalTo("World/Test"))
	}

	@Test
	fun `ignore-global-gitignore-file can be parsed`() {
		val configuration = xmlMapper.readValue("""<?xml version="1.0" encoding="utf-8"?>
			<configuration>
				<general>
					<ignore-global-gitignore-file>true</ignore-global-gitignore-file>
				</general>
			</configuration>
			""", Configuration::class.java)
		assertThat(configuration.general.ignoreGlobalGitIgnoreFile, equalTo(true))
	}

	@Test
	fun `last revision can be parsed`() {
		val configuration = xmlMapper.readValue("""<?xml version="1.0" encoding="utf-8"?>
			<configuration>
				<general>
					<last-revision>1234</last-revision>
				</general>
			</configuration>
			""", Configuration::class.java)
		assertThat(configuration.general.lastRevision, equalTo(1234))
	}

	@Test
	fun `complete branch can be parsed`() {
		val configuration = xmlMapper.readValue("""<?xml version="1.0" encoding="utf-8"?>
			<configuration>
				<branches>
					<branch>
						<name>test-branch</name>
						<origin>
							<tag>origin-tag</tag>
							<branch>origin-branch</branch>
							<revision>1234</revision>
						</origin>
						<revision-paths>
							<revision-path>
								<revision>2345</revision>
								<path>/path/to/test</path>
							</revision-path>
						</revision-paths>
						<merges>
							<merge>
								<revision>3456</revision>
								<branch>merge-branch</branch>
								<tag>merge-tag</tag>
							</merge>
						</merges>
						<tags>
							<tag>
								<revision>4567</revision>
								<name>test-tag</name>
								<message-revision>5678</message-revision>
							</tag>
						</tags>
						<fixes>
							<fix>
								<revision>6789</revision>
								<message>Fixed message</message>
							</fix>
						</fixes>
					</branch>
				</branches>
			</configuration>
			""", Configuration::class.java)
		assertThat(configuration.branches, contains(equalTo(
			Branch("test-branch",
				Origin("origin-tag", "origin-branch", 1234),
				mutableListOf(RevisionPath(2345, "/path/to/test")),
				mutableListOf(Merge(3456, "merge-branch", "merge-tag")),
				mutableListOf(Tag(4567, "test-tag", 5678)),
				mutableListOf(Fix(6789, "Fixed message"))
			)
		)))
	}

	@Test
	fun `committers can be parsed`() {
		val configuration = xmlMapper.readValue("""<?xml version="1.0" encoding="utf-8"?>
			<configuration>
				<committers>
					<committer>
						<id>com</id>
						<name>C. O. Mitter</name>
						<email>co@mitt.er</email>
					</committer>
				</committers>
			</configuration>
			""", Configuration::class.java)
		assertThat(configuration.committers, contains(equalTo(Committer("com", "C. O. Mitter", "co@mitt.er"))))
	}

	@Test
	fun `filters can be parsed`() {
		val configuration = xmlMapper.readValue("""<?xml version="1.0" encoding="utf-8"?>
			<configuration>
				<filters>
					<filter>/filter1</filter>
				</filters>
			</configuration>
			""", Configuration::class.java)
		assertThat(configuration.filters, contains(equalTo(Filter("/filter1"))))
	}

}

private val xmlMapper = XmlMapper.builder().addModule(kotlinModule()).build()
