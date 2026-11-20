# DrFTPD roadmap: 4.0.12 and 5.0

2026-11-20 · **Status: proposal, for discussion.** Nothing here is decided until the maintainers agree.

## Why a roadmap now

- The last release is **4.0.11 (March 2023)**. Since then, more than 40 pull requests are open at the same time: bug fixes, small opt-in features, and a few changes that break compatibility.
- Mixing them in one release would force every site to deal with all the breaking changes at once, just to get bug fixes.
- This proposal splits them in two:
  - **4.0.12**: what can be released without breaking an existing site;
  - **5.0**: what needs an upgrade step or breaks compatibility.

The rule used below: **a change goes into 5.0 if a site has to do something to upgrade, or cannot go back after upgrading.** Examples: install a new Java, rebuild an index, copy config files, or face a data format that older versions can't read. It also goes into 5.0 if a third-party plugin has to be changed.

## 4.0.12: maintenance release

Goal: release soon, from `master`, with bug fixes and **opt-in** features only. An existing 4.0.11 site upgrades by replacing the jars; its config and data stay valid, and it can go back.

### Bug fixes

| Area | Pull requests |
| --- | --- |
| Login, stats, credits | #495/#518, #520, #527 |
| SiteBot / IRC | #496/#522 |
| Dated sections | #498/#515 |
| AutoFreeSpace | #499, #508, #510 |
| Slaves and transfers | #500, #503, #514, #519, #525, #528 |
| Zipscript, mediainfo | #497, #504, #513/#516, #517, #537 |
| Archive | #538 |
| Users and passwords | #532, #533 |
| Build | #509 |

Where two PRs fix the same issue (for example #495 and #518), only one is merged; the pairs are listed together.

### Opt-in features (off by default, no change for existing sites)

- **#511**: certificate pinning for slaves.
- **#501**: IRCv3 SASL authentication.
- **#506**: configurable IMDB URLs.
- **#507**: richer status output.
- **#526**: `OPTS UTF8`.
- **#535**: `wipe`/`wipeex` path permissions.
- **#536**: the `bytes.maxunit` option.

### Dependencies that stay on Java 17

- **#531**: commons, guava, gson, httpclient, log4j, JUnit and Maven plugins.
- **#529**: replaces Spring Boot, nifty and bcprov-jdk15on with maintained libraries.
  - Behaviour change to note in the release notes: a **new** bcrypt password longer than 72 bytes is refused instead of being cut to 72 bytes.
- The Dependabot PRs for GitHub Actions.
- **#521**: the Dependabot rule that keeps the Docker image on Java 17. It is the 4.0.12 counterpart of the rule in #530; only one of the two should land in each release.

### Cleanup

- **#539**: removes the dead STOU/APPE code.

### Release steps for 4.0.12

1. Create GitHub milestones `4.0.12` and `5.0.0`, and put each open PR in one of them.
2. Merge the 4.0.12 PRs. Keep `MAJORCHANGES.txt` and `UPGRADING.txt` limited to what 4.0.12 contains.
3. Run a release candidate on at least one real site (a master and two slaves) for a week.
4. Tag `4.0.12`. Then create a `4.0.x` branch for later bug-fix releases, and move `master` to `5.0.0-SNAPSHOT`.

## 5.0: platform release

Goal: modernise the base in **one** upgrade with **one** upgrade guide, instead of a breaking change in every minor release.

### Breaking changes

| Change | PR | What a site has to do |
| --- | --- | --- |
| Java 25 LTS (current LTS; needed by Lucene 10) | #530 | Install Java 25 on the master and **every** slave |
| Lucene 3.6 → 10 search index | #534 | Nothing by hand: the old index is set aside and rebuilt automatically at first start, which is I/O-heavy on large sites. Third-party plugins that add index fields must be updated |
| VFS per-slave file counts | #494 | Back up `userdata/vfs`; after the upgrade, older versions can't read it. Run `SITE FIXSLAVECOUNT` once |
| Plugin command configs renamed to `.dist` | #505 | Copy the `.dist` files and re-apply local changes |
| json-io 4.14 → 4.x latest | #427 | No action expected, but users, groups and the VFS are stored with json-io, so this needs a migration test on real data before it ships |

Three of these PRs currently describe themselves as 4.0.12 (`MAJORCHANGES.txt`, `UPGRADING.txt`). If this plan is accepted, those notes move to a `5.0` section.

### Technical debt to clear in 5.0

These dependencies are unmaintained or several major versions behind, and some changes break the plugin API. 5.0 is the moment to do them, since plugins already have to be rebuilt.

- **Plugin discovery.** It uses the `reflections` library, which is no longer maintained. Replace it with the JDK's `ServiceLoader` or a small index generated at build time. This makes startup faster and removes the dependency.
- **Logging.** SLF4J 1.7 with `log4j-slf4j-impl`: move to SLF4J 2 with `log4j-slf4j2-impl`.
- **Smaller libraries.** Review the remaining old ones (for example `isoparser` in mediainfo) and drop or replace those that are unmaintained.
- **Plugin API.** Document which interfaces are meant for plugins: commands, hooks, index extensions, events. Treat changes to them as breaking from 5.0 on, so third-party plugins know what is stable.

### Upgrade tooling

- Write a single **`UPGRADING.txt` section "4.x → 5.0"**. It should give the steps in order: back up, install Java 25, copy the configs, first start, rebuild the index, run `SITE FIXSLAVECOUNT`.
- Optionally, add a small **pre-flight check** at master startup. It would refuse to start, with a clear message, when it finds a 4.x VFS without a backup flag or a slave running an older Java. That is better than failing halfway.

### Release steps for 5.0

1. Merge the breaking PRs into `master` (5.0.0-SNAPSHOT) in this order:
   1. Java 25;
   2. dependencies;
   3. VFS and json-io, with a migration test on a copy of a real site;
   4. Lucene;
   5. config renames.
2. Publish `5.0.0-beta1` and ask site owners to test on a copy of their data. Bug fixes found then also go to `4.0.x` when they apply.
3. Publish `5.0.0` when no data migration bug has been reported for a few weeks.

## 5.x: features after 5.0

These features are opt-in and additive, so they can come in minor releases (5.1, 5.2, …) without breaking anything. Most of them need a design decision first. That is what the RFCs are for (`docs/rfc/`).

| Feature | Issue | State |
| --- | --- | --- |
| IPv6, phase 1 (EPRT/EPSV, IPv6 masks) | #19 | Draft PR #523, RFC 0001 (#524). Could also be in 5.0 if ready |
| IPv6, phases 2 and 3 | #19 | Outlined in RFC 0001 |
| FXP between sites, with SSCN | — | WIP PR #512 |
| FTP client certificate authentication | #136 | RFC 0003 (#544) |
| IMDB lookup without an .nfo | #56 | RFC 0002 (#544) |
| srrDB support | #137 | RFC 0004 (#544) |
| Sorted link views (genre, year, group) | #67 | RFC 0005 (#544) |
| Dupe cleanup with `site find -dupe` | #264 | Draft PR #540 |
| User home directories | #156 | Draft PR #543 |
| Speedtest server chosen by the slave | #179 | Draft PR #541 |
| Nuke by sample audio language | #291 | Draft PR #542 |
| Speedkicker after N% / IP blocking | #173 | Waiting for a decision |
| Clearer "permission denied" details | #59 | Waiting for a decision |

## Project process (applies to both)

- **CI on pull requests from forks.** The build workflow listens to `pull_request`, but fork PRs have not been getting CI runs. Approving fork runs, or running the build job with read-only permissions, would let reviewers see test results on every PR.
- **One PR per change, one issue per PR**, linked with `Fixes #N`, as done in most recent PRs.
- **RFC first** for anything that adds config syntax, changes a protocol between master and slaves, or touches security. Use a short document in `docs/rfc/`, discussed in its own PR.
- **Milestones** decide what goes into which release. A PR without a milestone isn't merged.

## Open questions for the maintainers

1. Do you agree with the split: anything that needs a step or prevents going back goes to 5.0?
2. Should #494 (VFS format) wait for 5.0? It fixes real bugs but makes downgrading impossible.
3. Should IPv6 phase 1 be part of 5.0, or come in 5.1?
4. Is a `4.0.x` maintenance branch acceptable, or should 4.0.12 be the last 4.x release?
5. Who can run a release candidate on a real site?
