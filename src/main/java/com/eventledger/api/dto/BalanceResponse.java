package com.eventledger.api.dto;

import java.math.BigDecimal;

public class BalanceResponse {
    private String accountId;
    private BigDecimal balance;

    public BalanceResponse() {
    }

    public BalanceResponse(String accountId, BigDecimal balance) {
        this.accountId = accountId;
        this.balance = balance;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}
