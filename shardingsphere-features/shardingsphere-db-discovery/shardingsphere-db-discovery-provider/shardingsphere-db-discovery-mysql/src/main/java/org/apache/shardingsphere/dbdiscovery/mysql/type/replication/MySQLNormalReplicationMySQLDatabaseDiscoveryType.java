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

package org.apache.shardingsphere.dbdiscovery.mysql.type.replication;

import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.dbdiscovery.mysql.AbstractMySQLDatabaseDiscoveryType;
import org.apache.shardingsphere.infra.eventbus.ShardingSphereEventBus;
import org.apache.shardingsphere.infra.rule.event.impl.DataSourceDisabledEvent;
import org.apache.shardingsphere.infra.storage.StorageNodeDataSource;
import org.apache.shardingsphere.infra.storage.StorageNodeRole;
import org.apache.shardingsphere.infra.storage.StorageNodeStatus;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

/**
 * Normal replication database discovery type for MySQL.
 */
@Slf4j
public final class MySQLNormalReplicationMySQLDatabaseDiscoveryType extends AbstractMySQLDatabaseDiscoveryType {
    
    private static final String SHOW_SLAVE_STATUS = "SHOW SLAVE STATUS";
    
    @Override
    public MySQLNormalReplicationHighlyAvailableStatus loadHighlyAvailableStatus(final DataSource dataSource) throws SQLException {
        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            return new MySQLNormalReplicationHighlyAvailableStatus(loadPrimaryDatabaseInstanceURL(statement).orElse(null));
        }
    }
    
    @Override
    protected Optional<String> loadPrimaryDatabaseInstanceURL(final Statement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(SHOW_SLAVE_STATUS)) {
            if (resultSet.next()) {
                String masterHost = resultSet.getString("Master_Host");
                String masterPort = resultSet.getString("Master_Port");
                if (null != masterHost && null != masterPort) {
                    return Optional.of(String.format("%s:%s", masterHost, masterPort));
                }
            }
            return Optional.empty();
        }
    }
    
    @Override
    public void updateMemberState(final String databaseName, final Map<String, DataSource> dataSourceMap, final String groupName) {
        for (Entry<String, DataSource> entry : dataSourceMap.entrySet()) {
            if (!entry.getKey().equals(getPrimaryDataSource())) {
                postDataSourceDisabledEvent(databaseName, groupName, entry.getKey(), entry.getValue());
            }
        }
    }
    
    private void postDataSourceDisabledEvent(final String databaseName, final String groupName, final String datasourceName, final DataSource replicaDataSource) {
        ShardingSphereEventBus.getInstance().post(new DataSourceDisabledEvent(databaseName, groupName, datasourceName, getStorageNodeDataSource(replicaDataSource)));
    }
    
    private StorageNodeDataSource getStorageNodeDataSource(final DataSource replicaDataSource) {
        try (
                Connection connection = replicaDataSource.getConnection();
                Statement statement = connection.createStatement()) {
            long replicationDelayMilliseconds = loadReplicationDelayMilliseconds(statement);
            StorageNodeStatus storageNodeStatus = replicationDelayMilliseconds < Long.parseLong(getProps().getProperty("delay-milliseconds-threshold"))
                    ? StorageNodeStatus.ENABLED
                    : StorageNodeStatus.DISABLED;
            return new StorageNodeDataSource(StorageNodeRole.MEMBER, storageNodeStatus, replicationDelayMilliseconds);
        } catch (SQLException ex) {
            log.error("An exception occurred while find member data source `Seconds_Behind_Master`", ex);
        }
        return new StorageNodeDataSource(StorageNodeRole.MEMBER, StorageNodeStatus.DISABLED);
    }
    
    private long loadReplicationDelayMilliseconds(final Statement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(SHOW_SLAVE_STATUS)) {
            return resultSet.next() ? resultSet.getLong("Seconds_Behind_Master") * 1000L : 0L;
        }
    }
    
    @Override
    public String getType() {
        return "MySQL.NORMAL_REPLICATION";
    }
}
