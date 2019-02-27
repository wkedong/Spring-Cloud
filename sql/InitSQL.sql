CREATE SCHEMA spring_cloud_config;
CREATE SCHEMA spring_cloud_demo;
CREATE SCHEMA spring_cloud_zipkin;

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