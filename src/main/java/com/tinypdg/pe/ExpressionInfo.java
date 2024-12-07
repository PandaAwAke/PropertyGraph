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
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

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
     * The qualifier name, such as "a" in "a.foo()" or "a.value"
     * @see org.eclipse.jdt.core.dom.QualifiedName
     */
    private ProgramElementInfo qualifier;

    /**
     * All children elements in this expression.
     * In fact this list might contain many types of ProgramElementInfo (except BlockInfo)
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

    @Override
    public SortedSet<String> getAssignedVariables() {
        final SortedSet<String> variables = new TreeSet<>();
        switch (this.category) {
            case Assignment -> {
                final ProgramElementInfo left = this.expressions.get(0);
                variables.addAll(left.getReferencedVariables());
                final ProgramElementInfo right = this.expressions.get(2);
                variables.addAll(right.getAssignedVariables());
            }
            case VariableDeclarationFragment -> variables.add(this.getExpressions().get(0).getText());
            case Postfix, Prefix -> {
                final ProgramElementInfo operand = this.expressions.get(0);
                variables.addAll(operand.getReferencedVariables());
            }
            //hj add
            case MethodInvocation -> {
                String text = this.getText();
//			if ((text.indexOf(".add(")!=-1 || text.indexOf(".remove(")!=-1 || text.indexOf(".get(")!=-1 || text.indexOf(".put(")!=-1 ||
//					text.indexOf(".pop(")!=-1 || text.indexOf(".push(")!=-1)){
                if (this.qualifier != null && text.charAt(0) >= 'a' && text.charAt(0) <= 'z') {
                    variables.add(this.qualifier.getText());
                }
                //}
            }
            default -> {
                for (final ProgramElementInfo expression : this.expressions) {
                    variables.addAll(expression.getAssignedVariables());
                }
                if (null != this.getAnonymousClassDeclaration()) {
                    for (final MethodInfo method : this.getAnonymousClassDeclaration().getMethods()) {
                        variables.addAll(method.getAssignedVariables());
                    }
                }
            }
        }
        return variables;
    }

    @Override
    public SortedSet<String> getReferencedVariables() {
        final SortedSet<String> variables = new TreeSet<>();
        switch (this.category) {
            case Assignment -> {
                final ProgramElementInfo right = this.expressions.get(2);
                variables.addAll(right.getReferencedVariables());
            }
            case VariableDeclarationFragment -> {
                if (1 < this.getExpressions().size()) {
                    variables.addAll(this.getExpressions().get(1).getReferencedVariables());
                }
            }
            case Postfix, Prefix -> {
                /*hj*/
                for (final ProgramElementInfo expression : this.expressions) {
                    variables.addAll(expression.getReferencedVariables());
                }
//hj			final ProgramElementInfo operand = this.expressions.get(0);
//hj			variables.addAll(operand.getReferencedVariables());
            }
            case SimpleName -> variables.add(this.getText());
            case MethodInvocation -> {
                if (this.qualifier != null) {
                    variables.addAll((this.qualifier).getReferencedVariables());
                }
                for (final ProgramElementInfo expression : this.expressions) {
                    variables.addAll(expression.getReferencedVariables());
                }
                if (null != this.getAnonymousClassDeclaration()) {
                    for (final MethodInfo method : this.getAnonymousClassDeclaration().getMethods()) {
                        variables.addAll(method.getReferencedVariables());
                    }
                }  //该case hj
            }
            default -> {
                for (final ProgramElementInfo expression : this.expressions) {
                    variables.addAll(expression.getReferencedVariables());
                }
                if (null != this.getAnonymousClassDeclaration()) {
                    for (final MethodInfo method : this.getAnonymousClassDeclaration().getMethods()) {
                        variables.addAll(method.getReferencedVariables());
                    }
                }
            }
        }
        return variables;
    }

}
