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

import org.hamcrest.core.IsCollectionContaining;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class ShadingClassLoaderTest {

    public static class CheckingClassLoader extends ClassLoader {
        public List<String> invocations = new ArrayList<>();

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            invocations.add(name);
            return super.loadClass(name, resolve);
        }
    }

    @Test
    public void testShadedLoading() throws Exception {
        assertReplacement(
                "io.crate.action.sql.SQLActionException",
                "io.crate.testserver.action.sql.SQLActionException");
        assertReplacement(
                "io.crate.client.CrateClient",
                "io.crate.testserver.client.CrateClient");
        assertReplacement(
                "io.crate.shade.com.google.common.base.MoreObjects",
                "io.crate.testserver.shade.com.google.common.base.MoreObjects");
        assertReplacement(
                "org.apache.commons.compress.utils.IOUtils",
                "io.crate.testserver.apache.commons.compress.utils.IOUtils");
        assertReplacement(
                "com.tdunning.math.stats.TDigest",
                "io.crate.testserver.tdunning.math.stats.TDigest");
    }

    @Test
    public void testNoShading() throws Exception {
        CheckingClassLoader checkingClassLoader = new CheckingClassLoader();
        ShadingClassLoader shadingClassLoader = new ShadingClassLoader(checkingClassLoader);
        try {
            shadingClassLoader.loadClass("org.junit.Test");
        } catch (ClassNotFoundException e) {
            // ignore
        }
        assertThat(checkingClassLoader.invocations.size(), is(1));
        assertThat(checkingClassLoader.invocations.get(0), is("org.junit.Test"));
    }

    private void assertReplacement(String original, String ... expected) {
        CheckingClassLoader checkingClassLoader = new CheckingClassLoader();
        ShadingClassLoader shadingClassLoader = new ShadingClassLoader(checkingClassLoader);
        try {
            shadingClassLoader.loadClass(original);
        } catch (ClassNotFoundException e) {
            // ignore
        }
        assertThat(checkingClassLoader.invocations, IsCollectionContaining.hasItems(expected));
    }
}
