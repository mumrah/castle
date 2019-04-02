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

package io.confluent.castle.tool;

import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class CastleSignalHandler implements AutoCloseable {
    public enum CastleSignal {
        HUP;
    }

    private final Map<CastleSignal, SignalHandler> prevSignalHandlers;

    public CastleSignalHandler() {
        this.prevSignalHandlers = new HashMap<>();
    }

    public synchronized void register(final CastleSignal signal, final Runnable handler) {
        if (prevSignalHandlers.containsKey(signal)) {
            throw new RuntimeException("There is already a handler for " + signal);
        }
        prevSignalHandlers.put(signal,
            Signal.handle(new Signal(signal.toString()),
                new SignalHandler() {
                    @Override
                    public void handle(Signal signal) {
                        handler.run();
                    }
                }));
    }

    public synchronized void unregister(final CastleSignal signal) {
        prevSignalHandlers.remove(signal);
    }

    @Override
    public synchronized void close() throws Exception {
        for (Iterator<Map.Entry<CastleSignal, SignalHandler>> iterator =
                 prevSignalHandlers.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<CastleSignal, SignalHandler> entry = iterator.next();
            Signal.handle(new Signal(entry.getKey().toString()), entry.getValue());
            iterator.remove();
        }
    }
};
