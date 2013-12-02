/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.jackrabbit.oak.plugins.observation.filter;

import static org.apache.jackrabbit.JcrConstants.JCR_UUID;
import static org.apache.jackrabbit.oak.plugins.identifier.IdentifierManager.generateUUID;
import static org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState.EMPTY_NODE;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.jackrabbit.oak.core.ImmutableTree;
import org.junit.Test;

public class UuidPredicateTest {

    @Test
    public void emptyUuidList() {
        UuidPredicate p = new UuidPredicate(new String[] {});
        ImmutableTree tree = createTreeWithUuid(generateUUID());
        assertFalse(p.apply(tree));
    }

    @Test
    public void singleUuidMatch() {
        String uuid = generateUUID();
        UuidPredicate p = new UuidPredicate(new String[] {uuid});
        ImmutableTree tree = createTreeWithUuid(uuid);
        assertTrue(p.apply(tree));
    }

    @Test
    public void singleUuidMiss() {
        UuidPredicate p = new UuidPredicate(new String[] {generateUUID()});
        ImmutableTree tree = createTreeWithUuid(generateUUID());
        assertFalse(p.apply(tree));
    }

    @Test
    public void multipleUuidsMatch() {
        String uuid = generateUUID();
        UuidPredicate p = new UuidPredicate(
                new String[] {generateUUID(), generateUUID(), uuid});
        ImmutableTree tree = createTreeWithUuid(uuid);
        assertTrue(p.apply(tree));
    }

    @Test
    public void multipleUuidsMiss() {
        UuidPredicate p = new UuidPredicate(
                new String[] {generateUUID(), generateUUID(), generateUUID()});
        ImmutableTree tree = createTreeWithUuid(generateUUID());
        assertFalse(p.apply(tree));
    }

    private static ImmutableTree createTreeWithUuid(String uuid) {
        return new ImmutableTree(EMPTY_NODE.builder()
                .setProperty(JCR_UUID, uuid)
                .getNodeState());
    }
}
