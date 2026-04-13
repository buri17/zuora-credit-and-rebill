package com.example.zuora.service;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.example.zuora.client.ZuoraClient;
import com.example.zuora.model.Invoice;
import com.example.zuora.model.OrderEvent;

import java.time.LocalDate;
import java.util.List;

public class InvoiceCorrectionService {

    private final ZuoraClient zuoraClient;
    private final LambdaLogger logger;

    public InvoiceCorrectionService(ZuoraClient zuoraClient, LambdaLogger logger) {
        this.zuoraClient = zuoraClient;
        this.logger = logger;
    }

    /**
     * Order.Completed イベントを受け取り、影響を受ける請求書を補正する。
     * 1. オーダーの全 Order Action から triggerDate 一覧を取得
     * 2. いずれかの triggerDate がサービス期間に含まれる Posted 請求書を特定
     * 3. 各請求書に Invoice Reversal を実行（Credit Memo 自動生成 + 元請求書キャンセル）
     *    ※ 非トランザクショナル操作。途中失敗時は例外を即伝搬し Bill Run は作成しない。
     * 4. Bill Run で再請求
     */
    public void correct(OrderEvent event) throws Exception {
        String accountId = event.getAccountId();
        String orderNumber = event.getOrderNumber();

        logger.log("Processing Order.Completed: orderId=" + event.getId()
                + ", orderNumber=" + orderNumber
                + ", accountId=" + accountId);

        List<LocalDate> triggerDates = zuoraClient.getOrderTriggerDates(orderNumber);

        if (triggerDates.isEmpty()) {
            logger.log("No trigger dates found for orderNumber=" + orderNumber + ", skipping");
            return;
        }

        logger.log("Trigger dates: " + triggerDates);

        List<Invoice> allInvoices = zuoraClient.getPostedInvoices(accountId);
        List<Invoice> affected = allInvoices.stream()
                .filter(inv -> triggerDates.stream().anyMatch(inv::coversDate))
                .toList();

        if (affected.isEmpty()) {
            logger.log("No affected invoices found for accountId=" + accountId);
            return;
        }

        logger.log("Found " + affected.size() + " affected invoice(s) for accountId=" + accountId);

        // Bill Run の invoiceDate/targetDate には triggerDate の最小値を使用する。
        // 最小の変更発効日から再請求することで、その後のすべての期間をカバーできる。
        LocalDate billRunDate = triggerDates.stream().min(LocalDate::compareTo).orElseThrow();

        for (Invoice invoice : affected) {
            logger.log("Reversing invoice: invoiceNumber=" + invoice.getInvoiceNumber()
                    + ", invoiceId=" + invoice.getId()
                    + ", serviceStartDate=" + invoice.getServiceStartDate()
                    + ", serviceEndDate=" + invoice.getServiceEndDate());
            zuoraClient.reverseInvoice(invoice.getId(), billRunDate);
            logger.log("Reversed: " + invoice.getInvoiceNumber());
        }

        logger.log("Creating bill run: accountId=" + accountId + ", date=" + billRunDate);
        String billRunId = zuoraClient.createBillRun(accountId, billRunDate);
        logger.log("Bill run created: billRunId=" + billRunId);

        logger.log("Correction completed for accountId=" + accountId);
    }
}
