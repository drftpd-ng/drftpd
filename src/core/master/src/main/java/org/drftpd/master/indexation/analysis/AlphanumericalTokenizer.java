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

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

import java.io.IOException;

/**
 * Splits the input on every character that is not a letter, a digit or one of the
 * wildcard characters {@code ?} and {@code *}, and lowercases letters.
 * <p>
 * This reproduces what the Lucene 3.6 based implementation did through {@code CharTokenizer}
 * (which is no longer part of lucene-core): the input is examined code point by code point and
 * a token reaching {@link #MAX_WORD_LEN} chars is cut there, the rest becoming a new token.
 * <p>
 * The indexed values (inode names, slave lists, plugin metadata) are short, so the whole input
 * is read in memory before tokenizing.
 */
public final class AlphanumericalTokenizer extends Tokenizer {

    /** Same limit as the Lucene 3.6 CharTokenizer. */
    static final int MAX_WORD_LEN = 255;

    private final CharTermAttribute _termAtt = addAttribute(CharTermAttribute.class);
    private final OffsetAttribute _offsetAtt = addAttribute(OffsetAttribute.class);

    private final StringBuilder _text = new StringBuilder();
    private final char[] _readBuffer = new char[1024];
    private boolean _textRead = false;
    private int _pos = 0;

    static boolean isTokenChar(int c) {
        return Character.isLetter(c) || Character.isDigit(c) || c == '?' || c == '*';
    }

    static int normalize(int c) {
        return Character.isLetter(c) ? Character.toLowerCase(c) : c;
    }

    private void readText() throws IOException {
        int len;
        while ((len = input.read(_readBuffer)) != -1) {
            _text.append(_readBuffer, 0, len);
        }
        _textRead = true;
    }

    @Override
    public boolean incrementToken() throws IOException {
        clearAttributes();
        if (!_textRead) {
            readText();
        }
        final int textLength = _text.length();
        // skip separators
        while (_pos < textLength) {
            int c = Character.codePointAt(_text, _pos);
            if (isTokenChar(c)) {
                break;
            }
            _pos += Character.charCount(c);
        }
        if (_pos >= textLength) {
            return false;
        }
        final int start = _pos;
        int length = 0;
        char[] buffer = _termAtt.buffer();
        while (_pos < textLength && length < MAX_WORD_LEN) {
            int c = Character.codePointAt(_text, _pos);
            if (!isTokenChar(c)) {
                break;
            }
            if (length + 2 > buffer.length) {
                buffer = _termAtt.resizeBuffer(length + 2);
            }
            length += Character.toChars(normalize(c), buffer, length);
            _pos += Character.charCount(c);
        }
        _termAtt.setLength(length);
        _offsetAtt.setOffset(correctOffset(start), correctOffset(_pos));
        return true;
    }

    @Override
    public void end() throws IOException {
        super.end();
        int finalOffset = correctOffset(_text.length());
        _offsetAtt.setOffset(finalOffset, finalOffset);
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        _text.setLength(0);
        _textRead = false;
        _pos = 0;
    }
}
