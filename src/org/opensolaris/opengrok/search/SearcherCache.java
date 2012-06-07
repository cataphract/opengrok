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

package org.opensolaris.opengrok.search;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.FSDirectory;
import org.opensolaris.opengrok.util.IOUtils;

/**
 * This class caches  {@link IndexSearcher} objects so that they do not have
 * to be recreated on each search. It also provides a thread pool on which
 * the searches can be executed.
 */
public class SearcherCache {

    private final ConcurrentHashMap<File, SearcherManager> searcherManagerMap =
            new ConcurrentHashMap<File, SearcherManager>();

    private final ExecutorService searchThreadPool;

    public SearcherCache(int numSearchThreads) {
        if (numSearchThreads <= 0) {
            numSearchThreads =
                    2 + (2 * Runtime.getRuntime().availableProcessors());
        }

        this.searchThreadPool =
            Executors.newFixedThreadPool(
                    numSearchThreads,
                    new ThreadFactory() {
                        private ThreadGroup group = new ThreadGroup("search-pool");
                        private int i = 1;

                        @Override
                        public synchronized Thread newThread(Runnable r) {
                            Thread ret = new Thread(group, r,
                                    "search-pool-thread-" + i++);
                            ret.setDaemon(true);
                            return ret;
                        }
                    });
    }

    public ExecutorService getSearchThreadPool() {
        return this.searchThreadPool;
    }

    public SearcherManager fetchSearchManager(File index) throws IOException {
        SearcherManager sm = this.searcherManagerMap.get(index);
        if (sm == null) {
            sm = new SearcherManager(FSDirectory.open(index),
                    new SearcherFactory() {
                        @Override public IndexSearcher newSearcher(
                                IndexReader r) throws IOException {
                            return new IndexSearcher(r,
                                    SearcherCache.this.searchThreadPool);
                        }
                    });
            if (this.searcherManagerMap.putIfAbsent(index, sm) != null) {
                /* another thread opened the index in the meantime */
                IOUtils.close(sm);
                sm = this.searcherManagerMap.get(index);
            }
        } else {
            /* a better way would be to maybeRefresh periodically in another
             * thread to avoid penalizing the query that happens to refresh,
             * as the Lucene docs say
             */
            sm.maybeRefresh();
        }

        return sm;
    }

}
