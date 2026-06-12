package com.zwf.aisupport.controller;

import com.zwf.aisupport.common.result.Result;
import com.zwf.aisupport.dto.TicketCreateDTO;
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
    @GetMapping("/my")
    public Result<List<TicketVO>> listMyTickets() {
        List<TicketVO> tickets = ticketService.listMyTickets();
        return Result.success(tickets);
    }
}