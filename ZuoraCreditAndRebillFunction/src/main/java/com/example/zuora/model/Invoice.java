package com.example.zuora.model;

import java.time.LocalDate;

/**
 * Zuora 請求書。
 * serviceStartDate / serviceEndDate は Invoice Item レベルから集約した値。
 */
public class Invoice {

    private String id;
    private String invoiceNumber;
    private String status;
    private LocalDate invoiceDate;
    private LocalDate serviceStartDate;
    private LocalDate serviceEndDate;
    private double amount;

    public String getId() { return id; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public String getStatus() { return status; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public LocalDate getServiceStartDate() { return serviceStartDate; }
    public LocalDate getServiceEndDate() { return serviceEndDate; }
    public double getAmount() { return amount; }

    public void setId(String id) { this.id = id; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public void setStatus(String status) { this.status = status; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }
    public void setServiceStartDate(LocalDate serviceStartDate) { this.serviceStartDate = serviceStartDate; }
    public void setServiceEndDate(LocalDate serviceEndDate) { this.serviceEndDate = serviceEndDate; }
    public void setAmount(double amount) { this.amount = amount; }

    /**
     * 指定日がこの請求書のサービス期間に含まれるか判定する。
     */
    public boolean coversDate(LocalDate date) {
        if (serviceStartDate == null || serviceEndDate == null) return false;
        return !date.isBefore(serviceStartDate) && !date.isAfter(serviceEndDate);
    }
}
