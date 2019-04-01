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

package io.confluent.castle.role;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.confluent.castle.action.Action;
import io.confluent.castle.action.TrogdorDaemonType;
import io.confluent.castle.action.TrogdorStartAction;
import io.confluent.castle.action.TrogdorStatusAction;
import io.confluent.castle.action.TrogdorStopAction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class TrogdorCoordinatorRole implements Role {
    private final int initialDelayMs;

    private final List<String> log4j;

    public final static int PORT = 8889;

    @JsonCreator
    public TrogdorCoordinatorRole(@JsonProperty("initialDelayMs") int initialDelayMs,
                                  @JsonProperty("log4j") List<String> log4j) {
        this.initialDelayMs = initialDelayMs;
        if (log4j == null) {
            this.log4j = Collections.singletonList("log4j.logger.org.apache.kafka=DEBUG");
        } else {
            this.log4j = Collections.unmodifiableList(new ArrayList<>(log4j));
        }
    }

    @JsonProperty
    public int initialDelayMs() {
        return initialDelayMs;
    }

    @JsonProperty
    public List<String> log4j() {
        return log4j;
    }

    @Override
    public Collection<Action> createActions(String nodeName) {
        ArrayList<Action> actions = new ArrayList<>();
        actions.add(new TrogdorStartAction(TrogdorDaemonType.COORDINATOR,
            nodeName, initialDelayMs, log4j));
        actions.add(new TrogdorStatusAction(TrogdorDaemonType.COORDINATOR,
            nodeName));
        actions.add(new TrogdorStopAction(TrogdorDaemonType.COORDINATOR,
            nodeName, initialDelayMs));
        return actions;
    }
};
