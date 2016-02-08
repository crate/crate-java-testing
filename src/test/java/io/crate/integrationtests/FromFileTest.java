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

import io.crate.testing.CrateTestServer;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;

public class FromFileTest extends BaseTest {

    private static final String CLUSTER_NAME = "from-file";
    private static final String VERSION = "0.53.0";

    private static final String FILE = FromFileTest.class
            .getResource(String.format("crate-%s.tar.gz", VERSION))
            .getPath();

    @ClassRule
    public static CrateTestServer fromFileServer = CrateTestServer.fromFile(FILE, CLUSTER_NAME);

    @BeforeClass
    public static void setUp() {
        crateClient = crateClient(fromFileServer.crateHost(), fromFileServer.transportPort());
    }

    @Test
    public void testFromFile() throws Exception {
        assertThat(
                (String) execute("select name from sys.cluster").rows()[0][0],
                is(CLUSTER_NAME));
        assertThat(
                (String) execute("select version['number'] from sys.nodes").rows()[0][0],
                is(VERSION));
    }

}
