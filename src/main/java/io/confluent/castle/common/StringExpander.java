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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A utility class for substituting variable expressions inside a string.  The
 * variable expressions must be prefixed by a percent sign and enclosed in
 * brackets.  For example, this object will expand %{foo} to the value of the
 * "foo" variable.
 */
public interface StringExpander {
    String lookupVariable(String key) throws Exception;

    default String expand(String input) throws Exception {
        StringBuilder output = new StringBuilder();
        char delimiter = '\0';
        String variableName = "";
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (delimiter) {
                case '\\':
                    output.append(c);
                    delimiter = '\0';
                    break;
                case '%':
                    if (c != '{') {
                        output.append('%').append(c);
                        delimiter = '\0';
                    } else {
                        delimiter = '{';
                    }
                    break;
                case '{':
                    if (c == '}') {
                        String value = lookupVariable(variableName);
                        if (value == null) {
                            output.append("%{" + variableName + "}");
                        } else {
                            output.append(value);
                        }
                        variableName = "";
                        delimiter = '\0';
                    } else {
                        variableName = variableName + c;
                    }
                    break;
                default:
                    switch (c) {
                        case '\\':
                            delimiter = '\\';
                            break;
                        case '%':
                            delimiter = '%';
                            break;
                        default:
                            output.append(c);
                            break;
                    }
                    break;
            }
        }
        return output.toString();
    }

    /**
     * Recursively expands all strings inside a JSON node.
     */
    default JsonNode expand(JsonNode input) throws Exception {
        switch (input.getNodeType()) {
            case STRING:
                return TextNode.valueOf(expand(input.textValue()));
            case ARRAY:
                ArrayNode arrayResult = new ArrayNode(JsonNodeFactory.instance);
                int index = 0;
                for (Iterator<JsonNode> iter = input.elements(); iter.hasNext(); ) {
                    JsonNode child = iter.next();
                    arrayResult.insert(index, expand(child));
                    index++;
                }
                return arrayResult;
            case OBJECT:
                ObjectNode objectResult = new ObjectNode(JsonNodeFactory.instance);
                for (Iterator<Map.Entry<String, JsonNode>> iter = input.fields(); iter.hasNext(); ) {
                    Map.Entry<String, JsonNode> entry = iter.next();
                    objectResult.set(expand(entry.getKey()), expand(entry.getValue()));
                }
                return objectResult;
            default:
                return input.deepCopy();
        }
    }

    /**
     * Returns a copy of a map with all the keys and values expanded.
     */
    default Map<String, String> expand(Map<String, String> input) throws Exception {
        Map<String, String> newMap = new HashMap<>();
        for (Map.Entry<String, String> entry : input.entrySet()) {
            newMap.put(expand(entry.getKey()), expand(entry.getValue()));
        }
        return newMap;
    }
}
