package com.lab.mapper;

import com.lab.model.Device;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DeviceMapper {

    String SELECT_BASE = """
            select d.*, c.name as category_name
            from device d left join device_category c on d.category_id = c.id
            """;

    String WHERE_CLAUSE = """
            <where>
              <if test="keyword != null and keyword != ''">
                and (d.name like concat('%', #{keyword}, '%')
                     or d.code like concat('%', #{keyword}, '%')
                     or d.model like concat('%', #{keyword}, '%')
                     or d.location like concat('%', #{keyword}, '%'))
              </if>
              <if test="categoryId != null"> and d.category_id = #{categoryId} </if>
              <if test="status != null and status != ''"> and d.status = #{status} </if>
            </where>
            """;

    @Select("<script>" + SELECT_BASE + WHERE_CLAUSE + " order by d.id desc limit #{offset}, #{size} </script>")
    List<Device> page(@Param("keyword") String keyword,
                      @Param("categoryId") Long categoryId,
                      @Param("status") String status,
                      @Param("offset") int offset,
                      @Param("size") int size);

    @Select("<script> select count(*) from device d " + WHERE_CLAUSE + " </script>")
    long count(@Param("keyword") String keyword,
               @Param("categoryId") Long categoryId,
               @Param("status") String status);

    @Select(SELECT_BASE + " where d.id = #{id}")
    Device findById(long id);

    @Select(SELECT_BASE + " where d.code = #{code}")
    Device findByCode(String code);

    @Select("select count(*) from device where code = #{code} and id != #{excludeId}")
    int countByCode(@Param("code") String code, @Param("excludeId") long excludeId);

    @Select("select count(*) from device where category_id = #{categoryId}")
    long countByCategory(long categoryId);

    @Select("select count(*) from device")
    long countAll();

    @Select("select count(*) from device where status = #{status}")
    long countByStatus(String status);

    @Options(useGeneratedKeys = true, keyProperty = "id")
    @Insert("""
            insert into device(code, name, category_id, model, location, status, description)
            values(#{code}, #{name}, #{categoryId}, #{model}, #{location}, #{status}, #{description})
            """)
    int insert(Device device);

    @Update("""
            update device set code = #{code}, name = #{name}, category_id = #{categoryId},
              model = #{model}, location = #{location}, status = #{status}, description = #{description}
            where id = #{id}
            """)
    int update(Device device);

    @Delete("delete from device where id = #{id}")
    int deleteById(long id);
}
