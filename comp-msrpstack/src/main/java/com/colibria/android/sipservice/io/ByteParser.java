/*
 *
 * Copyright (C) 2010 Colibria AS
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.colibria.android.sipservice.io;

import java.io.EOFException;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * <p/>
 * <code>$Id:  $</code>
 *
 * @author Arild Nilsen
 * @version $Revision: $
 */
public class ByteParser {

    public static final int READ_EOF = -1;
    public static final int READ_EOL = -2;
    public static final int READ_WORD = -3;
    public static final int READ_SPACE = 32;

    private static final int STATE_INIT = 0;
    private static final int STATE_IN_WORD = 1;
    private static final int STATE_DELIM = 2;
    private static final int STATE_CR = 4;
    private static final int STATE_LF = 5;
    private static final int STATE_OPEN_QUOTE = 6;
    private static final int STATE_EOF = 7;

    private static final byte UNDEF = 0;
    private static final byte DELIM = 1;
    private static final byte SPACE = 2;
    private static final byte CR = 3;
    private static final byte LF = 4;
    private static final byte QUOTE = 5;
    private static final byte CHAR_0 = 6;
    private static final byte CHAR_10 = 7;
    private static final byte CHAR_110 = 8;
    private static final byte CHAR_1110 = 9;
    private static final byte CHAR_11110 = 10;

    private static final byte CR_Byte = 13;
    private static final byte LF_Byte = 10;

    private int state;
    private char word[];
    private int wordPosition;
    private int currentDelim;
    private boolean quoting;
    private int quoteOpen;
    private int quoteClose;
    private boolean inUTF8;
    private int utf8Cache;
    private int utf8State;


    public static class Pattern {
        private final boolean throwEOF;
        private byte codes[];
        private byte quoteCloses[];
        private char values[];
        private boolean writeQuotes;
        private boolean delimEOF;
        private final boolean strictCRLF;

        public Pattern() {
            this(false);
        }

        public Pattern(boolean delimEOF) {
            if (delimEOF) {
                this.delimEOF = true;
                this.throwEOF = false;
            } else {
                this.delimEOF = false;
                this.throwEOF = true;
            }

            codes = new byte[256];
            quoteCloses = new byte[128];
            values = new char[128];
            writeQuotes = false;
            strictCRLF = false;
            for (int i = 0; i < values.length; i++)
                values[i] = (char) i;

            codes[CR_Byte] = CR;
            codes[LF_Byte] = LF;
        }

        public void allowUTF8WordCharacters() {
            for (int i = 128; i < 192; i++)
                codes[i] = CHAR_10;

            for (int j = 192; j < 224; j++)
                codes[j] = CHAR_110;

            for (int k = 224; k < 240; k++)
                codes[k] = CHAR_1110;

            for (int l = 240; l < 248; l++)
                codes[l] = CHAR_11110;

        }

        public void setWordCharacter(int i) {
            codes[i] = CHAR_0;
        }

        public void setWordCharacter(int i, int j) {
            for (int k = i; k <= j; k++)
                if (k != CR_Byte && k != LF_Byte)
                    codes[k] = CHAR_0;

        }

        public void setDelimiterCharacter(int i) {
            codes[i] = DELIM;
        }

        public void setSpaceCharacter(int i) {
            codes[i] = SPACE;
        }

        public void setQuoteCharacter(int i) {
            codes[i] = QUOTE;
            quoteCloses[i] = (byte) i;
        }
    }

    public ByteParser() {
        word = new char[512];
        wordPosition = 0;
        currentDelim = 0;
        inUTF8 = false;
        utf8Cache = 0;
        utf8State = 0;
        reset();
    }

    public void reset() {
        state = STATE_INIT;
        wordPosition = 0;
        quoting = false;
        inUTF8 = false;
    }

    public int readConstant(ByteBuffer bb, byte constant[], boolean throwEOF) throws IOException {
        if (state != STATE_INIT)
            throw new IOException("Invalid stream state");

        try {
            int len = constant.length;
            for (int ix = 0; ix < len; ix++) {
                byte b = bb.get();
                if (b != (constant[ix]))
                    throw new IOException((new StringBuilder()).append("Unexpected byte : ").append(b).append(" (character=").append((char) b).append(")").toString());
            }

            return len;
        } catch (BufferUnderflowException e) {
            if (throwEOF) {
                throw new EOFException();
            } else {
                return 0;
            }
        }
    }

    public int read(ByteBuffer bb, Pattern pattern) throws IOException {
        char ac[];
        switch (state) {
            case STATE_DELIM: // '\002'
                state = STATE_INIT;
                return currentDelim;

            case STATE_LF: // '\005'
                state = STATE_INIT;
                return -2;

            case STATE_CR: // '\004'
                return readLF(bb, pattern);

            case STATE_EOF: // '\007'
                state = STATE_INIT;
                return READ_EOF;

            case STATE_OPEN_QUOTE: // '\006'
                word[wordPosition++] = pattern.values[quoteOpen];
                // fall through

            case 3: // '\003'
            default:
                ac = pattern.values;
                break;
        }
        do {
            byte b;
            do {

                try {
                    b = bb.get();
                } catch (BufferUnderflowException e) {
                    if (pattern.throwEOF)
                        throw new EOFException();
                    if (pattern.delimEOF) {
                        if (inUTF8)
                            throw new IOException("Invalid UTF-8 character");
                        if (quoting)
                            throw new IOException("Missing Quote-Close");
                        if (state == STATE_IN_WORD) {
                            state = STATE_EOF;
                            return READ_WORD;
                        } else {
                            state = 0;
                            return READ_EOF;
                        }
                    } else {
                        return -READ_EOF;
                    }
                }

                if (!inUTF8)
                    break;
                if (pattern.codes[b & 0xFF] == CHAR_10) {
                    utf8State--;
                    utf8Cache <<= 6;
                    utf8Cache |= b & 0x3f;
                    if (utf8State == 0) {
                        word[wordPosition++] = (char) utf8Cache;
                        inUTF8 = false;
                    }
                } else {
                    throw new IOException("Invalid UTF-8 character");
                }
            } while (true);
            switch (pattern.codes[b & 0xFF]) {
                case CHAR_11110: // '\n'
                    inUTF8 = true;
                    utf8State = 3;
                    utf8Cache = b & 7;
                    state = STATE_IN_WORD;
                    break;

                case CHAR_1110: // '\t'
                    inUTF8 = true;
                    utf8State = 2;
                    utf8Cache = b & 0xf;
                    state = STATE_IN_WORD;
                    break;

                case CHAR_110: // '\b'
                    inUTF8 = true;
                    utf8State = 1;
                    utf8Cache = b & 0x1f;
                    state = STATE_IN_WORD;
                    break;

                case CHAR_10: // '\007'
                    throw new IOException("Invalid UTF-8 character");

                case CHAR_0: // '\006'
                    word[wordPosition++] = ac[b];
                    state = STATE_IN_WORD;
                    break;

                case DELIM: // '\001'
                    if (quoting)
                        word[wordPosition++] = ac[b];
                    else if (state == STATE_IN_WORD) {
                        state = STATE_DELIM;
                        currentDelim = b;
                        return READ_WORD;
                    } else {
                        return b;
                    }
                    break;

                case SPACE: // '\002'
                    if (quoting)
                        word[wordPosition++] = ac[b];
                    else if (state == STATE_IN_WORD) {
                        state = STATE_INIT;
                        return READ_WORD;
                    }
                    break;

                case CR: // '\003'
                    if (quoting)
                        throw new IOException("Missing Quote-Close");
                    if (state == 1) {
                        state = STATE_CR;
                        return READ_WORD;
                    } else {
                        return readLF(bb, pattern);
                    }

                case LF: // '\004'
                    if (quoting)
                        throw new IOException("Missing Quote-Close");
                    if (pattern.strictCRLF)
                        throw new IOException("Found LF without CR");
                    if (state == STATE_IN_WORD) {
                        state = STATE_LF;
                        return READ_WORD;
                    } else {
                        state = STATE_INIT;
                        return READ_EOL;
                    }

                case QUOTE: // '\005'
                    if (quoting) {
                        if (b == quoteClose) {
                            if (pattern.writeQuotes)
                                word[wordPosition++] = ac[b];
                            quoting = false;
                            state = STATE_INIT;
                            return READ_WORD;
                        }
                        word[wordPosition++] = ac[b];
                    } else {
                        if (state == STATE_IN_WORD) {
                            quoting = true;
                            quoteClose = pattern.quoteCloses[b];
                            if (pattern.writeQuotes) {
                                quoteOpen = b;
                                state = STATE_OPEN_QUOTE;
                            }
                            return READ_WORD;
                        }
                        quoting = true;
                        quoteClose = pattern.quoteCloses[b];
                        if (quoteClose == 0)
                            throw new IOException("Quote-Close without Quote-Open");
                        if (pattern.writeQuotes)
                            word[wordPosition++] = ac[b];
                        state = STATE_IN_WORD;
                    }
                    break;

                case UNDEF: // '\0'
                    throw new IOException((new StringBuilder()).append("Unexpected byte : ").append(b).append(" (character=").append((char) b).append(")").toString());
            }
        } while (true);
    }

    private int readLF(ByteBuffer bb, Pattern pattern) throws IOException {

        byte b;
        try {
            b = bb.get();
        } catch (BufferUnderflowException e) {
            if (pattern.throwEOF) {
                throw new EOFException();
            } else {
                state = STATE_CR;
                return READ_EOF;
            }
        }

        if (b == LF_Byte) {
            state = STATE_INIT;
            return READ_EOL;
        } else {
            throw new IOException("Missing LF");
        }
    }

    public boolean compareWord(String s) {
        return s.equals(getWordAsString());
    }


    public char[] getWord() {
        return word;
    }

    public int getWordLength() {
        return wordPosition;
    }

    public void resetWord() {
        wordPosition = 0;
    }

    public int getWordAsInt(int failureIndicator) {
        try {
            return Integer.parseInt(getWordAsString());
        } catch (NumberFormatException e) {
            return failureIndicator;
        }
    }

    public String getWordAsString() {
        return new String(word, 0, wordPosition);
    }
}