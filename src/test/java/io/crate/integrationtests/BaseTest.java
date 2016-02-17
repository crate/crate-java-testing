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

package io.crate.integrationtests;

import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import io.crate.action.sql.SQLRequest;
import io.crate.action.sql.SQLResponse;
import io.crate.client.CrateClient;
import io.crate.shade.org.elasticsearch.client.transport.TransportClient;
import io.crate.shade.org.elasticsearch.common.io.FileSystemUtils;
import io.crate.shade.org.elasticsearch.common.settings.ImmutableSettings;
import io.crate.shade.org.elasticsearch.common.transport.InetSocketTransportAddress;
import io.crate.shade.org.elasticsearch.common.unit.TimeValue;
import io.crate.testing.CrateTestCluster;
import io.crate.testing.CrateTestServer;

@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
public abstract class BaseTest extends RandomizedTest {

    static {
        FileSystemUtils.deleteRecursively(CrateTestServer.TMP_WORKING_DIR, false);
        FileSystemUtils.deleteRecursively(CrateTestServer.TMP_CACHE_DIR, false);
    }

    private static final TimeValue DEFAULT_TIMEOUT = TimeValue.timeValueSeconds(10);

    protected static CrateClient crateClient;

    protected static CrateClient crateClient(CrateTestCluster crateCluster) {
        CrateTestServer crateTestServer = crateCluster.randomServer();
        return new CrateClient(String.format("%s:%d", crateTestServer.crateHost(), crateTestServer.transportPort()));
    }

    protected SQLResponse execute(String statement) {
        return execute(statement, SQLRequest.EMPTY_ARGS, DEFAULT_TIMEOUT);
    }

    protected SQLResponse execute(String statement, TimeValue timeout) {
        return execute(statement, SQLRequest.EMPTY_ARGS, timeout);
    }

    protected SQLResponse execute(String statement, Object[] args) {
        return execute(statement, args, DEFAULT_TIMEOUT);
    }

    protected SQLResponse execute(String statement, Object[] args, TimeValue timeout) {
        return crateClient.sql(new SQLRequest(statement, args)).actionGet(timeout);
    }

    private TransportClient ensureTransportClient(CrateTestServer server) {
        TransportClient transportClient = new TransportClient(ImmutableSettings.builder()
                .put("cluster.name", server.clusterName())
                .build());
        transportClient.addTransportAddress(
                new InetSocketTransportAddress(server.crateHost(), server.transportPort())
        );
        return transportClient;
    }

    protected void ensureYellow(CrateTestServer server) {
        ensureTransportClient(server).admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
    }

    protected void ensureGreen(CrateTestServer server) {
        ensureTransportClient(server).admin().cluster().prepareHealth().setWaitForGreenStatus().execute().actionGet();
    }

}
