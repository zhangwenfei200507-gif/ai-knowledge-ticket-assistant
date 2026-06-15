package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.dto.TicketAssignDTO;
import com.zwf.aisupport.dto.TicketCreateDTO;
import com.zwf.aisupport.dto.TicketStatusUpdateDTO;
import com.zwf.aisupport.entity.Ticket;
import com.zwf.aisupport.mapper.TicketMapper;
import com.zwf.aisupport.mapper.UserMapper;
import com.zwf.aisupport.service.TicketService;
import com.zwf.aisupport.vo.TicketVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class TicketServiceImpl implements TicketService {

    private final TicketMapper ticketMapper;
    private static final Set<String> ALLOWED_STATUSES = Set.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED");
    private final UserMapper userMapper;

    public TicketServiceImpl(TicketMapper ticketMapper, UserMapper userMapper) {
        this.ticketMapper = ticketMapper;
        this.userMapper = userMapper;
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
    @Override
    public void updateStatus(Long id, TicketStatusUpdateDTO ticketStatusUpdateDTO) {
        Long currentUserId = CurrentUserUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("User is not authenticated");
        }

        Ticket ticket = ticketMapper.findById(id);
        if (ticket == null || !currentUserId.equals(ticket.getCreatedBy())) {
            throw new BusinessException("Ticket not found");
        }

        String status = ticketStatusUpdateDTO.getStatus().trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new BusinessException("Invalid ticket status");
        }

        // Only the owner can change status in the current learning-stage workflow.
        ticketMapper.updateStatus(id, status);
    }
    @Override
    public List<TicketVO> listAllTickets() {
        String role = CurrentUserUtil.getCurrentUserRole();
        if (!"ADMIN".equals(role)) {
            throw new BusinessException("Only administrators can access all tickets");
        }

        // Admin users can review all tickets for support management.
        return ticketMapper.findAll()
                .stream()
                .map(this::convertToTicketVO)
                .toList();
    }
    @Override
    public void assignTicket(Long id, TicketAssignDTO ticketAssignDTO) {
        String role = CurrentUserUtil.getCurrentUserRole();
        if (!"ADMIN".equals(role)) {
            throw new BusinessException("Only administrators can assign tickets");
        }

        Ticket ticket = ticketMapper.findById(id);
        if (ticket == null) {
            throw new BusinessException("Ticket not found");
        }

        if (userMapper.findById(ticketAssignDTO.getAssignedTo()) == null) {
            throw new BusinessException("Assigned user not found");
        }

        // Assigning a ticket moves it into active processing.
        ticketMapper.assignTicket(id, ticketAssignDTO.getAssignedTo());
    }
}