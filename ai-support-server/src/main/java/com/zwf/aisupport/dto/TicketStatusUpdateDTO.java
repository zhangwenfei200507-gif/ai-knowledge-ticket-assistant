package com.zwf.aisupport.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TicketStatusUpdateDTO {

    @NotBlank(message = "Status cannot be blank")
    private String status;
}