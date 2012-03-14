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
package com.nitorcreations.robotframework.eclipseide.builder.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.IDocument;

import com.nitorcreations.robotframework.eclipseide.structure.ParsedString;

/**
 * This class splits a robot text files into individual lines, each represented
 * by an instance of {@link RFELine}.
 */
public class RFELexer {

    private final List<RFELine> lexLines = new ArrayList<RFELine>();
    private final String filename;
    private final Reader filestream;
    private final IProgressMonitor monitor;

    /**
     * For files being "compiled" from disk.
     * 
     * @param file
     * @param monitor
     * @throws UnsupportedEncodingException
     * @throws CoreException
     */
    public RFELexer(final IFile file, IProgressMonitor monitor) throws UnsupportedEncodingException, CoreException {
        this.filename = file.toString();
        this.filestream = new InputStreamReader(file.getContents(), file.getCharset());
        this.monitor = monitor == null ? new NullProgressMonitor() : monitor;
    }

    /**
     * For unit tests.
     * 
     * @param file
     *            the file path
     * @param charset
     *            the charset to read the file in
     * @param markerManager
     *            for managing markers
     * @throws UnsupportedEncodingException
     * @throws FileNotFoundException
     */
    public RFELexer(File file, String charset) throws UnsupportedEncodingException, FileNotFoundException {
        this.filename = file.getName();
        this.filestream = new InputStreamReader(new FileInputStream(file), charset);
        this.monitor = new NullProgressMonitor();
    }

    /**
     * For documents being edited.
     * 
     * @param document
     */
    public RFELexer(IDocument document) {
        this.filename = "<document being edited>";
        this.filestream = new StringReader(document.get());
        this.monitor = new NullProgressMonitor();
    }

    public List<RFELine> lex() throws CoreException {
        try {
            System.out.println("Lexing " + filename);
            CountingLineReader contents = new CountingLineReader(filestream);
            String line;
            int lineNo = 1;
            int charPos = 0;
            while (null != (line = contents.readLine())) {
                if (monitor.isCanceled()) {
                    return null;
                }
                try {
                    lexLine(line, lineNo, charPos);
                } catch (CoreException e) {
                    throw new RuntimeException("Error when lexing line " + lineNo + ": '" + line + "'", e);
                } catch (RuntimeException e) {
                    throw new RuntimeException("Internal error when lexing line " + lineNo + ": '" + line + "'", e);
                }
                ++lineNo;
                charPos = contents.getCharPos();
            }

            // TODO store results
        } catch (Exception e) {
            throw new RuntimeException("Error lexing robot file " + filename, e);
        } finally {
            try {
                filestream.close();
            } catch (IOException e) {
                // ignore
            }
        }
        return lexLines;
    }

    private void lexLine(String line, int lineNo, int charPos) throws CoreException {
        List<ParsedString> arguments = TxtArgumentSplitter.splitLineIntoArguments(line, charPos);
        if (arguments.isEmpty()) {
            return;
        }
        if (arguments.size() == 1 && arguments.get(0).getValue().isEmpty()) {
            return;
        }
        lexLines.add(new RFELine(lineNo, charPos, arguments));
    }

}
