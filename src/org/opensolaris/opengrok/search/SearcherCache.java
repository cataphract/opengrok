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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiReader;
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
    
    private static final Logger log =
            Logger.getLogger(SearcherCache.class.getName());

    private final ConcurrentHashMap<File, SearcherManager> searcherManagerMap =
            new ConcurrentHashMap<File, SearcherManager>();

    private final ExecutorService searchThreadPool;

    private final SearcherFactory searcherFactory;
    
    private boolean isDestroyed = false;
    
    public abstract class SearcherWithCleanup implements Closeable {

        protected IndexSearcher searcher;
        
        public IndexSearcher getSearcher() {
            return searcher;
        }

    }
    
    private class SimpleSearcherWithCleanup extends SearcherWithCleanup {

        private SearcherManager sm;

        public SimpleSearcherWithCleanup(SearcherManager sm) {
            this.searcher = sm.acquire();
            this.sm = sm;
        }

        @Override
        public void close() throws IOException {
            sm.release(getSearcher());
        }

    }
    
    private class MultiSearcherWithCleanup extends SearcherWithCleanup {
        
        private List<SearcherManager> sms;
        private List<IndexSearcher> searchers;
        
        public MultiSearcherWithCleanup(List<SearcherManager> sms) {
            this.searchers = new ArrayList<IndexSearcher>(sms.size());
            this.sms = sms;
            
            IndexReader readers[] = new IndexReader[sms.size()];
            
            for (int i = 0; i < sms.size(); i++) {
                IndexSearcher searcher = sms.get(i).acquire();
                this.searchers.add(searcher);
                readers[i] = searcher.getIndexReader();
            }
            
            if (searchThreadPool != null) {
                this.searcher = new IndexSearcher(
                        new MultiReader(readers, false), searchThreadPool);
            } else {
                this.searcher = new IndexSearcher(
                        new MultiReader(readers, false));
            }
            this.sms = sms;
        }

        @Override
        public void close() throws IOException {
            IOUtils.close(searcher);

            for (int i = 0; i < sms.size(); i++) {
                try {
                    sms.get(i).release(searchers.get(i));
                } catch (IOException e) {
                    log.log(Level.WARNING, "Failed to release index searcher: ", e);
                }
            }
        }

    }

    public SearcherCache(int numSearchThreads) {
        if (numSearchThreads < 0) {
            numSearchThreads =
                    2 + (2 * Runtime.getRuntime().availableProcessors());
        }

        if (numSearchThreads != 0) {
            searchThreadPool =
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
            searcherFactory = new SearcherFactory() {
                @Override public IndexSearcher newSearcher(IndexReader r)
                        throws IOException {
                    return new IndexSearcher(r, searchThreadPool);
                }
            };
        } else {
            /* don't use a thread pool */
            searchThreadPool = null;
            searcherFactory = new SearcherFactory() {
                @Override public IndexSearcher newSearcher(IndexReader r)
                        throws IOException {
                    return new IndexSearcher(r);
                }
            };
        }
        
    }
    
    public SearcherWithCleanup fetchIndexSearcher(File index)
            throws IOException {
        
        return new SimpleSearcherWithCleanup(fetchSearchManager(index));
    }
    
    public SearcherWithCleanup fetchIndexSearcher(File indexes[])
            throws IOException {
        
        List<SearcherManager> sms =
                new ArrayList<SearcherManager>(indexes.length);
        
        for (int i = 0; i < indexes.length; i++) {
            sms.add(fetchSearchManager(indexes[i]));
        }
        
        return new MultiSearcherWithCleanup(sms);
    }

    private SearcherManager fetchSearchManager(File index) throws IOException {
        SearcherManager sm = searcherManagerMap.get(index);
        if (sm == null) {
            sm = new SearcherManager(FSDirectory.open(index), searcherFactory);
                    
            if (searcherManagerMap.putIfAbsent(index, sm) != null) {
                /* another thread opened the index in the meantime */
                IOUtils.close(sm);
                sm = searcherManagerMap.get(index);
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
    
    public boolean awaitTasksTermination(int waitSeconds)
            throws InterruptedException {
        
        searchThreadPool.shutdown();
        return searchThreadPool.awaitTermination(waitSeconds, TimeUnit.SECONDS);
    }
    
    /**
     * Destroy this cache. It should have been taken out of service before.
     */
    public void destroy() {
        if (isDestroyed) {
            throw new IllegalStateException(
                    "This object has already been destroyed");
        }
        
        isDestroyed = true;
        
        searchThreadPool.shutdownNow();
        
        //shutdown the searcher managers
        for (Entry<File, SearcherManager> e : searcherManagerMap.entrySet()) {
            IOUtils.close(e.getValue());
        }
        
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        
        //limit damage in case a logic error prevents the searcher cache from
        //being destroyed
        
        if (!isDestroyed) {
            log.log(Level.WARNING, "Object not destroyed upon finalization.");
            destroy();
        }
    }

}
