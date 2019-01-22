package com.wkedong.springcloud.config;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @author wkedong
 */
@EnableDiscoveryClient
@EnableConfigServer
@SpringBootApplication
public class ConfigApplication {

    public static void main(String[] args) {
        ApplicationContext context = new SpringApplicationBuilder(ConfigApplication.class).web(true).run(args);


        JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
        jdbcTemplate.execute("delete from properties");
        jdbcTemplate.execute("INSERT INTO properties VALUES(1, 'name', 'test-dev-master', 'service-producer', 'dev', 'master')");
        jdbcTemplate.execute("INSERT INTO properties VALUES(2, 'name', 'test-pro-master', 'service-producer', 'pro', 'master')");
        jdbcTemplate.execute("INSERT INTO properties VALUES (3, 'name', 'test-dev-develop', 'service-producer', 'dev', 'develop')");
        jdbcTemplate.execute("INSERT INTO properties VALUES (4, 'name', 'test-pro-develop', 'service-producer', 'pro', 'develop')");
    }
}
