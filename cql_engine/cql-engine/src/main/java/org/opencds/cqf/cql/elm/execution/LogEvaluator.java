package org.opencds.cqf.cql.elm.execution;

import org.opencds.cqf.cql.execution.Context;
import org.opencds.cqf.cql.runtime.Value;

import java.math.BigDecimal;

/*
Log(argument Decimal, base Decimal) Decimal

The Log operator computes the logarithm of its first argument, using the second argument as the base.
When invoked with Integer arguments, the arguments will be implicitly converted to Decimal.
If either argument is null, the result is null.
*/

/**
 * Created by Bryn on 5/25/2016.
 */
public class LogEvaluator extends org.cqframework.cql.elm.execution.Log {

    public static Object log(Object left, Object right) {
        if (left == null || right == null) {
            return null;
        }

        if (left instanceof BigDecimal) {
            Double base = Math.log(((BigDecimal)right).doubleValue());
            Double value = Math.log(((BigDecimal)left).doubleValue());

            if (base == 0) {
                return Value.verifyPrecision(new BigDecimal(value));
            }

            return Value.verifyPrecision(new BigDecimal(value / base));
        }

        throw new IllegalArgumentException(String.format("Cannot perform Log operation with arguments of type '%s' and '%s'.", left.getClass().getName(), right.getClass().getName()));
    }

    @Override
    public Object evaluate(Context context) {
        Object left = getOperand().get(0).evaluate(context);
        Object right = getOperand().get(1).evaluate(context);

        return context.logTrace(this.getClass(), log(left, right), left, right);
    }
}
