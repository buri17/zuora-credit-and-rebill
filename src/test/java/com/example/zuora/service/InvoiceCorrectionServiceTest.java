package com.example.zuora.service;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.example.zuora.client.ZuoraClient;
import com.example.zuora.model.Invoice;
import com.example.zuora.model.OrderEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceCorrectionServiceTest {

    @Mock ZuoraClient zuoraClient;
    @Mock LambdaLogger logger;

    InvoiceCorrectionService service;

    @BeforeEach
    void setUp() {
        BillRunService billRunService = new BillRunService(zuoraClient, logger);
        service = new InvoiceCorrectionService(zuoraClient, billRunService, logger);
    }

    @Test
    void correct_reversesAffectedInvoicesAndCreatesBillRun() throws Exception {
        OrderEvent event = orderEvent("account-1", "O-00000001");
        Invoice invoice = invoice("inv-1", "INV-001", LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 31));

        when(zuoraClient.getOrderTriggerDates("O-00000001"))
                .thenReturn(List.of(LocalDate.of(2024, 3, 15)));
        when(zuoraClient.getPostedInvoices("account-1")).thenReturn(List.of(invoice));
        when(zuoraClient.createBillRun(any(), any())).thenReturn("br-1");

        service.correct(event);

        verify(zuoraClient).reverseInvoice(eq("inv-1"), eq("2024-03-15"));
        verify(zuoraClient).createBillRun(eq("account-1"), eq("2024-03-15"));
    }

    @Test
    void correct_skipsInvoicesOutsideServicePeriod() throws Exception {
        OrderEvent event = orderEvent("account-1", "O-00000001");
        Invoice invoice = invoice("inv-2", "INV-002", LocalDate.of(2024, 4, 1), LocalDate.of(2024, 4, 30));

        when(zuoraClient.getOrderTriggerDates("O-00000001"))
                .thenReturn(List.of(LocalDate.of(2024, 3, 15)));
        when(zuoraClient.getPostedInvoices("account-1")).thenReturn(List.of(invoice));

        service.correct(event);

        verify(zuoraClient, never()).reverseInvoice(any(), any());
        verify(zuoraClient, never()).createBillRun(any(), any());
    }

    @Test
    void correct_handlesNoInvoices() throws Exception {
        OrderEvent event = orderEvent("account-1", "O-00000001");

        when(zuoraClient.getOrderTriggerDates("O-00000001"))
                .thenReturn(List.of(LocalDate.of(2024, 3, 15)));
        when(zuoraClient.getPostedInvoices("account-1")).thenReturn(List.of());

        service.correct(event);

        verify(zuoraClient, never()).reverseInvoice(any(), any());
        verify(zuoraClient, never()).createBillRun(any(), any());
    }

    @Test
    void correct_handlesNoTriggerDates() throws Exception {
        OrderEvent event = orderEvent("account-1", "O-00000001");

        when(zuoraClient.getOrderTriggerDates("O-00000001")).thenReturn(List.of());

        service.correct(event);

        verify(zuoraClient, never()).getPostedInvoices(any());
        verify(zuoraClient, never()).reverseInvoice(any(), any());
        verify(zuoraClient, never()).createBillRun(any(), any());
    }

    @Test
    void correct_multipleTriggerDates_affectsInvoicesCoveringAnyDate() throws Exception {
        OrderEvent event = orderEvent("account-1", "O-00000001");
        // triggerDate が 3/15 と 5/1 の2つある
        Invoice inv1 = invoice("inv-1", "INV-001", LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 31));  // 3/15 に該当
        Invoice inv2 = invoice("inv-2", "INV-002", LocalDate.of(2024, 5, 1), LocalDate.of(2024, 5, 31));  // 5/1 に該当
        Invoice inv3 = invoice("inv-3", "INV-003", LocalDate.of(2024, 4, 1), LocalDate.of(2024, 4, 30));  // どちらにも非該当

        when(zuoraClient.getOrderTriggerDates("O-00000001"))
                .thenReturn(List.of(LocalDate.of(2024, 3, 15), LocalDate.of(2024, 5, 1)));
        when(zuoraClient.getPostedInvoices("account-1")).thenReturn(List.of(inv1, inv2, inv3));
        when(zuoraClient.createBillRun(any(), any())).thenReturn("br-1");

        service.correct(event);

        verify(zuoraClient).reverseInvoice(eq("inv-1"), any());
        verify(zuoraClient).reverseInvoice(eq("inv-2"), any());
        verify(zuoraClient, never()).reverseInvoice(eq("inv-3"), any());
        verify(zuoraClient, times(1)).createBillRun(any(), any());
    }

    @Test
    void correct_reversesMultipleAffectedInvoices() throws Exception {
        OrderEvent event = orderEvent("account-1", "O-00000001");
        Invoice inv1 = invoice("inv-1", "INV-001", LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 31));
        Invoice inv2 = invoice("inv-2", "INV-002", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30));
        Invoice inv3 = invoice("inv-3", "INV-003", LocalDate.of(2024, 4, 1), LocalDate.of(2024, 4, 30)); // 対象外

        when(zuoraClient.getOrderTriggerDates("O-00000001"))
                .thenReturn(List.of(LocalDate.of(2024, 3, 15)));
        when(zuoraClient.getPostedInvoices("account-1")).thenReturn(List.of(inv1, inv2, inv3));
        when(zuoraClient.createBillRun(any(), any())).thenReturn("br-1");

        service.correct(event);

        verify(zuoraClient).reverseInvoice(eq("inv-1"), any());
        verify(zuoraClient).reverseInvoice(eq("inv-2"), any());
        verify(zuoraClient, never()).reverseInvoice(eq("inv-3"), any());
        verify(zuoraClient, times(1)).createBillRun(any(), any());
    }

    // --- ヘルパー ---

    private OrderEvent orderEvent(String accountId, String orderNumber) {
        OrderEvent event = new OrderEvent();
        event.setId("order-id");
        event.setOrderNumber(orderNumber);
        event.setAccountId(accountId);
        return event;
    }

    private Invoice invoice(String id, String number, LocalDate start, LocalDate end) {
        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setInvoiceNumber(number);
        invoice.setStatus("Posted");
        invoice.setServiceStartDate(start);
        invoice.setServiceEndDate(end);
        invoice.setAmount(1000.0);
        return invoice;
    }
}
