DROP TABLE IF EXISTS properties;
CREATE TABLE `properties` (
  `id`          int(11)      NOT NULL,
  `key`         varchar(50)  NOT NULL,
  `value`       varchar(500) NOT NULL,
  `application` varchar(50)  NOT NULL,
  `profile`     varchar(50)  NOT NULL,
  `label`       varchar(50)  NOT NULL,
  PRIMARY KEY (`id`)
)
  ENGINE = InnoDB
  DEFAULT CHARSET = utf8;

INSERT INTO properties
VALUES (1, 'name', 'test-dev-master', 'service-producer', 'dev', 'master');
INSERT INTO properties
VALUES (2, 'name', 'test-pro-master', 'service-producer', 'pro', 'master');
INSERT INTO properties
VALUES (3, 'name', 'test-dev-develop', 'service-producer', 'dev', 'develop');
INSERT INTO properties
VALUES (4, 'name', 'test-pro-develop', 'service-producer', 'pro', 'develop');