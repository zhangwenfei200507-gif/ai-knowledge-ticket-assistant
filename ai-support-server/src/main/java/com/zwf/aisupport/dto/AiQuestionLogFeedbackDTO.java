package com.zwf.aisupport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AiQuestionLogFeedbackDTO {

    @NotBlank(message = "Feedback cannot be blank")
    @Pattern(regexp = "USEFUL|NOT_USEFUL", message = "Feedback must be USEFUL or NOT_USEFUL")
    private String feedback;

    private String feedbackComment;
}