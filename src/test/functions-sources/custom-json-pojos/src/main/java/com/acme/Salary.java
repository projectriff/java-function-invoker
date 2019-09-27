package com.acme;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

public class Salary {

    private BigDecimal amount;

    private Currency currency;

    //@JsonProperty("dough") // Not supported yet
    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public static Salary of(long amount, Currency currency) {
        Salary result = new Salary();
        result.setAmount(BigDecimal.valueOf(amount));
        result.setCurrency(currency);
        return result;
    }

    @Override
    public String toString() {
        return "" + amount + currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Salary salary = (Salary) o;
        return amount.equals(salary.amount) &&
                currency.equals(salary.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }
}
