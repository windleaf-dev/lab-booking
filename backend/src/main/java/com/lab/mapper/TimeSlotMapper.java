package com.lab.mapper;

import com.lab.model.TimeSlot;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TimeSlotMapper {

    @Select("select * from time_slot order by sort_no asc, id asc")
    List<TimeSlot> listAll();

    @Select("select * from time_slot where id = #{id}")
    TimeSlot findById(long id);

    @Select("select count(*) from time_slot")
    long countAll();

    @Options(useGeneratedKeys = true, keyProperty = "id")
    @Insert("insert into time_slot(name, start_time, end_time, sort_no) " +
            "values(#{name}, #{startTime}, #{endTime}, #{sortNo})")
    int insert(TimeSlot slot);
}
