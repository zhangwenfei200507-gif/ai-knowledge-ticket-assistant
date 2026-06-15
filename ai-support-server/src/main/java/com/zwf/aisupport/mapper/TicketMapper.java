package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.Ticket;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface TicketMapper {

    @Insert("""
        insert into tickets (title, description, status, priority, created_by, assigned_to)
        values (#{title}, #{description}, #{status}, #{priority}, #{createdBy}, #{assignedTo})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Ticket ticket);

    @Select("""
            select * from tickets
            where created_by = #{createdBy}
            order by created_time desc
            """)
    List<Ticket> findByCreatedBy(Long createdBy);

    @Select("select * from tickets where id = #{id}")
    Ticket findById(Long id);

    @Update("""
            update tickets
            set status = #{status}, updated_time = CURRENT_TIMESTAMP
            where id = #{id}
            """)
    void updateStatus(@Param("id") Long id, @Param("status") String status);

    @Select("""
            select * from tickets
            order by created_time desc
            """)
    List<Ticket> findAll();

    @Update("""
        update tickets
        set assigned_to = #{assignedTo},
            status = 'IN_PROGRESS',
            updated_time = CURRENT_TIMESTAMP
        where id = #{id}
        """)
    void assignTicket(@Param("id") Long id, @Param("assignedTo") Long assignedTo);

    @Select("""
        select * from tickets
        where assigned_to = #{assignedTo}
        order by updated_time desc
        """)
    List<Ticket> findByAssignedTo(Long assignedTo);

    @Update("""
        update tickets
        set status = 'RESOLVED',
            updated_time = CURRENT_TIMESTAMP
        where id = #{id}
        """)
    void resolveTicket(Long id);

    @Update("""
        update tickets
        set status = 'CLOSED',
            updated_time = CURRENT_TIMESTAMP
        where id = #{id}
        """)
    void closeTicket(Long id);

    @Select("select count(*) from tickets")
    Long countAll();

    @Select("select count(*) from tickets where status in ('OPEN', 'IN_PROGRESS')")
    Long countOpenTickets();

    @Select("select count(*) from tickets where status in ('RESOLVED', 'CLOSED')")
    Long countResolvedTickets();
}