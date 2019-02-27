package com.wkedong.springcloud.serviceproducer.service.impl;

import com.wkedong.springcloud.serviceproducer.entity.UserEntity;
import com.wkedong.springcloud.serviceproducer.mapper.UserMapper;
import com.wkedong.springcloud.serviceproducer.service.OperateUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author wkedong
 * <p>
 * Create in 2019/2/27 10:03
 * Update in 2019/2/27 10:03
 */
@Service
public class OperateUserServiceImpl implements OperateUserService {

    private Logger logger = LoggerFactory.getLogger(OperateUserServiceImpl.class);

    @Autowired
    private UserMapper userMapper;

    @Override
    public UserEntity getOne(int id) {
        return userMapper.getOne(id);
    }

    @Override
    public List<UserEntity> getAll() {
        return userMapper.getAll();
    }

    @Override
    public boolean insertData(UserEntity user) {
        return userMapper.insert(user);
    }

    @Override
    public boolean updateData(UserEntity user) {
        return userMapper.update(user);
    }

    @Override
    public boolean deleteData(int id) {
        return userMapper.delete(id);
    }
}
