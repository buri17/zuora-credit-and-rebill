package com.example.zuora.client;

import com.example.zuora.config.ZuoraConfig;
import com.example.zuora.model.Invoice;
import com.zuora.ApiException;
import com.zuora.model.BillingDocumentStatus;
import com.zuora.model.BillingDocumentType;
import com.zuora.model.BillRunFilter;
import com.zuora.model.CreateBillRunRequest;
import com.zuora.model.GetBillingDocumentsResponse;
import com.zuora.model.InvoiceItem;
import com.zuora.model.Order;
import com.zuora.model.ReverseInvoiceRequest;
import com.zuora.model.TriggerDate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ZuoraClient {

    private final com.zuora.ZuoraClient sdk;

    public ZuoraClient(ZuoraConfig config) {
        this.sdk = new com.zuora.ZuoraClient(
                config.getClientId(),
                config.getClientSecret(),
                config.getZuoraEnv()
        );
    }

    /**
     * アカウントの Posted 請求書一覧をサービス期間付きで取得する。
     * サービス期間は Invoice Item レベルから取得し、インボイス全体の min/max に集約する。
     */
    public List<Invoice> getPostedInvoices(String accountId) throws ApiException {
        List<GetBillingDocumentsResponse> docs = sdk.billingDocumentsApi()
                .getBillingDocumentsApi()
                .accountId(accountId)
                .status(BillingDocumentStatus.POSTED)
                .execute()
                .getDocuments();

        if (docs == null) {
            return List.of();
        }

        List<Invoice> invoices = new ArrayList<>();
        for (GetBillingDocumentsResponse doc : docs) {
            if (doc.getDocumentType() != BillingDocumentType.INVOICE) {
                continue;
            }

            List<InvoiceItem> items = sdk.invoicesApi()
                    .getInvoiceItemsApi(doc.getId())
                    .execute()
                    .getInvoiceItems();

            LocalDate startDate = null;
            LocalDate endDate = null;
            if (items != null) {
                for (InvoiceItem item : items) {
                    LocalDate s = item.getServiceStartDate();
                    LocalDate e = item.getServiceEndDate();
                    if (s != null && (startDate == null || s.isBefore(startDate))) startDate = s;
                    if (e != null && (endDate == null || e.isAfter(endDate))) endDate = e;
                }
            }

            Invoice invoice = new Invoice();
            invoice.setId(doc.getId());
            invoice.setInvoiceNumber(doc.getDocumentNumber());
            invoice.setStatus(doc.getStatus() != null ? doc.getStatus().getValue() : null);
            invoice.setInvoiceDate(doc.getDocumentDate());
            invoice.setAmount(doc.getAmount() != null ? doc.getAmount().doubleValue() : 0.0);
            invoice.setServiceStartDate(startDate);
            invoice.setServiceEndDate(endDate);
            invoices.add(invoice);
        }
        return invoices;
    }

    /**
     * オーダーの全 Order Action に含まれる triggerDate を重複なしで返す。
     * 呼び出し元はいずれかの日付がサービス期間に含まれる請求書を補正対象とする。
     */
    public List<LocalDate> getOrderTriggerDates(String orderNumber) throws ApiException {
        Order order = sdk.ordersApi().getOrderApi(orderNumber).execute().getOrder();
        if (order == null || order.getSubscriptions() == null) {
            return List.of();
        }
        return order.getSubscriptions().stream()
                .filter(sub -> sub.getOrderActions() != null)
                .flatMap(sub -> sub.getOrderActions().stream())
                .filter(action -> action.getTriggerDates() != null)
                .flatMap(action -> action.getTriggerDates().stream())
                .map(TriggerDate::getTriggerDate)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * Invoice Reversal を実行する。Credit Memo が自動生成され、元請求書がキャンセルされる。
     */
    public void reverseInvoice(String invoiceId, String date) throws ApiException {
        LocalDate localDate = LocalDate.parse(date);
        ReverseInvoiceRequest request = new ReverseInvoiceRequest()
                .applyEffectiveDate(localDate)
                .memoDate(localDate);
        sdk.invoicesApi().reverseInvoiceApi(invoiceId, request).execute();
    }

    /**
     * Bill Run を作成して再請求をトリガーする。
     *
     * @return 作成された Bill Run の ID
     */
    public String createBillRun(String accountId, String date) throws ApiException {
        LocalDate localDate = LocalDate.parse(date);
        BillRunFilter filter = new BillRunFilter()
                .filterType(BillRunFilter.FilterTypeEnum.ACCOUNT)
                .accountId(accountId);
        CreateBillRunRequest request = new CreateBillRunRequest()
                .addBillRunFiltersItem(filter)
                .invoiceDate(localDate)
                .targetDate(localDate)
                .autoEmail(false)
                .autoPost(true)
                .autoRenewal(false)
                .noEmailForZeroAmountInvoice(true);
        return sdk.billRunApi().createBillRunApi(request).execute().getId();
    }
}
