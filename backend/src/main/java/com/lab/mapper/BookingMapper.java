package com.lab.mapper;

import com.lab.model.Booking;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Mapper
public interface BookingMapper {

    String SELECT_BASE = """
            select b.*, d.name as device_name, d.code as device_code,
                   s.name as slot_name, s.start_time, s.end_time,
                   u.username as user_name, u.real_name,
                   a.real_name as approver_name
            from booking b
            join device d on d.id = b.device_id
            join time_slot s on s.id = b.slot_id
            join sys_user u on u.id = b.user_id
            left join sys_user a on a.id = b.approver_id
            """;

    @Options(useGeneratedKeys = true, keyProperty = "id")
    @Insert("""
            insert into booking(user_id, device_id, slot_id, booking_date, purpose, status, active_key)
            values(#{userId}, #{deviceId}, #{slotId}, #{bookingDate}, #{purpose}, #{status}, #{activeKey})
            """)
    int insert(Booking booking);

    @Select(SELECT_BASE + " where b.id = #{id}")
    Booking findById(long id);

    /* ---------------- 我的预约 ---------------- */

    @Select("""
            <script>
            select count(*) from booking b
            join device d on d.id = b.device_id
            join sys_user u on u.id = b.user_id
            where b.user_id = #{userId}
            <if test="status != null and status != ''"> and b.status = #{status} </if>
            </script>
            """)
    long countMine(@Param("userId") long userId, @Param("status") String status);

    @Select("""
            <script>
            """ + SELECT_BASE + """
            where b.user_id = #{userId}
            <if test="status != null and status != ''"> and b.status = #{status} </if>
            order by b.booking_date desc, s.start_time desc
            limit #{offset}, #{size}
            </script>
            """)
    List<Booking> pageMine(@Param("userId") long userId,
                           @Param("status") String status,
                           @Param("offset") int offset,
                           @Param("size") int size);

    /* ---------------- 全部预约（审批 / 管理） ---------------- */

    String FILTER_WHERE = """
            <where>
              <if test="status != null and status != ''"> and b.status = #{status} </if>
              <if test="date != null"> and b.booking_date = #{date} </if>
              <if test="keyword != null and keyword != ''">
                and (d.name like concat('%', #{keyword}, '%')
                     or d.code like concat('%', #{keyword}, '%')
                     or u.real_name like concat('%', #{keyword}, '%')
                     or u.username like concat('%', #{keyword}, '%'))
              </if>
            </where>
            """;

    @Select("<script> select count(*) from booking b " +
            "join device d on d.id = b.device_id join sys_user u on u.id = b.user_id " +
            FILTER_WHERE + " </script>")
    long countAll(@Param("status") String status,
                  @Param("keyword") String keyword,
                  @Param("date") LocalDate date);

    @Select("<script>" + SELECT_BASE + FILTER_WHERE +
            " order by b.booking_date desc, s.start_time asc limit #{offset}, #{size} </script>")
    List<Booking> pageAll(@Param("status") String status,
                          @Param("keyword") String keyword,
                          @Param("date") LocalDate date,
                          @Param("offset") int offset,
                          @Param("size") int size);

    @Select("""
            select b.*, s.start_time, s.end_time
            from booking b join time_slot s on s.id = b.slot_id
            where b.status = 'PENDING'
              and (b.booking_date < #{today}
                   or (b.booking_date = #{today} and s.start_time <= #{now}))
            """)
    List<Booking> findExpiredPending(@Param("today") LocalDate today, @Param("now") LocalTime now);

    /* ---------------- 某天的预约（设备 × 时段矩阵） ---------------- */

    @Select(SELECT_BASE + """
            where b.booking_date = #{date}
              and b.status in ('PENDING', 'APPROVED', 'COMPLETED', 'VIOLATED')
            order by d.id asc, s.sort_no asc
            """)
    List<Booking> listByDate(LocalDate date);

    /* ---------------- 业务校验 ---------------- */

    @Select("""
            select count(*) from booking
            where user_id = #{userId} and booking_date = #{date} and slot_id = #{slotId}
              and status in ('PENDING', 'APPROVED')
            """)
    int countUserSlot(@Param("userId") long userId,
                      @Param("date") LocalDate date,
                      @Param("slotId") long slotId);

    @Select("""
            select b.*, s.start_time, s.end_time
            from booking b join time_slot s on s.id = b.slot_id
            where b.status = 'APPROVED'
              and (b.booking_date < #{today}
                   or (b.booking_date = #{today} and s.end_time <= #{now}))
            """)
    List<Booking> findFinishedApproved(@Param("today") LocalDate today, @Param("now") LocalTime now);

    @Select("select count(*) from booking")
    long countAllBookings();

    /* ---------------- 状态流转 ---------------- */

    @Update("""
            update booking
            set status = 'APPROVED', approver_id = #{approverId},
                approve_remark = #{remark}, approved_at = now()
            where id = #{id} and status = 'PENDING'
            """)
    int approve(@Param("id") long id,
                @Param("approverId") long approverId,
                @Param("remark") String remark);

    @Update("""
            update booking
            set status = 'REJECTED', approver_id = #{approverId}, approve_remark = #{remark},
                approved_at = now(), active_key = null
            where id = #{id} and status = 'PENDING'
            """)
    int reject(@Param("id") long id,
               @Param("approverId") long approverId,
               @Param("remark") String remark);

    @Update("""
            update booking set status = 'CANCELLED', active_key = null
            where id = #{id} and user_id = #{userId} and status in ('PENDING', 'APPROVED')
            """)
    int cancel(@Param("id") long id, @Param("userId") long userId);

    @Update("""
            update booking set checkin_at = now()
            where id = #{id} and user_id = #{userId} and status = 'APPROVED' and checkin_at is null
            """)
    int checkin(@Param("id") long id, @Param("userId") long userId);

    @Update("""
            update booking set status = #{status}, active_key = null, approve_remark = #{remark}
            where id = #{id}
            """)
    int finishBySystem(@Param("id") long id,
                       @Param("status") String status,
                       @Param("remark") String remark);
}
