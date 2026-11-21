package org.drftpd.archive.master.archivetypes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ArchiveTypeDestinationTest {

    private static String resolve(String template, String name) {
        return ArchiveType.resolveRlsDestination(template, name, ArchiveType.DEFAULT_RLS_REGEX);
    }

    private static final String RLS = "Some.Show.S01E02.720p.HDTV.x264-GRP";

    @Test
    void episodeIsSubstituted() {
        assertEquals("Some.Show/S01/E02", resolve("${rls}/S${season}/E${episode}", RLS));
    }

    @Test
    void singleDigitEpisodeIsPadded() {
        assertEquals("E05", resolve("E${episode}", "Some.Show.S1E5.720p-GRP"));
    }

    @Test
    void episodeUnresolvedGivesUnknown() {
        assertEquals("UNKNOWN", resolve("E${episode}", "Some.Movie.2020.1080p-GRP"));
    }

    @Test
    void rlsAndSeasonAreSubstituted() {
        assertEquals("Some.Show/Season.01", resolve("${rls}/Season.${season}", RLS));
    }

    @Test
    void rlsUnresolvedGivesUnknown() {
        assertEquals("UNKNOWN", resolve("${rls}", "Some.Anime.E01.720p-GRP"));
    }

    @Test
    void regexVariableIsSubstituted() {
        assertEquals("2010-12", resolve("2010-${regex:(\\d\\d).*}", "12.foo-GRP"));
    }

    @Test
    void regexVariableUnmatchedGivesUnknown() {
        assertEquals("UNKNOWN", resolve("${regex:(\\d\\d).*}", "foo-GRP"));
    }

    @Test
    void customRlsRegexHandlesAnimeNames() {
        assertEquals("Some.Anime", ArchiveType.resolveRlsDestination("${rls}", "Some.Anime.E01.720p-GRP", "(?i)(.*)(\\.|-|_)E\\d.*"));
    }

    @Test
    void customRlsRegexHandlesDatedNames() {
        assertEquals("Some.Show", ArchiveType.resolveRlsDestination("${rls}", "Some.Show.2023-11-11.720p-GRP", "(.*?)\\.\\d{4}-\\d\\d-\\d\\d.*"));
    }

    @Test
    void dollarInReleaseNameIsKept() {
        assertEquals("Some$1.Show/S01", resolve("${rls}/S${season}", "Some$1.Show.S01E02.720p-GRP"));
    }

    @Test
    void backslashInReleaseNameIsKept() {
        assertEquals("Some\\Show/S01", resolve("${rls}/S${season}", "Some\\Show.S01E02.720p-GRP"));
    }

    @Test
    void dollarAndBackslashInRegexVariableAreKept() {
        assertEquals("a$b\\c", resolve("${regex:(.*)-GRP}", "a$b\\c-GRP"));
    }
}
