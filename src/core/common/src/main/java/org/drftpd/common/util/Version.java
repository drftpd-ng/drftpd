/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The version of this DrFTPD build, read from {@code org/drftpd/common/version.properties}.
 * That file is filtered by Maven, so the version is declared once, in the root pom
 * ({@code <revision>}), and the git commit comes from git-commit-id-maven-plugin.
 * <p>
 * When the commit is not known (build from a source tarball without {@code .git}) it is simply left out.
 * When the properties file is missing or not filtered (some IDE builds) the version is {@code unknown}.
 */
public final class Version {

    static final String RESOURCE = "/org/drftpd/common/version.properties";
    static final String UNKNOWN = "unknown";

    private static final Version CURRENT = load();

    private final String _version;
    private final String _commit;

    Version(String version, String commit) {
        _version = clean(version);
        _commit = clean(commit);
    }

    /**
     * Builds a Version from the content of a version.properties file.
     * A value that is missing, blank or still an unresolved Maven placeholder ({@code ${...}}) is treated as absent.
     */
    static Version from(Properties props) {
        return new Version(props.getProperty("version"), props.getProperty("commit"));
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        value = value.trim();
        return value.startsWith("${") ? "" : value;
    }

    private static Version load() {
        Properties props = new Properties();
        try (InputStream in = Version.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException | RuntimeException e) {
            // fall through with whatever was read, the version is reported as unknown
        }
        return from(props);
    }

    /** The project version, for example {@code 4.0.12-SNAPSHOT}, or {@code unknown}. */
    public static String getVersion() {
        return CURRENT.version();
    }

    /** The abbreviated git commit this build was made from, or an empty string if unknown. */
    public static String getCommit() {
        return CURRENT.commit();
    }

    /** For example {@code DrFTPD 4.0.12-SNAPSHOT (87d6ba0)}, or {@code DrFTPD 4.0.12-SNAPSHOT} without commit. */
    public static String getDisplay() {
        return CURRENT.display();
    }

    /** Like {@link #getDisplay()} without the product name: {@code 4.0.12-SNAPSHOT (87d6ba0)}. */
    public static String getFullVersion() {
        return CURRENT.fullVersion();
    }

    /**
     * Replaces the {@code ${version}} placeholder in a text (used by login_prompt in perms.conf) by
     * {@link #getDisplay()}. A text without the placeholder is returned unchanged.
     */
    public static String expandPlaceholder(String text) {
        return text.replace("${version}", getDisplay());
    }

    String version() {
        return _version.isEmpty() ? UNKNOWN : _version;
    }

    String commit() {
        return _commit;
    }

    String fullVersion() {
        return _commit.isEmpty() ? version() : version() + " (" + _commit + ")";
    }

    String display() {
        return "DrFTPD " + fullVersion();
    }
}
