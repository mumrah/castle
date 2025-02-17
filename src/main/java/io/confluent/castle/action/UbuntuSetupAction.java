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

package io.confluent.castle.action;

import io.confluent.castle.cluster.CastleCluster;
import io.confluent.castle.cluster.CastleNode;
import io.confluent.castle.role.UbuntuNodeRole;

/**
 * Install some necessary components on Ubuntu.
 */
public final class UbuntuSetupAction extends Action {
    public final static String TYPE = "ubuntuSetup";

    private final static int MAX_TRIES = 3;

    private final static int APT_GET_RETRY_PERIOD = 100;

    private final UbuntuNodeRole role;

    public UbuntuSetupAction(String scope, UbuntuNodeRole role) {
        super(new ActionId(TYPE, scope),
            new TargetId[] {},
            new String[] {},
            0);
        this.role = role;
    }

    @Override
    public void call(CastleCluster cluster, CastleNode node) throws Throwable {
        node.log().printf("*** %s: Beginning UbuntuSetup...%n", node.nodeName());
        for (int tries = 0; tries < MAX_TRIES; tries++) {
            int result = node.uplink().command().args("-n", "--",
                "sudo", "dpkg", "--configure", "-a", "&&",
                "sudo", "apt-get", "update", "-y", "&&",
                "sudo", "apt-get", "upgrade", "-y", "&&",
                "sudo", "apt-get", "install", "-y", "iptables", "rsync", "wget", "curl", "collectd-core",
                "coreutils", "cmake", "pkg-config", "libfuse-dev", role.jdkPackage()).run();
            if (result == 0) {
                node.log().printf("*** %s: Finished UbuntuSetup.%n", node.nodeName());
                return;
            }
            Thread.sleep(APT_GET_RETRY_PERIOD);
        }
        throw new RuntimeException("Failed to setup Ubuntu after " + MAX_TRIES + " tries.");
    }
};
