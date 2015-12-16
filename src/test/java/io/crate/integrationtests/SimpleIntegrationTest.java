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

import io.crate.action.sql.SQLActionException;
import io.crate.action.sql.SQLResponse;
import io.crate.shade.org.elasticsearch.common.io.FileSystemUtils;
import io.crate.testing.CrateTestServer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class SimpleIntegrationTest {

    static {
        File downloadFolder = new File(CrateTestServer.DEFAULT_WORKING_DIR, "/parts");
        FileSystemUtils.deleteRecursively(downloadFolder, false);
    }

    private static final String CLUSTER_NAME = "crate-java-testing";

    @ClassRule
    public static final CrateTestServer testServer = new CrateTestServer(CLUSTER_NAME, "0.52.2");

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testSimpleTest() {
        testServer.execute("create table test (foo string)");
        testServer.execute("insert into test (foo) values ('bar')");
        testServer.execute("refresh table test");
        SQLResponse response = testServer.execute("select * from test");
        assertThat(response.rowCount(), is(1L));
    }



    @Test
    public void testClusterName() throws Exception {
        SQLResponse response = testServer.execute("select name from sys.cluster");
        assertThat((String)response.rows()[0][0], is(CLUSTER_NAME));
    }

    @Test
    public void testErrorTest() throws Exception {
        expectedException.expect(SQLActionException.class);
        expectedException.expectMessage("line 1:1: no viable alternative at input 'wrong'");
        testServer.execute("wrong");
    }
}
