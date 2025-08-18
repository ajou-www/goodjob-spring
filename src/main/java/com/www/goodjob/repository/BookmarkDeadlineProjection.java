package com.www.goodjob.repository;

import java.time.LocalDate;

public interface BookmarkDeadlineProjection {
    Long getUserId();
    Long getJobId();
    LocalDate getApplyEndDate();
    String getTitle();
    String getCompanyName();
}
