package app.views;

import java.math.BigDecimal;
import java.math.RoundingMode;

import app.models.Supplier;
import javafx.beans.property.*;

public class PurchaseRow {
    private final IntegerProperty day = new SimpleIntegerProperty(0);
    private final ObjectProperty<Supplier> supplier = new SimpleObjectProperty<>();
    private final StringProperty invoiceNo = new SimpleStringProperty("");
    private final ObjectProperty<BigDecimal> exempt = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> zeroRated = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> taxable = new SimpleObjectProperty<>(BigDecimal.ZERO);

    public IntegerProperty dayProperty() { return day; }
    public ObjectProperty<Supplier> supplierProperty() { return supplier; }
    public StringProperty invoiceNoProperty() { return invoiceNo; }
    public ObjectProperty<BigDecimal> exemptProperty() { return exempt; }
    public ObjectProperty<BigDecimal> zeroRatedProperty() { return zeroRated; }
    public ObjectProperty<BigDecimal> taxableProperty() { return taxable; }

    public BigDecimal getInputVat() {
        BigDecimal t = taxable.get() == null ? BigDecimal.ZERO : taxable.get();
        return t.multiply(new BigDecimal("0.12")).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getGross() {
        BigDecimal e = nz(exempt.get()), z = nz(zeroRated.get()), t = nz(taxable.get());
        return e.add(z).add(t).add(getInputVat());
    }

    public boolean isEmpty() {
        return day.get() == 0 && supplier.get() == null
                && (invoiceNo.get() == null || invoiceNo.get().isBlank())
                && isZero(exempt.get()) && isZero(zeroRated.get()) && isZero(taxable.get());
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static boolean isZero(BigDecimal v) { return v == null || v.compareTo(BigDecimal.ZERO) == 0; }
}