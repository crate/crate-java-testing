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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;

/**
 * testing multiple starts and stops of a testserver
 * when used as a method rule.
 * <p/>
 * This is the same behaviour as using the testserver as static instance in an abstract superclass
 * for many tests.
 */
public class ReuseServerInstanceTest extends BaseTest {

    private static final String CLUSTER_NAME = "rule";
    private static AtomicReference<String> clusterId = new AtomicReference<>();

    static CrateTestServer STATIC_SERVER = CrateTestServer
            .fromVersion("0.54.0")
            .clusterName(CLUSTER_NAME)
            .build();

    @Rule
    public final CrateTestServer testServer = STATIC_SERVER;

    @Test
    public void testFirstMethod() throws Exception {
        executeTest();
    }

    @Before
    public void setUp() {
        crateClient = crateClient(STATIC_SERVER.crateHost(), STATIC_SERVER.transportPort());
    }

    @Test
    public void testSecondMethod() throws Exception {
        executeTest();

    }

    private void executeTest() {
        String localClusterId = (String) execute("select id from sys.cluster").rows()[0][0];

        String otherClusterId = clusterId.getAndSet(localClusterId);
        if (otherClusterId != null) {
            assertThat(localClusterId, is(not(otherClusterId)));
        }
    }

}

