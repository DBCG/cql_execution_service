package org.opencds.cqf.cql.elm.execution;

import org.apache.commons.lang3.NotImplementedException;
import org.opencds.cqf.cql.execution.Context;
import org.opencds.cqf.cql.runtime.*;

import javax.management.ObjectName;
import java.math.BigDecimal;

/*
predecessor of<T>(argument T) T

The predecessor operator returns the predecessor of the argument.
  For example, the predecessor of 2 is 1. If the argument is already the minimum value for the type, a run-time error is thrown.
The predecessor operator is defined for the Integer, Decimal, DateTime, and Time types.
For Integer, predecessor is equivalent to subtracting 1.
For Decimal, predecessor is equivalent to subtracting the minimum precision value for the Decimal type, or 10^-08.
For DateTime and Time values, predecessor is equivalent to subtracting a time-unit quantity for the lowest specified precision of the value.
  For example, if the DateTime is fully specified, predecessor is equivalent to subtracting 1 millisecond;
    if the DateTime is specified to the second, predecessor is equivalent to subtracting one second, etc.
If the argument is null, the result is null.
*/

/**
 * Created by Bryn on 5/25/2016.
 */
public class PredecessorEvaluator extends org.cqframework.cql.elm.execution.Predecessor {

    public static Object predecessor(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Integer) {
            if ((Integer) value <= Value.MIN_INT) {
                throw new RuntimeException("The result of the predecessor operation preceeds the minimum value allowed for the Integer type");
            }
            return ((Integer)value) - 1;
        }
        else if (value instanceof BigDecimal) {
            if (((BigDecimal) value).compareTo(Value.MIN_DECIMAL) <= 0) {
                throw new RuntimeException("The result of the predecessor operation preceeds the minimum value allowed for the Decimal type");
            }
            return ((BigDecimal)value).subtract(new BigDecimal("0.00000001"));
        }
        // NOTE: Quantity successor is not standard - including it for simplicity
        else if (value instanceof Quantity) {
            if (((Quantity) value).getValue().compareTo(Value.MIN_DECIMAL) <= 0) {
                throw new RuntimeException("The result of the predecessor operation preceeds the minimum value allowed for the Decimal type");
            }
            Quantity quantity = (Quantity)value;
            return new Quantity().withValue((BigDecimal)predecessor(quantity.getValue())).withUnit(quantity.getUnit());
        }
        else if (value instanceof DateTime) {
            DateTime dt = (DateTime)value;
            return new DateTime(dt.getDateTime().minus(1, dt.getPrecision().toChronoUnit()), dt.getPrecision());
        }
        else if (value instanceof Time) {
            Time t = (Time)value;
            switch (t.getPrecision()) {
                case HOUR:
                    if (t.getTime().getHour() == 0) {
                        throw new RuntimeException("The result of the successor operation preceeds the minimum value allowed for the Time type");
                    }
                    break;
                case MINUTE:
                    if (t.getTime().getHour() == 0 && t.getTime().getMinute() == 0) {
                        throw new RuntimeException("The result of the successor operation preceeds the minimum value allowed for the Time type");
                    }
                    break;
                case SECOND:
                    if (t.getTime().getHour() == 0 && t.getTime().getMinute() == 0 && t.getTime().getSecond() == 0) {
                        throw new RuntimeException("The result of the successor operation preceeds the minimum value allowed for the Time type");
                    }
                    break;
                case MILLISECOND:
                    if (t.getTime().getHour() == 0 && t.getTime().getMinute() == 0
                            && t.getTime().getSecond() == 0 && t.getTime().get(Precision.MILLISECOND.toChronoField()) == 0)
                    {
                        throw new RuntimeException("The result of the successor operation preceeds the minimum value allowed for the Time type");
                    }
                    break;
            }
            return new Time(t.getTime().minus(1, t.getPrecision().toChronoUnit()), t.getPrecision());
        }

        throw new IllegalArgumentException(String.format("Cannot perform Predecessor operation with type %s", value.getClass().getName()));
    }

    @Override
    public Object evaluate(Context context) {
        Object value = getOperand().evaluate(context);
        return predecessor(value);
    }
}
