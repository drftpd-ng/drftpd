package org.drftpd.archive.master.archivetypes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ArchiveTypeDestinationTest {

    private static final String RLS = "Some.Show.S01E02.720p.HDTV.x264-GRP";

    @Test
    void episodeIsSubstituted() {
        assertEquals("Some.Show/S01/E02", ArchiveType.resolveRlsDestination("${rls}/S${season}/E${episode}", RLS));
    }

    @Test
    void singleDigitEpisodeIsPadded() {
        assertEquals("E05", ArchiveType.resolveRlsDestination("E${episode}", "Some.Show.S1E5.720p-GRP"));
    }

    @Test
    void episodeUnresolvedGivesUnknown() {
        assertEquals("UNKNOWN", ArchiveType.resolveRlsDestination("E${episode}", "Some.Movie.2020.1080p-GRP"));
    }

    @Test
    void rlsAndSeasonAreSubstituted() {
        assertEquals("Some.Show/Season.01", ArchiveType.resolveRlsDestination("${rls}/Season.${season}", RLS));
    }

    @Test
    void rlsUnresolvedGivesUnknown() {
        assertEquals("UNKNOWN", ArchiveType.resolveRlsDestination("${rls}", "Some.Anime.E01.720p-GRP"));
    }

    @Test
    void regexVariableIsSubstituted() {
        assertEquals("2010-12", ArchiveType.resolveRlsDestination("2010-${regex:(\\d\\d).*}", "12.foo-GRP"));
    }

    @Test
    void regexVariableUnmatchedGivesUnknown() {
        assertEquals("UNKNOWN", ArchiveType.resolveRlsDestination("${regex:(\\d\\d).*}", "foo-GRP"));
    }
}
