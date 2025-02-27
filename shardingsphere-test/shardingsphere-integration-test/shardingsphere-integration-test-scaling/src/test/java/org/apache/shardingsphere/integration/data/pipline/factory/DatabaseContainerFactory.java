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

package org.apache.shardingsphere.integration.data.pipline.factory;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.shardingsphere.infra.database.type.DatabaseType;
import org.apache.shardingsphere.integration.data.pipline.container.database.DockerDatabaseContainer;
import org.apache.shardingsphere.integration.data.pipline.container.database.MySQLContainer;
import org.apache.shardingsphere.integration.data.pipline.container.database.PostgreSQLContainer;

/**
 * Storage container factory.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DatabaseContainerFactory {
    
    /**
     * Create new instance of storage container.
     *
     * @param databaseType database type
     * @return new instance of storage container
     */
    public static DockerDatabaseContainer newInstance(final DatabaseType databaseType) {
        switch (databaseType.getName()) {
            case "MySQL":
                return new MySQLContainer("mysql:5.7");
            case "PostgreSQL":
                return new PostgreSQLContainer("postgres:12.6");
            default:
                throw new RuntimeException(String.format("Database [%s] is unknown.", databaseType.getName()));
        }
    }
}
