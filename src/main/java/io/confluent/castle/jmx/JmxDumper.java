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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.confluent.castle.common.CastleUtil;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

import javax.management.Attribute;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.QueryExp;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;
import static net.sourceforge.argparse4j.impl.Arguments.store;

/**
 * The JMX Dumper agent.
 *
 * Periodically dumps JMX state to comma-separated files.
 */
public final class JmxDumper {
    private final static int DEFAULT_PERIOD_MS = 1000;

    public static final ObjectMapper JSON_SERDE;

    static {
        JSON_SERDE = new ObjectMapper();
        JSON_SERDE.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        JSON_SERDE.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        JSON_SERDE.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }

    public static final class DumperUrl {
        final String host;
        final int port;
        final JMXServiceURL jmxUrl;

        DumperUrl(String endpoint) throws Exception {
            int lastColon = endpoint.lastIndexOf(':');
            if (lastColon < 0) {
                throw new RuntimeException("Failed to find a colon in the host:port " +
                    "string '" + endpoint + "'");
            }
            this.host = endpoint.substring(0, lastColon);
            String portString = endpoint.substring(lastColon + 1);
            try {
                this.port = Integer.parseInt(portString);
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse the port in '" + endpoint + "'", e);
            }
            this.jmxUrl = new JMXServiceURL(
                String.format("service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi", host, port));
        }

        boolean probe() throws IOException {
            try (Socket socket = new Socket(host, port)) {
                System.out.printf("** Successfully probed %s:%s%n",
                    host, port);
                return true;
            } catch (IOException e) {
                System.out.printf("** Failed to probe %s:%d: %s%n",
                    host, port, e.getMessage());
                return false;
            }
        }

        public String toString() {
            return String.format("%s:%d", host, port);
        }
    }

    private final class CsvFile implements AutoCloseable {
        private final JmxFileConfig file;
        private final OutputStreamWriter writer;

        public CsvFile(JmxFileConfig file) throws Exception {
            this.file = file;
            OutputStream outputStream = Files.newOutputStream(Paths.get(file.path()), WRITE, CREATE_NEW);
            try {
                this.writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            } catch (Throwable t) {
                outputStream.close();
                throw t;
            }
        }

        public void writeHeader() throws Exception {
            HashMap<String, String> shortNames = new HashMap<>();
            CsvRow headerRow = new CsvRow();
            headerRow.add("time");
            objectsAndAttrs.forEach(objectsAndAttr -> headerRow.add(objectsAndAttr.displayName));
            writer.write(headerRow.asString());
            writer.flush();
        }

        @Override
        public void close() throws IOException {
            writer.flush();
            writer.close();
        }

        public void storeJmx(long time) throws Exception {
            CsvRow row = new CsvRow();
            row.addTimeMs(time);

            for (CheckJmx.ObjectNameAndAttribute objectAndAttr : objectsAndAttrs) {
                Object attrValue = connection.getAttribute(objectAndAttr.objectName, objectAndAttr.attribute);
                if (!Objects.isNull(attrValue)) {
                    attrValue = objectAndAttr.valueTransformer.apply(attrValue);
                    row.addObject(attrValue);
                } else {
                    row.add("");
                }
            }

            writer.write(row.asString());
        }
    }

    private static final class CsvRow {
        private boolean first = true;
        private final StringBuilder bld = new StringBuilder();

        CsvRow addTimeMs(long timeMs) {
            if (!first) bld.append(", ");
            first = false;
            double time = timeMs;
            time /= 1000.0;
            bld.append(String.format("%.3f", time));
            return this;
        }

        CsvRow add(Number val) {
            if (!first) bld.append(", ");
            first = false;
            bld.append(val);
            return this;
        }

        CsvRow add(String val) {
            if (!first) bld.append(", ");
            first = false;
            bld.append("\"").append(val).append("\"");
            return this;
        }

        CsvRow addObject(Object value) {
            if (value instanceof Number) {
                add((Number) value);
            } else if (value instanceof String) {
                add((String) value);
            } else {
                add(value.toString());
            }
            return this;
        }

        String asString() {
            bld.append(System.lineSeparator());
            return bld.toString();
        }
    }

    public final class Probe implements Runnable {
        private final static int PROBE_DELAY_MS = 50;

        @Override
        public void run() {
            try {
                if (url.probe()) {
                    executorService.submit(new ConnectJmx());
                } else {
                    executorService.schedule(this, PROBE_DELAY_MS, TimeUnit.MILLISECONDS);
                }
            } catch (Throwable t) {
                completer.completeExceptionally(t);
            }
        }
    }

    public final class ConnectJmx implements Runnable {
        @Override
        public void run() {
            try {
                connector = JMXConnectorFactory.connect(url.jmxUrl, null);
                connection = connector.getMBeanServerConnection();
                executorService.submit(new CheckJmx());
            } catch (Throwable t) {
                completer.completeExceptionally(t);
            }
        }
    }

    public final class CheckJmx implements Runnable {
        private final static int LOAD_DELAY_MS = 100;

        @Override
        public void run() {
            try {
                if (load()) {
                    executorService.submit(new OpenFiles());
                } else {
                    executorService.schedule(this, LOAD_DELAY_MS, TimeUnit.MILLISECONDS);
                }
            } catch (Throwable t) {
                completer.completeExceptionally(t);
            }
        }

        private final boolean load() throws Exception {
            Collection<JmxObjectConfig> objects = dumperConfig.allObjects();
            for (JmxObjectConfig jmxObjectConfig : objects) {
                QueryExp jmxQuery = null;
                if (!jmxObjectConfig.className().isEmpty()) {
                    jmxQuery = Query.isInstanceOf(Query.value(jmxObjectConfig.className()));
                }
                Set<ObjectName> names = connection.queryNames(jmxObjectConfig.objectName(), jmxQuery);
                System.out.printf("** Looking for %s%n", jmxObjectConfig.objectName());
                for (ObjectName name : names) {
                    System.out.printf("** Found name %s%n", name.getCanonicalName());
                    MBeanInfo info = null;
                    try {
                        info = connection.getMBeanInfo(name);
                    } catch (InstanceNotFoundException e) {
                        System.out.printf("** Unable to locate %s%n", name.getCanonicalName());
                        continue;
                    }
                    if (info == null) {
                        continue;
                    }

                    ArrayList<String> foundAttrs = new ArrayList<>();
                    for (MBeanAttributeInfo attributeInfo : info.getAttributes()) {
                        System.out.printf("** %s contains: %s%n", name.getCanonicalName(), attributeInfo);
                        foundAttrs.add(attributeInfo.getName());
                    }

                    for (String simpleAttr : jmxObjectConfig.attributes()) {
                        if (foundAttrs.contains(simpleAttr)) {
                            objectsAndAttrs.add(new ObjectNameAndAttribute(name, simpleAttr, Function.identity(), jmxObjectConfig.shortName() + ":" + simpleAttr));
                        } else {
                            System.out.printf("** Could not find attribute %s for object %s%n", simpleAttr, name);
                        }
                    }

                    for (Map.Entry<String, String> compoundAttr : jmxObjectConfig.compoundAttributes().entrySet()) {
                        if (foundAttrs.contains(compoundAttr.getKey())) {
                            objectsAndAttrs.add(new ObjectNameAndAttribute(name, compoundAttr.getKey(), object -> {
                                if (object instanceof CompositeData) {
                                    return ((CompositeData)object).get(compoundAttr.getValue());
                                } else if (object instanceof Map) {
                                    return ((Map)object).get(compoundAttr.getValue());
                                } else {
                                    return object.toString();
                                }
                            }, jmxObjectConfig.shortName() + ":" + compoundAttr.getKey()));
                        } else {
                            System.out.printf("** Could not find attribute %s for object %s%n", compoundAttr.getKey(), name);
                        }
                    }
                }
            }
            System.out.printf("** Located %d object names.%n", objects.size());
            return true;
        }

        public class ObjectNameAndAttribute {
            public final ObjectName objectName;
            public final String attribute;
            public final Function<Object, Object> valueTransformer;
            public final String displayName;

            public ObjectNameAndAttribute(ObjectName objectName, String attribute, Function<Object, Object> valueTransformer, String displayName) {
                this.objectName = objectName;
                this.attribute = attribute;
                this.valueTransformer = valueTransformer;
                this.displayName = displayName;
            }
        }
    }

    public final class OpenFiles implements Runnable {
        @Override
        public void run() {
            try {
                for (JmxFileConfig file : dumperConfig.files()) {
                    CsvFile csvFile = new CsvFile(file);
                    csvFiles.add(csvFile);
                    csvFile.writeHeader();
                }
                executorService.submit(new StoreJmx());
            } catch (Throwable t) {
                completer.completeExceptionally(t);
            }
        }
    }

    public final class StoreJmx implements Runnable {
        @Override
        public void run() {
            long time = System.currentTimeMillis();
            try {
                for (CsvFile csvFile : csvFiles) {
                    csvFile.storeJmx(time);
                }
                executorService.schedule(this, dumperConfig.periodMs(),
                    TimeUnit.MILLISECONDS);
            } catch (Throwable t) {
                completer.completeExceptionally(t);
            }
        }
    }

    public final class Shutdown implements Runnable {
        @Override
        public void run() {
            try {
                System.out.printf("Closing csv files for %s.%n", url);
                for (Iterator<CsvFile> iter = csvFiles.iterator(); iter.hasNext(); ) {
                    CsvFile csvFile = iter.next();
                    csvFile.close();
                    iter.remove();
                }
                System.out.printf("Closing JMX connection for %s.%n", url);
                if (connector != null) {
                    connector.close();
                }
                System.out.printf("Shutting down executor service for %s.%n", url);
            } catch (Throwable t) {
                completer.completeExceptionally(t);
            }
            completer.countDown();
            executorService.shutdownNow();
        }
    }

    static final class Completer {
        private CompletableFuture<Void> future = new CompletableFuture<>();
        private AtomicInteger count;

        Completer(int count) {
            this.count = new AtomicInteger(count);
        }

        void countDown() {
            if (count.decrementAndGet() <= 0) {
                CastleUtil.completeNull(future);
            }
        }

        void completeExceptionally(Throwable throwable) {
            future.completeExceptionally(throwable);
        }

        boolean isDone() {
            return future.isDone();
        }

        void await() throws Exception {
            future.get();
        }
    }

    private final DumperUrl url;
    private final JmxDumperConfig dumperConfig;
    private final Completer completer;
    private final ScheduledExecutorService executorService;
    private JMXConnector connector = null;
    private MBeanServerConnection connection = null;
    private final List<CsvFile> csvFiles = new ArrayList<>();
    private final List<CheckJmx.ObjectNameAndAttribute> objectsAndAttrs = new ArrayList<>();


    JmxDumper(String endpoint, JmxDumperConfig dumperConfig, Completer completer) throws Exception {
        this.url = new DumperUrl(endpoint);
        this.dumperConfig = dumperConfig;
        this.completer = completer;
        this.executorService = Executors.newSingleThreadScheduledExecutor();
    }

    public synchronized void start() throws Exception {
        executorService.submit(new Probe());
    }

    void beginShutdown() throws Exception {
        try {
            executorService.submit(new Shutdown());
        } catch (RejectedExecutionException e) {
            /// ignore
        }
    }

    public static void main(String[] args) throws Exception {
        ArgumentParser parser = ArgumentParsers
            .newArgumentParser("jmx-dumper")
            .defaultHelp(true)
            .description("Periodically dumps JMX state to comma-separated files.");
        parser.addArgument("config_path")
            .action(store())
            .type(String.class)
            .required(true)
            .dest("config_path")
            .metavar("CONFIG_PATH")
            .help("The configuration file to use.  For example, <EXAMPLE_CONFIG>");

        Namespace res = parser.parseArgsOrFail(args);
        String configPath = res.getString("config_path");

        JmxDumpersConfig dumpersConfig = JSON_SERDE.
            readValue(new File(configPath), JmxDumpersConfig.class);
        final Completer completer = new Completer(dumpersConfig.map().size());
        Map<String, JmxDumper> dumpersMap = new HashMap<>();
        for (Map.Entry<String, JmxDumperConfig> entry : dumpersConfig.map().entrySet()) {
            dumpersMap.put(entry.getKey(),
                new JmxDumper(entry.getKey(), entry.getValue(), completer));
        }
        final Collection<JmxDumper> dumpers = Collections.unmodifiableCollection(dumpersMap.values());
        if (dumpers.isEmpty()) {
            System.out.printf("No DumperConfig entries found %s%n", configPath);
            System.exit(0);
        }
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("Running JmxDumper shutdown hook.");
                try {
                    for (JmxDumper dumper : dumpers) {
                        dumper.beginShutdown();
                    }
                    for (JmxDumper dumper : dumpers) {
                        dumper.executorService.awaitTermination(1, TimeUnit.DAYS);
                    }
                } catch (Exception e) {
                    System.out.println("Got exception while running JmxDumper shutdown hook: " +
                        CastleUtil.fullStackTrace(e));
                }
            }
        });
        for (JmxDumper dumper : dumpers) {
            dumper.start();
        }
        try {
            completer.await();
        } finally {
            for (JmxDumper dumper : dumpers) {
                dumper.beginShutdown();
            }
            for (JmxDumper dumper : dumpers) {
                dumper.executorService.awaitTermination(1, TimeUnit.DAYS);
            }
        }
    }
};
