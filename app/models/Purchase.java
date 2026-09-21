package app.models;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Purchase {
	private static final BigDecimal VAT_RATE = new BigDecimal("0.12");
	private int id, slspId,supplierId;
	private String purchaseDate, invoiceNum;
	private BigDecimal 
			exemptAmount = BigDecimal.ZERO,
            zeroRatedAmount = BigDecimal.ZERO,
            taxableAmount = BigDecimal.ZERO;
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
		this.exemptAmount =(exemptAmount!=null)? exemptAmount:BigDecimal.ZERO;
	}
	public BigDecimal getZeroRatedAmount() {
		return zeroRatedAmount;
	}
	public void setZeroRatedAmount(BigDecimal zeroRatedAmount) {
		this.zeroRatedAmount = (zeroRatedAmount!=null)?zeroRatedAmount:BigDecimal.ZERO;
	}
	public BigDecimal getTaxableAmount() {
		return taxableAmount;
	}
	public void setTaxableAmount(BigDecimal taxableAmount) {
		this.taxableAmount = (taxableAmount!=null)?taxableAmount:BigDecimal.ZERO;
	}
	public BigDecimal getInputVat() {
		return taxableAmount.multiply(VAT_RATE).setScale(2,RoundingMode.HALF_UP);
	}
	public BigDecimal getGrossAmount() {
		return exemptAmount.add(zeroRatedAmount).add(taxableAmount).add(getInputVat());
	}
	
}
