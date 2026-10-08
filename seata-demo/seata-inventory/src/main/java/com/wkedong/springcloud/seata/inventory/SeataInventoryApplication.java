package com.wkedong.springcloud.seata.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Seata 库存服务：分支事务参与方（端口 8260）。
 *
 * @author wkedong
 */
@SpringBootApplication
public class SeataInventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeataInventoryApplication.class, args);
    }
}
