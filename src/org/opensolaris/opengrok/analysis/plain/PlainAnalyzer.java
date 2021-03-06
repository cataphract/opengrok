/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.analysis.plain;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.opensolaris.opengrok.analysis.AnalyzerGuru;
import org.opensolaris.opengrok.analysis.Definitions;
import org.opensolaris.opengrok.analysis.ExpandTabsReader;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory;
import org.opensolaris.opengrok.analysis.Hash2Tokenizer;
import org.opensolaris.opengrok.analysis.TextAnalyzer;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.history.Annotation;

/**
 * Analyzer for plain text files Created on September 21, 2005
 *
 * @author Chandan
 */
public class PlainAnalyzer extends TextAnalyzer {

    protected char[] content;
    protected int len;
    protected PlainXref xref = new PlainXref((Reader) null);
    protected Definitions defs;

    /**
     * Creates a new instance of PlainAnalyzer
     */
    protected PlainAnalyzer(FileAnalyzerFactory factory) {
        super(factory);
        content = new char[64 * 1024];
        len = 0;
    }

    @Override
    public void analyze(Document doc, Reader in) throws IOException {
        Reader inReader =
                ExpandTabsReader.wrap(in, project);

        len = 0;
        do {
            int rbytes = inReader.read(content, len, content.length - len);
            if (rbytes >= 0) {
                if (rbytes == (content.length - len)) {
                    content = Arrays.copyOf(content, content.length * 2);
                }
                len += rbytes;
            } else {
                break;
            }
        } while (true);

        doc.add(new Field("full", AnalyzerGuru.dummyS, TextField.TYPE_STORED));
        String fullpath;
        if ((fullpath = doc.get("fullpath")) != null && ctags != null) {
            defs = ctags.doCtags(fullpath + "\n");
            if (defs != null && defs.numberOfSymbols() > 0) {
                doc.add(new Field("defs", AnalyzerGuru.dummyS, TextField.TYPE_STORED));
                doc.add(new Field("refs", AnalyzerGuru.dummyS, TextField.TYPE_STORED)); //@FIXME adding a refs field only if it has defs?
                byte[] tags = defs.serialize();
                doc.add(new StoredField("tags", tags));
            }
        }
    }

    @Override
    public TokenStreamComponents createComponents(String fieldName, Reader reader) {
        if ("full".equals(fieldName)) {
            final PlainFullTokenizer plainfull = new PlainFullTokenizer(AnalyzerGuru.dummyR);
            plainfull.reInit(content, len);
            TokenStreamComponents tsc_pf = new TokenStreamComponents(plainfull) {
                @Override
                protected void setReader(final Reader reader) throws IOException {
                    plainfull.reInit(content, len);
                    super.setReader(reader);
                }
            };
            return tsc_pf;
        } else if ("refs".equals(fieldName)) {
            final PlainSymbolTokenizer plainref = new PlainSymbolTokenizer(AnalyzerGuru.dummyR);
            plainref.reInit(content, len);
            TokenStreamComponents tsc_pr = new TokenStreamComponents(plainref) {
                @Override
                protected void setReader(final Reader reader) throws IOException {
                    plainref.reInit(content, len);
                    super.setReader(reader);
                }
            };
            return tsc_pr;
        } else if ("defs".equals(fieldName)) {
            final Hash2Tokenizer hash2Tokenizer = new Hash2Tokenizer(AnalyzerGuru.dummyR);
            hash2Tokenizer.reInit(defs.getSymbols());
            TokenStreamComponents tsc_h2t = new TokenStreamComponents(hash2Tokenizer) {
                @Override
                protected void setReader(final Reader reader) throws IOException {
                    hash2Tokenizer.reInit(defs.getSymbols());
                    super.setReader(reader);
                }
            };
            return tsc_h2t;
        }
        return super.createComponents(fieldName, reader);
    }

    /**
     * Write a cross referenced HTML file.
     *
     * @param out Writer to write HTML cross-reference
     */
    @Override
    public void writeXref(Writer out) throws IOException {
        xref.reInit(content, len);
        xref.project = project;
        xref.write(out);
    }

    /**
     * Write a cross referenced HTML file reads the source from in
     *
     * @param in Input source
     * @param out Output xref writer
     * @param defs definitions for the file (could be null)
     * @param annotation annotation for the file (could be null)
     */
    static void writeXref(Reader in, Writer out, Definitions defs, Annotation annotation, Project project) throws IOException {
        PlainXref xref = new PlainXref(in);
        xref.annotation = annotation;
        xref.project = project;
        xref.setDefs(defs);
        xref.write(out);
    }
}
