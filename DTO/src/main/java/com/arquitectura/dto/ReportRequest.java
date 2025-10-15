package com.arquitectura.dto;

public class ReportRequest {

    private String reportType;

    public ReportRequest() {
    }

    public ReportRequest(String reportType) {
        this.reportType = reportType;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }
}
