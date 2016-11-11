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

package io.crate.testing;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class CrateVersionTest {

    @Test
    public void testHigherCrateVersion() {
        assertThat(new CrateVersion("1.0").gt("1.0"), is(false));
        assertThat(new CrateVersion("1.0").gt("0.57.1"), is(true));
        assertThat(new CrateVersion("1.0.0").gt("0.57.1"), is(true));
        assertThat(new CrateVersion("1.0.0").gt("0.57"), is(true));
        assertThat(new CrateVersion("1.0.0").gt("1.1.1"), is(false));
    }

    @Test
    public void testLessCrateVersion() {
        assertThat(new CrateVersion("1.0").lt("1.1"), is(true));
        assertThat(new CrateVersion("1.0").lt("0.57.1"), is(false));
        assertThat(new CrateVersion("1.0.0").lt("0.57.1"), is(false));
        assertThat(new CrateVersion("1.0.0").lt("0.57"), is(false));
        assertThat(new CrateVersion("1.0.0").lt("1.1.1"), is(true));
    }
}
