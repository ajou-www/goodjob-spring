package com.www.goodjob.repository;

public interface RecommendScoreProjection {
    Long getUserId();
    Long getJobId();
    Double getScore();
    String getTitle();
    String getCompanyName();

    Long getCvId();
}
