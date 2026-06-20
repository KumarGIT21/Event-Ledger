package com.eventledger.account.service;

import com.eventledger.account.dto.AccountDetailsResponse;
import com.eventledger.account.dto.ApplyTransactionRequest;
import com.eventledger.account.dto.BalanceResponse;
import com.eventledger.account.dto.TransactionResponse;
import com.eventledger.account.model.Account;
import com.eventledger.account.model.TransactionRecord;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public TransactionResponse applyTransaction(String accountId, ApplyTransactionRequest request) {
        var existing = transactionRepository.findByEventId(request.eventId());
        if (existing.isPresent()) {
            log.info("Duplicate transaction eventId={} for accountId={}", request.eventId(), accountId);
            return toResponse(existing.get(), true);
        }

        Account account = accountRepository.findById(accountId)
                .orElseGet(() -> accountRepository.save(new Account(accountId, request.currency())));

        if (!account.getCurrency().equals(request.currency())) {
            throw new CurrencyMismatchException(accountId, account.getCurrency(), request.currency());
        }

        TransactionRecord record = new TransactionRecord(
                request.eventId(),
                accountId,
                request.type(),
                request.amount(),
                request.currency(),
                request.eventTimestamp()
        );
        transactionRepository.save(record);

        BigDecimal balance = transactionRepository.computeBalance(accountId);
        account.setBalance(balance);
        account.setUpdatedAt(Instant.now());
        accountRepository.save(account);

        log.info("Applied transaction eventId={} accountId={} type={} amount={} newBalance={}",
                request.eventId(), accountId, request.type(), request.amount(), balance);

        return toResponse(record, false);
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        BigDecimal balance = transactionRepository.computeBalance(accountId);
        return new BalanceResponse(accountId, balance, account.getCurrency(), Instant.now());
    }

    @Transactional(readOnly = true)
    public AccountDetailsResponse getAccountDetails(String accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        List<AccountDetailsResponse.TransactionSummary> transactions = transactionRepository
                .findByAccountIdOrderByEventTimestampAscAppliedAtAsc(accountId)
                .stream()
                .map(t -> new AccountDetailsResponse.TransactionSummary(
                        t.getEventId(),
                        t.getType(),
                        t.getAmount(),
                        t.getCurrency(),
                        t.getEventTimestamp(),
                        t.getAppliedAt()))
                .toList();

        BigDecimal balance = transactionRepository.computeBalance(accountId);

        return new AccountDetailsResponse(
                account.getAccountId(),
                balance,
                account.getCurrency(),
                account.getCreatedAt(),
                account.getUpdatedAt(),
                transactions
        );
    }

    private TransactionResponse toResponse(TransactionRecord record, boolean duplicate) {
        return new TransactionResponse(
                record.getEventId(),
                record.getAccountId(),
                record.getType(),
                record.getAmount(),
                record.getCurrency(),
                record.getEventTimestamp(),
                record.getAppliedAt(),
                duplicate
        );
    }
}
