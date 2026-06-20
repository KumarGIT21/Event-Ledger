package com.eventledger.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eventledger.account.dto.ApplyTransactionRequest;
import com.eventledger.account.model.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AccountServiceTest {

    @Autowired
    private AccountService accountService;

    @Test
    void appliesCreditAndDebitInAnyOrder() {
        String accountId = "acct-order";
        Instant t1 = Instant.parse("2026-05-15T10:00:00Z");
        Instant t2 = Instant.parse("2026-05-15T11:00:00Z");
        Instant t3 = Instant.parse("2026-05-15T09:00:00Z");

        accountService.applyTransaction(accountId, request("evt-2", TransactionType.DEBIT, "40.00", t2));
        accountService.applyTransaction(accountId, request("evt-1", TransactionType.CREDIT, "150.00", t1));
        accountService.applyTransaction(accountId, request("evt-3", TransactionType.CREDIT, "50.00", t3));

        var balance = accountService.getBalance(accountId);
        assertThat(balance.balance()).isEqualByComparingTo("160.00");

        var details = accountService.getAccountDetails(accountId);
        assertThat(details.recentTransactions())
                .extracting(t -> t.eventId())
                .containsExactly("evt-3", "evt-1", "evt-2");
    }

    @Test
    void duplicateEventIsIdempotent() {
        String accountId = "acct-dup";
        ApplyTransactionRequest request = request("evt-dup", TransactionType.CREDIT, "100.00",
                Instant.parse("2026-05-15T12:00:00Z"));

        var first = accountService.applyTransaction(accountId, request);
        var second = accountService.applyTransaction(accountId, request);

        assertThat(first.duplicate()).isFalse();
        assertThat(second.duplicate()).isTrue();
        assertThat(accountService.getBalance(accountId).balance()).isEqualByComparingTo("100.00");
    }

    @Test
    void rejectsCurrencyMismatch() {
        String accountId = "acct-currency";
        accountService.applyTransaction(accountId, request("evt-usd", TransactionType.CREDIT, "10.00",
                Instant.parse("2026-05-15T12:00:00Z")));

        assertThatThrownBy(() -> accountService.applyTransaction(accountId,
                new ApplyTransactionRequest("evt-eur", TransactionType.CREDIT, new BigDecimal("5.00"),
                        "EUR", Instant.parse("2026-05-15T13:00:00Z"), null)))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    private ApplyTransactionRequest request(String eventId, TransactionType type, String amount, Instant timestamp) {
        return new ApplyTransactionRequest(eventId, type, new BigDecimal(amount), "USD", timestamp, null);
    }
}
