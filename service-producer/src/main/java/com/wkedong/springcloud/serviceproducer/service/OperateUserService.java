package com.wkedong.springcloud.serviceproducer.service;

import com.wkedong.springcloud.serviceproducer.entity.UserEntity;

import java.util.List;

/**
 * @author wkedong
 * 操作user表
 * Create in 2019/2/27 9:59
 * Update in 2019/2/27 9:59
 */
public interface OperateUserService {
    UserEntity getOne(int id);

    List<UserEntity> getAll();

    boolean insertData(UserEntity user);

    boolean updateData(UserEntity user);

    boolean deleteData(int id);
}
