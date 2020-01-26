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
package org.drftpd.vfs.index.lucene;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.WildcardQuery;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;

/**
 * @author scitz0
 * @version $Id$
 */
public class LuceneUtils {
	private static final Logger logger = LogManager.getLogger(LuceneEngine.class);

	/**
	 * Parses the name removing unwanted chars from it.
	 *
	 * @param field
	 * @param term
	 * @param name
	 * @return Query
	 */
	public static Query analyze(String field, Term term, String name) {
		TokenStream ts = LuceneEngine.ANALYZER.tokenStream(field, new StringReader(name));

		BooleanQuery bQuery = new BooleanQuery();
		WildcardQuery wQuery;

		Set<String> tokens = new HashSet<>(); // avoids repeated terms.

		// get the CharTermAttribute from the TokenStream
		CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);

		try {
			ts.reset();
			while (ts.incrementToken()) {
				tokens.add(termAtt.toString());
			}
			ts.end();
			ts.close();
		} catch (IOException e) {
			logger.error("IOException analyzing string", e);
		}

		for (String text : tokens) {
			wQuery = new WildcardQuery(term.createTerm(text));
			bQuery.add(wQuery, BooleanClause.Occur.MUST);
		}

		return bQuery;
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
}
