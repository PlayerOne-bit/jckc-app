package app.views;

import java.math.BigDecimal;
import java.math.RoundingMode;

import app.models.Supplier;
import javafx.beans.InvalidationListener;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class PurchaseRow {

    private static final BigDecimal VAT_DIVISOR = new BigDecimal("1.12");

    private final IntegerProperty day = new SimpleIntegerProperty(0);

    private final ObjectProperty<Supplier> supplier =
            new SimpleObjectProperty<>();

    private final StringProperty invoiceNo =
            new SimpleStringProperty("");

    private final ObjectProperty<BigDecimal> exempt =
            new SimpleObjectProperty<>(BigDecimal.ZERO);

    private final ObjectProperty<BigDecimal> zeroRated =
            new SimpleObjectProperty<>(BigDecimal.ZERO);

    // Stored gross amount.
    private final ObjectProperty<BigDecimal> gross =
            new SimpleObjectProperty<>(BigDecimal.ZERO);

    // Derived VAT-exclusive taxable purchases.
    private final ReadOnlyObjectWrapper<BigDecimal> taxable =
            new ReadOnlyObjectWrapper<>(BigDecimal.ZERO);

    public PurchaseRow() {
        InvalidationListener recalculate = _ -> recalcTaxable();
        gross.addListener(recalculate);
        exempt.addListener(recalculate);
        zeroRated.addListener(recalculate);
    }

    public IntegerProperty dayProperty() {
        return day;
    }

    public ObjectProperty<Supplier> supplierProperty() {
        return supplier;
    }

    public StringProperty invoiceNoProperty() {
        return invoiceNo;
    }

    public ObjectProperty<BigDecimal> exemptProperty() {
        return exempt;
    }

    public ObjectProperty<BigDecimal> zeroRatedProperty() {
        return zeroRated;
    }

    public ObjectProperty<BigDecimal> grossProperty() {
        return gross;
    }

    public ReadOnlyObjectProperty<BigDecimal> taxableProperty() {
        return taxable.getReadOnlyProperty();
    }

    // Compatibility with existing controller code.
    public ObjectProperty<BigDecimal> grossInputProperty() {
        return gross;
    }

    private void recalcTaxable() {
        taxable.set(vatBase().divide(VAT_DIVISOR, 2, RoundingMode.HALF_UP));
    }

    public BigDecimal getInputVat() {
        return vatBase().subtract(nz(taxable.get())).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getGross() {
        return nz(gross.get());
    }

    public BigDecimal getTotalAmount() {
        return nz(gross.get());
    }
    private BigDecimal vatBase() {   // Total Amt. of VAT - VAT Exempt - Zero-rated
        BigDecimal base = nz(gross.get()).subtract(nz(exempt.get())).subtract(nz(zeroRated.get()));
        return base.signum() < 0 ? BigDecimal.ZERO : base;
    }
    public boolean isEmpty() {
        return (invoiceNo.get() == null || invoiceNo.get().isBlank())
                && supplier.get() == null
                && isZero(exempt.get())
                && isZero(zeroRated.get())
                && isZero(gross.get());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean isZero(BigDecimal value) {
        return value == null
                || value.compareTo(BigDecimal.ZERO) == 0;
    }
}