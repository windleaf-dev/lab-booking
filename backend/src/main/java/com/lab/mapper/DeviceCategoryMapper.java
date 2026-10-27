package com.lab.mapper;

import com.lab.model.DeviceCategory;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DeviceCategoryMapper {

    @Select("""
            select c.*, (select count(*) from device d where d.category_id = c.id) as device_count
            from device_category c
            order by c.id asc
            """)
    List<DeviceCategory> listAll();

    @Select("""
            select c.*, (select count(*) from device d where d.category_id = c.id) as device_count
            from device_category c
            where c.id = #{id}
            """)
    DeviceCategory findById(long id);

    @Select("select count(*) from device_category where code = #{code} and id != #{excludeId}")
    int countByCode(@Param("code") String code, @Param("excludeId") long excludeId);

    @Select("select count(*) from device_category where name = #{name} and id != #{excludeId}")
    int countByName(@Param("name") String name, @Param("excludeId") long excludeId);

    @Select("select count(*) from device_category")
    long countAll();

    @Options(useGeneratedKeys = true, keyProperty = "id")
    @Insert("insert into device_category(code, name, remark) values(#{code}, #{name}, #{remark})")
    int insert(DeviceCategory category);

    @Update("update device_category set code = #{code}, name = #{name}, remark = #{remark} where id = #{id}")
    int update(DeviceCategory category);

    @Delete("delete from device_category where id = #{id}")
    int deleteById(long id);
}
