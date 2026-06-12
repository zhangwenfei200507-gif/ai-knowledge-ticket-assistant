package com.zwf.aisupport.controller;

import com.zwf.aisupport.common.result.Result;
import com.zwf.aisupport.dto.TicketCreateDTO;
import com.zwf.aisupport.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}