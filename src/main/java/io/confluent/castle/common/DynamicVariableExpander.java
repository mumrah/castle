/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.confluent.castle.common;

import io.confluent.castle.cluster.CastleCluster;
import io.confluent.castle.cluster.CastleNode;

import java.util.HashMap;
import java.util.Map;

/**
 * A StringExpander which uses DynamicVariableProvider objects to find the value
 * of variables inside a string.
 */
public class DynamicVariableExpander implements StringExpander {
    private final Map<String, String> cache;
    private final DynamicVariableProviders providers;
    private final CastleCluster cluster;
    private final CastleNode node;

    public DynamicVariableExpander(DynamicVariableProviders providers,
                                   CastleCluster cluster,
                                   CastleNode node) {
        this.cache = new HashMap<>();
        this.providers = providers;
        this.cluster = cluster;
        this.node = node;
    }

    public DynamicVariableExpander(CastleCluster cluster,
                                   CastleNode node) {
        this(cluster.dynamicVariableProviders(), cluster, node);
    }

    @Override
    public String lookupVariable(String key) throws Exception {
        String value = cache.get(key);
        if (value != null) {
            return value;
        }
        DynamicVariableProvider provider = providers.get(key);
        if (provider == null) {
            return null;
        }
        value = provider.calculate(cluster, node);
        cache.put(key, value);
        return value;
    }
}
