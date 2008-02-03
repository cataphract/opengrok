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
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.history;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An interface for an external repository. 
 *
 * @author Trond Norbye
 */
public abstract class ExternalRepository {
    private String directoryName;

    /**
     * Get a parser capable of getting history log elements from this repository.
     * @return a specialized parser for this kind of repository
     */
    abstract Class<? extends HistoryParser> getHistoryParser();
    
    abstract Class<? extends HistoryParser> getDirectoryHistoryParser();
    
    abstract boolean fileHasHistory(File file);
    
    /**
     * Get an input stream that I may use to read a speciffic version of a
     * named file.
     * @param parent the name of the directory containing the file
     * @param basename the name of the file to get
     * @param rev the revision to get
     * @return An input stream containing the correct revision.
     */
    abstract InputStream getHistoryGet(
            String parent, String basename, String rev);

    /**
     * Checks whether this parser can annotate files.
     *
     * @return <code>true</code> if annotation is supported
     */
    abstract boolean supportsAnnotation();

    /**
     * Annotate the specified revision of a file.
     *
     * @param file the file to annotate
     * @param revision revision of the file
     * @return an <code>Annotation</code> object
     * @throws java.lang.Exception if an error occurs
     */
    abstract Annotation annotate(File file, String revision) throws Exception;

    /**
     * Check whether the parsed history should be cached.
     *
     * @return <code>true</code> if the history should be cached
     */
    abstract boolean isCacheable();
    
    /**
     * Get the name of the root directory for this repository.
     * @return the name of the root directory
     */
    public String getDirectoryName() {
        return directoryName;
    }

    /**
     * Specify the name of the root directory for this repository.
     * @param directoryName the new name of the root directory
     */
    public void setDirectoryName(String directoryName) {
        this.directoryName = directoryName;
    }

    /**
     * Create a history log cache for all of the files in this repository.
     * Some SCM's have a more optimal way to query the log information, so
     * the concrete repository could implement a smarter way to generate the
     * cache instead of creating it for each file being accessed. The default
     * implementation uses the history parser returned by
     * {@code getDirectoryHistoryParser()} to parse the repository's history.
     *
     * @throws Exception on error
     */
    void createCache() throws Exception {
        HistoryParser p = getDirectoryHistoryParser().newInstance();
        File directory = new File(getDirectoryName());
        History history = p.parse(directory, this);
        if (history != null && history.getHistoryEntries() != null) {
            HashMap<String, List<HistoryEntry>> map =
                    new HashMap<String, List<HistoryEntry>>();

            for (HistoryEntry e : history.getHistoryEntries()) {
                for (String s : e.getFiles()) {
                    List<HistoryEntry> list = map.get(s);
                    if (list == null) {
                        list = new ArrayList<HistoryEntry>();
                        map.put(s, list);
                    }
                    list.add(e);
                }
            }

            for (Map.Entry<String, List<HistoryEntry>> e : map.entrySet()) {
                for (HistoryEntry ent : e.getValue()) {
                    ent.strip();
                }
                History hist = new History();
                hist.setHistoryEntries(e.getValue());
                HistoryCache.writeCacheFile(e.getKey(), hist);
            }
        }
    }
    
    /**
     * Update the content in this repository by pulling the changes from the
     * upstream repository..
     * @throws Exception if an error occurs.
     */
    abstract void update() throws Exception;
    
}
