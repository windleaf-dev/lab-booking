package com.lab.mapper;

import com.lab.model.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserMapper {

    @Select("select * from sys_user where username = #{username} limit 1")
    User findByUsername(String username);

    @Select("select id, username, real_name, role, status, created_at from sys_user where id = #{id}")
    User findById(long id);

    @Select("select count(*) from sys_user")
    long countAll();

    @Select("""
            <script>
            select id, username, real_name, role, status, created_at from sys_user
            <where>
              <if test="keyword != null and keyword != ''">
                and (username like concat('%', #{keyword}, '%')
                     or real_name like concat('%', #{keyword}, '%'))
              </if>
              <if test="role != null and role != ''"> and role = #{role} </if>
            </where>
            order by id asc
            limit #{offset}, #{size}
            </script>
            """)
    List<User> page(@Param("keyword") String keyword,
                    @Param("role") String role,
                    @Param("offset") int offset,
                    @Param("size") int size);

    @Select("""
            <script>
            select count(*) from sys_user
            <where>
              <if test="keyword != null and keyword != ''">
                and (username like concat('%', #{keyword}, '%')
                     or real_name like concat('%', #{keyword}, '%'))
              </if>
              <if test="role != null and role != ''"> and role = #{role} </if>
            </where>
            </script>
            """)
    long count(@Param("keyword") String keyword, @Param("role") String role);

    @Select("select count(*) from sys_user where username = #{username} and id != #{excludeId}")
    int countByUsername(@Param("username") String username, @Param("excludeId") long excludeId);

    @Options(useGeneratedKeys = true, keyProperty = "id")
    @Insert("insert into sys_user(username, password, real_name, role, status) " +
            "values(#{username}, #{password}, #{realName}, #{role}, #{status})")
    int insert(User user);

    @Update("update sys_user set real_name = #{realName}, role = #{role}, status = #{status} where id = #{id}")
    int update(User user);

    @Update("update sys_user set password = #{password} where id = #{id}")
    int updatePassword(@Param("id") long id, @Param("password") String password);
}
