package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.Ticket;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TicketMapper {

    @Insert("""
            insert into tickets (title, description, status, priority, created_by, assigned_to)
            values (#{title}, #{description}, #{status}, #{priority}, #{createdBy}, #{assignedTo})
            """)
    void insert(Ticket ticket);
    @Select("""
        select * from tickets
        where created_by = #{createdBy}
        order by created_time desc
        """)
    List<Ticket> findByCreatedBy(Long createdBy);

    @Select("select * from tickets where id = #{id}")
    Ticket findById(Long id);
}