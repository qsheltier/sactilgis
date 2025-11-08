# sactilgis

## Purpose

The main purpose of sactilgis is to enable a user to create a Git repository for a project that’s stored in a Subversion server, potentially as one of many projects. It also aims to handle situations that arise from imperfect use of Subversion, such as non-standard repository layouts, branches without history, manual, unrecorded merges, modified tags, and whatever else a company might throw at a Subversion repository.

However, due to the large number of things you can do to a Subversion repository, only the really menial tasks can be automated; sactilgis needs your help for some high-level tasks, such as which branches do exist and where (and when!) are they, where are branches merged, which version has actually been tagged. With that information sactilgis should be able to create a reasonable Git representation of your project, straight from Subversion.

However, certain things are impractical to do at this stage of conversion, so in almost every case further massaging of the repository (e.g. using `git-filter-branch`) is recommended — unless you’re already satisfied.

While the actual conversion of your repository can take quite an amount of time (depending mostly on size of the repository and your network connection), it is expected that actually way more time will go into producing an XML file that will enable the “best” conversion possible. (What “best” means, is entirely up to you.) If you are planning on converting a repository that is also actively being worked on, you will have no choice but to have the sactilgis configuration always being a little behind the current state of the repository. Fortunately, sactilgis has a couple of features that can help you in this situation:

* sactilgis can resume a conversion from where it previously left off. This includes aborted sactilgis runs; when starting a new run, sactilgis will check for an existing Git repository, will take note which branch is checked out and will then do Everything Correctly™ in order to resume without issues.
* It is possible to restrict how far sactilgis will process a repository. When you have created a configuration that is valid up to revision X, you can prevent sactilgis from progressing beyond revision X. This also allows an automated conversion process: have a periodically starting job that gets the latest configuration and starts sactilgis. It will skip commits it has already processed and will stop once it reaches the last revision specified in the configuration.


## Building

sactilgis comes with its own Maven wrapper, so building it should be as easy as:

    # ./mvwn clean package

After that, a nice, big JAR file will have been dropped in `app/target/`.

    # java -jar app/target/app-3-jar-with-dependencies.jar my-configuration.xml

And already your conversion should be underway!

## Configuration

The configuration is done using one or more XML files (see [below](#merging-configurations)). Under the top level `configuration` tag, there are several sections that are used to control all of sactilgis’s behaviour: `general`, `committers`, `branches`, and `filters`.

```xml
<?xml version="1.0" encoding="utf-8"?>
<configuration>
	<general>
		…
	</general>
	<committers>
		…
	</committers>
	<branches>
		…
	</branches>
	<filters>
		…
	</filters>
</configuration>
```

### The `general` Section

This section contains certain top-level configuration settings.

subversion-url
: The URL of the Subversion repository. Supports at least `svn://` and `file://` repository URLs; may support `http://` and `https://` as well (has never been tested).

subversion-auth
: Contains a `username` and a `password` element that will be used to authenticate all access to the configured repository. This element can be omitted completely if authorization is unnecessary; if present, its elements *must* be set.

target-directory
: The directory in which to store the resulting Git repository. If there already is a Git repository in this directory, it will be assumed that it was created a previous run of sactilgis. If there is no Git repository in this directory, or the directory does not exist, it will be *removed and re-created* before the conversion starts!

committer
: The name and email address of the person doing the conversion. This name and email address will be used for the committer data, and for tags. Format is the same as for the [committers](#the-committers-section); however, the `id` is optional. If this element is omitted, the original commit author is used as committer.

use-commit-date-from-entry
: If `true`, the author date of the Subversion commits are used as commit date for the Git commits. The author date of the Git commit is always taken from the Subversion commit date. If `false`, the current time will be used as commit time.

timezone
: The ID of the timezone to use for the commit times, like `Europe/Berlin` or `Etc/Zulu`. If omitted, the default timezone is used.

ignore-global-gitignore-file
: If `true`, a globally configured `.gitignore` file (configured by `core.excludesFile` using `git config`) will be ignored when commits are created. Setting this to `false` may lead to repositories with all files defined in your `.gitignore` file missing which may or may not be the intended consequence. As I currently consider the use of sactilgis to be a matter of keeping history intact as much as possible, I would recommend setting this to `true`.

last-revision
: If set, the Subversion repository will only be processed up to this revision.

### The `committers` Section

Author data stored in Subversion boils down to a single username; no full name or email address in sight anywhere. As such, the usernames need to be translated into full author details, and that is the purpose of this section.

Each committer gets their own `committer` element:

```xml
<committer>
	<id>dr</id>
	<name>David Roden</name>
	<email>github-a8in@qsheltier.de</email>
</commiter>
```

Repeat this tag as often as necessary to include all authors that are a part of your project’s Subversion history.

id
: This is the username of the author of the Subversion commit. It will be used for the mapping.

name
: The full name of the author that will be stored in the Git commit.

email
: The email address of the author stored in the Git commit.

### The `branches` section

This is finally where the meat of the configuration file lives. Here all the branches (including the main branch, often refered to as “trunk” in Subversion-land) that are a part of your project need to be defined, because it’s nigh impossible to generate this data from the information in the Subversion repository; mostly, because Subversion doesn’t know what a branch even _is_, and developers are incredibly skilled at working around restrictions in software and gaps in their knowledge while still appearing to know it all. 😄

Each branch has a number of features; a name, an optional origin, a list of revision-dependent paths for locating commits belonging to it, merge commits, tags, and fixes.

```xml
<branch>
	<name>main</name>
	<origin>
		<tag>test-3</tag>
		<branch>test</branch>
		<revision>3</revision>
	</origin>
	<revision-paths>
		<revision-path>
			<revision>5</revision>
			<path>/trunk</path>
		</revision-path>
		<revision-path>
			<revision>841</revision>
			<path>/project1/trunk</path>
		</revision-path>
	</revision-paths>
	<merges>
		<merge>
			<revision>267</revision>
			<branch>new-feature</branch>
		</merge>
	</merges>
	<tags>
		<tag>
			<name>0.4</name>
			<revision>318</revision>
			<message-revision>325</message-revision>
		</tag>
	</tags>
	<fixes>
		<fix>
			<revision>13</revision>
			<message>Tëstïng för ümläutß</message>
		</fix>
	</fixes>
	<filters>
		<filter>\.bak$</filter>
	</filters>
</branch>
```

This sections needs to be repeated for every branch you want to transfer into a Git repository.

#### The `origin` section

This section is optional and should only be used if your branches are created from outside any branches you are processing. A common example for that would be branches created from tags; as tags in Subversion usually do not belong to a branch, sactilgis can not assign them automatically. Instead, the `origin` section is used to point sactilgis to where the tag is actually coming from.

If the origin of a branch is a tag defined in the configuration, use the `tag` element. If the origin of a branch is not a tag but a branch that sactilgis cannot detect automatically (e.g. because it was created without copying an existing branch), use the `branch` and `revision` elements.

If no origin is specified and no origin can be determined, sactilgis will create an orphan branch.

tag
: The name of the tag the branch was copied from. If this element is used, `branch` and `revision` will be ignored.

branch
: The name of the branch the tag was copied from.

revision
: The revision that was tagged.

#### The `revision-paths` section

A revision-path is a point at which a branch either comes into existence for the first time, or changes location in the repository.

Each branch only exists at a single location at a specific revision; in the example above, if a commit at revision 845 (i.e. four commits *after* its location has changed) has been done on `/trunk`, it will *not* be a part of that branch anymore!

revision
: The revision at which the branch changes location. The commit at this revision already lives at the new location.

path
: The location in the repository that your branch lives on. It will continue to live at this location until the next revision-path entry (or HEAD, whichever comes first).

#### The `merges` section

A merge defines a point where two branches are joined. This information cannot reliably be pulled from Subversion’s `svn:mergeinfo` property, because the property can be set on nested directories somewhere, or the mergeinfo property only contains a handful of commits from the merged branch. Git cannot handle selective merges like that, so here *you* have to decide if a commit in Subversion should be treated like a merge in Git, i.e. unifying two lines of development.

Merges defined here will be shown as merges in Git but there will be no actual merging performed at any time. The contents of the repository at the resulting commit will be exactly like the content of the Subversion repository at the given revision; the commit in Git, however, will have two parents.

The commit that will be chosen as the second parent of the commit is the latest revision that belongs to the specified branch.

It is also possible to merge tags; only one of `branch` and `tag` should be specified.

revision
: The revision at which the branch should be recorded.

branch
: The branch that should be merged into this branch. Obviously, this branch needs to be defined in this configuration file as well.

tag
: The tag that should be merged into this branch.

#### The `tags` section

Tags are markers for certain commits, most often used for released versions. But, just like Subversion doesn’t know what a branch is, it also doesn’t know what a tag is; Subversion just sees directories in a sea of directories. So once again, your help is required!

name
: The name of the tag. Tag names need to be unique in a repository.

revision
: The revision of this branch that should be tagged. The actual revision the tag will be made of may actually be lower than this; if the Subversion commit (e.g. on your `/tags` branch) refers to revision 10 but the last commit on this branch was actually 7, revision 7 will be tagged.

message-revision
: The revision that the message of the Git tag should be taken from.

#### The `fixes` section

If you live a country that does not have a US-ASCII-compatible national language, you may encounter problems with those pesky accented or otherwise multi-byted characters. This section allows you to replace messages that have not been converted correctly.

revision
: The revision to fix.

message
: The new message for this revision.

#### The `filters` section

The `filters` section in a branch has the same syntax and meaning as the [top-level `<filters>` section](#the-filters-section-1), but is restricted to this branch. When filters are defined both here and in the `<general>` section, they will be merged for this branch.

See the [top-level `<filters>` section](#the-filters-section-1) for details on how the filter is applied.


### The `filters` section

This section contains filters for files that should be ignored when creating commits.

```xml
<filter>(^|/)\.DS_Store$</filter>
```

A filter is a regex string that will be matched against the complete name of each file relative to the repository’s root directory. The regex will match anywhere in the string, unless anchored using `^` or `$`. Note that even on Windows the directory separator is a forward slash, `/`.

An arbitrary amount of filters can be added. Some care should be taken to make filters as unambiguous as possible; i.e. `.bak` would prevent ever-present backup files from being committed, but it would also block a file named `material/bakelite.png`.


## Merging Configurations

In order to be able to e.g. define a common mapping for committers (because in a corporate environment you have many repositories, but they are all being worked on by the same people) it is possible to specify multiple XML files on the command line. In general, the values from later files are used to override values from earlier files. The following exceptions apply:

1. Non-present tags in the `general` sections remain unchanged.
2. The `committer` and the `subversion-auth` value in the `general` section can only be overridden in total, i.e. it is not possible to only change the name of the committer, or the password for the authentication.
3. The committers from the `committers` section are merged by the subversion ID, i.e. if a later file has a committer with the same subversion ID as a previous file, the committer from the later file is used.
4. Branches are merged by replacing earlier definitions. If you define a branch _X_ in two XML files, only the definition contained in the later file will be present in the merged configuration.
5. Filters from all configurations are added.

This mechanism makes it possible to define a number of settings that can be applied to any number of different projects. The most notable target for use is the `committers` section.


## Known Problems

* jgit (the library for handling the Git repository) writes a packed-refs file that can be understood by Git, but not by all clients; e.g. [Fork](https://git-fork.com/) cannot handle it. Workaround: run `git pack-refs` in the Git repository after conversion.


## TODO

There are several things that I still want to implement:

* Handling of empty commits: often, when a new branch is created from an existing branch, the initial commit only contains a copy within Subversion, i.e. no changes are actually being done. Empty commits in Git are possible (and sactilgis already creates them) but they are kind of ugly and (mostly) pointless. I want to evaluate if simply skipping these commits is acceptable; it does also throw away the commit message which may or may not be important.
* Commit rewriting: sometimes, commits are a real mess. Initially this project was aimed at creating reasonable Git versions of Subversion project repositories, but why stop there? When commits can be surgically altered during conversion, it might just be possible to craft the perfect Git repository, even if not 100% historically accurate. It might come in handy, you never know!
