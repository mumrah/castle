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
import io.confluent.castle.action.AwsDestroyAction;
import io.confluent.castle.action.AwsInitAction;
import io.confluent.castle.action.CopyAdditionalFilesAction;
import io.confluent.castle.action.DestroyNodesAction;
import io.confluent.castle.action.SaveLogsAction;
import io.confluent.castle.action.SourceSetupAction;
import io.confluent.castle.action.UplinkCheckAction;
import io.confluent.castle.cloud.Ec2Cloud;
import io.confluent.castle.cloud.Ec2Settings;
import io.confluent.castle.cluster.CastleCluster;
import io.confluent.castle.cluster.CastleNode;
import io.confluent.castle.uplink.Ec2Uplink;
import io.confluent.castle.uplink.Uplink;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class AwsNodeRole implements Role, UplinkRole {
    private static final String IMAGE_ID_DEFAULT = "ami-29ebb519";

    private static final String INSTANCE_TYPE_DEFAULT = "m1.small";

    /**
     * The AWS keypair to use.
     */
    private final String keyPair;

    /**
     * The AWS security group to use.
     */
    private final String securityGroup;

    /**
     * Configures the AWS image ID to use.
     */
    private final String imageId;

    /**
     * Configures the AWS instance type to use.
     */
    private final String instanceType;

    /**
     * Configures the ssh identity file to use.
     * If this is not set, no identity file will be used.
     */
    private final String sshIdentityFile;

    /**
     * Configures the ssh username to use.
     * If this is not set, no username will be specified.
     */
    private final String sshUser;

    /**
     * Configures the ssh port to use.
     * If this is not set, the system default will be used.
     */
    private final int sshPort;

    /**
     * Configures whether to use the internal DNS when accessing this node.
     * Defaults to false.
     */
    private final boolean internal;

    /**
     * Configures the private DNS address of a node, or the empty string
     * if there is none yet.  Protected by the object lock.
     */
    private String privateDns;

    /**
     * Configures the public DNS address of a node, or the empty string
     * if there is none yet.  Protected by the object lock.
     */
    private String publicDns;

    /**
     * The AWS instance ID, or the empty string if there is none.
     * Protected by the object lock.
     */
    private String instanceId;

    /**
     * The AWS region to use.  If this is empty, the default region will be used.
     */
    private String region;

    /**
     * The AWS availability zone to use. If this is empty, the default zone will be used.
     */
    private String zone;

    /**
     * Additional files to copy into the docker image.
     */
    private final List<AdditionalFile> additionalFiles;

    @JsonCreator
    public AwsNodeRole(@JsonProperty("keyPair") String keyPair,
                       @JsonProperty("securityGroup") String securityGroup,
                       @JsonProperty("imageId") String imageId,
                       @JsonProperty("instanceType") String instanceType,
                       @JsonProperty("sshIdentityFile") String sshIdentityFile,
                       @JsonProperty("sshUser") String sshUser,
                       @JsonProperty("sshPort") int sshPort,
                       @JsonProperty("internal") boolean internal,
                       @JsonProperty("privateDns") String privateDns,
                       @JsonProperty("publicDns") String publicDns,
                       @JsonProperty("instanceId") String instanceId,
                       @JsonProperty("region") String region,
                       @JsonProperty("zone") String zone,
                       @JsonProperty("additionalFiles") List<AdditionalFile> additionalFiles) {
        this.keyPair = keyPair == null ? "" : keyPair;
        this.securityGroup = securityGroup == null ? "" : securityGroup;
        this.imageId = imageId == null ? IMAGE_ID_DEFAULT : imageId;
        this.instanceType = instanceType == null ? INSTANCE_TYPE_DEFAULT : instanceType;
        this.sshIdentityFile = sshIdentityFile == null ? "" : sshIdentityFile;
        this.sshUser = sshUser == null ? "" : sshUser;
        this.sshPort = sshPort;
        this.internal = internal;
        this.privateDns = privateDns == null ? "" : privateDns;
        this.publicDns = publicDns == null ? "" : publicDns;
        this.instanceId = instanceId == null ? "" : instanceId;
        this.region = region == null ? "" : region;
        this.zone = zone == null ? "" : zone;
        this.additionalFiles = additionalFiles == null ? Collections.emptyList() :
            Collections.unmodifiableList(new ArrayList<>(additionalFiles));
    }

    @JsonProperty
    public String keyPair() {
        return this.keyPair;
    }

    @JsonProperty
    public String securityGroup() {
        return securityGroup;
    }

    @JsonProperty
    public String imageId() {
        return imageId;
    }

    @JsonProperty
    public String instanceType() {
        return instanceType;
    }

    @JsonProperty
    public String sshIdentityFile() {
        return sshIdentityFile;
    }

    @JsonProperty
    public String sshUser() {
        return sshUser;
    }

    @JsonProperty
    public int sshPort() {
        return sshPort;
    }

    @JsonProperty
    public boolean internal() {
        return internal;
    }

    @JsonProperty
    public synchronized String privateDns() {
        return privateDns;
    }

    public synchronized void setPrivateDns(String privateDns) {
        this.privateDns = privateDns;
    }

    @JsonProperty
    public synchronized String publicDns() {
        return publicDns;
    }

    public synchronized void setPublicDns(String publicDns) {
        this.publicDns = publicDns;
    }

    @JsonProperty
    public synchronized String instanceId() {
        return instanceId;
    }

    @JsonProperty
    public String region() {
        return region;
    }

    @JsonProperty
    public String zone() {
        return zone;
    }

    public synchronized void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    @JsonProperty
    public List<AdditionalFile> additionalFiles() {
        return this.additionalFiles;
    }

    @Override
    public Collection<Action> createActions(String nodeName) {
        ArrayList<Action> actions = new ArrayList<>();
        actions.add(new AwsDestroyAction(nodeName, this));
        actions.add(new AwsInitAction(nodeName, this));
        actions.add(new DestroyNodesAction(nodeName));
        actions.add(new SaveLogsAction(nodeName));
        actions.add(new SourceSetupAction(nodeName));
        actions.add(new UplinkCheckAction(nodeName));
        if (!additionalFiles.isEmpty()) {
            actions.add(new CopyAdditionalFilesAction(nodeName, additionalFiles));
        }
        return actions;
    }

    public synchronized String dns() {
        if (internal) {
            return privateDns;
        } else {
            return publicDns;
        }
    }

    @Override
    public Uplink createUplink(CastleCluster cluster, CastleNode node) {
        Ec2Settings settings = new Ec2Settings(keyPair, securityGroup, region);
        Ec2Cloud cloud = cluster.cloudCache().getOrCreate(settings.toString(),
            new Function<Void, Ec2Cloud>() {
                @Override
                public Ec2Cloud apply(Void v) {
                    return new Ec2Cloud(settings);
                }
            });
        return new Ec2Uplink(this, cluster, node, cloud);
    }
};
