
package com.zwf.aisupport.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TicketAssignDTO {

    @NotNull(message = "Assigned user id cannot be null")
    private Long assignedTo;
}