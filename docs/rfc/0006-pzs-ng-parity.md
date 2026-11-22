# RFC: Feature parity with pzs-ng (gap analysis)

2026-11-22

## Summary

Sites that move from glftpd to DrFTPD usually bring their pzs-ng habits: progress bars, `(incomplete)` links, race announces, MP3 genre/year/bitrate rules, rescan, and a long list of tuning options. This document compares what pzs-ng offers with what DrFTPD already does, lists the gaps, and proposes a series of small pull requests to close the most valuable ones.

- **Status:** Draft, for discussion
- **Addresses:** the general question "what is missing for glftpd users"; related to [issue #67](https://github.com/drftpd-ng/drftpd/issues/67) (see RFC 0005)
- **Scope:** research and planning only. No code changes. Each gap becomes its own small PR (or its own RFC when it is large).

## Motivation

pzs-ng is the zipscript most glftpd sites run (repository `pzs-ng/pzs-ng` on GitHub, "A realtime CRC addon made specifically for glftpd"). It is a set of C binaries that glftpd calls as `cscript`s after uploads, deletes, nukes and so on, plus an IRC announce bot (`sitebot/`, the ngBot/dZSbot Tcl bot). Its behaviour is controlled by roughly 300 options in `zsconfig.h` (documented in `README.ZSCONFIG`).

DrFTPD has most of the core: SFV and zip checking, race statistics, status bars, missing-file markers, incomplete links, MP3/FLAC tag info, nuke tooling and announces. What it lacks are mostly the *policy* features around audio releases and a number of knobs and edge cases.

**This is a re-implementation of behaviours, not a port.** The architectures differ: pzs-ng keeps race data in its own files next to the release and must be told by glftpd about every delete, nuke and unnuke (`postdel`, `postunnuke`, `datacleaner`). DrFTPD stores files on slaves and the directory tree on the master, and computes race status from the live VFS (`SFVTools.getSFVStatus(sfvInfo, dir)`), so those helper tools have no equivalent need.

**No pzs-ng code is copied or translated.** pzs-ng is published under the **BSD 3-Clause licence** ("Copyright (c) 2007, project-zs-ng team", `LICENSE` in the repository; GitHub reports it as BSD-3-Clause). This document only describes observable behaviour taken from its public documentation (`README`, `README.ZSCONFIG`, `sitebot/themes/default.zst`, the file listing of `zipscript/src`).

## How this was researched

- **pzs-ng:** files read from `pzs-ng/pzs-ng` (default branch `master`): `README`, `README.ZSCONFIG` (option reference), `zipscript/conf/zsconfig.h.dist`, `LICENSE`, `sitebot/themes/default.zst` (announce names), and the file listing of `zipscript/src`, `sitebot/` and `scripts/`. Option names below come from `README.ZSCONFIG`. The C sources were *not* read, so behaviour descriptions are from the documentation only.
- **DrFTPD:** upstream `master` at the time of writing, `src/plugins/{zipscript,links,mediainfo,imdb,tvmaze,dupecheck,nukefilter,autonuke,raceleader,sitebot,speedtest,find}` and `src/core/master`, with their `.conf.dist` and theme files. Statements marked "not verified" are ones where only part of the code was read.

## Feature table

Legend: **Yes** = equivalent behaviour exists, **Partial** = exists with fewer options or different behaviour, **No** = not found. DrFTPD evidence is a class or config key that was read.

### SFV, zip and rar checking

| pzs-ng feature (option) | DrFTPD | Evidence and notes |
| --- | --- | --- |
| CRC check of files against the SFV (`sfv_dirs`) | Yes | `ZipscriptPostHook` compares the slave-computed transfer checksum with the SFV entry and deletes on mismatch. |
| Delete or keep bad files (`mark_file_as_bad`: rename to `.bad`) | Partial | Bad files are deleted (`deleteUnchecked`); no `.bad` rename option. |
| SFV first (`force_sfv_first`, `noforce_sfv_first_dirs`) | Yes | `zipscript.conf`: `sfvfirst.required`, `sfvfirst.pathcheck`, `sfvfirst.pathignore`, `sfvfirst.users`, `sfvfirst.allownoext`; `ZipscriptPreHook`. |
| Allowed files not in SFV (`allowed_types`, `allow_files_not_in_sfv`) | Yes | `allowedexts`, `sfv.restrict.files`. |
| Deny second SFV (`deny_double_sfv`, `DOUBLE_SFV`) | Yes | `ZipscriptPreHook` denies a second `.sfv` ("Multiple SFV files not allowed"). `deny_resume_sfv` (also deny a resumed or overwritten SFV and restore a backup): not verified. |
| Check files uploaded before the SFV, delete mismatches (`enable_unduper_script`, `ng-undupe`) | Yes | `sfv.check.existing`. The unduper helper is not needed (see "Things DrFTPD should not copy"). |
| SFV hygiene: `sfv_cleanup`, `sfv_dupecheck`, `sfv_lenient`, `sfv_cleanup_lowercase`, `sfv_cleanup_crlf`, `sfv_cleanup_comments`, `strict_sfv_check`, `allow_slash_in_sfv`, `EMPTY_SFV` | Partial | SFV parsing lives in `SFVInfo` and the slave `ZipscriptHandler`; no config keys for these. Not verified how malformed or duplicate entries are handled. |
| Zero-byte files (`ignore_zero_size`, `EMPTY_FILE`) | Yes | `ZipscriptPostHook` deletes a 0-byte file with checksum 0. |
| Zip integrity (`zip_dirs`, `unzip_bin`, `BAD_ZIP`) | Yes | `stor.zip.integrity.check.enabled`, `ZipscriptZipPostHook`, slave `ZipscriptZipHandler`. |
| Clean banned files inside zips (`zip_clean`, `banned_filelist`) | No | No banned-name list. |
| file_id.diz info and completeness for zip releases | Yes | `cwd.dizinfo.enabled`, `DizInfo`, `ZipscriptDizStatusBar`. |
| Deny NFO in zip dirs, deny second NFO (`deny_nfo_upload_in_zip`, `deny_double_nfo`, `ZIP_NFO`, `DUPE_NFO`) | No | Not found in `ZipscriptPreHook`. |
| Banned file names (`check_for_banned_files`, `banned_filelist`, `BANNED_FILE`, `del_banned_release`) | Partial | Path patterns can be refused with `perms.conf` (`upload` path permission). No shared banned-name list, no "delete the whole release" action. |
| Delay on bad file (`sleep_on_bad`) | No | Not found. |
| Per-directory exclusion (`nocheck_dirs`, `group_dirs`, `speedtest_dirs`) | Partial | Exclusions exist for sfv-first (`sfvfirst.pathignore`) and in link and announce configs; no single "do not check here" list for all checks. |
| Deny SFV upload when a CDn subdir exists | Extra in DrFTPD | `sfvdeny.subdir`, `sfvdeny.subdir.include`, `sfvdeny.subdir.exclude`. Whether pzs-ng has an equivalent was not looked for. |

### Race statistics and progress

| pzs-ng feature (option) | DrFTPD | Evidence and notes |
| --- | --- | --- |
| Progress bar in the directory (`status_bar_type`, `progressmeter`, `charbar_filled`, `charbar_missing`, `chmod_completebar`) | Yes | `statusbar.enabled`, `statusbar.directory`, theme `zip.statusbar.format`; `ZipscriptList`. Shows "x of y files = n% complete"; no per-character bar, and the format is a theme key rather than pzs-ng cookies. |
| Per-file "-MISSING" markers (`create_missing_files`) | Yes | `files.missing.enabled`, theme `zip.files.missing.filename`. Shown at listing time, not as real files. |
| Complete / incomplete markers in listings | Yes | Status bar plus links (see below). |
| Race stats shown to the uploader (`get_user_stats`, `show_user_info`, `show_group_info`) | Yes | `stor.racestats.enabled`, `cwd.racestats.enabled`, `ZipscriptPostHook`, theme `zipscript.post.hook.theme.default`. |
| Stats file written into the release (`write_complete_message`, `message_file_name`, `.message`) | No | Stats are printed, not stored as a file. |
| First-file, halfway, race, complete announces | Yes | `SFVAnnouncer` event types `store.first`, `store.halfway`, `store.race`, `store.complete`; themes `sfv.store.*`; zip equivalents in `ZipAnnouncer` (`zip.store.*`). |
| Halfway thresholds (`min_halfway_files`, `min_halfway_size`) | Partial | Halfway is announced only for SFVs with at least 4 files (hard-coded in `SFVAnnouncer`); no size threshold, not configurable. |
| New-leader announce and thresholds (`min_newleader_files`, `min_newleader_size`, `newleader_files_ahead`, `enable_files_ahead`) | Partial | `NewRaceLeader` and `NewRaceLeaderManager` (event type `store.newraceleader`). Throttled by a fixed 30 minute expiry; no files-ahead or minimum-size options. |
| Update announce (`min_update_files`, `min_update_size`, `UPDATE_*`) | No | No update announce type. |
| Single-racer behaviour (`announce_norace`, `*_norace_complete`) | Partial | The complete announce lists racers and groups; no option to suppress single-racer announces. |
| Top N users and groups in complete (`max_users_in_top`, `max_groups_in_top`) | Yes | `irc.sfv.maxusers`, `irc.sfv.maxgroups`, `irc.zip.maxusers`, `irc.zip.maxgroups`. |
| Per-type announce switches (`disable_{audio,rar,video,zip,other}_{race,halfway,newleader,update,complete,sfv}`) | Partial | Announces are routed per path and event in the sitebot announce config (`getPathWriter`), which can silence a section, but not per file type. |
| Hide users or affils in stats (`hide_uname`, `hide_affil_users`, `hide_group_uploaders`, `use_group_dirs_as_affil_list`) | No | Not found in the zipscript or sitebot code that was searched. |
| Competitor list cookie (`get_competitor_list`) | Not verified | |

### Audio

| pzs-ng feature (option) | DrFTPD | Evidence and notes |
| --- | --- | --- |
| ID3 info on first MP3, shown to uploader and announced | Yes | `cwd.id3info.enabled`, `stor.mp3info.enabled`, `ZipscriptMP3PostHook`, `MP3Announcer` (event type `mp3`, theme key `id3tag`), `MP3Info`, `ID3Tag`. |
| FLAC / Vorbis info | Yes | `FlacAnnouncer`, `ZipscriptFlacPostHook`, `cwd.flacinfo.enabled`, `stor.flacinfo.enabled`. pzs-ng's `audio_types` default is `mp3,flac`; whether it reads Vorbis tags is not verified. |
| Genre and year in the status bar | Yes | `zipscript.mp3.statusbar.theme.default` (`statusbar.id3tag`). |
| Banned or allowed genres (`banned_genres`, `allowed_genres`, `audio_banned_genre_check`, `audio_allowed_genre_check`, `audio_genre_warn`, `BANNED_GENRE`) | No | `ZipscriptMP3PostHook` reports tags but never refuses. The nuke filter only checks directory names. |
| Allowed years (`allowed_years`, `audio_year_check`, `audio_year_warn`, `BANNED_YEAR`) | No | As above. `nukefilter` can filter or enforce a year found in the *directory name* (`global.filter.year`, `global.enforce.year`), not the ID3 year. |
| Allowed constant bitrates (`allowed_constant_bitrates`, `audio_cbr_check`, `audio_cbr_warn`, `BANNED_BITRATE`) | No | The data exists (`MP3Info.getBitrate()`, `getEncodingtype()`), there is no rule. |
| "Warn instead of delete" mode (`audio_*_warn`) | No | Would come with the checks above. |
| Sort audio into genre, year, artist, group and language dirs (`audio_genre_sort`, `audio_year_sort`, `audio_artist_sort`, `audio_group_sort`, `audio_language_sort`, `audio_*_path`, `audio_nosort_dirs`, `spaces_to_dots`) | No | Designed in [RFC 0005](0005-sorted-symlink-views.md) (`SORTED` link type), not repeated here. Language sorting is not covered by RFC 0005. |
| Create `.m3u` on completion (`create_m3u`) | No | Not found. Needs a slave-side file write. |
| Audio-specific complete and halfway text (CBR/VBR variants, `progressmeter_audio`) | Partial | One set of themes for all types. |

### Video and other media

| pzs-ng feature (option) | DrFTPD | Evidence and notes |
| --- | --- | --- |
| Video and sample info, announces (`video_types`, `sample_list`, `sample_types`, `SAMPLE_*`) | Yes | `mediainfo` plugin: `MediaInfoPostHook`, `MediaInfoAnnouncer` (event type `mediainfo`), `mediabar.enabled`, `mediabar.directory`, `extensions` in `mediainfo.conf`. |
| Missing sample check (`check_for_missing_sample_dirs`, `create_missing_sample_link`, `incomplete_*sample_indicator`) | Partial | `autonuke` `Missing` config can nuke a release without `sample` (example in `autonuke.conf.dist`); there is no `(no-sample)` link type in the links plugin. |
| Missing NFO check and link (`check_for_missing_nfo_dirs`, `incomplete_nfo_indicator`) | Yes | `NFOMissing` link type; autonuke `Missing` with `x.missing=^(?i).+.nfo$`. |
| IMDB info from the NFO (`nfo_script`, `scripts/psxc-imdb`) | Yes | `imdb` plugin (`IMDBAnnouncer`, event type `imdb`); RFC 0002 extends it. pzs-ng relies on a separate script. |
| TVMaze / TVRage | Yes | `tvmaze` plugin (`TvMazeAnnouncer`); pzs-ng ships `sitebot/plugins/TVMaze.tcl` and `TVRage.tcl`. |
| External scripts per event (`audio_script`, `sample_script`, `nfo_script`, `complete_script`, `accept_script`, `affil_script`, `rescan_script`, `delbanned_script`) | Partial | DrFTPD uses plugins and events instead; no generic "run a command on complete" hook was found in zipscript. |

### Dupe handling

| pzs-ng feature | DrFTPD | Evidence and notes |
| --- | --- | --- |
| Dupe check on files and dirs (done by glftpd itself) | Yes | `dupecheck` plugin: `type` and `exempt` in `dupecheck.conf`, `DupeCheckHooks`. |
| Remove the dupe entry for a file that failed SFV (`ng-undupe`) | N/A | Dupe data is held with the VFS (`DupeCheckFileData`); a deleted bad file leaves nothing to undupe. Not verified end to end. |
| `site undupe` / `site dupe` | Yes | `SITE UNDUPE`, `SITE DUPE`, `SITE XDUPE` in the command configs. |

### Links, cleanup, rescan, nukes

| pzs-ng feature | DrFTPD | Evidence and notes |
| --- | --- | --- |
| `(incomplete)` links, also for multi-CD (`incomplete_indicator`, `incomplete_cd_indicator`, `incompleteislink`, `create_incomplete_links_in_group_dirs`) | Yes | `links.conf` `SFVINCOMPLETE` and `ZIPINCOMPLETE` with `x.dirname=%` (link next to the release) or a central dir; `x.addparentdir` for CDn. |
| `(no-nfo)` link | Yes | `NFOMISSING`. |
| `(no-sfv)` link (`create_missing_sfv_link`) | Yes | `SFVMISSING`. |
| `(no-sample)` link | No | No `NOSAMPLE` type in `src/plugins/links/.../types`. |
| Latest uploads dir | Yes | `LATESTDIR`. |
| Dead-link cleanup (`cleanup`, `cleanupdirs`, `cleanupdirs_dated`, `days_back_cleanup`, `delete_old_link`) | Yes | `LinkManager` removes links on `VirtualFileSystemInodeDeletedEvent` and repoints them on rename; `SITE FIXLINKS` repairs. No dated-dir scan needed because links are VFS inodes. |
| Rescan (`rescan`, `--quick`, `--normal`, `rescan_default_to_quick`, `create_missing_sfv`, `ignore_zero_sized_on_rescan`) | Partial | `SITE RESCAN [-r] [noforce] [nodelete] [nodelete0byte] [quiet] [path]` (`ZipscriptCommands.doSITE_RESCAN`). Missing: create an SFV when none exists, mark empty dirs as incomplete. |
| `postdel`, `datacleaner`, `ng-deldir` | N/A | Race status is recomputed from the VFS; there is nothing to clean. |
| `postunnuke` (re-check after unnuke) | Partial | `SITE UNNUKE` exists in core (`Nuke`); status is live. Whether a rescan runs after unnuke is not verified. |
| Auto-nuke of incomplete, empty, no-NFO, no-sample, low IMDB rating | Beyond pzs-ng | `autonuke` plugin (`x.type=Incomplete`, `Empty`, `Missing`, `IMDB`; `SITE AUTONUKES`). |
| Nuke by name or year filter | Beyond pzs-ng | `nukefilter` plugin. |
| `racestats`, `racedebug` | Partial | `SITE STAT`, `SITE TOP`, `SITE NEW`, `SITE NUKES`; no per-release race report command found beyond the listing and STOR output. |
| Speedtest dirs (`speedtest_dirs`, `SPEEDTEST`, `dl_speedtest`) | Yes | `speedtest` plugin (`SITE SPEEDTEST`, `SpeedTestAnnouncer`). |

### Announces and sitebot

| pzs-ng announce (name in `default.zst`) | DrFTPD announce type | Status |
| --- | --- | --- |
| `NEWDIR`, `DELDIR`, `WIPE` | `mkdir`, `rmdir`, `wipe` (`BasicAnnouncer`) | Yes |
| `NUKE`, `UNNUKE` | `nuke`, `unnuke` (`NukeAnnouncer`) | Yes |
| `PRE` | `pre` (`PreAnnouncer`) | Yes |
| `INVITE` | `invite` (`BasicAnnouncer`) | Yes |
| `SFV_*`, `RACE_*`, `HALFWAY_*`, `NEWLEADER_*`, `COMPLETE_*`, `COMPLETE_STAT_RACE_*` | `store.first`, `store.race`, `store.halfway`, `store.newraceleader`, `store.complete` | Yes, but one theme for all file types and no CBR/VBR split |
| `UPDATE_*` | none | No |
| `INCOMPLETE` | `autonuke` | Partial |
| `BAD_FILE_*` (CRC, 0 size, not in SFV, bitrate, genre, year, zip, dupe NFO, ...), `BADBITRATE`, `BADGENRE`, `BADYEAR`, `DOUBLESFV`, `RESUMESFV` | none | **No.** The uploader gets an FTP error but nothing is announced; no such type in the event lists of `SFVAnnouncer`, `ZipAnnouncer`, `MP3Announcer`, `FlacAnnouncer`. |
| `SAMPLE_*` | `mediainfo` | Yes |
| `ULTEST`, `DLTEST`, `SPEEDERROR` | `speedtest` | Yes |
| `REQUEST`, `REQFILLED` | request plugin | Not examined in detail |
| `LOGIN`, `LOGOUT`, `BADPASSWORD`, `KILLED`, ... | sitebot core | Not verified |
| Channel commands `NEW`, `NUKES`, `SEARCH`, `UPTIME`, `FREE`, `BW`, top lists | `SITE NEW`, `SITE NUKES`, `SITE SEARCH`, `SITE MASTERUPTIME`, `SITE DF`, `SITE BW`, `SITE TOP` and the IRC command configs | Not examined in detail |
| Tcl plugins `TVMaze`, `PreTime`, `GameFAQs` | `tvmaze`, `prebw`; nothing for PreTime or GameFAQs | Out of scope |

### Configuration knobs

pzs-ng compiles its options into the binary (`zsconfig.h`, changed with a rebuild). DrFTPD reads `zipscript.conf`, `links.conf`, themes and so on at runtime and reloads them (`SITE RELOAD`). New options below should follow the existing style: plain keys in the existing `.conf.dist` files, with defaults that keep today's behaviour.

## Gap list, prioritized

Size: S = under about 200 lines of change, M = a few hundred, L = needs its own RFC. "4.x-safe" means opt-in with defaults that change nothing and no stored-format change. Gaps 1 to 6 are the most valuable.

| # | Gap | What users would see | Size | Where | 4.x-safe? | Depends on |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | **Audio policy: banned or allowed genres, allowed years, allowed CBR bitrates, with a warn-only mode** | Unwanted MP3 uploads are refused (file or release deleted) with a clear message, or only warned, like `audio_banned_genre_check`, `audio_year_check`, `audio_cbr_check`. | M | `ZipscriptMP3PostHook` (data already parsed: `ID3Tag` genre and year, `MP3Info` bitrate and CBR/VBR); keys in `zipscript.conf`, optionally per section | Yes, off by default | none |
| 2 | **Bad-file announces** | The channel sees "bad CRC / 0 byte / not in SFV / banned genre ..." lines, like ngBot `BAD_FILE_*`. | S-M | New event published from `ZipscriptPostHook` and `ZipscriptPreHook` (and gap 1); new announce type and theme keys | Yes (new event and theme keys) | Gap 1 for the audio reasons; the CRC and 0-byte part is independent |
| 3 | **Audio sort links (genre, year, artist, group)** | `/MP3/SORTED/BY-GENRE/Techno/...` | L | `SORTED` link type, see [RFC 0005](0005-sorted-symlink-views.md) | Yes (new link type) | RFC 0005 accepted |
| 4 | **Announce thresholds and switches** | Halfway, new-leader and complete announces suppressed for small releases and single racers (`min_halfway_files`, `min_halfway_size`, `newleader_files_ahead`, `min_newleader_size`, `announce_norace`). | S | `SFVAnnouncer` (replace the hard-coded 4 files), `NewRaceLeaderManager` (replace the fixed 30 min), new keys in `zipscript.conf` and for raceleader | Yes, defaults equal today's behaviour | none |
| 5 | **`(no-sample)` link and missing-sample view** | `_NO_SAMPLE_` view or `(no-sample)-` links, like `check_for_missing_sample_dirs`. | S | New `NOSAMPLE` link type next to `NFOMissing` in `links/master/types/`; same config keys | Yes (new link type) | none |
| 6 | **Update announce and per-type announce themes (audio CBR/VBR, rar, video, zip)** | `UPDATE_*` lines during long races; different layouts per file type. | M | `SFVAnnouncer`, `ZipAnnouncer`, themes | Yes (extra theme keys that fall back to the existing ones) | Gap 4 |
| 7 | **Rescan options: create an SFV when missing, rename bad files to `.bad`, quick mode** | `site rescan` closer to pzs-ng's `--quick`, `--normal` and `mark_file_as_bad`. | S-M | `ZipscriptCommands.doSITE_RESCAN`, `ZipscriptPostHook` | Yes | none |
| 8 | **SFV hygiene options (lenient names, dedupe, strict, lowercase, `deny_resume_sfv`)** | Fewer false "not in SFV" and bad-SFV problems with sloppy releases. | M | SFV parsing in `SFVInfo` and slave `ZipscriptHandler`; first check what happens today | Yes if opt-in, but parsing runs on the slave, so master and slave must be upgraded together for new options | A look at current behaviour |
| 9 | **Banned file-name list** (`banned_filelist`, `zip_clean`, `del_banned_release`) | Unwanted names (ads, `.url`, scene tools) refused automatically. | S-M | `ZipscriptPreHook`, a list file under `config/plugins`; or a `perms.conf` pattern set | Yes, off by default | none |
| 10 | **Hide users and affils in stats and announces** | Pre groups can hide uploader names. | M | `SFVAnnouncer`, race stats output; a name-mapping config | Yes | Decision on whether DrFTPD wants this |
| 11 | **`.m3u` creation on complete audio releases** | A playlist appears when an MP3 release completes. | M-L | Needs a slave-side write plus VFS registration (`ZipscriptMP3Handler`, new job) | No: touches the slave protocol | Maintainer view on creating files on slaves |
| 12 | **Stats message file in the release (`.message`)** | A text file with the race result inside the release. | M | Same slave-write mechanism as 11 | No | Gap 11 |
| 13 | **Minor strictness options:** deny second NFO / NFO in zip dir, `sleep_on_bad`, a global no-check dir list | Small parity items. | S each | `ZipscriptPreHook`, `ZipscriptPostHook` | Yes | none |
| 14 | **Language sort for audio** | A `BY-LANGUAGE` view. | M | Extension of RFC 0005; needs a language source (ID3 has none by default) | Yes | Gap 3 |

## Things DrFTPD should not copy

- **Race data files and the helper tools `datacleaner`, `postdel`, `postunnuke`, `cleanup`, `ng-deldir`, `ng-undupe`, `ng-chown`.** They exist because glftpd has no built-in knowledge of races and can only run scripts after events. DrFTPD computes status from the VFS, removes links on VFS events, and runs checks in hooks.
- **glftpd plumbing:** chroot and 32-bit handling, `program_uid`, `program_gid`, `lock_optimize`, `max_seconds_wait_for_lock`, the `storage` mirror tree, `combine_path`, `strict_path_match`, `test_for_password`, `PASSWDFILE`, `GROUPFILE`, `gl_userfiles`.
- **Compile-time configuration.** DrFTPD reloads plain config files at runtime.
- **External scripts for NFO and IMDB lookups** (`psxc-imdb`). DrFTPD has the `imdb` and `tvmaze` plugins.
- **Real 0-byte `-MISSING` files and real `(incomplete)` marker files.** DrFTPD shows missing files at listing time and uses VFS links, so nothing needs deleting when files arrive.
- **Dated cleanup dirs** (`cleanupdirs_dated`, `days_back_cleanup`). Links are VFS inodes removed on events.
- **Non-recursive scans.** `SITE RESCAN -r` and `SITE FIXLINKS` already walk the tree.
- **External video checkers.** DrFTPD uses mediainfo (recent history: mkvalidator dropped in favour of mediainfo).
- **Script-driven auto-nuking.** `autonuke` and `nukefilter` already cover more than pzs-ng's single-release checks.

## Proposed order of small pull requests

1. **Announce thresholds** (gap 4): configurable halfway minimum, new-leader thresholds, single-racer switch. Defaults unchanged. Smallest and lowest risk.
2. **`NOSAMPLE` link type** (gap 5): follow the `NFOMissing` structure; update `links.conf.dist` and tests.
3. **Audio policy checks** (gap 1): genre, year and CBR rules with a warn-only mode, in `zipscript.conf`, default off.
4. **Bad-file announces** (gap 2): first CRC, 0-byte, not-in-SFV and double SFV, then the audio reasons from PR 3.
5. **RFC 0005 implementation** (gap 3), after the maintainers accept RFC 0005: one PR for the `SORTED` type with genre and year, then group and artist.
6. **Rescan improvements** (gap 7), and **SFV hygiene** (gap 8) after checking current behaviour.
7. **Per-type themes and update announce** (gap 6).
8. **Banned file-name list** (gap 9) and the small strictness options (gap 13).
9. Only if wanted: hide-users (gap 10), and `.m3u` plus stats file (gaps 11 and 12), which need a design for slave-side writes.

Every PR should add a test where practicable (the zipscript module already has `SFVToolsTest` and `ZipscriptMP3HandlerTest`), update the relevant `.conf.dist` and theme defaults, and avoid changing the stored VFS format.

## Decisions

Answers from the maintainer (2026-11-22) to the open questions below:

1. **Audio rules apply per section**, like `nukefilter` and `autonuke`.
2. **Rejected audio removes the whole release**, not only the offending file.
3. **Bad-file events get a new announcer**, separate from `SFVAnnouncer`, `ZipAnnouncer` and `MP3Announcer`.
4. **Slave-side file writes are acceptable**, so `.m3u` (gap 11) and the stats file (gap 12) are in scope.
5. **Hide-users is in scope as an option that defaults to off** (gap 10).
6. The 4-file halfway minimum and the 30-minute leader expiry are convenient defaults. **Halfway should be computed from the SFV**, i.e. 50 % of the files it lists (gap 4).
7. **Adding keys to `zipscript.conf` and the theme files is fine for 4.x.**
8. **One tracking issue per gap.** Gap 3 is already tracked by #67.

Tracking issues: gap 1 #580, gap 2 #581, gap 3 #67, gap 4 #582, gap 5 #583, gap 6 #584, gap 7 #585, gap 8 #586, gap 9 #587, gap 10 #588, gap 11 #589, gap 12 #590, gap 13 #591, gap 14 #592.

## Open questions

1. Should audio rules (gap 1) apply per section, per path, or globally? `nukefilter` and `autonuke` use sections; zipscript keys are global today.
2. For rejected audio, delete only the offending file, or the whole release directory (pzs-ng offers both, `del_banned_release`)? Which should be the default?
3. Should bad-file events get a new announcer, or extend `SFVAnnouncer`, `ZipAnnouncer` and `MP3Announcer` separately?
4. Are slave-side file writes (`.m3u`, stats message file) acceptable at all, given files are otherwise only created by uploads and jobs?
5. Are hide-users features in scope, given the stats data model (gap 10)?
6. Are the hard-coded minimum of 4 files for halfway (in `SFVAnnouncer`) and the 30 minute raceleader expiry intentional, or just convenient defaults?
7. Everything proposed here is meant to be opt-in on 4.x. Does adding keys to `zipscript.conf` and theme files meet the compatibility rules?
8. Should this RFC become one tracking issue plus one issue per gap, to match how the maintainers want to review small PRs?

## Not verified

- pzs-ng behaviour is taken from `README.ZSCONFIG`, `README` and `default.zst`, not from the C sources.
- DrFTPD: how SFV parsing treats malformed and duplicate entries, whether a rescan follows `SITE UNNUKE`, the `request` and sitebot core announce types, and whether anything like `deny_resume_sfv` exists.
