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

package io.confluent.castle.jmx;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.management.ObjectName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JmxObjectConfig {
    private final String name;
    private final String className;
    private final String shortName;
    private final List<String> attributes;
    private final Map<String, String> compoundAttributes;
    private final ObjectName objectName;

    @JsonCreator
    public JmxObjectConfig(@JsonProperty("name") String name,
                           @JsonProperty("class") String className,
                           @JsonProperty("shortName") String shortName,
                           @JsonProperty("attributes") List<String> attributes,
                           @JsonProperty("compoundAttributes") Map<String, String> compoundAttributes) throws Exception {
        this.name = (name == null) ? "" : name;
        this.className = (className == null) ? "" : className;
        this.shortName = (shortName == null) ? "" : shortName;
        this.attributes = (attributes == null) ? Collections.emptyList() : new ArrayList<>(attributes);
        this.compoundAttributes = (compoundAttributes == null) ? Collections.emptyMap() : new LinkedHashMap<>(compoundAttributes);
        this.objectName = new ObjectName(this.name);
    }

    @JsonProperty
    public String name() {
        return name;
    }

    @JsonProperty("class")
    public String className() {
        return className;
    }

    @JsonProperty
    public String shortName() {
        return shortName;
    }

    @JsonProperty
    public List<String> attributes() {
        return attributes;
    }

    @JsonProperty
    public Map<String, String> compoundAttributes() {
        return compoundAttributes;
    }

    ObjectName objectName() {
        return objectName;
    }
}

