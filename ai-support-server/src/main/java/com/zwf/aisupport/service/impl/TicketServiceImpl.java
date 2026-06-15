package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.dto.TicketCreateDTO;
import com.zwf.aisupport.entity.Ticket;
import com.zwf.aisupport.mapper.TicketMapper;
import com.zwf.aisupport.service.TicketService;
import com.zwf.aisupport.vo.TicketVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TicketServiceImpl implements TicketService {

    private final TicketMapper ticketMapper;

    public TicketServiceImpl(TicketMapper ticketMapper) {
        this.ticketMapper = ticketMapper;
    }

    @Override
    public void create(TicketCreateDTO ticketCreateDTO) {
        Long currentUserId = CurrentUserUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("User is not authenticated");
        }

        Ticket ticket = new Ticket();
        ticket.setTitle(ticketCreateDTO.getTitle());
        ticket.setDescription(ticketCreateDTO.getDescription());
        ticket.setStatus("OPEN");
        ticket.setPriority(resolvePriority(ticketCreateDTO.getPriority()));
        ticket.setCreatedBy(currentUserId);

        ticketMapper.insert(ticket);
    }

    private String resolvePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return "MEDIUM";
        }

        // Normalize priority values so downstream filtering can rely on consistent data.
        return priority.trim().toUpperCase();
    }
    @Override
    public List<TicketVO> listMyTickets() {
        Long currentUserId = CurrentUserUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("User is not authenticated");
        }

        return ticketMapper.findByCreatedBy(currentUserId)
                .stream()
                .map(this::convertToTicketVO)
                .toList();
    }

    private TicketVO convertToTicketVO(Ticket ticket) {
        TicketVO ticketVO = new TicketVO();
        ticketVO.setId(ticket.getId());
        ticketVO.setTitle(ticket.getTitle());
        ticketVO.setDescription(ticket.getDescription());
        ticketVO.setStatus(ticket.getStatus());
        ticketVO.setPriority(ticket.getPriority());
        ticketVO.setCreatedBy(ticket.getCreatedBy());
        ticketVO.setAssignedTo(ticket.getAssignedTo());
        ticketVO.setCreatedTime(ticket.getCreatedTime());
        ticketVO.setUpdatedTime(ticket.getUpdatedTime());
        return ticketVO;
    }
    @Override
    public TicketVO getDetail(Long id) {
        Long currentUserId = CurrentUserUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("User is not authenticated");
        }

        Ticket ticket = ticketMapper.findById(id);
        if (ticket == null || !currentUserId.equals(ticket.getCreatedBy())) {
            throw new BusinessException("Ticket not found");
        }

        // Return only tickets owned by the current user.
        return convertToTicketVO(ticket);
    }
}