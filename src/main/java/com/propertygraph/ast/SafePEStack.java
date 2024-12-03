package com.propertygraph.ast;

import com.propertygraph.pe.ProgramElementInfo;
import org.apache.poi.ss.formula.functions.T;

import java.util.Stack;

/**
 * The default java.util.Stack used in ASTVisitor has a problem:
 *      when an unsupported node was visited, the children might be supported and visited,
 *      causing additional stack pushes (i.e. at least pushing twice) without popping anything.
 * This safe stack is used for ensuring that every supported node will only cause <= 1 pushes when leaving the visitor.
 */
public class SafePEStack {

    final private Stack<ProgramElementInfo> stack = new Stack<>();

    public int size() {
        return stack.size();
    }

    /**
     * Return the stack size after pushing. Used for securely popping.
     * @param element The element to push
     * @return The stack size after pushing
     */
    public int push(final ProgramElementInfo element) {
        stack.push(element);
        return stack.size();
    }

    /**
     * Pop the stack until stack.size <= maxSizeAfterPop.
     * @param maxSizeAfterPop The max size allowed to reserve in the stack
     * @param <PE> The expectedTypeClass to return
     * @return The latest popped value, or null when: <br>
     *      1. Nothing was popped <br>
     *      2. The latest popped value is not the expected type <br>
     *      3. Popped more than 1 value
     */
    public <PE extends ProgramElementInfo> PE pop(int maxSizeAfterPop, Class<PE> expectedTypeClass) {
        ProgramElementInfo result = null;
        int count = 0;
        while (stack.size() > maxSizeAfterPop) {
            result = stack.pop();
            count++;
        }

        if (count > 1) {
            return null;    // Avoid unsupported situation
        }
        try {
            return expectedTypeClass.cast(result);
        } catch (ClassCastException e) {
            return null;
        }
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public ProgramElementInfo peek() {
        return stack.peek();
    }

}
