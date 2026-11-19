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

import org.apache.lucene.index.FilteredTermsEnum;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.util.AttributeSource;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches the terms of a field against a {@link java.util.regex.Pattern}.
 * <p>
 * Lucene 3.6 RegexQuery (lucene-queries) used java.util.regex by default, requiring the whole term
 * to match ({@link Matcher#matches()}). Lucene's own {@code RegexpQuery} has a different syntax
 * (no {@code \d}/{@code \w} classes before 9.x, no lookarounds or backreferences, {@code @}, {@code &},
 * {@code <}, {@code >}, {@code #}, {@code ~} and {@code "} are operators...), so SITE SEARCH/FIND
 * {@code -regex} patterns users already have would silently stop matching. This query keeps the
 * java.util.regex semantics; like the 3.6 one it has to examine every term of the field.
 */
public final class JavaRegexQuery extends MultiTermQuery {

    private final Pattern _pattern;

    /**
     * @throws java.util.regex.PatternSyntaxException if the regex is not valid
     */
    public JavaRegexQuery(String field, String regex) {
        super(field, CONSTANT_SCORE_BLENDED_REWRITE);
        _pattern = Pattern.compile(regex);
    }

    public Pattern getPattern() {
        return _pattern;
    }

    @Override
    protected TermsEnum getTermsEnum(Terms terms, AttributeSource atts) throws IOException {
        final Matcher matcher = _pattern.matcher("");
        return new FilteredTermsEnum(terms.iterator(), false) {
            @Override
            protected AcceptStatus accept(BytesRef term) {
                return matcher.reset(term.utf8ToString()).matches() ? AcceptStatus.YES : AcceptStatus.NO;
            }
        };
    }

    @Override
    public void visit(QueryVisitor visitor) {
        if (visitor.acceptField(getField())) {
            visitor.visitLeaf(this);
        }
    }

    @Override
    public String toString(String field) {
        return (getField().equals(field) ? "" : getField() + ":") + "/" + _pattern.pattern() + "/";
    }

    @Override
    public boolean equals(Object other) {
        return super.equals(other)
                && _pattern.pattern().equals(((JavaRegexQuery) other)._pattern.pattern())
                && _pattern.flags() == ((JavaRegexQuery) other)._pattern.flags();
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + _pattern.pattern().hashCode();
    }
}
