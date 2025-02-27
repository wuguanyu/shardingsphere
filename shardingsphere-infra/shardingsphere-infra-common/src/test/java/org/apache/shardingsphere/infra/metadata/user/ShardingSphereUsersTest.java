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

package org.apache.shardingsphere.infra.metadata.user;

import org.junit.Test;

import java.util.LinkedList;
import java.util.Collection;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class ShardingSphereUsersTest {
    
    @Test
    public void assertFindUser() {
        Collection<ShardingSphereUser> shardingSphereUserCollection = new LinkedList<>();
        ShardingSphereUser shardingSphereUser = mock(ShardingSphereUser.class);
        Grantee testGrantee = mock(Grantee.class);
        when(shardingSphereUser.getGrantee()).thenReturn(testGrantee);
        shardingSphereUserCollection.add(shardingSphereUser);
        ShardingSphereUsers shardingSphereUsers = new ShardingSphereUsers(shardingSphereUserCollection);
        assertThat(shardingSphereUsers.findUser(testGrantee).get(), is(shardingSphereUser));
        Grantee notExistGrantee = mock(Grantee.class);
        assertFalse(shardingSphereUsers.findUser(notExistGrantee).isPresent());
    }
}
