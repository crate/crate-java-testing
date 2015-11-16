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
import io.crate.action.sql.SQLResponse;
import io.crate.client.CrateClient;
import io.crate.testing.CrateTestServer;
import org.junit.ClassRule;
import org.junit.Test;

import static org.hamcrest.core.Is.is;

public class SimpleIntegrationTest extends RandomizedTest {

    private static final String CLUSTER_NAME = "crate-jave-testing";
    private static CrateClient crateClient;

    @ClassRule
    public static final CrateTestServer testServer = new CrateTestServer(CLUSTER_NAME, "0.52.2");


    @Test
    public void testSimpleTest() {
        testServer.execute("create table test (foo string)");
        testServer.execute("insert into test (foo) values ('bar')");
        testServer.execute("refresh table test");
        SQLResponse response = testServer.execute("select * from test");
        assertThat(response.rowCount(), is(1L));
    }

}
