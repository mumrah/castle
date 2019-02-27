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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks all the dynamic variable providers.  Immutable.
 */
public class DynamicVariableProviders {
    public Map<String, DynamicVariableProvider> providers;

    public static class Builder {
        public final Map<String, DynamicVariableProvider> providers;

        public Builder() {
            this.providers = new HashMap<>();
        }

        public Builder addAll(Map<String, DynamicVariableProvider> newProviders) {
            for (Map.Entry<String, DynamicVariableProvider> entry : newProviders.entrySet()) {
                add(entry.getKey(), entry.getValue());
            }
            return this;
        }

        public Builder add(String name, DynamicVariableProvider provider) {
            DynamicVariableProvider curProvider = this.providers.get(name);
            if (curProvider != null) {
                if (curProvider.priority() > provider.priority()) {
                    return this;
                }
            }
            this.providers.put(name, provider);
            return this;
        }

        public DynamicVariableProviders build() {
            return new DynamicVariableProviders(Collections.unmodifiableMap(providers));
        }
    }

    private DynamicVariableProviders(Map<String, DynamicVariableProvider> providers) {
        this.providers = providers;
    }

    public DynamicVariableProvider get(String name) {
        DynamicVariableProvider provider = providers.get(name);
        if (provider == null) {
            return null;
        }
        return provider;
    }

    public Map<String, DynamicVariableProvider> providers() {
        return providers;
    }
}
