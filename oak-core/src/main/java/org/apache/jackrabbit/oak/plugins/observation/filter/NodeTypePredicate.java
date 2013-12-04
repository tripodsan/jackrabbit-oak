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

import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Nonnull;

import com.google.common.base.Predicate;
import org.apache.jackrabbit.oak.core.ImmutableTree;
import org.apache.jackrabbit.oak.plugins.nodetype.ReadOnlyNodeTypeManager;

/**
 * A predicate for matching against a list of node types. This predicate
 * holds whenever the tree passed to its apply functions is of the node type
 * of any of the node types whose names has been passed to the predicate's
 * constructor.
 */
public class NodeTypePredicate implements Predicate<ImmutableTree> {
    private final ReadOnlyNodeTypeManager ntManager;
    private final String[] ntNames;

    /**
     * @param ntManager  node type manager for determining whether the node types of {@code Tree}s
     * @param ntNames    names of node types
     */
    public NodeTypePredicate(
            @Nonnull ReadOnlyNodeTypeManager ntManager,
            @Nonnull String[] ntNames) {
        this.ntManager = checkNotNull(ntManager);
        this.ntNames = checkNotNull(ntNames);
    }

    @Override
    public boolean apply(ImmutableTree tree) {
        for (String ntName : ntNames) {
            if (ntManager.isNodeType(tree, ntName)) {
                return true;
            }
        }
        return false;
    }
}
