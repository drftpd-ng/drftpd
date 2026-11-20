# DrFTPD design RFCs

This directory holds design proposals ("RFCs") for changes that are large, touch several modules, or need decisions from the maintainers before code is written.

## Process

- Each RFC is one Markdown file named `NNNN-short-title.md`, numbered in the order they are proposed. Numbers are never reused.
- An RFC is proposed and discussed in a pull request. Comments on the PR are the discussion; the file is updated as decisions are taken.
- Each RFC starts with a **Status**:
  - **Draft**: open for discussion.
  - **Accepted**: the maintainers agreed on the design; implementation can follow in separate PRs.
  - **Rejected**: not pursued; the file is kept for the record, with the reason.
- An RFC links the GitHub issue it addresses and ends with numbered open questions for the maintainers.

Sections used so far: Summary (Status, Addresses, Scope), Motivation, Background (how the code works today), Proposal, Configuration, Upgrade / compatibility, Security considerations, Alternatives considered, Test plan, Open questions.

## Index

| RFC | Title | Issue | Status |
| --- | --- | --- | --- |
| [0001](https://github.com/drftpd-ng/drftpd/pull/524) | IPv6 support, phase 1 (in PR #524; the file `0001-ipv6-phase1.md` lands with it) | [#19](https://github.com/drftpd-ng/drftpd/issues/19) | Draft |
| [0002](0002-imdb-lookup-without-nfo.md) | IMDB lookup from the release name, without an NFO | [#56](https://github.com/drftpd-ng/drftpd/issues/56) | Draft |
| [0003](0003-ftp-client-certificate-auth.md) | FTP client certificate authentication | [#136](https://github.com/drftpd-ng/drftpd/issues/136) | Draft |
| [0004](0004-srrdb-plugin.md) | srrDB plugin (building SRR files for releases) | [#137](https://github.com/drftpd-ng/drftpd/issues/137) | Draft |
| [0005](0005-sorted-symlink-views.md) | Sorted symlink views (genre, year, group, …) | [#67](https://github.com/drftpd-ng/drftpd/issues/67) | Draft |
