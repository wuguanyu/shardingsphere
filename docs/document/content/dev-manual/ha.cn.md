+++
pre = "<b>6.9. </b>"
title = "高可用"
weight = 9
chapter = true
+++

## DatabaseDiscoveryType

| *SPI 名称*                                       | *详细说明*                     |
| ----------------------------------------------- | ----------------------------- |
| DatabaseDiscoveryType                           | 数据库发现类型                   |

| *已知实现类*                                      | *详细说明*                      |
| ----------------------------------------------- | ------------------------------ |
| MGRDatabaseDiscoveryType                        | 基于 MySQL MGR 的数据库发现       |
| MySQLNormalReplicationDatabaseDiscoveryType     | 基于 MySQL 主从同步的数据库发现     |
| OpenGaussNormalReplicationDatabaseDiscoveryType | 基于 openGauss 主从同步的数据库发现 |
