package com.lab.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface StatsMapper {

    @Select("select count(*) from booking")
    long countBookings();

    @Select("select count(*) from booking where device_id = #{deviceId}")
    long countBookingsByDevice(long deviceId);

    @Select("select count(*) from booking where status = #{status}")
    long countByStatus(String status);

    @Select("""
            select count(*) from booking
            where booking_date = curdate() and status not in ('CANCELLED', 'REJECTED')
            """)
    long countTodayBookings();

    @Select("""
            select c.name as name, count(d.id) as value
            from device_category c
            left join device d on d.category_id = c.id
            group by c.id, c.name
            order by c.id asc
            """)
    List<Map<String, Object>> deviceByCategory();

    @Select("""
            select status as name, count(*) as value
            from booking
            group by status
            """)
    List<Map<String, Object>> statusDistribution();

    @Select("""
            select booking_date as date, count(*) as value
            from booking
            where booking_date >= #{start} and status not in ('CANCELLED', 'REJECTED')
            group by booking_date
            order by booking_date asc
            """)
    List<Map<String, Object>> bookingTrend(@Param("start") LocalDate start);

    @Select("""
            select d.name as name, count(b.id) as value
            from booking b
            join device d on d.id = b.device_id
            where b.status not in ('CANCELLED', 'REJECTED')
            group by d.id, d.name
            order by value desc, d.id asc
            limit 5
            """)
    List<Map<String, Object>> topDevices();
}
