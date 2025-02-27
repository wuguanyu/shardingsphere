/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.data.pipeline.api.config.rulealtered;

import com.google.common.collect.Lists;
import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public final class HandleConfigurationTest {
    
    @Test
    public void assertGetJobShardingCountByNull() {
        assertThat(new HandleConfiguration().getJobShardingCount(), is(0));
    }
    
    @Test
    public void assertGetJobShardingCount() {
        HandleConfiguration handleConfig = new HandleConfiguration();
        handleConfig.setJobShardingDataNodes(Arrays.asList("node1", "node2"));
        assertThat(handleConfig.getJobShardingCount(), is(2));
    }
    
    @Test
    public void assertSplitLogicTableNames() {
        HandleConfiguration handleConfig = new HandleConfiguration();
        handleConfig.setLogicTables("foo_tbl,bar_tbl");
        assertThat(handleConfig.splitLogicTableNames(), is(Lists.newArrayList("foo_tbl", "bar_tbl")));
    }
    
    @Test
    public void assertGetJobIdDigestByLongName() {
        HandleConfiguration handleConfig = new HandleConfiguration();
        handleConfig.setJobId("abcdefg");
        assertThat(handleConfig.getJobIdDigest(), is("abcdef"));
    }
    
    @Test
    public void assertGetJobIdDigestByShortName() {
        HandleConfiguration handleConfiguration = new HandleConfiguration();
        handleConfiguration.setJobId("abcdef");
        assertThat(handleConfiguration.getJobIdDigest(), is("abcdef"));
    }
}
