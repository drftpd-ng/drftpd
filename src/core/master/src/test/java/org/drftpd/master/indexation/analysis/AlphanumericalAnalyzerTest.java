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
package org.drftpd.master.indexation.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AlphanumericalAnalyzerTest {

    private final Analyzer _analyzer = new AlphanumericalAnalyzer();

    private List<String> tokens(String text) throws IOException {
        List<String> tokens = new ArrayList<>();
        try (TokenStream ts = _analyzer.tokenStream("name", text)) {
            CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                tokens.add(term.toString());
            }
            ts.end();
        }
        return tokens;
    }

    @Test
    public void splitsOnNonAlphanumericalCharsAndLowercases() throws IOException {
        assertEquals(List.of("the", "movie", "2009", "720p", "bluray", "x264", "grp"),
                tokens("The.Movie.2009.720p.BluRay.x264-GRP"));
        assertEquals(List.of("slave1", "slave2"), tokens("slave1,slave2,"));
        assertEquals(List.of(), tokens(""));
        assertEquals(List.of(), tokens(" .-_,"));
    }

    @Test
    public void keepsWildcards() throws IOException {
        assertEquals(List.of("mov*", "20?9"), tokens("Mov* 20?9"));
    }

    @Test
    public void handlesNonAsciiLettersAndDigits() throws IOException {
        assertEquals(List.of("café", "größe", "ünïcödé"), tokens("Café_GRÖßE-ÜnÏcÖdÉ"));
        // a supplementary letter (U+10400 DESERET CAPITAL LONG I) is lowercased to U+10428
        assertEquals(List.of("a𐐨b"), tokens("a𐐀b"));
        // emoji are not letters nor digits
        assertEquals(List.of("a", "b"), tokens("a😀b"));
    }

    @Test
    public void splitsLongTokensLikeLucene36() throws IOException {
        String longWord = "a".repeat(AlphanumericalTokenizer.MAX_WORD_LEN + 10);
        assertEquals(List.of("a".repeat(AlphanumericalTokenizer.MAX_WORD_LEN), "a".repeat(10)), tokens(longWord));
    }

    @Test
    public void offsetsAndReuse() throws IOException {
        try (TokenStream ts = _analyzer.tokenStream("name", "ab..cd")) {
            OffsetAttribute offset = ts.addAttribute(OffsetAttribute.class);
            ts.reset();
            ts.incrementToken();
            assertEquals(0, offset.startOffset());
            assertEquals(2, offset.endOffset());
            ts.incrementToken();
            assertEquals(4, offset.startOffset());
            assertEquals(6, offset.endOffset());
            ts.end();
            assertEquals(6, offset.endOffset());
        }
        // the analyzer reuses the tokenizer, state must not leak between calls
        assertEquals(List.of("first"), tokens("first"));
        assertEquals(List.of("second", "one"), tokens("second one"));
    }
}
