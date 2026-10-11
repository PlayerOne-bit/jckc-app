package app.models;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Sale {
	private int id, slspId;
	private String siNum, saleDate;
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
	public BigDecimal getGrossAmount() {
		return grossAmount;
	}
	public void setGrossAmount(BigDecimal grossAmount) {
	    this.grossAmount = (grossAmount != null)
	            ? grossAmount : BigDecimal.ZERO;
	}
	public BigDecimal getOutputVat() {
	    BigDecimal vatInclusiveSales = grossAmount
	            .subtract(exemptAmount)
	            .subtract(zeroRatedAmount);

	    return vatInclusiveSales.subtract(getTaxableAmount())
	            .setScale(2, RoundingMode.HALF_UP);
	}
	public BigDecimal getTaxableAmount() {
	    BigDecimal vatInclusiveSales = grossAmount
	            .subtract(exemptAmount)
	            .subtract(zeroRatedAmount);

	    return vatInclusiveSales.divide(
	            new BigDecimal("1.12"),
	            2,
	            RoundingMode.HALF_UP
	    );
	}
}
