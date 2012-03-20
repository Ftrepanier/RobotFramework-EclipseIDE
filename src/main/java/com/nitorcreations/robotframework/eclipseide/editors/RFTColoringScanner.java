/**
 * Copyright 2012 Nitor Creations Oy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nitorcreations.robotframework.eclipseide.editors;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.ITokenScanner;
import org.eclipse.jface.text.rules.Token;

import com.nitorcreations.robotframework.eclipseide.builder.parser.RFELine;
import com.nitorcreations.robotframework.eclipseide.builder.parser.RobotFile;
import com.nitorcreations.robotframework.eclipseide.structure.ParsedString;
import com.nitorcreations.robotframework.eclipseide.structure.ParsedString.ArgumentType;

// TODO Variable references
public class RFTColoringScanner implements ITokenScanner {

    private final ColorManager manager;
    private final TokenQueue tokenQueue = new TokenQueue();

    // private IDocument document;
    private List<RFELine> lines;
    private ListIterator<RFELine> lineIterator;
    private RFELine line;
    private int argOff;
    private int argLen;

    // private RFELine lastParsedLine;

    public RFTColoringScanner(ColorManager colorManager) {
        this.manager = colorManager;
        // IToken tokARGUMENT = new Token(new
        // TextAttribute(manager.getColor(IRFTColorConstants.ARGUMENT)));
        // IToken tokARGUMENT_SEPARATOR = new Token(new
        // TextAttribute(manager.getColor(IRFTColorConstants.ARGUMENT_SEPARATOR),
        // null, TextAttribute.UNDERLINE));
    }

    private final Map<ArgumentType, IToken> argTypeToTokenMap = new HashMap<ArgumentType, IToken>();

    private void prepareTokens() {
        // TODO dynamically fetched colors
        // TODO consider combining tokVARIABLE_KEY with tokKEYWORD_LVALUE
        // ArgumentType.IGNORED is deliberately left out here
        argTypeToTokenMap.put(ArgumentType.COMMENT, new Token(new TextAttribute(manager.getColor(ColorConstants.COMMENT))));
        argTypeToTokenMap.put(ArgumentType.TABLE, new Token(new TextAttribute(manager.getColor(ColorConstants.TABLE))));
        argTypeToTokenMap.put(ArgumentType.SETTING_KEY, new Token(new TextAttribute(manager.getColor(ColorConstants.SETTING))));
        argTypeToTokenMap.put(ArgumentType.VARIABLE_KEY, new Token(new TextAttribute(manager.getColor(ColorConstants.VARIABLE))));
        argTypeToTokenMap.put(ArgumentType.NEW_TESTCASE, new Token(new TextAttribute(manager.getColor(ColorConstants.TESTCASE_NEW))));
        argTypeToTokenMap.put(ArgumentType.NEW_KEYWORD, new Token(new TextAttribute(manager.getColor(ColorConstants.KEYWORD_NEW))));
        argTypeToTokenMap.put(ArgumentType.SETTING_VAL, new Token(new TextAttribute(manager.getColor(ColorConstants.SETTING_VALUE))));
        argTypeToTokenMap.put(ArgumentType.SETTING_FILE, new Token(new TextAttribute(manager.getColor(ColorConstants.SETTING_FILE))));
        argTypeToTokenMap.put(ArgumentType.SETTING_FILE_WITH_NAME_KEY, new Token(new TextAttribute(manager.getColor(ColorConstants.DEFAULT))));
        argTypeToTokenMap.put(ArgumentType.SETTING_FILE_ARG, new Token(new TextAttribute(manager.getColor(ColorConstants.SETTING_FILE_ARG))));
        argTypeToTokenMap.put(ArgumentType.SETTING_FILE_WITH_NAME_VALUE, new Token(new TextAttribute(manager.getColor(ColorConstants.SETTING_FILE))));
        argTypeToTokenMap.put(ArgumentType.VARIABLE_VAL, new Token(new TextAttribute(manager.getColor(ColorConstants.VARIABLE_VALUE))));
        argTypeToTokenMap.put(ArgumentType.KEYWORD_LVALUE, new Token(new TextAttribute(manager.getColor(ColorConstants.KEYWORD_LVALUE))));
        argTypeToTokenMap.put(ArgumentType.FOR_PART, new Token(new TextAttribute(manager.getColor(ColorConstants.FOR_PART))));
        argTypeToTokenMap.put(ArgumentType.KEYWORD_CALL, new Token(new TextAttribute(manager.getColor(ColorConstants.KEYWORD))));
        argTypeToTokenMap.put(ArgumentType.KEYWORD_ARG, new Token(new TextAttribute(manager.getColor(ColorConstants.KEYWORD_ARG))));
    }

    @Override
    public void setRange(IDocument document, int offset, int length) {
        prepareTokens();
        // this.document = document;
        tokenQueue.reset();
        lines = RobotFile.getLines(document);
        lineIterator = lines.listIterator();
        prepareNextLine();
    }

    void prepareNextToken() {
        assert argOff >= 0;
        assert argOff < argLen;
        if (++argOff == argLen) {
            prepareNextLine();
        }
    }

    void prepareNextLine() {
        assert argOff >= 0;
        assert argOff <= argLen;
        line = getNextNonemptyLine();
        if (line != null) {
            argLen = line.arguments.size();
        } else {
            lines = null;
            lineIterator = null;
            argLen = 0;
        }
        argOff = 0;
    }

    private RFELine getNextNonemptyLine() {
        while (lineIterator.hasNext()) {
            RFELine line = lineIterator.next();
            if (!line.arguments.isEmpty()) {
                return line;
            }
        }
        return null;
    }

    @Override
    public IToken nextToken() {
        while (!tokenQueue.hasPending()) {
            // lastParsedLine = line;
            parseMoreTokens();
        }
        IToken t = tokenQueue.take();
        // int tokenOff = getTokenOffset();
        // int tokenLen = getTokenLength();
        // System.out.print("TOK: " + (lastParsedLine != null ? "[" +
        // lastParsedLine.lineNo + ":" + lastParsedLine.lineCharPos + "] " : "")
        // + t + " off " + tokenOff
        // + " end " + (tokenOff + tokenLen) + " len " + tokenLen);
        // if (t instanceof Token) {
        // Token tt = (Token) t;
        // if (tt.getData() instanceof TextAttribute) {
        // TextAttribute ta = (TextAttribute) tt.getData();
        // System.out.print(" " + ta.getForeground());
        // }
        // }
        // System.out.println(" txt \"" + document.get().substring(tokenOff,
        // tokenOff + tokenLen).replace("\n", "\\n") + "\"");
        return t;
    }

    void parseMoreTokens() {
        if (line == null) {
            tokenQueue.addEof();
            return;
        }
        // TODO merge successive arguments with same type into one token, even
        // spanning multiple lines
        ParsedString arg = line.arguments.get(argOff);
        IToken token = argTypeToTokenMap.get(arg.getType());
        if (token != null) {
            tokenQueue.add(arg, token);
        }
        prepareNextToken();
    }

    static class TokenQueue {
        private static class PendingToken {
            final IToken token;
            final int len;

            public PendingToken(IToken token, int len) {
                assert token != null;
                this.token = token;
                this.len = len;
            }
        }

        private final List<PendingToken> pendingTokens = new LinkedList<PendingToken>();
        private int nextTokenStart = 0;
        private int curTokenOff, curTokenLen;

        public void reset() {
            nextTokenStart = 0;
            assert pendingTokens.isEmpty();
            pendingTokens.clear();
            curTokenOff = curTokenLen = 0;
        }

        public IToken take() {
            PendingToken removed = pendingTokens.remove(0);
            curTokenOff += curTokenLen;
            curTokenLen = removed.len;
            assert removed.token != null;
            return removed.token;
        }

        public void addEof() {
            addToken(0, Token.EOF);
        }

        public boolean hasPending() {
            return !pendingTokens.isEmpty();
        }

        public void add(ParsedString arg, IToken token) {
            add(arg.getArgCharPos(), arg.getArgEndCharPos(), token);
        }

        public void add(int off, int eoff, IToken token) {
            if (off > nextTokenStart) {
                addToken(off - nextTokenStart, Token.UNDEFINED);
            }
            addToken(eoff - off, token);
            nextTokenStart = eoff;
        }

        private void addToken(int len, IToken token) {
            pendingTokens.add(new PendingToken(token, len));
        }

        public int getLastTakenTokenOffset() {
            return curTokenOff;
        }

        public int getLastTakenTokenLength() {
            return curTokenLen;
        }

    }

    @Override
    public int getTokenOffset() {
        return tokenQueue.getLastTakenTokenOffset();
    }

    @Override
    public int getTokenLength() {
        return tokenQueue.getLastTakenTokenLength();
    }

}
