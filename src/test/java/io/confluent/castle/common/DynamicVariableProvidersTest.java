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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static org.junit.Assert.assertEquals;

public class DynamicVariableProvidersTest {
    @Rule
    final public Timeout globalTimeout = Timeout.millis(120000);

    private static class TestProvider extends DynamicVariableProvider {
        private final String value;

        TestProvider(int priority, String value) {
            super(priority);
            this.value = value;
        }

        @Override
        public String calculate(CastleCluster cluster, CastleNode node) throws Exception {
            return value;
        }
    }

    private static final String FOO = "foo";
    private static final String BAR = "bar";

    private static final DynamicVariableProviders PROVIDERS =
        new DynamicVariableProviders.Builder().
            add(FOO, new TestProvider(0, "foo0")).
            add(FOO, new TestProvider(1, "foo1")).
            add(BAR, new TestProvider(0, "bar0")).
            add(FOO, new TestProvider(2, "foo2")).
            build();

    @Test
    public void testPriority() throws Exception {
        assertEquals(2, PROVIDERS.get(FOO).priority());
        assertEquals(0, PROVIDERS.get(BAR).priority());
    }

    @Test
    public void testExpander() throws Exception {
        DynamicVariableExpander expander =
            new DynamicVariableExpander(PROVIDERS, null, null);
        assertEquals("foo2", expander.lookupVariable("foo"));
        assertEquals("bar0", expander.lookupVariable("bar"));
        assertEquals(null, expander.lookupVariable("baz"));
        assertEquals("foo2 and also bar0", expander.expand("%{foo} and also %{bar}"));
    }
}
