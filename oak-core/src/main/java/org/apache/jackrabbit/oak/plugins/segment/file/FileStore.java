/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.plugins.segment.file;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.newLinkedList;
import static org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState.EMPTY_NODE;
import static org.apache.jackrabbit.oak.plugins.segment.SegmentIdFactory.isBulkSegmentId;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import org.apache.jackrabbit.oak.plugins.segment.AbstractStore;
import org.apache.jackrabbit.oak.plugins.segment.Journal;
import org.apache.jackrabbit.oak.plugins.segment.RecordId;
import org.apache.jackrabbit.oak.plugins.segment.Segment;
import org.apache.jackrabbit.oak.plugins.segment.SegmentNodeState;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;

public class FileStore extends AbstractStore {

    private static final int DEFAULT_MEMORY_CACHE_SIZE = 256;

    private static final String FILE_NAME_FORMAT = "%s%05d.tar";

    private static final String JOURNAL_FILE_NAME = "journal.log";

    private final File directory;

    private final int maxFileSize;

    private final boolean memoryMapping;

    private final LinkedList<TarFile> bulkFiles = newLinkedList();

    private final LinkedList<TarFile> dataFiles = newLinkedList();

    private final RandomAccessFile journalFile;

    private volatile RecordId head;

    private volatile boolean updated = false;

    private volatile boolean alive = true;

    private final Thread flushThread;

    public FileStore(File directory, int maxFileSizeMB, boolean memoryMapping)
            throws IOException {
        this(directory, EMPTY_NODE, maxFileSizeMB, DEFAULT_MEMORY_CACHE_SIZE, memoryMapping);
    }

    public FileStore(File directory, int maxFileSizeMB, int cacheSizeMB,
            boolean memoryMapping) throws IOException {
        this(directory, EMPTY_NODE, maxFileSizeMB, cacheSizeMB, memoryMapping);
    }

    public FileStore(File directory, NodeState initial, int maxFileSizeMB,
            int cacheSizeMB, boolean memoryMapping) throws IOException {
        super(cacheSizeMB);
        checkNotNull(directory).mkdirs();
        this.directory = directory;
        this.maxFileSize = maxFileSizeMB * MB;
        this.memoryMapping = memoryMapping;

        for (int i = 0; true; i++) {
            String name = String.format(FILE_NAME_FORMAT, "bulk", i);
            File file = new File(directory, name);
            if (file.isFile()) {
                bulkFiles.add(new TarFile(file, maxFileSizeMB, memoryMapping));
            } else {
                break;
            }
        }

        for (int i = 0; true; i++) {
            String name = String.format(FILE_NAME_FORMAT, "data", i);
            File file = new File(directory, name);
            if (file.isFile()) {
                dataFiles.add(new TarFile(file, maxFileSizeMB, memoryMapping));
            } else {
                break;
            }
        }

        head = null;
        journalFile = new RandomAccessFile(
                new File(directory, JOURNAL_FILE_NAME), "rw");
        String line = journalFile.readLine();
        while (line != null) {
            int space = line.indexOf(' ');
            if (space != -1) {
                head = RecordId.fromString(line.substring(0, space));
            }
            line = journalFile.readLine();
        }

        if (head == null) {
            NodeBuilder builder = EMPTY_NODE.builder();
            builder.setChildNode("root", initial);
            SegmentNodeState root =
                    getWriter().writeNode(builder.getNodeState());
            head = root.getRecordId();
            updated = true;
        }

        this.flushThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (flushThread) {
                        flushThread.wait(1000);
                        while (alive) {
                            flush();
                            flushThread.wait(5000);
                        }
                    }
                } catch (InterruptedException e) {
                    // stop flushing
                }
            }
        });
        flushThread.setName("TarMK flush thread " + directory);
        flushThread.setDaemon(true);
        flushThread.setPriority(Thread.MIN_PRIORITY);
        flushThread.start();
    }

    private synchronized void flush() {
        if (updated) {
            try {
                getWriter().flush();
                for (TarFile file : bulkFiles) {
                    file.flush();
                }
                for (TarFile file : dataFiles) {
                    file.flush();
                }
                journalFile.writeBytes(head + " root\n");
                journalFile.getChannel().force(false);
            } catch (IOException e) {
                e.printStackTrace(); // FIXME
            }
        }
    }

    public Iterable<UUID> getSegmentIds() {
        List<UUID> ids = newArrayList();
        for (TarFile file : dataFiles) {
            ids.addAll(file.getUUIDs());
        }
        for (TarFile file : bulkFiles) {
            ids.addAll(file.getUUIDs());
        }
        return ids;
    }

    @Override
    public synchronized void close() {
        try {
            super.close();

            alive = false;
            synchronized (flushThread) {
                flushThread.notify();
            }
            flushThread.join();
            flush();

            journalFile.close();

            for (TarFile file : bulkFiles) {
                file.close();
            }
            bulkFiles.clear();
            for (TarFile file : dataFiles) {
                file.close();
            }
            dataFiles.clear();

            System.gc(); // for any memory-mappings that are no longer used
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Journal getJournal(String name) {
        checkArgument("root".equals(name)); // only root supported for now
        return new Journal() {
            @Override
            public RecordId getHead() {
                return head;
            }
            @Override
            public boolean setHead(RecordId base, RecordId head) {
                synchronized (FileStore.this) {
                    if (base.equals(FileStore.this.head)) {
                        updated = !head.equals(FileStore.this.head);
                        FileStore.this.head = head;
                        return true;
                    } else {
                        return false;
                    }
                }
            }
            @Override
            public void merge() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override @Nonnull
    protected Segment loadSegment(UUID id) {
        for (TarFile file : dataFiles) {
            try {
                ByteBuffer buffer = file.readEntry(id);
                if (buffer != null) {
                    return new Segment(FileStore.this, id, buffer);
                }
            } catch (IOException e) {
                throw new RuntimeException(
                        "Failed to access data file " + file, e);
            }
        }

        for (TarFile file : bulkFiles) {
            try {
                ByteBuffer buffer = file.readEntry(id);
                if (buffer != null) {
                    return new Segment(FileStore.this, id, buffer);
                }
            } catch (IOException e) {
                throw new RuntimeException(
                        "Failed to access bulk file " + file, e);
            }
        }

        throw new IllegalStateException("Segment " + id + " not found");
    }

    @Override
    public synchronized void writeSegment(
            UUID segmentId, byte[] data, int offset, int length) {
        try {
            writeEntry(segmentId, data, offset, length);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeEntry(
            UUID segmentId, byte[] buffer, int offset, int length)
            throws IOException {
        LinkedList<TarFile> files = dataFiles;
        String base = "data";
        if (isBulkSegmentId(segmentId)) {
            files = bulkFiles;
            base = "bulk";
        }
        if (files.isEmpty() || !files.getLast().writeEntry(
                segmentId, buffer, offset, length)) {
            String name = String.format(FILE_NAME_FORMAT, base, files.size());
            File file = new File(directory, name);
            TarFile last = new TarFile(file, maxFileSize, memoryMapping);
            checkState(last.writeEntry(segmentId, buffer, offset, length));
            files.add(last);
        }
    }

    @Override
    public void deleteSegment(UUID segmentId) {
        // TODO: implement
        super.deleteSegment(segmentId);
    }

}
