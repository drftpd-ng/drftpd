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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.WildcardQuery;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Helpers shared by the index engine and the index/query extensions of the plugins.
 * <p>
 * Extensions must add their fields through {@link #addTextField}, {@link #addIntField} and
 * {@link #addLongField} only: the engine relies on this convention to rebuild a document from its
 * stored values when an inode is renamed (see {@link IndexDataExtensionInterface}).
 */
public class LuceneUtils {
    private static final Logger logger = LogManager.getLogger(LuceneUtils.class);

    /**
     * Adds a stored text field, analyzed with {@link LuceneEngine#ANALYZER}.
     * A {@code null} value is indexed as an empty string.
     */
    public static void addTextField(Document doc, String name, String value) {
        doc.add(new TextField(name, value == null ? "" : value, Field.Store.YES));
    }

    /**
     * Adds a stored int field that can be searched with {@link #intRangeQuery}.
     */
    public static void addIntField(Document doc, String name, int value) {
        doc.add(new IntPoint(name, value));
        doc.add(new StoredField(name, value));
    }

    /**
     * Adds a stored long field that can be searched with {@link #longRangeQuery}.
     */
    public static void addLongField(Document doc, String name, long value) {
        doc.add(new LongPoint(name, value));
        doc.add(new StoredField(name, value));
    }

    /**
     * Inclusive range query on a field added with {@link #addIntField}, a {@code null} bound is open.
     */
    public static Query intRangeQuery(String field, Integer min, Integer max) {
        return IntPoint.newRangeQuery(field,
                min == null ? Integer.MIN_VALUE : min,
                max == null ? Integer.MAX_VALUE : max);
    }

    /**
     * Inclusive range query on a field added with {@link #addLongField}, a {@code null} bound is open.
     */
    public static Query longRangeQuery(String field, Long min, Long max) {
        return LongPoint.newRangeQuery(field,
                min == null ? Long.MIN_VALUE : min,
                max == null ? Long.MAX_VALUE : max);
    }

    /**
     * Splits the given text with {@link LuceneEngine#ANALYZER} and returns a query where every
     * resulting token (which may contain the {@code *} and {@code ?} wildcards) must match the field.
     *
     * @param field the analyzed field to search
     * @param text  the user input
     * @return Query
     */
    public static Query analyze(String field, String text) {
        Set<String> tokens = new LinkedHashSet<>(); // avoids repeated terms.

        try (TokenStream ts = LuceneEngine.ANALYZER.tokenStream(field, text)) {
            CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                tokens.add(termAtt.toString());
            }
            ts.end();
        } catch (IOException e) {
            logger.error("IOException analyzing string", e);
        }

        BooleanQuery.Builder bQuery = new BooleanQuery.Builder();
        for (String token : tokens) {
            bQuery.add(new WildcardQuery(new Term(field, token)), BooleanClause.Occur.MUST);
        }

        return bQuery.build();
    }

    /**
     * Parses the text and checks if wildcards given are valid or not, ie not allowed within first three chars.
     *
     * @param text
     * @return boolean
     */
    public static boolean validWildcards(String text) {
        int wc1 = text.indexOf("*");
        int wc2 = text.indexOf("?");
        return !((wc1 > 0 && wc1 <= 3) || (wc2 > 0 && wc2 <= 3));
    }

    /**
     * Escapes the WildcardQuery escape char ({@code \}), which Lucene 3.6 did not have, so that
     * a backslash in user input is matched literally as it was before.
     */
    static String escapeWildcardEscapeChar(String text) {
        return text.replace("\\", "\\\\");
    }
}
