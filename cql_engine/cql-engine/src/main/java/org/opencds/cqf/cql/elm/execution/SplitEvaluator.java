package org.opencds.cqf.cql.elm.execution;

import org.opencds.cqf.cql.execution.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
Split(stringToSplit String, separator String) List<String>

The Split operator splits a string into a list of strings using a separator.
If the stringToSplit argument is null, the result is null.
If the stringToSplit argument does not contain any appearances of the separator,
  the result is a list of strings containing one element that is the value of the stringToSplit argument.
*/

/**
 * Created by Bryn on 5/25/2016.
 */
public class SplitEvaluator extends org.cqframework.cql.elm.execution.Split {

    public static Object split(Object stringToSplit, Object separator) {
        if (stringToSplit == null) {
            return null;
        }

        List<Object> result = new ArrayList<>();
        if (separator == null) {
            result.add(stringToSplit);
        }
        else {
            Collections.addAll(result, ((String) stringToSplit).split((String) separator));
        }
        return result;
    }

    @Override
    public Object evaluate(Context context) {
        Object stringToSplit = getStringToSplit().evaluate(context);
        Object separator = getSeparator().evaluate(context);

        return context.logTrace(this.getClass(), split(stringToSplit, separator), stringToSplit, separator);
    }
}
