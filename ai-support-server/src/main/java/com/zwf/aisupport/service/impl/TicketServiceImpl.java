package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.common.util.PageRequestUtil;
import com.zwf.aisupport.dto.TicketAssignDTO;
import com.zwf.aisupport.dto.TicketCreateDTO;
import com.zwf.aisupport.dto.TicketStatusUpdateDTO;
import com.zwf.aisupport.entity.Ticket;
import com.zwf.aisupport.mapper.TicketMapper;
import com.zwf.aisupport.mapper.UserMapper;
import com.zwf.aisupport.service.TicketService;
import com.zwf.aisupport.vo.PageVO;
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
        Long currentUserId = CurrentUserUtil.requireCurrentUserId();

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
        Long currentUserId = CurrentUserUtil.requireCurrentUserId();

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
        Ticket ticket = requireVisibleTicket(id, CurrentUserUtil.requireCurrentUserId());

        // Admins can inspect any ticket; users can inspect tickets they created or own.
        return convertToTicketVO(ticket);
    }

    @Override
    public void updateStatus(Long id, TicketStatusUpdateDTO ticketStatusUpdateDTO) {
        CurrentUserUtil.requireAdmin("Only administrators can update ticket status directly");

        String status = ticketStatusUpdateDTO.getStatus().trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new BusinessException("Invalid ticket status");
        }

        // Users follow the dedicated resolve/close actions to preserve ticket lifecycle rules.
        ticketMapper.updateStatus(id, status);
    }
    @Override
    public PageVO<TicketVO> listAllTickets(String keyword, String status, String priority, Integer page, Integer size) {
        CurrentUserUtil.requireAdmin("Only administrators can access all tickets");

        int safePage = PageRequestUtil.page(page);
        int safeSize = PageRequestUtil.size(size);
        List<TicketVO> items = ticketMapper.findPage(
                        PageRequestUtil.optionalText(keyword),
                        PageRequestUtil.optionalText(status),
                        PageRequestUtil.optionalText(priority),
                        PageRequestUtil.offset(safePage, safeSize),
                        safeSize
                )
                .stream()
                .map(this::convertToTicketVO)
                .toList();
        long total = ticketMapper.countPage(
                PageRequestUtil.optionalText(keyword),
                PageRequestUtil.optionalText(status),
                PageRequestUtil.optionalText(priority)
        );
        return PageVO.of(items, safePage, safeSize, total);
    }
    @Override
    public void assignTicket(Long id, TicketAssignDTO ticketAssignDTO) {
        CurrentUserUtil.requireAdmin("Only administrators can assign tickets");

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
    @Override
    public List<TicketVO> listAssignedToMe() {
        Long currentUserId = CurrentUserUtil.requireCurrentUserId();

        // Use assigned_to so support users can focus on tickets assigned to them.
        return ticketMapper.findByAssignedTo(currentUserId)
                .stream()
                .map(this::convertToTicketVO)
                .toList();
    }
    @Override
    public void resolveTicket(Long id) {
        Long currentUserId = CurrentUserUtil.requireCurrentUserId();

        Ticket ticket = ticketMapper.findById(id);
        if (ticket == null) {
            throw new BusinessException("Ticket not found");
        }

        if (!CurrentUserUtil.isAdmin() && !currentUserId.equals(ticket.getAssignedTo())) {
            throw new BusinessException("Only the assigned user can resolve this ticket");
        }

        if (!"IN_PROGRESS".equals(ticket.getStatus()) && !"OPEN".equals(ticket.getStatus())) {
            throw new BusinessException("Only open or in-progress tickets can be resolved");
        }

        // The assigned support user marks the ticket as resolved after handling it.
        ticketMapper.resolveTicket(id);
    }
    @Override
    public void closeTicket(Long id) {
        Long currentUserId = CurrentUserUtil.requireCurrentUserId();

        Ticket ticket = ticketMapper.findById(id);
        if (ticket == null) {
            throw new BusinessException("Ticket not found");
        }

        if (!CurrentUserUtil.isAdmin() && !currentUserId.equals(ticket.getCreatedBy())) {
            throw new BusinessException("Only the creator can close this ticket");
        }

        if (!"RESOLVED".equals(ticket.getStatus())) {
            throw new BusinessException("Only resolved tickets can be closed");
        }

        // The creator confirms the issue is resolved and closes the ticket.
        ticketMapper.closeTicket(id);
    }

    private Ticket requireVisibleTicket(Long id, Long currentUserId) {
        Ticket ticket = ticketMapper.findById(id);
        if (ticket == null || (!CurrentUserUtil.isAdmin()
                && !currentUserId.equals(ticket.getCreatedBy())
                && !currentUserId.equals(ticket.getAssignedTo()))) {
            throw new BusinessException("Ticket not found");
        }
        return ticket;
    }
}
