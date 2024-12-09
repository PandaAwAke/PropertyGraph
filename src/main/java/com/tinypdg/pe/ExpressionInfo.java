/*
 * Copyright 2024 Ma Yingshuo
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tinypdg.pe;

import lombok.Getter;
import lombok.Setter;
import org.eclipse.jdt.core.dom.ASTNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Describe the information of an expression (in the ast).
 */
@Getter
public class ExpressionInfo extends ProgramElementInfo {

    /**
     * The category of this expression.
     */
    public final CATEGORY category;

    /**
     * The qualifier name, such as: <br>
     * - "a" in "a.foo()" or "a.value" <br>
     * - "a.f()" in "a.f().g()" <br>
     * - "a" in "a.x"
     * @see org.eclipse.jdt.core.dom.QualifiedName
     */
    private ProgramElementInfo qualifier;

    /**
     * All children elements in this expression.
     * For assignment: [lhs, operator, rhs]
     * For method invocation: [functionName, arg1, arg2, ...]
     */
    private final List<ProgramElementInfo> expressions = new ArrayList<>();

    /**
     * Used for ClassInstanceCreation.
     * @see org.eclipse.jdt.core.dom.ClassInstanceCreation
     */
    private ClassInfo anonymousClassDeclaration;

//	@Setter
    //private ITypeBinding iTypeBinding;

    /**
     * If this expression is MethodInvocation, this is the full qualified name of the method.
     */
    @Setter
    private String apiName;

    public ExpressionInfo(final CATEGORY category, final ASTNode node, final int startLine, final int endLine) {
        super(node, startLine, endLine);
        this.category = category;
        this.qualifier = null;
        this.anonymousClassDeclaration = null;
        //this.iTypeBinding = iTypeBinding;
    }

    /**
     * All supported expression types.
     */
    public enum CATEGORY {
        ArrayAccess,
        ArrayCreation,
        ArrayInitializer,
        Assignment,
        Boolean,
        Cast,
        Character,
        ClassInstanceCreation,
        ConstructorInvocation,
        FieldAccess,
        Infix,
        Instanceof,
        MethodInvocation,
        Null,
        Number,
        Parenthesized,
        Postfix,
        Prefix,
        QualifiedName,
        SimpleName,
        String,
        SuperConstructorInvocation,
        SuperFieldAccess,
        SuperMethodInvocation,
        This,
        Trinomial,  // such as "x ? a : b"
        TypeLiteral,
        VariableDeclarationExpression,
        VariableDeclarationFragment,
        MethodEnter,
    }

    public void setQualifier(final ProgramElementInfo qualifier) {
        assert null != qualifier : "\"qualifier\" is null.";
        this.qualifier = qualifier;
    }

    public void addExpression(final ProgramElementInfo expression) {
        assert null != expression : "\"expression\" is null.";
        this.expressions.add(expression);
    }

    public void setAnonymousClassDeclaration(final ClassInfo anonymousClassDeclaration) {
        assert null != anonymousClassDeclaration : "\"anonymousClassDeclaration\" is null.";
        this.anonymousClassDeclaration = anonymousClassDeclaration;
    }


    static Collection<String> noDefMethodNames = List.of(
            "equals", "hashCode", "toString",   // Object
            "isEmpty", "size", "length", "stream"     // Collection
    );
    static Collection<String> defMethodNames = List.of(
            "push", "pop", "offer", "poll"       // Collection
    );
    static Collection<String> noDefMethodPrefixes = List.of(
            "get",
            "print", "debug", "trace", "info", "warn", "error"     // print and logs
    );
    static Collection<String> defMethodPrefixes = List.of(
            "set",
            "add", "remove", "put", "insert", "contains"     // Collection
    );

    /**
     * Judge whether the method may define its base variables by the method name.
     * Actually this is an exclusion strategy for defs in MethodInvocation.
     * @param methodName The name of the method
     * @return NO_DEF if this method doesn't define any variable <br>
     * DEF if this method surely defined the variables <br>
     * MAY_DEF if this method possibly defined the variables
     */
    protected VarDef.Type judgeMethodMayDefBase(final String methodName) {
        for (String noDefName : noDefMethodNames) {
            if (methodName.equals(noDefName)) {
                return VarDef.Type.NO_DEF;
            }
        }
        for (String noDefName : defMethodNames) {
            if (methodName.equals(noDefName)) {
                return VarDef.Type.DEF;
            }
        }
        for (String noDefName : noDefMethodPrefixes) {
            if (methodName.startsWith(noDefName)) {
                return VarDef.Type.NO_DEF;
            }
        }
        for (String noDefName : defMethodPrefixes) {
            if (methodName.startsWith(noDefName)) {
                return VarDef.Type.DEF;
            }
        }
        return VarDef.Type.MAY_DEF;
    }

    /**
     * Judge whether a ProgramElementInfo is a Variable, and return its variable name.
     * Now variable is one of: [SimpleName, ArrayAccess, FieldAccess, QualifiedName]
     * Fields will remain, but array index will be excluded.
     * <br>
     * Example: <br>
     * - "a[0]" -> "a" (We don't care about the index) <br>
     * - "a.x" -> "a.x" <br>
     * - "foo().bar" -> null
     * @param pe ProgramElementInfo
     * @return The name of the variable if pe is a variable, otherwise return null
     */
    protected String getVariableName(ProgramElementInfo pe) {
        if (!(pe instanceof ExpressionInfo expr)) {
            return null;
        }
        CATEGORY category = expr.category;
        return switch (category) {
            case SimpleName -> expr.getText();
            case ArrayAccess -> {
                if (!expr.getExpressions().isEmpty()) {
                    ProgramElementInfo base = expr.getExpressions().get(0);
                    if (!(base instanceof ExpressionInfo baseExpr)) {
                        yield null;
                    }
                    CATEGORY baseCategory = baseExpr.category;
                    if (baseCategory.equals(CATEGORY.SimpleName)) {
                        yield base.getText();
                    }
                }
                yield null;
            }
            case FieldAccess -> {
                if (expr.getExpressions().size() == 2) {
                    ProgramElementInfo base = expr.getExpressions().get(0);
                    if (!(base instanceof ExpressionInfo baseExpr)) {
                        yield null;
                    }
                    CATEGORY baseCategory = baseExpr.category;
                    if (baseCategory.equals(CATEGORY.SimpleName) || baseCategory.equals(CATEGORY.This)) {
                        yield expr.getText();
                    }
                }
                yield null;
            }
            case QualifiedName -> {
                if (!expr.getExpressions().isEmpty()) {
                    ProgramElementInfo base = expr.getQualifier();
                    if (!(base instanceof ExpressionInfo baseExpr)) {
                        yield null;
                    }
                    CATEGORY baseCategory = baseExpr.category;
                    if (baseCategory.equals(CATEGORY.SimpleName)) {
                        yield expr.getText();
                    }
                }
                yield null;
            }
            default -> null;
        };
    }

    @Override
    protected void doCalcDefVariables() {
        switch (this.category) {
            case Assignment -> {
                if (this.expressions.size() == 3) {
                    // Assignment: LHS values are surely DEF, defs in RHS are kept
                    final ProgramElementInfo left = this.expressions.get(0);
                    String variableName = getVariableName(left);
                    if (variableName != null) {
                        this.addVarDef(variableName, VarDef.Type.DEF);
                    } else {
                        left.getDefVariables().forEach(this::addVarDef);
                    }

                    // expressions[1] is an operator

                    final ProgramElementInfo right = this.expressions.get(2);
                    right.getDefVariables().forEach(this::addVarDef);
                }
            }
            case VariableDeclarationFragment -> {
                if (this.expressions.size() == 2) {
                    // VD Assignment: LHS values are surely DEF, defs in RHS are at least MAY_DEF
                    final ProgramElementInfo left = this.expressions.get(0);
                    String variableName = getVariableName(left);
                    if (variableName != null) {
                        this.addVarDef(variableName, VarDef.Type.DEF);
                    } else {
                        left.getDefVariables().forEach(this::addVarDef);
                    }

                    final ProgramElementInfo right = this.expressions.get(1);
                    right.getDefVariables().forEach(this::addVarDef);
                }
            }
            case Postfix -> {
                // Postfix only contains: x++, x--, so it's surely DEF
                if (this.expressions.size() == 2) {
                    ProgramElementInfo expression = expressions.get(0);
                    String variableName = getVariableName(expression);
                    if (variableName != null) {
                        this.addVarDef(variableName, VarDef.Type.DEF);
                    } else {
                        expression.getDefVariables().forEach(this::addVarDef);
                    }
                }
            }
            case Prefix -> {
                // Prefix contains: ++x, --x, +x, -x, ~x, !x
                if (this.expressions.size() == 2 && expressions.get(0) instanceof OperatorInfo operator) {
                    ProgramElementInfo expression = expressions.get(1);
                    String variableName = getVariableName(expression);
                    if ((operator.token.equals("++") || operator.token.equals("--")) && variableName != null) {
                        // Only ++ and -- are surely DEF
                        this.addVarDef(variableName, VarDef.Type.DEF);
                    } else {
                        expression.getDefVariables().forEach(this::addVarDef);
                    }
                }
            }
            case MethodInvocation -> {
                // MethodInvocation
                // - In fact, params are also MAY_DEF, but that's uncommon and will lead to a lot of FP,
                //   so we don't add them here
                if (this.qualifier != null && !this.expressions.isEmpty()) {
                    // Judge: whether this method defines variables?
                    VarDef.Type callDefType = this.judgeMethodMayDefBase(this.expressions.get(0).getText());

                    // The qualifier can be "request.getSession(true)"
                    // So the variables defined in the qualifier should be defined

                    String variableName = getVariableName(this.qualifier);
                    if (variableName != null) {
                        // Base is a simple name, add it
                        // Note: no matter what the type is, we will add it, so it may be NO_DEF
                        this.addVarDef(variableName, callDefType);
                    } else {
                        // The base is not a simple name, we should add all defs in it.
                        // Here we don't use callDefType, because it may be a Chained Method Call,
                        // such as "a.getMap().get(key).setX(b)".

                        if (callDefType.isAtLeastMayDef()) {
                            // This function may defs something
                            // In this case, we promote sub defs as at least MAY_DEF
                            // For example: "a.getX().set(1)", should treat "a" as MAY_DEF
                            this.qualifier.getDefVariables().forEach(
                                    def -> this.addVarDef(def.promote(VarDef.Type.MAY_DEF))
                            );
                        } else {
                            this.qualifier.getDefVariables().forEach(this::addVarDef);
                        }
                    }
                }
            }
            default -> {
                for (final ProgramElementInfo expression : this.expressions) {
                    expression.getDefVariables().forEach(this::addVarDef);
                }
                if (null != this.getAnonymousClassDeclaration()) {
                    for (final MethodInfo method : this.getAnonymousClassDeclaration().getMethods()) {
                        method.getDefVariables().forEach(this::addVarDef);
                    }
                }
            }
        }
    }

    @Override
    protected void doCalcUseVariables() {
        switch (this.category) {
            case Assignment -> {
                if (this.getExpressions().size() == 3) {
                    // Assignment: RHS values are used for sure
                    final ProgramElementInfo right = this.expressions.get(2);
                    right.getUseVariables().forEach(rhs -> this.addVarUse(rhs.promote(VarUse.Type.USE)));
                }
            }
            case VariableDeclarationFragment -> {
                // Assignment: RHS values are used for sure
                if (this.getExpressions().size() == 2) {
                    ProgramElementInfo right = this.getExpressions().get(1);
                    right.getUseVariables().forEach(rhs -> this.addVarUse(rhs.promote(VarUse.Type.USE)));
                }
            }
            case Postfix, Prefix -> {
                // Postfix only contains: x++, x--
                // Prefix contains: ++x, --x, +x, -x, ~x, !x
                // All values are used for sure
                for (final ProgramElementInfo expression : this.expressions) {
                    expression.getUseVariables().forEach(rhs -> this.addVarUse(rhs.promote(VarUse.Type.USE)));
                }
            }
            case SimpleName -> {
                this.addVarUse(this.getText(), VarUse.Type.MAY_USE);
            }
            case MethodInvocation -> {
                // MethodInvocation:
                // - Params are USE
                // - Base are MAY_USE
                if (this.qualifier != null) {
                    this.qualifier.getUseVariables().forEach(this::addVarUse);
                }

                // The first element of expressions is the method name
                // The rest are the params
                for (int i = 1; i < this.expressions.size(); i++) {
                    ProgramElementInfo expression = this.expressions.get(i);
                    expression.getUseVariables().forEach(this::addVarUse);
                }
            }
            default -> {
                for (final ProgramElementInfo expression : this.expressions) {
                    expression.getUseVariables().forEach(this::addVarUse);
                }
                if (null != this.getAnonymousClassDeclaration()) {
                    for (final MethodInfo method : this.getAnonymousClassDeclaration().getMethods()) {
                        // At least MAY_USE
                        method.getUseVariables().forEach(this::addVarUse);
                    }
                }
            }
        }
    }

}
