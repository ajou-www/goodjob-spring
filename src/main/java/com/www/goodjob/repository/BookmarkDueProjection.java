package com.www.goodjob.repository;

import java.time.LocalDate;

public interface BookmarkDueProjection {
    Long getUserId();
    Long getJobId();
    LocalDate getApplyDueDate();
}
