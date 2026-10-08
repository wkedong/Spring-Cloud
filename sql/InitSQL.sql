-- Zipkin 2.14+ 不再作为 Spring Boot 库嵌入，改为官方独立发行版（docker-compose 启动），
-- 默认内存存储无需建库；如需 MySQL 持久化，使用官方 zipkin 依赖的 mysql1 建表脚本：
-- https://github.com/openzipkin/zipkin/blob/master/zipkin-storage/mysql-v1/src/main/resources/mysql.sql
CREATE SCHEMA spring_cloud_config;
CREATE SCHEMA spring_cloud_demo;

USE spring_cloud_demo;

CREATE TABLE user
(
  id       INT AUTO_INCREMENT
  COMMENT '主键'
    PRIMARY KEY,
  name     VARCHAR(64) NOT NULL
  COMMENT '姓名',
  birthday DATE
  COMMENT '生日',
  address  VARCHAR(256)
  COMMENT '地址'
)
  CHARSET = utf8;

INSERT INTO user ( name, birthday, address) VALUES('test-admin', '1994-12-21', '测试地址');