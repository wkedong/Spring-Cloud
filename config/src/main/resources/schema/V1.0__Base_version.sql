DROP TABLE IF EXISTS properties;
CREATE TABLE `properties` (
  `id`          int(11) AUTO_INCREMENT
  COMMENT '主键'
    PRIMARY KEY,
  `key`         varchar(50)  NOT NULL
  COMMENT 'key值_属性名',
  `value`       varchar(500) NOT NULL
  COMMENT 'value值_属性值',
  `application` varchar(50)  NOT NULL
  COMMENT '应用名',
  `profile`     varchar(50)  NOT NULL
  COMMENT '激活的剖面',
  `label`       varchar(50)  NOT NULL
  COMMENT '资源的label'
)
  ENGINE = InnoDB
  DEFAULT CHARSET = utf8;
