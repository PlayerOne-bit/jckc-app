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

    // Purchases subject to 12% VAT (VAT-exclusive)
    public BigDecimal getTaxableAmount() {
        BigDecimal vatInclusivePurchases = grossAmount
                .subtract(exemptAmount)
                .subtract(zeroRatedAmount);

        return vatInclusivePurchases.divide(
                VAT_DIVISOR,
                2,
                RoundingMode.HALF_UP
        );
    }

    // Input VAT (12%)
    public BigDecimal getInputVat() {
        BigDecimal vatInclusivePurchases = grossAmount
                .subtract(exemptAmount)
                .subtract(zeroRatedAmount);

        return vatInclusivePurchases.subtract(getTaxableAmount())
                .setScale(2, RoundingMode.HALF_UP);
    }
}