package com.example.zuora.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * Zuora Order.Completed callout ペイロード。
 * フィールド名は Zuora の callout 設定に合わせて調整すること。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEvent {

    @JsonProperty("Id")
    private String id;

    @JsonProperty("OrderNumber")
    private String orderNumber;

    @JsonProperty("OrderDate")
    private LocalDate orderDate;

    @JsonProperty("AccountId")
    private String accountId;

    @JsonProperty("AccountNumber")
    private String accountNumber;

    public String getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public LocalDate getOrderDate() { return orderDate; }
    public String getAccountId() { return accountId; }
    public String getAccountNumber() { return accountNumber; }

    public void setId(String id) { this.id = id; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public void setOrderDate(LocalDate orderDate) { this.orderDate = orderDate; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
}
