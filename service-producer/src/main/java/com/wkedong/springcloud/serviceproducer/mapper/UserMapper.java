package com.wkedong.springcloud.serviceproducer.mapper;

import com.wkedong.springcloud.serviceproducer.entity.UserEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * @author wkedong
 * <p>
 * 2019/1/15
 */
public interface UserMapper {

    @Select("SELECT * FROM user")
    List<UserEntity> getAll();

    @Select("SELECT * FROM user WHERE id = #{id}")
    UserEntity getOne(int id);

    @Insert("INSERT INTO user(name,birthday,address) VALUES(#{name}, #{birthday}, #{address})")
    void insert(UserEntity user);

    @Update("UPDATE user SET name=#{name},birthday=#{birthday},address=#{address} WHERE id =#{id}")
    void update(UserEntity user);

    @Delete("DELETE FROM user WHERE id =#{id}")
    void delete(int id);
}
