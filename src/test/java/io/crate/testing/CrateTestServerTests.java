/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.testing;

import com.carrotsearch.randomizedtesting.RandomizedTest;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.core.Is.is;

public class CrateTestServerTests extends RandomizedTest {


    private CrateTestServer serverOf(String version) {
        return new CrateTestServer(
                "test-cluster",
                4200,
                4300,
                5432,
                newTempDir().toPath(),
                "localhost",
                Collections.emptyMap(),
                Collections.emptyMap(),
                version,
                "localhost",
                "1.1.1.1");
    }

    @Test
    public void testPrepareSettingsForAnyVersion() {
        CrateTestServer server = serverOf("0.0.0");
        Map<String, Object> settings = server.prepareSettings();

        assertThat(settings.get("cluster.name"), is("test-cluster"));
        assertThat(settings.get("network.host"), is("localhost"));
        assertThat(settings.get("http.port"), is(4200));
        assertThat(settings.get("transport.tcp.port"), is(4300));
        assertThat(settings.get("psql.port"), is(5432));
        assertThat(settings.get("psql.enabled"), is(true));
    }

    @Test
    public void testPrepareSettingsForLt2_0() {
        CrateTestServer server = serverOf("1.0.0");
        Map<String, Object> settings = server.prepareSettings();

        assertThat(settings.get("discovery.zen.ping.multicast.enabled"), is("false"));
        assertThat(settings.get("index.storage.type"), is("memory"));
    }

    @Test
    public void testPrepareSettingsForLt4_0() {
        CrateTestServer server = serverOf("3.0.0");
        Map<String, Object> settings = server.prepareSettings();

        assertThat(settings.get("discovery.zen.ping.unicast.hosts"), is("localhost,1.1.1.1"));
    }

    @Test
    public void testPrepareSettingsForGte4_0() {
        CrateTestServer server = serverOf("4.0.0");
        Map<String, Object> settings = server.prepareSettings();

        assertThat(settings.get("discovery.seed_hosts"), is("localhost,1.1.1.1"));
        assertThat(settings.get("cluster.initial_master_nodes"), is("localhost,1.1.1.1"));
    }

    @Test
    public void testJDK8IsUsedForCrateLt3_2() {
        HashMap<String, String> env = new HashMap<>();
        CrateTestServer.prepareEnvironment(env, "3.0.0");
        assertThat(env.get("JAVA_HOME"), anyOf(containsString("1.8"), containsString("java-8")));
    }

    @Test
    public void testJDK8IsNotUsedForCrateGte3_2() {
        HashMap<String, String> env = new HashMap<>();
        CrateTestServer.prepareEnvironment(env, "3.2.0");
        assertThat(env.get("JAVA_HOME"), nullValue());
    }
}
