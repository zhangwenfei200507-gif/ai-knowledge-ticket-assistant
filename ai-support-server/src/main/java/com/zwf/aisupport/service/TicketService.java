package com.zwf.aisupport.service;

import com.zwf.aisupport.dto.TicketAssignDTO;
import com.zwf.aisupport.dto.TicketCreateDTO;
import com.zwf.aisupport.dto.TicketStatusUpdateDTO;
import com.zwf.aisupport.vo.PageVO;
import com.zwf.aisupport.vo.TicketVO;

import java.util.List;

public interface TicketService {

    void create(TicketCreateDTO ticketCreateDTO);

    List<TicketVO> listMyTickets();

    TicketVO getDetail(Long id);

    void updateStatus(Long id, TicketStatusUpdateDTO ticketStatusUpdateDTO);

    PageVO<TicketVO> listAllTickets(String keyword, String status, String priority, Integer page, Integer size);

    void assignTicket(Long id, TicketAssignDTO ticketAssignDTO);

    List<TicketVO> listAssignedToMe();

    void resolveTicket(Long id);

    void closeTicket(Long id);
}
