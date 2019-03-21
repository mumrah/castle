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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.Rule;
import org.junit.rules.Timeout;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static io.confluent.castle.common.JsonUtil.JSON_SERDE;
import static org.junit.Assert.assertEquals;

public class JsonUtilTest {
    @Rule
    final public Timeout globalTimeout = Timeout.millis(120000);

    static class TestObject {
        @JsonProperty
        private final Map<String, String> map;

        TestObject() {
             this.map = new HashMap<>();
        }

        @JsonCreator
        TestObject(@JsonProperty("map") Map<String, String> map) {
            this.map = map;
        }
    }

    // Test that we can serialize and deserialize a map without losing track of
    // empty entries.
    @Test
    public void testMapSerialization() throws Exception {
        TestObject testObject = new TestObject();
        testObject.map.put("abc", "123");
        testObject.map.put("def", "");
        TestObject testObject2 = JSON_SERDE.readValue(
            JSON_SERDE.writeValueAsString(testObject),
                TestObject.class);
        assertEquals("123", testObject2.map.get("abc"));
        assertEquals("", testObject2.map.get("def"));
    }
};
