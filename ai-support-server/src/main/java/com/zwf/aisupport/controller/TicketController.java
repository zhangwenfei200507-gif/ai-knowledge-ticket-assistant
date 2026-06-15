package com.zwf.aisupport.controller;

import com.zwf.aisupport.common.result.Result;
import com.zwf.aisupport.dto.TicketAssignDTO;
import com.zwf.aisupport.dto.TicketCreateDTO;
import com.zwf.aisupport.dto.TicketStatusUpdateDTO;
import com.zwf.aisupport.service.TicketService;
import com.zwf.aisupport.vo.TicketVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody TicketCreateDTO ticketCreateDTO) {
        ticketService.create(ticketCreateDTO);
        return Result.success();
    }
    @GetMapping
    public Result<List<TicketVO>> listAllTickets() {
        List<TicketVO> tickets = ticketService.listAllTickets();
        return Result.success(tickets);
    }
    @GetMapping("/assigned-to-me")
    public Result<List<TicketVO>> listAssignedToMe() {
        List<TicketVO> tickets = ticketService.listAssignedToMe();
        return Result.success(tickets);
    }
    @GetMapping("/my")
    public Result<List<TicketVO>> listMyTickets() {
        List<TicketVO> tickets = ticketService.listMyTickets();
        return Result.success(tickets);
    }
    @GetMapping("/{id}")
    public Result<TicketVO> getDetail(@PathVariable Long id) {
        TicketVO ticket = ticketService.getDetail(id);
        return Result.success(ticket);
    }
    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody TicketStatusUpdateDTO ticketStatusUpdateDTO) {
        ticketService.updateStatus(id, ticketStatusUpdateDTO);
        return Result.success();
    }
    @PatchMapping("/{id}/resolve")
    public Result<Void> resolveTicket(@PathVariable Long id) {
        ticketService.resolveTicket(id);
        return Result.success();
    }
    @PatchMapping("/{id}/assign")
    public Result<Void> assignTicket(@PathVariable Long id,
                                     @Valid @RequestBody TicketAssignDTO ticketAssignDTO) {
        ticketService.assignTicket(id, ticketAssignDTO);
        return Result.success();
    }
}