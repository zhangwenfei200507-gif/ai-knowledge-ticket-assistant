package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.Ticket;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TicketMapper {

    @Insert("""
            insert into tickets (title, description, status, priority, created_by, assigned_to)
            values (#{title}, #{description}, #{status}, #{priority}, #{createdBy}, #{assignedTo})
            """)
    void insert(Ticket ticket);
}