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

package org.apache.shardingsphere.transaction.core.fixture;

import lombok.Setter;
import org.apache.shardingsphere.infra.database.type.DatabaseType;
import org.apache.shardingsphere.transaction.core.ResourceDataSource;
import org.apache.shardingsphere.transaction.core.TransactionType;
import org.apache.shardingsphere.transaction.rule.TransactionRule;
import org.apache.shardingsphere.transaction.spi.ShardingSphereTransactionManager;

import java.sql.Connection;
import java.util.Collection;

public final class ShardingSphereTransactionManagerFixture implements ShardingSphereTransactionManager {
    
    @Setter
    private Runnable caller;
    
    @Override
    public void init(final DatabaseType databaseType, final Collection<ResourceDataSource> resourceDataSources, final TransactionRule transactionRule) {
        caller.run();
    }
    
    @Override
    public TransactionType getTransactionType() {
        return TransactionType.XA;
    }
    
    @Override
    public boolean isInTransaction() {
        return true;
    }
    
    @Override
    public Connection getConnection(final String dataSourceName) {
        return null;
    }
    
    @Override
    public void begin() {
    }
    
    @Override
    public void begin(final int timeout) {
    }
    
    @Override
    public void commit(final boolean rollbackOnly) {
    }
    
    @Override
    public void rollback() {
    }
    
    @Override
    public void close() {
    }
}
