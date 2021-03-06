/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Lists;
import org.gradle.api.internal.changedetection.rules.ChangeType;
import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.api.internal.tasks.cache.TaskCacheKeyBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

class OrderInsensitiveTaskFilePropertyCompareStrategy implements TaskFilePropertyCompareStrategy {

    private final boolean includeAdded;

    public OrderInsensitiveTaskFilePropertyCompareStrategy(boolean includeAdded) {
        this.includeAdded = includeAdded;
    }

    @Override
    public Iterator<TaskStateChange> iterateContentChangesSince(final Map<String, IncrementalFileSnapshot> current, Map<String, IncrementalFileSnapshot> previous, final String fileType) {
        final Map<String, IncrementalFileSnapshot> remainingPrevious = new HashMap<String, IncrementalFileSnapshot>(previous);
        final Iterator<String> currentFiles = current.keySet().iterator();
        return new AbstractIterator<TaskStateChange>() {
            private Iterator<String> removedFiles;

            @Override
            protected TaskStateChange computeNext() {
                while (currentFiles.hasNext()) {
                    String currentFile = currentFiles.next();
                    IncrementalFileSnapshot previousFile = remainingPrevious.remove(currentFile);
                    if (previousFile == null) {
                        if (includeAdded) {
                            return new FileChange(currentFile, ChangeType.ADDED, fileType);
                        }
                    } else if (!current.get(currentFile).isContentUpToDate(previousFile)) {
                        return new FileChange(currentFile, ChangeType.MODIFIED, fileType);
                    }
                }

                // Create a single iterator to use for all of the removed files
                if (removedFiles == null) {
                    removedFiles = remainingPrevious.keySet().iterator();
                }

                if (removedFiles.hasNext()) {
                    return new FileChange(removedFiles.next(), ChangeType.REMOVED, fileType);
                }

                return endOfData();
            }
        };
    }

    @Override
    public void appendToCacheKey(TaskCacheKeyBuilder builder, Map<String, IncrementalFileSnapshot> snapshots) {
        ArrayList<Entry> entries = Lists.newArrayListWithCapacity(snapshots.size());
        for (Map.Entry<String, IncrementalFileSnapshot> entry : snapshots.entrySet()) {
            entries.add(new Entry(entry.getKey(), entry.getValue().getHash().asBytes()));
        }
        Collections.sort(entries);
        for (Entry entry : entries) {
            entry.appendToCacheKey(builder);
        }
    }

    private static class Entry implements Comparable<Entry> {
        private final String key;
        private final byte[] hashCode;

        public Entry(String key, byte[] hashCode) {
            this.key = key;
            this.hashCode = hashCode;
        }

        public void appendToCacheKey(TaskCacheKeyBuilder hasher) {
            hasher.putString(key);
            hasher.putBytes(hashCode);
        }

        @Override
        public int compareTo(Entry o) {
            int result = key.compareTo(o.key);
            if (result == 0) {
                int len = hashCode.length;
                result = len - o.hashCode.length;
                if (result == 0) {
                    for (int idx = 0; idx < len; idx++) {
                        result = hashCode[idx] - o.hashCode[idx];
                        if (result != 0) {
                            break;
                        }
                    }
                }
            }
            return result;
        }
    }
}
