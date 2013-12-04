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
package org.apache.jackrabbit.oak.plugins.segment;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.apache.jackrabbit.oak.plugins.segment.SegmentNodeStore.ROOT;

import java.util.Random;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.spi.commit.CommitHook;
import org.apache.jackrabbit.oak.spi.commit.CommitInfo;
import org.apache.jackrabbit.oak.spi.state.ConflictAnnotatingRebaseDiff;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;

class SegmentNodeStoreBranch {

    private static final Random RANDOM = new Random();

    private final SegmentNodeStore store;

    private final SegmentWriter writer;

    private SegmentNodeState base;

    private SegmentNodeState head;

    private long maximumBackoff;

    SegmentNodeStoreBranch(
            SegmentNodeStore store, SegmentWriter writer,
            SegmentNodeState base, long maximumBackoff) {
        this.store = store;
        this.writer = writer;
        this.base = base;
        this.head = base;
        this.maximumBackoff = maximumBackoff;
    }

    void setRoot(NodeState newRoot) {
        NodeBuilder builder = head.builder();
        builder.setChildNode(ROOT, newRoot);
        head = writer.writeNode(builder.getNodeState());
    }

    private void rebase() {
        SegmentNodeState newBase = store.head;
        if (!base.getRecordId().equals(newBase.getRecordId())) {
            NodeBuilder builder = newBase.builder();
            head.getChildNode(ROOT).compareAgainstBaseState(
                    base.getChildNode(ROOT),
                    new ConflictAnnotatingRebaseDiff(builder.child(ROOT)));
            base = newBase;
            head = writer.writeNode(builder.getNodeState());
        }
    }

    private long optimisticMerge(CommitHook hook, CommitInfo info)
            throws CommitFailedException, InterruptedException {
        long timeout = 1;

        SegmentNodeState originalBase = base;
        SegmentNodeState originalHead = head;

        // use exponential backoff in case of concurrent commits
        for (long backoff = 1; backoff < maximumBackoff; backoff *= 2) {
            rebase(); // rebase to latest head, a no-op if already there

            long start = System.nanoTime();

            if (base.hasProperty("token")
                    && base.getLong("timeout") >= System.currentTimeMillis()) {
                // someone else has a pessimistic lock on the journal,
                // so we should not try to commit anything
            } else {
                // apply commit hooks on the rebased changes
                NodeBuilder builder = head.builder();
                builder.setChildNode(ROOT, hook.processCommit(
                        base.getChildNode(ROOT), head.getChildNode(ROOT)));
                SegmentNodeState newHead = writer.writeNode(builder.getNodeState());

                // use optimistic locking to update the journal
                if (store.setHead(base, newHead, info)) {
                    base = newHead;
                    head = newHead;
                    return -1;
                }
            }

            // someone else was faster, so restore state and retry later
            base = originalBase;
            head = originalHead;

            RANDOM.wait(backoff, RANDOM.nextInt(1000000));

            long stop = System.nanoTime();
            if (stop - start > timeout) {
                timeout = stop - start;
            }
        }

        return MILLISECONDS.convert(timeout, NANOSECONDS);
    }

    private void pessimisticMerge(
            CommitHook hook, long timeout, CommitInfo info)
            throws CommitFailedException, InterruptedException {
        while (true) {
            SegmentNodeState before = store.head;
            long now = System.currentTimeMillis();
            if (before.hasProperty("token")
                    && before.getLong("timeout") >= now) {
                // locked by someone else, wait until unlocked or expired
                RANDOM.wait(
                        Math.min(before.getLong("timeout") - now, 1000),
                        RANDOM.nextInt(1000000));
            } else {
                // attempt to acquire the lock
                NodeBuilder builder = before.builder();
                builder.setProperty("token", UUID.randomUUID().toString());
                builder.setProperty("timeout", now + timeout);

                SegmentNodeState after =
                        writer.writeNode(builder.getNodeState());
                if (store.setHead(before, after, info)) {
                    SegmentNodeState originalBase = base;
                    SegmentNodeState originalHead = head;

                    // lock acquired; rebase, apply commit hooks, and unlock
                    rebase();
                    builder.setChildNode(ROOT, hook.processCommit(
                            base.getChildNode(ROOT), head.getChildNode(ROOT)));
                    builder.removeProperty("token");
                    builder.removeProperty("timeout");

                    // complete the commit
                    SegmentNodeState newHead =
                            writer.writeNode(builder.getNodeState());
                    if (store.setHead(after, newHead, info)) {
                        base = newHead;
                        head = newHead;
                        return;
                    } else {
                        // something else happened, perhaps a timeout, so
                        // undo the previous rebase and try again
                        base = originalBase;
                        head = originalHead;
                    }
                }
            }
        }
    }

    @Nonnull
    SegmentRootState merge(@Nonnull CommitHook hook, @Nullable CommitInfo info)
            throws CommitFailedException {
        checkNotNull(hook);
        if (base != head) {
            synchronized (RANDOM) {
                try {
                    long timeout = optimisticMerge(hook, info);
                    if (timeout >= 0) {
                        pessimisticMerge(hook, timeout, info);
                    }
                } catch (InterruptedException e) {
                    throw new CommitFailedException(
                            "Segment", 1, "Commit interrupted", e);
                } finally {
                    RANDOM.notifyAll();
                }
            }
        }
        return new SegmentRootState(head);
    }

}
