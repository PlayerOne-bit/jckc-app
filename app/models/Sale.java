package app.models;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Sale {
	private static final BigDecimal VAT_RATE = new BigDecimal("0.12");
	private int id, slspId;
	private String siNum, saleDate;
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
	public String getSaleDate() {
		return saleDate;
	}
	public void setSaleDate(String saleDate) {
		this.saleDate = saleDate;
	}
	public String getSiNum() {
		return siNum;
	}
	public void setSiNum(String siNum) {
		this.siNum = siNum;
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
	public BigDecimal getOutputVat() {
		return taxableAmount.multiply(VAT_RATE).setScale(2,RoundingMode.HALF_UP);
	}
	public BigDecimal getGrossAmount() {
		return exemptAmount.add(zeroRatedAmount).add(taxableAmount).add(getOutputVat());
	}
}
