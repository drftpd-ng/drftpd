# Releasing

The version is declared once, in the root `pom.xml`:

```xml
<properties>
    <revision>4.0.12-SNAPSHOT</revision>
</properties>
```

Every module uses `${revision}`. At build time Maven writes the version and the git commit into
`org/drftpd/common/version.properties` (drftpd-common). `org.drftpd.common.util.Version` reads it, and it is what
the login prompt (`${version}` in `perms.conf`), `SITE VERSION`, `SITE STATUS`, the slave start banner and the sitebot
report, for example `DrFTPD 4.0.12-beta3 (87d6ba0)`. A build from a source tarball without `.git` shows no commit.

## Cutting a release

1. Set `<revision>` in the root `pom.xml` to the release version, for example `4.0.12`, and nothing else.
2. Run `mvn clean install` and check the result.
3. Commit (for example `Release 4.0.12`).
4. Tag with exactly the version, **without a leading `v`** (`4.0.12`, `4.0.12-beta3`), and push the tag:
   `git tag 4.0.12 && git push origin 4.0.12`
   The Docker workflow builds the images for that tag, and stops with an error if the tag is not exactly `<revision>`.
5. Set `<revision>` back to the next `-SNAPSHOT` (for example `4.0.13-SNAPSHOT`) and commit.

## Building another version without editing the pom

`mvn -Drevision=4.0.12-beta3 clean install` builds jars named `*-4.0.12-beta3.jar` and reports that version.
This is useful for a test build or for CI. The installed poms are flattened (`flatten-maven-plugin`), so
`${revision}` is resolved in them.

The `runtime/master/build` and `runtime/slave/build` directories are emptied by `mvn clean`; always build with `clean`
when you change the version, so that jars of two versions do not end up together on the classpath.
