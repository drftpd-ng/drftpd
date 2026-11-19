/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.indexation;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.store.FSDirectory;
import org.drftpd.common.dynamicdata.Key;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.master.indexation.AdvancedSearchParams.InodeType;
import org.drftpd.master.vfs.IndexTestInodes;
import org.drftpd.master.vfs.event.ImmutableInodeHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End to end tests of {@link LuceneEngine} on a temporary directory, with fake releases.
 */
public class LuceneEngineTest {

    private static final long MB = 1024L * 1024L;
    private static final long T0 = 1_700_000_000_000L;
    private static final long DAY = 24L * 3600L * 1000L;

    private static final String MOVIE = "/MOVIES/The.Movie.2009.720p.BluRay-GRP";
    private static final String MOVIE2 = "/MOVIES/Other.Film.1999.DVDRip-XYZ";
    private static final String SHOW = "/TV/Show.S01E01.HDTV-TVG";

    @TempDir
    Path _tmp;

    private LuceneEngine _engine;

    /** Test plugin: stores the year found in the name and a text tag, like the zipscript/imdb ones. */
    public static class YearDataExtension implements IndexDataExtensionInterface {
        private static final Pattern YEAR = Pattern.compile("\\.((?:19|20)\\d\\d)\\.");

        @Override
        public void addData(Document doc, ImmutableInodeHandle inode) {
            Matcher m = YEAR.matcher(inode.getName());
            if (inode.isDirectory() && m.find()) {
                LuceneUtils.addIntField(doc, "testyear", Integer.parseInt(m.group(1)));
                LuceneUtils.addTextField(doc, "testtag", "Release " + inode.getName());
            } else {
                LuceneUtils.addIntField(doc, "testyear", -1);
                LuceneUtils.addTextField(doc, "testtag", "");
            }
        }
    }

    public static class YearQueryParams {
        static final Key<YearQueryParams> KEY = new Key<>(YearQueryParams.class, "yearqueryparams");
        Integer minYear;
        Integer maxYear;
        String tag;
    }

    public static class YearQueryExtension implements QueryTermExtensionInterface {
        @Override
        public void addQueryTerms(BooleanQuery.Builder query, AdvancedSearchParams params) {
            try {
                YearQueryParams p = params.getExtensionData(YearQueryParams.KEY);
                if (p.minYear != null || p.maxYear != null) {
                    query.add(LuceneUtils.intRangeQuery("testyear", p.minYear, p.maxYear), Occur.MUST);
                }
                if (p.tag != null) {
                    query.add(LuceneUtils.analyze("testtag", p.tag), Occur.MUST);
                }
            } catch (KeyNotFoundException e) {
                // nothing to add
            }
        }
    }

    private LuceneEngine newEngine(Path indexDir) throws IndexException {
        LuceneEngine engine = new LuceneEngine(indexDir);
        engine.addDataExtension(new YearDataExtension());
        engine.addQueryExtension(new YearQueryExtension());
        engine.openStreams();
        return engine;
    }

    private Path indexDir() {
        return _tmp.resolve("index");
    }

    @BeforeEach
    void setUp() throws IndexException {
        _engine = newEngine(indexDir());
        _engine.addInode(IndexTestInodes.dir("/MOVIES", "drftpd", "drftpd", T0, 0));
        _engine.addInode(IndexTestInodes.dir(MOVIE, "alice", "grpA", T0 + DAY, 0));
        _engine.addInode(IndexTestInodes.file(MOVIE + "/grp-movie.r00", "alice", "grpA", T0 + DAY, 100 * MB, "slave1", "slave2"));
        _engine.addInode(IndexTestInodes.file(MOVIE + "/grp-movie.sfv", "alice", "grpA", T0 + DAY + 1, 1024, "slave1"));
        _engine.addInode(IndexTestInodes.dir(MOVIE2, "carol", "grpB", T0 + 2 * DAY, 0));
        _engine.addInode(IndexTestInodes.file(MOVIE2 + "/xyz-film.avi", "carol", "grpB", T0 + 2 * DAY, 700 * MB, "slave2"));
        _engine.addInode(IndexTestInodes.dir("/TV", "drftpd", "drftpd", T0, 0));
        _engine.addInode(IndexTestInodes.dir(SHOW, "bob", "grpB", T0 + 3 * DAY, 0));
        _engine.addInode(IndexTestInodes.file(SHOW + "/show.s01e01.mkv", "bob", "grpB", T0 + 3 * DAY, 500 * MB, "slave3"));
    }

    @AfterEach
    void tearDown() {
        if (_engine != null) {
            _engine.closeAll();
        }
    }

    private Map<String, String> find(String startPath, AdvancedSearchParams params) throws IndexException {
        if (params.getLimit() == null) {
            params.setLimit(0);
        }
        return _engine.advancedFind(startPath, params, "test");
    }

    private Set<String> paths(String startPath, AdvancedSearchParams params) throws IndexException {
        return find(startPath, params).keySet();
    }

    @Test
    public void nameSearchIsAnalyzed() throws IndexException {
        AdvancedSearchParams params = new AdvancedSearchParams();
        params.setName("MOVIE grp");
        assertEquals(Set.of(MOVIE + "/", MOVIE + "/grp-movie.r00", MOVIE + "/grp-movie.sfv"), paths("/", params));

        params = new AdvancedSearchParams();
        params.setName("movie");
        params.setInodeType(InodeType.DIRECTORY);
        assertEquals(Set.of(MOVIE + "/"), paths("/", params));

        params = new AdvancedSearchParams();
        params.setName("movie");
        params.setInodeType(InodeType.FILE);
        assertEquals(Set.of(MOVIE + "/grp-movie.r00", MOVIE + "/grp-movie.sfv"), paths("/", params));
    }

    @Test
    public void nameWildcards() throws IndexException {
        AdvancedSearchParams params = new AdvancedSearchParams();
        params.setName("blur*");
        assertEquals(Set.of(MOVIE + "/"), paths("/", params));

        params = new AdvancedSearchParams();
        params.setName("s01e0?");
        assertEquals(Set.of(SHOW + "/", SHOW + "/show.s01e01.mkv"), paths("/", params));

        AdvancedSearchParams invalid = new AdvancedSearchParams();
        invalid.setName("m*");
        assertThrows(IllegalArgumentException.class, () -> find("/", invalid));
    }

    @Test
    public void exactAndEndsWith() throws IndexException {
        AdvancedSearchParams params = new AdvancedSearchParams();
        params.setExact("The.Movie.2009.720p.BluRay-GRP");
        assertEquals(Set.of(MOVIE + "/"), paths("/", params));

        // exact is case sensitive and not analyzed
        params = new AdvancedSearchParams();
        params.setExact("the.movie.2009.720p.bluray-grp");
        assertTrue(paths("/", params).isEmpty());

        params = new AdvancedSearchParams();
        params.setExact("Other.Film.*");
        assertEquals(Set.of(MOVIE2 + "/"), paths("/", params));

        params = new AdvancedSearchParams();
        params.setEndsWith(".sfv");
        assertEquals(Set.of(MOVIE + "/grp-movie.sfv"), paths("/", params));
    }

    @Test
    public void regexUsesJavaSyntaxOnTheFullPath() throws IndexException {
        AdvancedSearchParams params = new AdvancedSearchParams();
        params.setRegex(".*\\.S\\d{2}E\\d{2}\\..*");
        assertEquals(Set.of(SHOW + "/", SHOW + "/show.s01e01.mkv"), paths("/", params));

        params = new AdvancedSearchParams();
        params.setRegex(".*/\\w+\\.S\\d{2}E\\d{2}\\.[^/]*/");
        assertEquals(Set.of(SHOW + "/"), paths("/", params));

        // case sensitive unless asked otherwise
        params = new AdvancedSearchParams();
        params.setRegex(".*/show\\..*");
        assertEquals(Set.of(SHOW + "/show.s01e01.mkv"), paths("/", params));

        // the whole path must match, as Lucene 3.6 RegexQuery (Matcher.matches()) did
        params = new AdvancedSearchParams();
        params.setRegex("Show");
        assertTrue(paths("/", params).isEmpty());

        params = new AdvancedSearchParams();
        params.setRegex("(?i).*/show\\..*");
        assertEquals(Set.of(SHOW + "/", SHOW + "/show.s01e01.mkv"), paths("/", params));

        params = new AdvancedSearchParams();
        params.setRegex("/MOVIES/[^/]+/(?!grp-).+");
        assertEquals(Set.of(MOVIE2 + "/xyz-film.avi"), paths("/", params));

        AdvancedSearchParams invalid = new AdvancedSearchParams();
        invalid.setRegex("(unclosed");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> find("/", invalid));
        assertTrue(e.getMessage().startsWith("Invalid regular expression"), e.getMessage());
    }

    @Test
    public void numericRanges() throws IndexException {
        AdvancedSearchParams params = new AdvancedSearchParams();
        params.setMinSize(50 * MB);
        assertEquals(Set.of(MOVIE + "/grp-movie.r00", MOVIE2 + "/xyz-film.avi", SHOW + "/show.s01e01.mkv"),
                paths("/", params));

        params = new AdvancedSearchParams();
        params.setMinSize(50 * MB);
        params.setMaxSize(600 * MB);
        assertEquals(Set.of(MOVIE + "/grp-movie.r00", SHOW + "/show.s01e01.mkv"), paths("/", params));

        params = new AdvancedSearchParams();
        params.setMinAge(T0 + 2 * DAY);
        params.setMaxAge(T0 + 3 * DAY - 1);
        assertEquals(Set.of(MOVIE2 + "/", MOVIE2 + "/xyz-film.avi"), paths("/", params));

        params = new AdvancedSearchParams();
        params.setMinSlaves(2);
        assertEquals(Set.of(MOVIE + "/grp-movie.r00"), paths("/", params));
    }

    @Test
    public void sectionOwnerGroupAndSlaves() throws IndexException {
        AdvancedSearchParams params = new AdvancedSearchParams();
        assertEquals(Set.of(SHOW + "/", SHOW + "/show.s01e01.mkv"), paths("/TV", params));

        params = new AdvancedSearchParams();
        params.setInodeType(InodeType.DIRECTORY);
        assertEquals(Set.of(MOVIE + "/", MOVIE2 + "/"), paths("/MOVIES", params));

        params = new AdvancedSearchParams();
        params.setOwner("carol");
        assertEquals(Set.of(MOVIE2 + "/", MOVIE2 + "/xyz-film.avi"), paths("/", params));

        params = new AdvancedSearchParams();
        params.setGroup("grpB");
        params.setInodeType(InodeType.FILE);
        assertEquals(Set.of(MOVIE2 + "/xyz-film.avi", SHOW + "/show.s01e01.mkv"), paths("/", params));

        params = new AdvancedSearchParams();
        params.setSlaves(Set.of("slave1"));
        assertEquals(Set.of(MOVIE + "/grp-movie.r00", MOVIE + "/grp-movie.sfv"), paths("/", params));

        params = new AdvancedSearchParams();
        params.setSlaves(Set.of("slave1", "slave2"));
        assertEquals(Set.of(MOVIE + "/grp-movie.r00"), paths("/", params));
    }

    @Test
    public void typesAreReturned() throws IndexException {
        AdvancedSearchParams params = new AdvancedSearchParams();
        Map<String, String> result = find("/TV", params);
        assertEquals("d", result.get(SHOW + "/"));
        assertEquals("f", result.get(SHOW + "/show.s01e01.mkv"));
    }

    @Test
    public void sortingAndLimit() throws IndexException {
        AdvancedSearchParams params = new AdvancedSearchParams();
        params.setInodeType(InodeType.FILE);
        params.setSortField("size");
        params.setSortOrder(true);
        params.setLimit(2);
        assertEquals(List.of(MOVIE2 + "/xyz-film.avi", SHOW + "/show.s01e01.mkv"),
                new ArrayList<>(find("/", params).keySet()));

        params = new AdvancedSearchParams();
        params.setInodeType(InodeType.DIRECTORY);
        params.setSortField("lastmodified"); // lower case, as used by the nuke and approve commands
        params.setSortOrder(false);
        assertEquals(List.of("/MOVIES/", "/TV/", MOVIE + "/", MOVIE2 + "/", SHOW + "/"),
                new ArrayList<>(find("/", params).keySet()));

        params = new AdvancedSearchParams();
        params.setInodeType(InodeType.DIRECTORY);
        params.setSortField("lastModified");
        params.setSortOrder(true);
        params.setLimit(1);
        assertEquals(List.of(SHOW + "/"), new ArrayList<>(find("/", params).keySet()));

        params = new AdvancedSearchParams();
        params.setSortField("owner");
        params.setSortOrder(false);
        params.setInodeType(InodeType.FILE);
        assertEquals(List.of(MOVIE + "/grp-movie.r00", MOVIE + "/grp-movie.sfv", SHOW + "/show.s01e01.mkv",
                MOVIE2 + "/xyz-film.avi"), new ArrayList<>(find("/", params).keySet()));

        // default: full path
        params = new AdvancedSearchParams();
        params.setInodeType(InodeType.FILE);
        assertEquals(List.of(MOVIE2 + "/xyz-film.avi", MOVIE + "/grp-movie.r00", MOVIE + "/grp-movie.sfv",
                SHOW + "/show.s01e01.mkv"), new ArrayList<>(find("/", params).keySet()));

        params = new AdvancedSearchParams();
        params.setInodeType(InodeType.FILE);
        params.setSortOrder(true);
        assertEquals(List.of(SHOW + "/show.s01e01.mkv", MOVIE + "/grp-movie.sfv", MOVIE + "/grp-movie.r00",
                MOVIE2 + "/xyz-film.avi"), new ArrayList<>(find("/", params).keySet()));
    }

    @Test
    public void randomSortReturnsEveryHitOnce() throws IndexException {
        Set<List<String>> orders = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            AdvancedSearchParams params = new AdvancedSearchParams();
            params.setSortOrder(null);
            params.setMinSize(0);
            List<String> result = new ArrayList<>(find("/", params).keySet());
            assertEquals(9, result.size());
            assertEquals(9, new HashSet<>(result).size());
            orders.add(result);

            params = new AdvancedSearchParams();
            params.setSortOrder(null);
            params.setMinSize(0);
            params.setLimit(3);
            assertEquals(3, find("/", params).size());
        }
        assertTrue(orders.size() > 1, "random sort always returned the same order");
    }

    @Test
    public void defaultLimitIsMaxHits() throws IndexException {
        _engine.setMaxHitsNumber(2);
        AdvancedSearchParams params = new AdvancedSearchParams();
        params.setMinSize(0);
        assertEquals(2, _engine.advancedFind("/", params, "test").size());
        assertEquals(2, _engine.findInode("/", "movie", InodeType.ANY).size());
        _engine.setMaxHitsNumber(0);
        params = new AdvancedSearchParams();
        params.setMinSize(0);
        assertEquals(9, _engine.advancedFind("/", params, "test").size());
        // a search without any criteria matches nothing, as it did with Lucene 3.6
        assertEquals(0, _engine.advancedFind("/", new AdvancedSearchParams(), "test").size());
        assertEquals(3, _engine.findInode("/", "movie", InodeType.ANY).size());
    }

    @Test
    public void pluginFields() throws IndexException {
        AdvancedSearchParams params = new AdvancedSearchParams();
        YearQueryParams year = new YearQueryParams();
        year.minYear = 2000;
        params.addExtensionData(YearQueryParams.KEY, year);
        assertEquals(Set.of(MOVIE + "/"), paths("/", params));

        params = new AdvancedSearchParams();
        year = new YearQueryParams();
        year.maxYear = 2010;
        year.minYear = 1990;
        params.addExtensionData(YearQueryParams.KEY, year);
        assertEquals(Set.of(MOVIE + "/", MOVIE2 + "/"), paths("/", params));

        params = new AdvancedSearchParams();
        year = new YearQueryParams();
        year.tag = "release dvd*";
        params.addExtensionData(YearQueryParams.KEY, year);
        assertEquals(Set.of(MOVIE2 + "/"), paths("/", params));
    }

    @Test
    public void findInode() throws IndexException {
        assertEquals(Set.of(MOVIE + "/", MOVIE + "/grp-movie.r00", MOVIE + "/grp-movie.sfv"),
                _engine.findInode("/", "movie", InodeType.ANY));
        assertEquals(Set.of(MOVIE + "/"), _engine.findInode("/", "movie", InodeType.DIRECTORY));
        assertEquals(Set.of(), _engine.findInode("/TV", "movie", InodeType.ANY));
    }

    @Test
    public void updateDeleteAndNoDuplicates() throws IndexException {
        // adding the same inode again must not create a second document
        _engine.addInode(IndexTestInodes.file(SHOW + "/show.s01e01.mkv", "bob", "grpB", T0 + 3 * DAY, 500 * MB, "slave3"));
        assertEquals("9", _engine.getStatus().get("inodes"));

        _engine.updateInode(IndexTestInodes.file(SHOW + "/show.s01e01.mkv", "bob", "grpB", T0 + 3 * DAY, 5 * MB, "slave3", "slave4"));
        AdvancedSearchParams params = new AdvancedSearchParams();
        params.setMaxSize(10 * MB);
        params.setInodeType(InodeType.FILE);
        assertEquals(Set.of(MOVIE + "/grp-movie.sfv", SHOW + "/show.s01e01.mkv"), paths("/", params));
        params = new AdvancedSearchParams();
        params.setSlaves(Set.of("slave4"));
        assertEquals(Set.of(SHOW + "/show.s01e01.mkv"), paths("/", params));

        _engine.deleteInode(IndexTestInodes.file(SHOW + "/show.s01e01.mkv", "bob", "grpB", 0, 0));
        assertEquals(Set.of(SHOW + "/"), paths("/TV", new AdvancedSearchParams()));
        _engine.deleteInode(IndexTestInodes.dir(SHOW, "bob", "grpB", 0, 0));
        assertEquals(Set.of(), paths("/TV", new AdvancedSearchParams()));
        assertEquals("7", _engine.getStatus().get("inodes"));
    }

    @Test
    public void renameDirectoryMovesItsChildren() throws IndexException {
        String archived = "/ARCHIVE/The.Movie.2009.720p.BluRay-GRP";
        _engine.addInode(IndexTestInodes.dir("/ARCHIVE", "drftpd", "drftpd", T0, 0));
        _engine.addInode(IndexTestInodes.dir(MOVIE + "/Sample", "alice", "grpA", T0 + DAY, 0));
        _engine.addInode(IndexTestInodes.file(MOVIE + "/Sample/sample.mkv", "alice", "grpA", T0 + DAY, 20 * MB, "slave2"));

        _engine.renameInode(IndexTestInodes.dir(MOVIE, "alice", "grpA", T0 + DAY, 0),
                IndexTestInodes.dir(archived, "alice", "grpA", T0 + DAY, 0));

        AdvancedSearchParams params = new AdvancedSearchParams();
        params.setName("movie");
        assertEquals(Set.of(archived + "/", archived + "/grp-movie.r00", archived + "/grp-movie.sfv"),
                paths("/", params));
        assertEquals(Set.of(MOVIE2 + "/", MOVIE2 + "/xyz-film.avi"), paths("/MOVIES", new AdvancedSearchParams()));

        // parentPath of the children has been updated
        assertEquals(Set.of(archived + "/", archived + "/grp-movie.r00", archived + "/grp-movie.sfv",
                        archived + "/Sample/", archived + "/Sample/sample.mkv"),
                paths("/ARCHIVE", new AdvancedSearchParams()));
        assertEquals(Set.of(archived + "/Sample/sample.mkv"), paths(archived + "/Sample", new AdvancedSearchParams()));

        // numeric fields (points + doc values) and plugin fields have been rebuilt
        params = new AdvancedSearchParams();
        params.setMinSize(10 * MB);
        params.setMaxSize(200 * MB);
        params.setSortField("size");
        params.setSortOrder(false);
        assertEquals(List.of(archived + "/Sample/sample.mkv", archived + "/grp-movie.r00"),
                new ArrayList<>(find("/ARCHIVE", params).keySet()));
        params = new AdvancedSearchParams();
        params.setSlaves(Set.of("slave1", "slave2"));
        params.setMinSlaves(2);
        assertEquals(Set.of(archived + "/grp-movie.r00"), paths("/", params));
        params = new AdvancedSearchParams();
        params.setOwner("alice");
        params.setExact("grp-movie.*");
        assertEquals(Set.of(archived + "/grp-movie.r00", archived + "/grp-movie.sfv"), paths("/", params));

        params = new AdvancedSearchParams();
        YearQueryParams year = new YearQueryParams();
        year.minYear = 2009;
        year.maxYear = 2009;
        params.addExtensionData(YearQueryParams.KEY, year);
        assertEquals(Set.of(archived + "/"), paths("/", params));

        assertEquals("12", _engine.getStatus().get("inodes"));
    }

    @Test
    public void indexSurvivesRestart() throws IndexException {
        _engine.closeAll();
        _engine = newEngine(indexDir());
        assertFalse(_engine.isRebuildRequired());
        assertEquals("9", _engine.getStatus().get("inodes"));
        AdvancedSearchParams params = new AdvancedSearchParams();
        params.setName("show");
        assertEquals(Set.of(SHOW + "/", SHOW + "/show.s01e01.mkv"), paths("/", params));
    }

    @Test
    public void backupIsAConsistentCopyOfTheIndex() throws IOException, IndexException {
        Path backup = _tmp.resolve("index.bkp").resolve("20240101-000000");
        _engine.backupTo(backup);
        // writes after the backup do not reach it
        _engine.addInode(IndexTestInodes.dir("/NEW", "drftpd", "drftpd", T0, 0));

        try (FSDirectory dir = FSDirectory.open(backup); DirectoryReader reader = DirectoryReader.open(dir)) {
            assertEquals(9, reader.numDocs());
        }

        // the backup can be used as an index
        LuceneEngine restored = newEngine(backup);
        try {
            assertFalse(restored.isRebuildRequired());
            AdvancedSearchParams params = new AdvancedSearchParams();
            params.setName("film");
            params.setLimit(0);
            assertEquals(Set.of(MOVIE2 + "/", MOVIE2 + "/xyz-film.avi"),
                    restored.advancedFind("/", params, "test").keySet());
        } finally {
            restored.closeAll();
        }

        // the live index still works and old commits were released
        _engine.backupTo(_tmp.resolve("index.bkp").resolve("20240101-000001"));
        assertEquals("10", _engine.getStatus().get("inodes"));
    }

    @Test
    public void status() {
        Map<String, String> status = _engine.getStatus();
        assertTrue(status.get("backend").contains("Lucene 10."), status.get("backend"));
        assertEquals("9", status.get("inodes"));
        assertNotNull(status.get("size"));
        assertFalse(status.containsKey("rebuild"));
    }

    private static Path findMovedAside(Path tmp) throws IOException {
        try (Stream<Path> list = Files.list(tmp)) {
            return list.filter(p -> p.getFileName().toString().startsWith("index" + LuceneEngine.INCOMPATIBLE_SUFFIX))
                    .findFirst().orElse(null);
        }
    }

    private void checkIncompatibleIndexIsMovedAside(byte[] segmentsContent) throws IOException, IndexException {
        _engine.closeAll();
        _engine = null;
        Path index = indexDir();
        try (Stream<Path> files = Files.list(index)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                Files.delete(p);
            }
        }
        Files.write(index.resolve("segments_2"), segmentsContent);
        Files.write(index.resolve("segments.gen"), new byte[]{-1, -1, -1, -2, 0, 0, 0, 0, 0, 0, 0, 2});
        Files.write(index.resolve("_0.cfs"), "old data".getBytes(StandardCharsets.US_ASCII));

        _engine = newEngine(index);

        assertTrue(_engine.isRebuildRequired());
        Path movedAside = findMovedAside(_tmp);
        assertNotNull(movedAside, "the old index was not moved aside");
        assertEquals(movedAside, _engine.getMovedAsideIndex());
        // nothing was deleted
        assertArrayEquals(segmentsContent, Files.readAllBytes(movedAside.resolve("segments_2")));
        assertEquals("old data", Files.readString(movedAside.resolve("_0.cfs")));
        assertTrue(_engine.getStatus().get("rebuild").contains("SITE REBUILDINDEX"));

        // a new, working, empty index has been created
        assertEquals("0", _engine.getStatus().get("inodes"));
        _engine.addInode(IndexTestInodes.dir("/NEW", "drftpd", "drftpd", T0, 0));
        AdvancedSearchParams params = new AdvancedSearchParams();
        params.setName("new");
        assertEquals(Set.of("/NEW/"), paths("/", params));

        // and it is fine on the next start
        _engine.closeAll();
        _engine = newEngine(index);
        assertFalse(_engine.isRebuildRequired());
        assertEquals("1", _engine.getStatus().get("inodes"));
    }

    @Test
    public void lucene36IndexIsMovedAside() throws IOException, IndexException {
        // Lucene 3.x segments_N files start with a negative format number (-11 for 3.1+), not the codec magic
        checkIncompatibleIndexIsMovedAside(new byte[]{-1, -1, -1, -11, 0, 0, 0, 0, 0, 0, 0, 7, 0, 0, 0, 1});
    }

    @Test
    public void corruptIndexIsMovedAside() throws IOException, IndexException {
        // codec magic followed by garbage
        checkIncompatibleIndexIsMovedAside(new byte[]{0x3f, (byte) 0xd7, 0x6c, 0x17, 8, 's', 'e', 'g', 'm', 'e',
                'n', 't', 's', 0, 0, 0, 10, 1, 2, 3});
    }

    @Test
    public void truncatedIndexIsMovedAside() throws IOException, IndexException {
        checkIncompatibleIndexIsMovedAside(new byte[]{0x3f, (byte) 0xd7});
    }
}
