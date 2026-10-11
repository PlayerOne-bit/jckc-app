package app.views;

import java.math.BigDecimal;
import java.math.RoundingMode;

import javafx.beans.InvalidationListener;
import javafx.beans.property.*;

public class SaleRow {
    private static final BigDecimal VAT_DIVISOR = new BigDecimal("1.12");
    private static final BigDecimal VAT_RATE = new BigDecimal("0.12");

    private final IntegerProperty day = new SimpleIntegerProperty(0);
    private final StringProperty invoiceNo = new SimpleStringProperty("");
    private final ObjectProperty<BigDecimal> exempt = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> zeroRated = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> gross = new SimpleObjectProperty<>(BigDecimal.ZERO);   // stored + editable "Total Amount"
    private final ReadOnlyObjectWrapper<BigDecimal> taxable = new ReadOnlyObjectWrapper<>(BigDecimal.ZERO); // derived "VAT Receipts (1.12)"

    public SaleRow() {
        InvalidationListener recalc = _ -> recalcTaxable();
        gross.addListener(recalc);
        exempt.addListener(recalc);
        zeroRated.addListener(recalc);
    }

    public IntegerProperty dayProperty() { return day; }
    public StringProperty invoiceNoProperty() { return invoiceNo; }
    public ObjectProperty<BigDecimal> exemptProperty() { return exempt; }
    public ObjectProperty<BigDecimal> zeroRatedProperty() { return zeroRated; }
    public ObjectProperty<BigDecimal> grossProperty() { return gross; }
    public ReadOnlyObjectProperty<BigDecimal> taxableProperty() { return taxable.getReadOnlyProperty(); }

    /** Same property as grossProperty(); kept so existing controller code keeps compiling. */
    public ObjectProperty<BigDecimal> grossInputProperty() { return gross; }

    private void recalcTaxable() {
        // VAT Receipts = (Total Amount - VAT Exempt - Zero-rated) / 1.12
        BigDecimal base = nz(gross.get()).subtract(nz(exempt.get())).subtract(nz(zeroRated.get()));
        if (base.signum() < 0) base = BigDecimal.ZERO;
        taxable.set(base.divide(VAT_DIVISOR, 2, RoundingMode.HALF_UP));
    }

    /** Output Tax (12%) */
    public BigDecimal getOutputVat() {
        return nz(taxable.get()).multiply(VAT_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    /** The Total Amount that is stored. */
    public BigDecimal getGross() {
        return nz(gross.get());
    }

    public boolean isEmpty() {
        return (invoiceNo.get() == null || invoiceNo.get().isBlank())
                && isZero(exempt.get()) && isZero(zeroRated.get()) && isZero(gross.get());
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static boolean isZero(BigDecimal v) { return v == null || v.compareTo(BigDecimal.ZERO) == 0; }
}