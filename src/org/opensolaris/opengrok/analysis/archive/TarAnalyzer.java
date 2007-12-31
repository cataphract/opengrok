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
 * Copyright 2005 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

/*
 * ident	"@(#)TarAnalyzer.java 1.1     05/11/11 SMI"
 */

package org.opensolaris.opengrok.analysis.archive;

import org.apache.lucene.analysis.*;
import java.io.*;
import org.opensolaris.opengrok.analysis.*;
import org.opensolaris.opengrok.analysis.plain.*;
import org.apache.lucene.document.*;
import org.opensolaris.opengrok.web.Util;
import org.apache.tools.tar.*;

/**
 * Analyzes TAR files
 * Created on September 22, 2005
 *
 * @author Chandan
 */

public class TarAnalyzer extends FileAnalyzer {
    /** Creates a new instance of ZipAnalyzer */
    static char[] content;
    int len;
    
    private static Reader dummy = new StringReader("");
    
    private PlainFullTokenizer plainfull;
    protected TarAnalyzer(FileAnalyzerFactory factory) {
        super(factory);
        content = new char[64*1024];
        plainfull = new PlainFullTokenizer(dummy);
    }

    public void analyze(Document doc, InputStream in) {
        len = 0;
        try {
            TarInputStream zis = new TarInputStream(in);
            TarEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String ename = entry.getName();
                if(len + ename.length() >= content.length) {
                    int max = content.length * 2;
                    char[] content2 = new char[max];
                    System.arraycopy(content, 0, content2, 0, len);
                    content = content2;
                }
                ename.getChars(0, ename.length(), content, len);
                len += ename.length();
                content[len++] = '\n';
            }
            doc.add(new Field("full",dummy));
        } catch (IOException e) {
        }
    }
    
    public TokenStream tokenStream(String fieldName, Reader reader) {
        if("full".equals(fieldName)) {
            plainfull.reInit(content,len);
            return plainfull;
        }
        return super.tokenStream(fieldName, reader);
    }
    
    /**
     * Write a cross referenced HTML file.
     * @param out Writer to store HTML cross-reference
     */
    public void writeXref(Writer out) throws IOException {
        Util.Htmlize(content, len, out);
    }
}
