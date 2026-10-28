package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.dto.TicketStatusUpdateDTO;
import com.zwf.aisupport.mapper.TicketMapper;
import com.zwf.aisupport.mapper.UserMapper;
import com.zwf.aisupport.security.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TicketServiceImplTest {

    private final TicketMapper ticketMapper = mock(TicketMapper.class);
    private final TicketServiceImpl service = new TicketServiceImpl(ticketMapper, mock(UserMapper.class));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void genericStatusMutationRequiresAdministrator() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CurrentUser(7L, "alice", "USER"), null)
        );
        TicketStatusUpdateDTO request = new TicketStatusUpdateDTO();
        request.setStatus("CLOSED");

        assertThatThrownBy(() -> service.updateStatus(21L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only administrators can update ticket status directly");
        verify(ticketMapper, never()).updateStatus(21L, "CLOSED");
    }
}
