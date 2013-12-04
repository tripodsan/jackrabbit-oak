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

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.plugins.observation.filter.EventGenerator.Filter;
import org.apache.jackrabbit.oak.spi.security.authorization.permission.TreePermission;
import org.apache.jackrabbit.oak.spi.state.NodeState;

/**
 * {@code EventTypeFilter} filters based on the access rights of the observing session.
 */
public class ACFilter implements Filter {
    private final Tree beforeTree;
    private final Tree afterTree;
    private final TreePermission treePermission;

    /**
     * Create a new {@code Filter} instance that includes an event when the
     * observing session has sufficient permissions to read the associated item.
     *
     * @param beforeTree  before state of the node being filtered
     * @param afterTree   after state of the node being filtered
     * @param treePermission  tree permission for the node being filtered
     */
    public ACFilter(@Nonnull Tree beforeTree, @Nonnull Tree afterTree,
            @Nonnull TreePermission treePermission) {
        this.beforeTree = checkNotNull(beforeTree);
        this.afterTree = checkNotNull(afterTree);
        this.treePermission = checkNotNull(treePermission);
    }

    @Override
    public boolean includeAdd(PropertyState after) {
        return treePermission.canRead(after);
    }

    @Override
    public boolean includeChange(PropertyState before, PropertyState after) {
        return treePermission.canRead(after);
    }

    @Override
    public boolean includeDelete(PropertyState before) {
        return treePermission.canRead(before);
    }

    @Override
    public boolean includeAdd(String name, NodeState after) {
        return treePermission.getChildPermission(name, after).canRead();
    }

    @Override
    public boolean includeChange(String name, NodeState before, NodeState after) {
        return treePermission.getChildPermission(name, after).canRead();
    }

    @Override
    public boolean includeDelete(String name, NodeState before) {
        return treePermission.getChildPermission(name, before).canRead();
    }

    @Override
    public boolean includeMove(String sourcePath, String name, NodeState moved) {
        return treePermission.getChildPermission(name, moved).canRead();
    }

    @Override
    public Filter create(String name, NodeState before, NodeState after) {
        return new ACFilter(beforeTree.getChild(name), afterTree.getChild(name),
                treePermission.getChildPermission(name, after));
    }
}
