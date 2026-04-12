package com.example.zuora.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * Zuora 請求書。
 * serviceStartDate / serviceEndDate は Zuora の Invoice オブジェクトレベルのフィールド。
 * Zuora の設定によってはこれらが Invoice Item レベルにある場合があり、
 * その場合は GET /v1/invoices/{id}/items での取得に変更が必要。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Invoice {

    @JsonProperty("id")
    private String id;

    @JsonProperty("invoiceNumber")
    private String invoiceNumber;

    @JsonProperty("status")
    private String status;

    @JsonProperty("invoiceDate")
    private LocalDate invoiceDate;

    @JsonProperty("serviceStartDate")
    private LocalDate serviceStartDate;

    @JsonProperty("serviceEndDate")
    private LocalDate serviceEndDate;

    @JsonProperty("amount")
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
