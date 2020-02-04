/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.integrationtests;

import io.crate.testing.CrateTestCluster;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.concurrent.atomic.AtomicReference;

import static io.crate.testing.Constants.CRATE_VERSION_FOR_TESTS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * testing multiple starts and stops of a crate test cluster
 * when used as a method rule.
 * <p/>
 * This is the same behaviour as using the testserver as static instance in an abstract superclass
 * for many tests.
 */
public class ReuseStaticClusterInstanceTest extends BaseTest {

    private static final String CLUSTER_NAME = "rule";
    private static AtomicReference<String> clusterId = new AtomicReference<>();

    private static CrateTestCluster staticCluster = CrateTestCluster
        .fromVersion(CRATE_VERSION_FOR_TESTS)
        .clusterName(CLUSTER_NAME)
        .build();

    @Rule
    public final CrateTestCluster testServer = staticCluster;

    @Before
    public void setUp() throws MalformedURLException {
        prepare(staticCluster);
    }

    @Test
    public void testFirstMethod() throws Exception {
        executeTest();
    }

    @Test
    public void testSecondMethod() throws Exception {
        executeTest();
    }

    private void executeTest() throws IOException {
        String localClusterId = null;
        do {
            localClusterId = execute("select id from sys.cluster").getAsJsonArray("rows").get(0).getAsString();
        } while (localClusterId.equals("_na_")); // make sure cluster is fully started

        String otherClusterId = clusterId.getAndSet(localClusterId);
        if (otherClusterId != null) {
            assertThat(localClusterId, is(not(otherClusterId)));
        }
    }
}
