package org.opencds.cqf.cql.elm.execution;

import org.opencds.cqf.cql.execution.Context;

/*
or (left Boolean, right Boolean) Boolean

The or operator returns true if either of its arguments are true.
If both arguments are false, the result is false. Otherwise, the result is null.
*/

/**
 * Created by Bryn on 5/25/2016.
 */
public class OrEvaluator extends org.cqframework.cql.elm.execution.Or {

    public static Boolean or(Boolean left, Boolean right) {
        if (left == null || right == null) {
            if ((left != null && left) || (right != null && right)) {
                return true;
            }
            return null;
        }

        return (left || right);
    }

    @Override
    public Object evaluate(Context context) {
        Boolean left = (Boolean) getOperand().get(0).evaluate(context);
        Boolean right = (Boolean) getOperand().get(1).evaluate(context);

        return context.logTrace(this.getClass(), or(left, right), left, right);
    }
}
