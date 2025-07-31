package com.www.goodjob.dto;

import com.www.goodjob.enums.ApplicationStatus;
import lombok.*;

import java.time.LocalDate;

@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationUpdateRequest {
    private ApplicationStatus applyStatus;
    private String note;
    private LocalDate applyDueDate;
}