package com.www.goodjob.repository;

import java.time.LocalDate;

public interface ApplicationDueProjection {
    Long getUserId();
    Long getJobId();
    LocalDate getApplyEndDate();  // alias = applications.apply_due_date
    String getTitle();            // jobs.title  (확실하지 않음)
    String getCompanyName();      // jobs.company_name (확실하지 않음)
}