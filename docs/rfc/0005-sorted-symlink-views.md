# RFC: Sorted symlink views (genre, year, group, …)

2026-11-20

## Summary

A new link type in the existing links plugin creates symlinks to completed releases under directories named after a metadata value, such as `/MP3/SORTED/BY-GENRE/Techno/`, using data the zipscript, IMDB and TvMaze plugins already store.

- **Status:** Draft, for discussion
- **Addresses:** [issue #67](https://github.com/drftpd-ng/drftpd/issues/67), "Plugin: Genre, Crew, Year..."
- **Scope:** a `SORTED` link type in `src/plugins/links`, its configuration, the events that create and remove links, and a rebuild command. No change to the VFS itself.

## Motivation

The issue asks for browsable views such as `/MP3/SORTED/BY-GENRE`, `BY-YEAR` and `BY-CREW`, which glftpd sites get from their zipscript (pzs-ng can create genre, year, artist and group symlinks for MP3 releases; option names **unverified**). Users moving from glftpd look for this, and the data needed is already collected by DrFTPD plugins; only the links are missing.

## Background

### The links plugin

`org.drftpd.links.master` (in `src/plugins/links`) already creates VFS symlinks for several purposes:

- `LinkManager` loads numbered entries from `links.conf` (`x.type`, `x.dirname`, `x.section`, `x.exclude`, `x.sectionexclude`, `x.deleteon`, `x.linkname`, `x.addparentdir`) and finds `LinkType` subclasses by reflection, by simple class name.
- `LinkType` holds the shared logic: `createLink()` calls `DirectoryHandle.createLinkUnchecked()`, and `doRename()` repoints links when a target is renamed, including the rename done by nuke (it checks for the `[NUKED]-` prefix).
- Types today: `LatestDir`, `NFOMissing`, `SFVMissing`, `SFVIncomplete`, `ZipIncomplete`, each with a manager in `types/<name>/` that subscribes to events such as `TransferEvent`, `DirectoryFtpEvent` and `VirtualFileSystemInodeDeletedEvent`.
- `LinkManager` itself removes links on `VirtualFileSystemInodeDeletedEvent` (wipe, rmd) and repoints them on `VirtualFileSystemRenameEvent`.
- `LinkManagerCommands.doSITE_FIXLINKS()` walks the current directory tree and calls `doFixLink()` for every configured type.

### VFS links

A link is a `VirtualFileSystemLink` inode, handled through `LinkHandle`. Like every inode it is stored as a small JSON file under `userdata/vfs` on the master. Links cost no slave space, but each one is a file on the master's disk.

### Metadata available

| Source | Where it is stored | Values | When it appears |
| --- | --- | --- | --- |
| MP3 (zipscript) | `MP3Info.MP3INFO` on the directory, holding an `ID3Tag` (`getGenre()` via `ID3GenreList`, `getYear()`, artist, album) | One genre, one year | First MP3 parsed in the directory; `ZipscriptVFSDataMP3` then publishes `MP3Event` |
| IMDB | `IMDBInfo.IMDBINFO` on the directory | Genres as one `\|`-separated string, year | After the NFO is parsed; `IMDBUtils.publishEvent()` publishes `IMDBEvent` |
| TvMaze | `TvMazeInfo.TVMAZEINFO` on the directory | `getGenres()` array | On `MKD` / `PRE`; `TvMazeUtils` publishes `TvMazeEvent` |
| Release name | The directory name | Group (after the last `-`), year (a 4-digit token) | Always |
| Race status | `SFVAnnouncer.SFV_COMPLETE` on the directory | Complete or not | When `SFVStatus.isFinished()` first becomes true |

Mediainfo (`MediaInfo.MEDIAINFO`) holds codec and resolution data, not genres; sorting by it is possible later but not part of this RFC.

## Proposal

### A `SORTED` link type

A new `Sorted` class extending `LinkType`, with its manager, in `links/master/types/sorted/`. One configuration entry produces one view:

```
5.type=SORTED
5.section=MP3
5.by=mp3.genre
5.dirname=/MP3/SORTED/BY-GENRE/${value}
5.linkname=${dirname}
5.when=complete
5.deleteon=*
5.exclude=^/(.*)/(?i)(cd[1-9]|sample|subs)$
5.sectionexclude=
```

- `by` names the value source: `mp3.genre`, `mp3.year`, `mp3.artist`, `imdb.genre`, `imdb.year`, `tvmaze.genre`, `name.group`, `name.year`.
- `dirname` gains a `${value}` placeholder. A multi-valued source (IMDB and TvMaze genres) creates one link per value.
- Values are cleaned for use as a directory name: `/` and control characters removed, spaces kept or replaced (open question), length capped, and an optional `x.map.<value>=<name>` table to merge values (for example `Hip-Hop` and `Hip Hop`).
- `when=complete` waits until the release is complete (`SFV_COMPLETE`) and the metadata exists; `when=metadata` links as soon as the metadata exists.

### When links are created and removed

| Event | Action |
| --- | --- |
| `MP3Event`, `IMDBEvent`, `TvMazeEvent` | Store nothing; if `when=metadata`, or the directory is already complete, create the link |
| `SFVMemberTransferEvent` with `isFinished()` | If `when=complete` and the metadata exists, create the link |
| `MKD` / `DirectoryFtpEvent` | For `name.*` sources with `when=metadata` |
| Wipe, `RMD` (`VirtualFileSystemInodeDeletedEvent`) | Already handled by `LinkManager`: links removed |
| Nuke (rename with the nuke prefix) | Already handled by `LinkType.doRename()`: link renamed for `[NUKED]-` names. `links.conf` documents `deleteon=NUKE`, but no code in the plugin handles nukes, so `Sorted` must subscribe to `NukeEvent` if nuked releases should leave the views |
| Rename, archive move (`ArchiveType` uses `renameToUnchecked()`) | `VirtualFileSystemRenameEvent`; `Sorted` overrides the repoint logic because its link directory depends on the value, not only on `dirname` |
| Metadata removed (for example an NFO deleted: `IMDBPostHook.cleanup()` removes `IMDBInfo`) | Not detected today; handled by the rebuild (open question 4) |

When the last link in a value directory is removed, the empty directory is removed too.

### Rebuild

- `SITE FIXLINKS` already calls `doFixLink()` per directory; `Sorted.doFixLink()` creates missing links and removes links whose target no longer has the value.
- A new `SITE SORTEDLINKS REBUILD [n]` deletes the whole view tree of entry `n` (or all `SORTED` entries) and walks the configured sections again, in a background thread at low priority, like `runFixLinks`. Needed after changing `by`, `dirname` or the value map.
- The walk reads metadata from the inodes only; it does not trigger new MP3, IMDB or TvMaze lookups.

### Cost

- One link inode (a JSON file on the master) per release per view, and per value for multi-valued sources. A site with 200,000 MP3 releases and three views has about 600,000 extra small files under `userdata/vfs`.
- Creation is a few VFS operations per completed release; negligible next to the race itself.
- A full rebuild walks every directory of the configured sections; on large sites this takes minutes and should not run at peak times.

## Configuration

New entries in `links.conf.dist`, documented like the existing types, plus the example above for `mp3.genre`, `mp3.year` and `name.group`. Nothing else changes in existing configs.

## Upgrade / compatibility

- No effect until a `SORTED` entry is added.
- Sites with an existing archive run `SITE SORTEDLINKS REBUILD` once to populate the views; releases whose metadata was never collected (for example IMDB never ran) are not linked.
- Links appear in listings like other links; clients and scripts that already handle the `LatestDir` links need nothing new.

## Security considerations

- **Values come from uploaded files.** ID3 tags and directory names are user-controlled. Value cleaning must make path traversal (`..`, `/`) and hidden names (leading `.`) impossible, and cap length, before anything reaches `createLinkUnchecked()`.
- **Permissions:** links are created as `drftpd`/`drftpd`, as by other link types. Access to the target is still checked when a user follows the link; the view directories need the same perms as the section they mirror, or they reveal release names from hidden sections.
- **Disk exhaustion:** a release with many genre values could create many links; cap links per release per view (for example 5).

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Virtual directories computed from the index (Lucene already holds `mp3genre`, `mp3year` and IMDB fields) | `AddListElementsInterface` (as used by `IMDBList`) can only add entries to a listing; `CWD` into a virtual directory would need changes to VFS path resolution. Less disk use, but a much larger change |
| Real directories with copies | Wastes slave space; not acceptable |
| A separate plugin outside `links` | Would duplicate the rename, delete and nuke handling that `LinkType` already has |
| `SITE SEARCH`-style commands only | Already exist for some fields; do not give the browsable tree the issue asks for |

## Test plan

- Unit tests for value extraction and cleaning: ID3 genre names with `/` and `&`, IMDB `Action|Drama` split, group and year from names such as `Artist-Album-2024-GROUP`.
- Integration on a test VFS: complete an MP3 release, check the genre and year links; nuke it, check the `[NUKED]-` rename; wipe it, check the link and the empty value directory are gone; archive-move it, check the link is repointed.
- Rebuild on a tree of a few thousand directories, checking the result equals incremental creation.

## Open questions

1. Symlinks in the VFS (proposed) or a virtual-directory feature in the VFS core, which would avoid hundreds of thousands of link inodes on big sites?
2. Should links be created on race completion only, or as soon as the metadata exists (configurable per entry, as proposed)?
3. Which value sources should ship in phase 1: MP3 genre and year and group only, or IMDB and TvMaze genres too?
4. Should plugins publish an event when they remove metadata (for example `IMDBPostHook.cleanup()` on NFO delete), so links follow, or is the rebuild enough?
5. How should value directory names be normalised: spaces kept, replaced with `_`, or case-folded?
