package com.votewithblock.gateway.dto;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class PublicDashboardDto {
    private String electionTitle;
    private String electionCode;
    private String status;
    private double turnoutPercentage;
    private long totalVotesCast;
    private long registeredVoters;
    private Long countdownSeconds;
}
