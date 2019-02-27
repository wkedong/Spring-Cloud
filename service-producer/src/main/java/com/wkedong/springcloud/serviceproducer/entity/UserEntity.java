package com.wkedong.springcloud.serviceproducer.entity;

import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

/**
 * @author wkedong
 * 测试实体
 * 2019/1/15
 */
@RefreshScope
@Component//加入到Spring容器
public class UserEntity {

    private String id;
    private String name;
    @DateTimeFormat(style = "yyyy-MM-dd")
    private String birthday;
    private String address;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBirthday() {
        return birthday;
    }

    public void setBirthday(String birthday) {
        this.birthday = birthday;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
