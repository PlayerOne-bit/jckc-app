package app.models;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Purchase {

    private static final BigDecimal VAT_DIVISOR = new BigDecimal("1.12");

    private int id, slspId, supplierId;
    private String purchaseDate, invoiceNum;

    private BigDecimal
            exemptAmount = BigDecimal.ZERO,
            zeroRatedAmount = BigDecimal.ZERO,
            grossAmount = BigDecimal.ZERO;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getSlspId() {
        return slspId;
    }

    public void setSlspId(int slspId) {
        this.slspId = slspId;
    }

    public int getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(int supplierId) {
        this.supplierId = supplierId;
    }

    public String getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(String purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public String getInvoiceNum() {
        return invoiceNum;
    }

    public void setInvoiceNum(String invoiceNum) {
        this.invoiceNum = invoiceNum;
    }

    public BigDecimal getExemptAmount() {
        return exemptAmount;
    }

    public void setExemptAmount(BigDecimal exemptAmount) {
        this.exemptAmount = (exemptAmount != null)
                ? exemptAmount : BigDecimal.ZERO;
    }

    public BigDecimal getZeroRatedAmount() {
        return zeroRatedAmount;
    }

    public void setZeroRatedAmount(BigDecimal zeroRatedAmount) {
        this.zeroRatedAmount = (zeroRatedAmount != null)
                ? zeroRatedAmount : BigDecimal.ZERO;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public void setGrossAmount(BigDecimal grossAmount) {
        this.grossAmount = (grossAmount != null)
                ? grossAmount : BigDecimal.ZERO;
    }

    private BigDecimal vatBase() {   // Total Amt. of VAT - VAT Exempt - Zero-rated, never below zero
        BigDecimal base = grossAmount.subtract(exemptAmount).subtract(zeroRatedAmount);
        return base.signum() < 0 ? BigDecimal.ZERO : base;
    }

    public BigDecimal getTaxableAmount() {
        return vatBase().divide(VAT_DIVISOR, 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getInputVat() {
        return vatBase().subtract(getTaxableAmount()).setScale(2, RoundingMode.HALF_UP);
    }
}