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

package com.propertygraph.ast;

import com.propertygraph.pe.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.internal.core.dom.NaiveASTFlattener;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ASTVisitor extends NaiveASTFlattener {

    private static final String lineSeparator = System.lineSeparator();

    public static CompilationUnit createAST(final String source) {
        final ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(source.toCharArray());
        // hj add 4.22
        parser.setEnvironment(null, null, null, true);
        parser.setUnitName("any_name");
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);

        return (CompilationUnit) parser.createAST(null);
    }

	public static CompilationUnit createAST(final File file) {
		final StringBuilder text = new StringBuilder();
		final BufferedReader reader;
		try {
			reader = new BufferedReader(new InputStreamReader(
                    Files.newInputStream(file.toPath()), "JISAutoDetect"));

			while (reader.ready()) {
				final String line = reader.readLine();
				text.append(line);
				text.append(lineSeparator);
			}
			reader.close();
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}

		return createAST(text.toString());
	}

    @Getter
    final private CompilationUnit root;

    @Getter
    final private List<MethodInfo> methods = new ArrayList<>();

    final private SafePEStack stack = new SafePEStack();

    public ASTVisitor(final CompilationUnit root) {
        this.root = root;
    }

    //class
    @Override
    public boolean visit(final TypeDeclaration node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ClassInfo typeDeclaration = new ClassInfo(node.getName().toString(), node, startLine, endLine);
        int maxStackSize = this.stack.push(typeDeclaration);

        final StringBuilder text = new StringBuilder();
        text.append("class ");
        text.append(node.getName().toString());
        text.append("{");
        text.append(lineSeparator);
        for (final Object o : node.bodyDeclarations()) {
            if (o instanceof MethodDeclaration) {
                ((ASTNode) o).accept(this);
                final MethodInfo method = this.stack.pop(maxStackSize, MethodInfo.class);
                if (method != null) {
                    this.methods.add(method);
                    typeDeclaration.addMethod(method);
                    text.append(method.getText());
                    text.append(lineSeparator);
                }
            }
        }
        text.append("}");
        typeDeclaration.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(ExpressionMethodReference node) {
        return super.visit(node);
    }

    @Override
    public boolean visit(final TypeDeclarationStatement node) {
        if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {
            final int startLine = this.getStartLineNumber(node);
            final int endLine = this.getEndLineNumber(node);
            final ProgramElementInfo ownerBlock = this.stack.peek();
            final StatementInfo statement = new StatementInfo(ownerBlock,
                    StatementInfo.CATEGORY.TypeDeclaration, node, startLine, endLine);
            int maxStackSize = this.stack.push(statement);

            node.getDeclaration().accept(this);
            final ProgramElementInfo typeDeclaration = this.stack.pop(maxStackSize, ProgramElementInfo.class);
            if (typeDeclaration != null) {
                statement.addExpression(typeDeclaration);
                statement.setText(typeDeclaration.getText());
            }
        }

        return false;
    }

    @Override
    public boolean visit(final AnnotationTypeDeclaration node) {
        for (final Object o : node.bodyDeclarations()) {
            int maxStackSize = this.stack.size();
            ((ASTNode) o).accept(this);
            final ProgramElementInfo method = this.stack.pop(maxStackSize, ProgramElementInfo.class);
        }

        return false;
    }

    @Override
    public boolean visit(final AnonymousClassDeclaration node) {
        final StringBuilder text = new StringBuilder();
        text.append("{");
        text.append(lineSeparator);

        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ClassInfo anonymousClass = new ClassInfo(null, node, startLine, endLine);
        int maxStackSize = this.stack.push(anonymousClass);

        for (final Object o : node.bodyDeclarations()) {
            if (o instanceof MethodDeclaration) {
                ((ASTNode) o).accept(this); // class 到 method
                final MethodInfo method = this.stack.pop(maxStackSize, MethodInfo.class);
                if (method != null) {
                    anonymousClass.addMethod(method);
                    text.append(method.getText());
                }
            }
        }

        text.append("}");
        anonymousClass.setText(text.toString());

        return false;
    }

    // method
    @Override
    public boolean visit(final MethodDeclaration node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final String name = node.getName().getIdentifier();
        final MethodInfo method = new MethodInfo(false, name, node, startLine, endLine);
        int maxStackSize = this.stack.push(method);

        final StringBuilder text = new StringBuilder();
        for (final Object modifier : node.modifiers()) {
            method.addModifier(modifier.toString());
            text.append(modifier);
            text.append(" ");
        }
        if (null != node.getReturnType2()) { //返回值
            text.append(node.getReturnType2().toString());
            text.append(" ");
        }
        text.append(name);
        text.append(" (");

        for (final Object o : node.parameters()) {
            ((ASTNode) o).accept(this);
            final VariableInfo parameter = this.stack.pop(maxStackSize, VariableInfo.class);
            if (parameter != null) {
                parameter.setCategory(VariableInfo.CATEGORY.PARAMETER);
                method.addParameter(parameter);
                text.append(parameter.getText());
                text.append(" ,");
            }
        }
        if (text.toString().endsWith(",")) {
            text.deleteCharAt(text.length() - 1);
        }
        text.append(" ) ");

        if (null != node.getBody()) {
            node.getBody().accept(this);
            final StatementInfo body = this.stack.pop(maxStackSize, StatementInfo.class);
            if (body != null) {
                method.setStatement(body);
                text.append(body.getText());
            }
        }
        method.setText(text.toString());

        return false;
    }

    /*
     * Added to handle lambda expressions.
     */
    @Override
    public boolean visit(LambdaExpression node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);

        final MethodInfo method = new MethodInfo(true, null, node, startLine, endLine);
        int maxStackSize = this.stack.push(method);

        final StringBuilder text = new StringBuilder();

        boolean hasParentheses = node.hasParentheses();
		if (hasParentheses)
			text.append('(');

		for (final Object o : node.parameters()) {
			VariableDeclaration v = (VariableDeclaration) o;
            assert v instanceof VariableDeclarationFragment;
			v.accept(this);

            final ExpressionInfo vdFragment = this.stack.pop(maxStackSize, ExpressionInfo.class);
            text.append(vdFragment.getExpressions().get(0).getText());  // The name of the parameter
            text.append(",");
		}
        if (text.toString().endsWith(",")) {
            text.deleteCharAt(text.length() - 1);
        }

		if (hasParentheses)
			text.append(')');

		text.append(" -> ");

        if (null != node.getBody()) {
            node.getBody().accept(this);
            final ProgramElementInfo body = this.stack.pop(maxStackSize, ProgramElementInfo.class);
            if (body != null) {
                // The body is either a Block or an Expression
                if (body instanceof StatementInfo) {
                    method.setStatement((StatementInfo) body);
                } else if (body instanceof ExpressionInfo) {
                    method.setLambdaExpression((ExpressionInfo) body);
                } else {
                    assert false;
                }
                text.append(body.getText());
            }
        } else {
            text.append("{}");
        }

        method.setText(text.toString());
		return false;
    }

    private int getStartLineNumber(final ASTNode node) {
        return root.getLineNumber(node.getStartPosition());
    }

    private int getEndLineNumber(final ASTNode node) {
        if (node instanceof IfStatement) {
            final ASTNode elseStatement = ((IfStatement) node).getElseStatement();
            final int thenEnd = (elseStatement == null) ?
                    node.getStartPosition() + node.getLength() :
                    elseStatement.getStartPosition() - 1;
            return root.getLineNumber(thenEnd);
        } else if (node instanceof TryStatement tryStatement) {
            int tryEnd = 0;
            for (Object obj : tryStatement.catchClauses()) {
                CatchClause catchClause = (CatchClause) obj;
                tryEnd = catchClause.getStartPosition() - 1;
                break;
            }
            if (tryEnd == 0) {
                final Block finallyBlock = tryStatement.getFinally();
                if (finallyBlock != null) {
                    tryEnd = finallyBlock.getStartPosition() - 1;
                }
            }
            if (tryEnd == 0) {
                tryEnd = node.getStartPosition() + node.getLength();
            }
            return root.getLineNumber(tryEnd);
        } else {
            return root.getLineNumber(node.getStartPosition() + node.getLength());
        }
    }

    @Override
    public boolean visit(final AssertStatement node) {
        if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {
            int maxStackSize = this.stack.size();

            node.getExpression().accept(this);
            final ProgramElementInfo expression = this.stack.pop(maxStackSize, ProgramElementInfo.class);

            node.getMessage().accept(this);
            final ProgramElementInfo message = this.stack.pop(maxStackSize, ProgramElementInfo.class);

            final int startLine = this.getStartLineNumber(node);
            final int endLine = this.getEndLineNumber(node);
            final ProgramElementInfo ownerBlock = this.stack.peek();
            final StatementInfo statement = new StatementInfo(ownerBlock,
                    StatementInfo.CATEGORY.Assert, node, startLine, endLine);
            if (expression != null) {
                statement.addExpression(expression);
            }
            if (message != null) {
                statement.addExpression(message);
            }

            maxStackSize = this.stack.push(statement);
        }

        return false;
    }

    @Override
    public boolean visit(final ArrayAccess node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo expression = new ExpressionInfo(ExpressionInfo.CATEGORY.ArrayAccess,
                node, startLine, endLine);
        int maxStackSize = this.stack.push(expression);

        final StringBuilder text = new StringBuilder();

        node.getArray().accept(this);
        final ProgramElementInfo array = this.stack.pop(maxStackSize, ProgramElementInfo.class);
        if (array != null) {
            expression.addExpression(array);
            text.append(array.getText());
        }

        node.getIndex().accept(this);
        final ProgramElementInfo index = this.stack.pop(maxStackSize, ProgramElementInfo.class);
        if (index != null) {
            expression.addExpression(index);
            text.append("[");
            text.append(index.getText());
            text.append("]");
        }
        expression.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final ArrayType node) {
        final StringBuilder text = new StringBuilder();
        text.append(node.getElementType().toString());
        text.append("[]".repeat(Math.max(0, node.getDimensions())));
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final TypeInfo type = new TypeInfo(text.toString(), node, startLine, endLine);
        int maxStackSize = this.stack.push(type);

        return false;
    }

    @Override
    public boolean visit(final NullLiteral node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ProgramElementInfo expression = new ExpressionInfo(ExpressionInfo.CATEGORY.Null,
                node, startLine, endLine);
        expression.setText("null");
        int maxStackSize = this.stack.push(expression);

        return false;
    }

    @Override
    public boolean visit(final NumberLiteral node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ProgramElementInfo expression = new ExpressionInfo(ExpressionInfo.CATEGORY.Number,
                node, startLine, endLine);
        expression.setText(node.getToken());
        int maxStackSize = this.stack.push(expression);

        return false;
    }

    @Override
    public boolean visit(final PostfixExpression node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo postfixExpression = new ExpressionInfo(ExpressionInfo.CATEGORY.Postfix,
                node, startLine, endLine);
        int maxStackSize = this.stack.push(postfixExpression);

        final StringBuilder text = new StringBuilder();

        node.getOperand().accept(this);
        final ProgramElementInfo operand = this.stack.pop(maxStackSize, ProgramElementInfo.class);
        if (operand != null) {
            postfixExpression.addExpression(operand);
            text.append(operand.getText());
        }

        final OperatorInfo operator = new OperatorInfo(node.getOperator().toString(),
                node, startLine, endLine);
        postfixExpression.addExpression(operator);

        text.append(operator.getText());
        postfixExpression.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final PrefixExpression node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo prefixExpression = new ExpressionInfo(ExpressionInfo.CATEGORY.Prefix,
                node, startLine, endLine);
        int maxStackSize = this.stack.push(prefixExpression);
        final StringBuilder text = new StringBuilder();

        final OperatorInfo operator = new OperatorInfo(node.getOperator().toString(),
                node.getOperator(), startLine, endLine);
        prefixExpression.addExpression(operator);
        text.append(operator.getText());

        node.getOperand().accept(this);
        final ProgramElementInfo operand = this.stack.pop(maxStackSize, ProgramElementInfo.class);
        if (operand != null) {
            prefixExpression.addExpression(operand);
            text.append(operand.getText());
        }

        prefixExpression.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final StringLiteral node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ProgramElementInfo expression = new ExpressionInfo(ExpressionInfo.CATEGORY.String,
                node, startLine, endLine);
        expression.setText("\"" + node.getLiteralValue() + "\"");
        int maxStackSize = this.stack.push(expression);

        return false;
    }

    @Override
    public boolean visit(final SuperFieldAccess node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo superFieldAccess = new ExpressionInfo(ExpressionInfo.CATEGORY.SuperFieldAccess,
				node, startLine, endLine);
        int maxStackSize = this.stack.push(superFieldAccess);

        final StringBuilder text = new StringBuilder();
        text.append("super.");

        node.getName().accept(this);
        final ProgramElementInfo name = this.stack.pop(maxStackSize, ProgramElementInfo.class);
        if (name != null) {
            superFieldAccess.addExpression(name);
            text.append(name.getText());
        }

        superFieldAccess.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final SuperMethodInvocation node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo superMethodInvocation = new ExpressionInfo(ExpressionInfo.CATEGORY.SuperMethodInvocation,
				node, startLine, endLine);
        int maxStackSize = this.stack.push(superMethodInvocation);

        final StringBuilder text = new StringBuilder();
        text.append("super.");

        node.getName().accept(this);
        final ProgramElementInfo name = this.stack.pop(maxStackSize, ProgramElementInfo.class);
        if (name != null) {
            superMethodInvocation.addExpression(name);
            text.append(name);
        }

        for (final Object argument : node.arguments()) {
            ((ASTNode) argument).accept(this);
            final ProgramElementInfo argumentExpression = this.stack.pop(maxStackSize, ProgramElementInfo.class);
            if (argumentExpression != null) {
                superMethodInvocation.addExpression(argumentExpression);
                text.append(argumentExpression.getText());
            }
        }
        superMethodInvocation.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final TypeLiteral node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ProgramElementInfo expression = new ExpressionInfo(ExpressionInfo.CATEGORY.TypeLiteral,
                node, startLine, endLine);
        int maxStackSize = this.stack.push(expression);

        return false;
    }

    @Override
    public boolean visit(final QualifiedName node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo qualifiedName = new ExpressionInfo(ExpressionInfo.CATEGORY.QualifiedName,
                node, startLine, endLine);
        int maxStackSize = this.stack.push(qualifiedName);

        final StringBuilder text = new StringBuilder();

        node.getQualifier().accept(this);
        final ProgramElementInfo qualifier = this.stack.pop(maxStackSize, ProgramElementInfo.class);
        if (qualifier != null) {
            qualifiedName.setQualifier(qualifier);
            text.append(qualifier.getText());
        }

        node.getName().accept(this);
        final ProgramElementInfo name = this.stack.pop(maxStackSize, ProgramElementInfo.class);
        if (name != null) {
            qualifiedName.addExpression(name);
            text.append(".");
            text.append(name.getText());
        }

        qualifiedName.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final SimpleName node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ProgramElementInfo simpleName = new ExpressionInfo(ExpressionInfo.CATEGORY.SimpleName,
                node, startLine, endLine);
        simpleName.setText(node.getIdentifier());
        int maxStackSize = this.stack.push(simpleName);

        return false;
    }

    @Override
    public boolean visit(final CharacterLiteral node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ProgramElementInfo expression = new ExpressionInfo(ExpressionInfo.CATEGORY.Character,
                node, startLine, endLine);
        expression.setText("'" + node.charValue() + "'");
        int maxStackSize = this.stack.push(expression);

        return false;
    }

    @Override
    public boolean visit(final FieldAccess node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo fieldAccess = new ExpressionInfo(ExpressionInfo.CATEGORY.FieldAccess,
                node, startLine, endLine);
        int maxStackSize = this.stack.push(fieldAccess);

        final StringBuilder text = new StringBuilder();

        node.getExpression().accept(this);
        final ProgramElementInfo expression = this.stack.pop(maxStackSize, ProgramElementInfo.class);
        if (expression != null) {
            fieldAccess.addExpression(expression);
            text.append(expression.getText());
        }

        node.getName().accept(this);
        final ProgramElementInfo name = this.stack.pop(maxStackSize, ProgramElementInfo.class);
        if (name != null) {
            fieldAccess.addExpression(name);
            text.append(".");
            text.append(name.getText());
        }
        fieldAccess.setText(text.toString());

        return false;
    }

    // if ( InfixExpression ) 从ifBlock跳过来的 while for switch
    @Override
    public boolean visit(final InfixExpression node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo infixExpression = new ExpressionInfo(ExpressionInfo.CATEGORY.Infix,
				node, startLine, endLine);
        int maxStackSize = this.stack.push(infixExpression);

        final StringBuilder text = new StringBuilder();
        //text.append("if");
        text.append(" ( ");

        node.getLeftOperand().accept(this);
        final ProgramElementInfo left = this.stack.pop(maxStackSize, ProgramElementInfo.class);
        if (left != null) {
            infixExpression.addExpression(left);
            text.append(left.getText());
            text.append(" ");
        }

        final OperatorInfo operator = new OperatorInfo(node.getOperator().toString(),
                node.getOperator(), startLine, endLine);
        infixExpression.addExpression(operator);

        text.append(operator.getText());
        text.append(" ");

        node.getRightOperand().accept(this);
        final ProgramElementInfo right = this.stack.pop(maxStackSize, ProgramElementInfo.class);

        if (right != null) {
            infixExpression.addExpression(right);
            text.append(right.getText());
            text.append(" )");
        }

        if (node.hasExtendedOperands()) {
            for (final Object operand : node.extendedOperands()) {
                ((ASTNode) operand).accept(this);
                final ProgramElementInfo operandExpression = this.stack.pop(maxStackSize, ProgramElementInfo.class);
                if (operandExpression != null) {
                    infixExpression.addExpression(operator);
                    infixExpression.addExpression(operandExpression);
                    text.append(" ");
                    text.append(operator.getText());
                    text.append(" ");
                    text.append(operandExpression.getText());
                }
            }
        }
        infixExpression.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final ArrayCreation node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo arrayCreation = new ExpressionInfo(ExpressionInfo.CATEGORY.ArrayCreation,
				node, startLine, endLine);
        int maxStackSize = this.stack.push(arrayCreation);

        final StringBuilder text = new StringBuilder();
        text.append("new ");

        node.getType().accept(this);
        final ProgramElementInfo type = this.stack.pop(maxStackSize, ProgramElementInfo.class);

        if (type != null) {
            arrayCreation.addExpression(type);
            text.append(type.getText());
            text.append("[]");
        }

        if (null != node.getInitializer()) {
            node.getInitializer().accept(this);
            final ProgramElementInfo initializer = this.stack.pop(maxStackSize, ProgramElementInfo.class);
            if (initializer != null) {
                arrayCreation.addExpression(initializer);
                text.append(arrayCreation);
            }
        }
        arrayCreation.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final ArrayInitializer node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo initializer = new ExpressionInfo(ExpressionInfo.CATEGORY.ArrayInitializer,
				node, startLine, endLine);
        int maxStackSize = this.stack.push(initializer);

        final StringBuilder text = new StringBuilder();
        text.append("{");
        for (final Object expression : node.expressions()) {
            ((ASTNode) expression).accept(this);
            final ProgramElementInfo subexpression = this.stack.pop(maxStackSize, ProgramElementInfo.class);
            if (subexpression != null) {
                initializer.addExpression(subexpression);
                text.append(subexpression.getText());
                text.append(",");
            }
        }
        if (text.toString().endsWith(",")) {
            text.deleteCharAt(text.length() - 1);
        }
        text.append("}");
        initializer.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final BooleanLiteral node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ProgramElementInfo expression = new ExpressionInfo(ExpressionInfo.CATEGORY.Boolean,
                node, startLine, endLine);
        int maxStackSize = this.stack.push(expression);
        expression.setText(node.toString());

        return false;
    }

    @Override
    public boolean visit(final Assignment node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo assignment = new ExpressionInfo(ExpressionInfo.CATEGORY.Assignment,
                node, startLine, endLine);
        int maxStackSize = this.stack.push(assignment);

        final StringBuilder text = new StringBuilder();

        node.getLeftHandSide().accept(this);
        final ProgramElementInfo left = this.stack.pop(maxStackSize, ProgramElementInfo.class);
        if (left != null) {
            assignment.addExpression(left);
            text.append(left.getText());
        }

        final OperatorInfo operator = new OperatorInfo(node.getOperator().toString(),
                node.getOperator(), startLine, endLine);
        assignment.addExpression(operator);

        text.append(" ");
        text.append(operator.getText());
        text.append(" ");

        node.getRightHandSide().accept(this);
        final ProgramElementInfo right = this.stack.pop(maxStackSize, ProgramElementInfo.class);
        if (right != null) {
            assignment.addExpression(right);
            text.append(right.getText());
        }
        assignment.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final CastExpression node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo cast = new ExpressionInfo(ExpressionInfo.CATEGORY.Cast,
                node, startLine, endLine);
        int maxStackSize = this.stack.push(cast);

        final StringBuilder text = new StringBuilder();

        final TypeInfo type = new TypeInfo(node.getType().toString(), node.getType(), startLine, endLine);
        cast.addExpression(type);

        text.append("(");
        text.append(type.getText());
        text.append(")");

        node.getExpression().accept(this);
        final ProgramElementInfo expression = this.stack.pop(maxStackSize, ProgramElementInfo.class);
        if (expression != null) {
            cast.addExpression(expression);
            text.append(expression.getText());
        }

        cast.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final ClassInstanceCreation node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo classInstanceCreation = new ExpressionInfo(
                ExpressionInfo.CATEGORY.ClassInstanceCreation, node, startLine, endLine);
        int maxStackSize = this.stack.push(classInstanceCreation);

        final TypeInfo type = new TypeInfo(node.getType().toString(), node.getType(), startLine, endLine);
        classInstanceCreation.addExpression(type);

        final StringBuilder text = new StringBuilder();
        text.append("new ");
        text.append(type.getText());
        text.append("(");
        for (final Object argument : node.arguments()) {
            ((ASTNode) argument).accept(this);
            final ProgramElementInfo argumentExpression = this.stack.pop(maxStackSize, ProgramElementInfo.class);
            if (argumentExpression != null) {
                classInstanceCreation.addExpression(argumentExpression);
                text.append(argumentExpression.getText());
                text.append(",");
            }
        }
        if (text.toString().endsWith(",")) {
            text.deleteCharAt(text.length() - 1);
        }
        text.append(")");

        if (null != node.getExpression()) {
            node.getExpression().accept(this);
            final ProgramElementInfo expression = this.stack.pop(maxStackSize, ProgramElementInfo.class);
            if (expression != null) {
                classInstanceCreation.addExpression(expression);
                text.append(expression.getText());
            }
        }

        if (null != node.getAnonymousClassDeclaration()) {
            node.getAnonymousClassDeclaration().accept(this);
            final ClassInfo anonymousClass = this.stack.pop(maxStackSize, ClassInfo.class);
            if (anonymousClass != null) {
                classInstanceCreation.setAnonymousClassDeclaration(anonymousClass);
                text.append(anonymousClass.getText());
            }
        }

        classInstanceCreation.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final ConditionalExpression node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo trinomial = new ExpressionInfo(ExpressionInfo.CATEGORY.Trinomial,
				node, startLine, endLine);
        int maxStackSize = this.stack.push(trinomial);

        final StringBuilder text = new StringBuilder();
        
        node.getExpression().accept(this);
        final ProgramElementInfo expression = this.stack.pop(maxStackSize, ProgramElementInfo.class);
        if (expression != null) {
            trinomial.addExpression(expression);
            text.append(expression.getText());
            text.append("? ");
        }

        node.getThenExpression().accept(this);
        final ProgramElementInfo thenExpression = this.stack.pop(maxStackSize, ProgramElementInfo.class);
        if (thenExpression != null) {
            trinomial.addExpression(thenExpression);
            text.append(thenExpression.getText());
            text.append(": ");
        }

        node.getElseExpression().accept(this);
        final ProgramElementInfo elseExpression = this.stack.pop(maxStackSize, ProgramElementInfo.class);
        if (elseExpression != null) {
            trinomial.addExpression(elseExpression);
            text.append(elseExpression.getText());
        }
        
        trinomial.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final ConstructorInvocation node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);

        final ExpressionInfo invocation = new ExpressionInfo(
                ExpressionInfo.CATEGORY.ConstructorInvocation, node, startLine, endLine);
        int maxStackSize = this.stack.push(invocation);

        final StringBuilder text = new StringBuilder();
        text.append("this(");
        for (final Object argument : node.arguments()) {
            ((ASTNode) argument).accept(this);
            final ProgramElementInfo argumentExpression = this.stack.pop(maxStackSize, ProgramElementInfo.class);
            if (argumentExpression != null) {
                invocation.addExpression(argumentExpression);
                text.append(argumentExpression.getText());
                text.append(",");
            }
        }
        if (text.toString().endsWith(",")) {
            text.deleteCharAt(text.length() - 1);
        }
        text.append(")");
        invocation.setText(text.toString());

        this.stack.pop(maxStackSize, ProgramElementInfo.class);
        final ProgramElementInfo ownerBlock = this.stack.peek();
        final StatementInfo statement = new StatementInfo(ownerBlock, StatementInfo.CATEGORY.Expression,
				node, startLine, endLine);
        maxStackSize = this.stack.push(statement);

        statement.addExpression(invocation);

        text.append(";");
        statement.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final ExpressionStatement node) {
        if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {
            final int startLine = this.getStartLineNumber(node);
            final int endLine = this.getEndLineNumber(node);
            final ProgramElementInfo ownerBlock = this.stack.peek();
            final StatementInfo statement = new StatementInfo(ownerBlock, StatementInfo.CATEGORY.Expression,
					node, startLine, endLine);
            int maxStackSize = this.stack.push(statement);

            final StringBuilder text = new StringBuilder();

            node.getExpression().accept(this);
            final ProgramElementInfo expression = this.stack.pop(maxStackSize, ProgramElementInfo.class);
            if (expression != null) {
                statement.addExpression(expression);
                text.append(expression.getText());
                text.append(";");
            }

            statement.setText(text.toString());
        }

        return false;
    }

    @Override
    public boolean visit(final InstanceofExpression node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo instanceofExpression = new ExpressionInfo(ExpressionInfo.CATEGORY.Instanceof,
				node, startLine, endLine);
        int maxStackSize = this.stack.push(instanceofExpression);

        final StringBuilder text = new StringBuilder();

        node.getLeftOperand().accept(this);
        final ProgramElementInfo left = this.stack.pop(maxStackSize, ProgramElementInfo.class);
        if (left != null) {
            instanceofExpression.addExpression(left);
            text.append(left.getText());
        }
        node.getRightOperand().accept(this);
        final ProgramElementInfo right = this.stack.pop(maxStackSize, ProgramElementInfo.class);
        if (right != null) {
            instanceofExpression.addExpression(right);
            text.append(" instanceof ");
            text.append(right.getText());
        }

        instanceofExpression.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final MethodInvocation node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo methodInvocation = new ExpressionInfo(ExpressionInfo.CATEGORY.MethodInvocation,
				node, startLine, endLine);
        int maxStackSize = this.stack.push(methodInvocation);

        final StringBuilder text = new StringBuilder();

        if (null != node.getExpression()) {
            node.getExpression().accept(this);
            final ProgramElementInfo expression = this.stack.pop(maxStackSize, ProgramElementInfo.class);
            if (expression != null) {
                methodInvocation.setQualifier(expression);
                text.append(expression.getText());
                text.append(".");
            }
        }

        node.getName().accept(this);
        final ProgramElementInfo name = this.stack.pop(maxStackSize, ProgramElementInfo.class);
        if (name != null) {
            methodInvocation.addExpression(name);
            text.append(name.getText());
            text.append("(");
        }

        for (final Object argument : node.arguments()) {
            ((ASTNode) argument).accept(this);
            final ProgramElementInfo argumentExpression = this.stack.pop(maxStackSize, ProgramElementInfo.class);
            if (argumentExpression != null) {
                methodInvocation.addExpression(argumentExpression);
                text.append(argumentExpression.getText());
                text.append(",");
            }
        }
        if (text.toString().endsWith(",")) {
            text.deleteCharAt(text.length() - 1);
        }
        text.append(")");
        methodInvocation.setText(text.toString());
        Expression exp = node.getExpression();
        if (exp != null) {
            ITypeBinding typeBinding = exp.resolveTypeBinding();
            if (typeBinding != null) {
                methodInvocation.setApiName(typeBinding.getQualifiedName() + "." + node.getName() + "()");
            } else {
                methodInvocation.setApiName(exp.toString() + '.' + node.getName() + "()");
            }
        }
        //String exp = typeBinding.getQualifiedName();
        //methodInvocation.setiTypeBinding(node.getExpression().resolveTypeBinding());

        return false;
    }

    @Override
    public boolean visit(final ParenthesizedExpression node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo parenthesizedExpression = new ExpressionInfo(ExpressionInfo.CATEGORY.Parenthesized,
				node, startLine, endLine);
        int maxStackSize = this.stack.push(parenthesizedExpression);

        final StringBuilder text = new StringBuilder();

        node.getExpression().accept(this);
        final ProgramElementInfo expression = this.stack.pop(maxStackSize, ProgramElementInfo.class);
        if (expression != null) {
            parenthesizedExpression.addExpression(expression);

            text.append("(");
            text.append(expression.getText());
            text.append(")");
            parenthesizedExpression.setText(text.toString());
        }

        return false;
    }

    @Override
    public boolean visit(final ReturnStatement node) {
        if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {
            final int startLine = this.getStartLineNumber(node);
            final int endLine = this.getEndLineNumber(node);
            final ProgramElementInfo ownerBlock = this.stack.peek();
            final StatementInfo returnStatement = new StatementInfo(ownerBlock,
					StatementInfo.CATEGORY.Return, node, startLine, endLine);
            int maxStackSize = this.stack.push(returnStatement);

            final StringBuilder text = new StringBuilder();
            text.append("return");

            if (null != node.getExpression()) {
                node.getExpression().accept(this);
                final ProgramElementInfo expression = this.stack.pop(maxStackSize, ProgramElementInfo.class);
                if (expression != null) {
                    returnStatement.addExpression(expression);
                    text.append(" ");
                    text.append(expression.getText());
                }
            }

            text.append(";");
            returnStatement.setText(text.toString());
        }

        return false;
    }

    @Override
    public boolean visit(final SuperConstructorInvocation node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);

        final ExpressionInfo superConstructorInvocation = new ExpressionInfo(
                ExpressionInfo.CATEGORY.SuperConstructorInvocation, node, startLine, endLine);
        int maxStackSize = this.stack.push(superConstructorInvocation);

        final StringBuilder text = new StringBuilder();

        if (null != node.getExpression()) {
            node.getExpression().accept(this);
            final ProgramElementInfo qualifier = this.stack.pop(maxStackSize, ProgramElementInfo.class);
            if (qualifier != null) {
                superConstructorInvocation.setQualifier(qualifier);
                text.append(qualifier.getText());
                text.append(".super(");
            }
        } else {
            text.append("super(");
        }

        for (final Object argument : node.arguments()) {
            ((ASTNode) argument).accept(this);
            final ProgramElementInfo argumentExpression = this.stack.pop(maxStackSize, ProgramElementInfo.class);
            if (argumentExpression != null) {
                superConstructorInvocation.addExpression(argumentExpression);
                text.append(argumentExpression.getText());
                text.append(",");
            }
        }
        if (text.toString().endsWith(",")) {
            text.deleteCharAt(text.length() - 1);
        }
        text.append(")");
        superConstructorInvocation.setText(text.toString());

        this.stack.pop(maxStackSize, ProgramElementInfo.class);
        final ProgramElementInfo ownerBlock = this.stack.peek();
        final StatementInfo statement = new StatementInfo(ownerBlock,
                StatementInfo.CATEGORY.Expression, node, startLine, endLine);
        maxStackSize = this.stack.push(statement);

        statement.addExpression(superConstructorInvocation);
        text.append(";");
        statement.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final ThisExpression node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ProgramElementInfo expression = new ExpressionInfo(
                ExpressionInfo.CATEGORY.This, node, startLine, endLine);
        int maxStackSize = this.stack.push(expression);
        expression.setText("this");

        return false;
    }

    @Override
    public boolean visit(final VariableDeclarationExpression node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo vdExpression = new ExpressionInfo(
				ExpressionInfo.CATEGORY.VariableDeclarationExpression,
                node, startLine, endLine);
        int maxStackSize = this.stack.push(vdExpression);

        final TypeInfo type = new TypeInfo(node.getType().toString(), node.getType(), startLine, endLine);
        vdExpression.addExpression(type);

        final StringBuilder text = new StringBuilder();
        text.append(type.getText());
        text.append(" ");

        for (final Object fragment : node.fragments()) {
            ((ASTNode) fragment).accept(this);
            final ProgramElementInfo fragmentExpression = this.stack.pop(maxStackSize, ProgramElementInfo.class);
            if (fragmentExpression != null) {
                vdExpression.addExpression(fragmentExpression);
                text.append(fragmentExpression.getText());
            }
        }

        vdExpression.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final VariableDeclarationStatement node) {
        if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {
            final int startLine = this.getStartLineNumber(node);
            final int endLine = this.getEndLineNumber(node);
            final ProgramElementInfo ownerBlock = this.stack.peek();
            final StatementInfo vdStatement = new StatementInfo(ownerBlock,
                    StatementInfo.CATEGORY.VariableDeclaration, node, startLine, endLine);
            int maxStackSize = this.stack.push(vdStatement);

            final StringBuilder text = new StringBuilder();
            for (final Object modifier : node.modifiers()) {
                text.append(modifier.toString());
                text.append(" ");
            }

            final ProgramElementInfo type = new TypeInfo(node.getType().toString(), node.getType(), startLine, endLine);
            vdStatement.addExpression(type);

            text.append(node.getType().toString());
            text.append(" ");

            boolean anyExpression = false;
            for (final Object fragment : node.fragments()) {
                anyExpression = true;
                ((ASTNode) fragment).accept(this);
                final ProgramElementInfo fragmentExpression = this.stack.pop(maxStackSize, ProgramElementInfo.class);
                if (fragmentExpression != null) {
                    vdStatement.addExpression(fragmentExpression);
                    text.append(fragmentExpression.getText()).append(",");
                }
            }
            if (text.toString().endsWith(",")) {
                text.deleteCharAt(text.length() - 1);
            }

            text.append(";");
            vdStatement.setText(text.toString());
        }

        return false;
    }

    @Override
    public boolean visit(final VariableDeclarationFragment node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo vdFragment = new ExpressionInfo(ExpressionInfo.CATEGORY.VariableDeclarationFragment,
				node, startLine, endLine);
        int maxStackSize = this.stack.push(vdFragment);

        final StringBuilder text = new StringBuilder();

        node.getName().accept(this);
        final ProgramElementInfo name = this.stack.pop(maxStackSize, ProgramElementInfo.class);
        if (name != null) {
            vdFragment.addExpression(name);
            text.append(name.getText());
        }

        if (null != node.getInitializer()) {
            node.getInitializer().accept(this);
            final ProgramElementInfo expression = this.stack.pop(maxStackSize, ProgramElementInfo.class);
            if (expression != null) {
                vdFragment.addExpression(expression);

                text.append(" = ");
                text.append(expression.getText());
            }
        }

        vdFragment.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final DoStatement node) {
        if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {
            final int startLine = this.getStartLineNumber(node);
            final int endLine = this.getEndLineNumber(node);
            final ProgramElementInfo ownerBlock = this.stack.peek();
            final StatementInfo doBlock = new StatementInfo(ownerBlock,
                    StatementInfo.CATEGORY.Do, node, startLine, endLine);
            int maxStackSize = this.stack.push(doBlock);

            final StringBuilder text = new StringBuilder();

            node.getBody().accept(this);
            final StatementInfo body = this.stack.pop(maxStackSize, StatementInfo.class);

            text.append("do ");
            if (body != null) {
                doBlock.setStatement(body);
                text.append(body.getText());
            }

            node.getExpression().accept(this);
            final ProgramElementInfo condition = this.stack.pop(maxStackSize, ProgramElementInfo.class);
            if (condition != null) {
                doBlock.setCondition(condition);
                condition.setOwnerConditinalBlock(doBlock);

                text.append("while (");
                text.append(condition.getText());
                text.append(");");
            }
            
            doBlock.setText(text.toString());
        }

        return false;
    }

    @Override
    public boolean visit(final EnhancedForStatement node) {
        if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {
            int maxStackSize = this.stack.size();

            final StringBuilder text = new StringBuilder();
            text.append("for (");

            node.getParameter().accept(this);
            final ProgramElementInfo parameter = this.stack.pop(maxStackSize, ProgramElementInfo.class);
            if (parameter != null) {
                text.append(parameter.getText());
                text.append(" : ");
            }

            node.getExpression().accept(this);
            final ProgramElementInfo expression = this.stack.pop(maxStackSize, ProgramElementInfo.class);
            if (expression != null) {
                text.append(expression.getText());
                text.append(")");
            }

            final int startLine = this.getStartLineNumber(node);
            final int endLine = this.getEndLineNumber(node);
            final ProgramElementInfo ownerBlock = this.stack.peek();
            final StatementInfo foreachBlock = new StatementInfo(ownerBlock, StatementInfo.CATEGORY.Foreach,
					node, startLine, endLine);
            foreachBlock.addInitializer(parameter);
            foreachBlock.addInitializer(expression);
            maxStackSize = this.stack.push(foreachBlock);

            node.getBody().accept(this);
            final StatementInfo body = this.stack.pop(maxStackSize, StatementInfo.class);
            if (body != null) {
                foreachBlock.setStatement(body);
                text.append(body.getText());
            }

            foreachBlock.setText(text.toString());
        }

        return false;
    }

    @Override
    public boolean visit(final ForStatement node) {
        if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {
            final int startLine = this.getStartLineNumber(node);
            final int endLine = this.getEndLineNumber(node);
            final ProgramElementInfo ownerBlock = this.stack.peek();
            final StatementInfo forBlock = new StatementInfo(ownerBlock, StatementInfo.CATEGORY.For,
					node, startLine, endLine);
            int maxStackSize = this.stack.push(forBlock);

            final StringBuilder text = new StringBuilder();
            text.append("for (");

            for (final Object o : node.initializers()) {
                ((ASTNode) o).accept(this);
                final ExpressionInfo initializer = this.stack.pop(maxStackSize, ExpressionInfo.class);
                if (initializer != null) {
                    forBlock.addInitializer(initializer);
                    text.append(initializer.getText());
                    text.append(",");
                }
            }
            if (text.toString().endsWith(",")) {
                text.deleteCharAt(text.length() - 1);
            }

            text.append("; ");

            if (null != node.getExpression()) {
                node.getExpression().accept(this);
                final ProgramElementInfo condition = this.stack.pop(maxStackSize, ProgramElementInfo.class);
                if (condition != null) {
                    forBlock.setCondition(condition);
                    condition.setOwnerConditinalBlock(forBlock);
                    text.append(condition.getText());
                }
            }

            text.append("; ");

            for (final Object o : node.updaters()) {
                ((ASTNode) o).accept(this);
                final ExpressionInfo updater = this.stack.pop(maxStackSize, ExpressionInfo.class);
                if (updater != null) {
                    forBlock.addUpdater(updater);
                    text.append(updater.getText());
                    text.append(",");
                }
            }
            if (text.toString().endsWith(",")) {
                text.deleteCharAt(text.length() - 1);
            }

            text.append(")");

            node.getBody().accept(this);
            final StatementInfo body = this.stack.pop(maxStackSize, StatementInfo.class);
            if (body != null) {
                forBlock.setStatement(body);
                text.append(body.getText());
                forBlock.setText(text.toString());
            }
        }

        return false;
    }

    // if 块
    @Override
    public boolean visit(final IfStatement node) {
        if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {
            final int startLine = this.getStartLineNumber(node);
            final int endLine = this.getEndLineNumber(node);
            final ProgramElementInfo ownerBlock = this.stack.peek();
            final StatementInfo ifBlock = new StatementInfo(ownerBlock, StatementInfo.CATEGORY.If,
					node, startLine, endLine);
            int maxStackSize = this.stack.push(ifBlock);

            final StringBuilder text = new StringBuilder();
            text.append("if (");
            
            node.getExpression().accept(this);
            final ProgramElementInfo condition = this.stack.pop(maxStackSize, ProgramElementInfo.class);
            if (condition != null) {
                ifBlock.setCondition(condition);
                condition.setOwnerConditinalBlock(ifBlock);
                text.append(condition.getText());
                condition.setText("if " + condition.getText()); //hj 加
            }
            
            text.append(") ");

            if (null != node.getThenStatement()) {
                node.getThenStatement().accept(this);
                final StatementInfo thenBody = this.stack.pop(maxStackSize, StatementInfo.class);
                if (thenBody != null) {
                    ifBlock.setStatement(thenBody);
                    text.append(thenBody.getText());
                }
            }

            if (null != node.getElseStatement()) {
                node.getElseStatement().accept(this);
                final StatementInfo elseBody = this.stack.pop(maxStackSize, StatementInfo.class);
                if (elseBody != null) {
                    ifBlock.setElseStatement(elseBody);
                    text.append(elseBody.getText());
                }
            }

            ifBlock.setText(text.toString());
        }

        return false;
    }

    @Override
    public boolean visit(final SwitchStatement node) {
        if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {
            final int startLine = this.getStartLineNumber(node);
            final int endLine = this.getEndLineNumber(node);
            final ProgramElementInfo ownerBlock = this.stack.peek();
            final StatementInfo switchBlock = new StatementInfo(ownerBlock,
                    StatementInfo.CATEGORY.Switch, node, startLine, endLine);
            int maxStackSize = this.stack.push(switchBlock);
            
            final StringBuilder text = new StringBuilder();
            text.append("switch (");

            node.getExpression().accept(this);
            final ProgramElementInfo condition = this.stack.pop(maxStackSize, ProgramElementInfo.class);
            if (condition != null) {
                switchBlock.setCondition(condition);
                condition.setOwnerConditinalBlock(switchBlock);
                text.append(condition.getText());
            }
            
            text.append(") {");
            text.append(lineSeparator);

            for (final Object o : node.statements()) {
                ((ASTNode) o).accept(this);
                final StatementInfo statement = this.stack.pop(maxStackSize, StatementInfo.class);
                if (statement != null) {
                    switchBlock.addStatement(statement);
                    text.append(statement.getText());
                    text.append(lineSeparator);
                }
            }

            switchBlock.setText(text.toString());
        }

        return false;
    }

    @Override
    public boolean visit(final SynchronizedStatement node) {
        if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {
            final int startLine = this.getStartLineNumber(node);
            final int endLine = this.getEndLineNumber(node);
            final ProgramElementInfo ownerBlock = this.stack.peek();
            final StatementInfo synchronizedBlock = new StatementInfo(
                    ownerBlock, StatementInfo.CATEGORY.Synchronized, node, startLine, endLine);
            int maxStackSize = this.stack.push(synchronizedBlock);

            final StringBuilder text = new StringBuilder();
            text.append("synchronized (");
            
            node.getExpression().accept(this);
            final ProgramElementInfo condition = this.stack.pop(maxStackSize, ProgramElementInfo.class);
            if (condition != null) {
                synchronizedBlock.setCondition(condition);
                condition.setOwnerConditinalBlock(synchronizedBlock);
                text.append(condition.getText());
            }
            
            text.append(") ");

            node.getBody().accept(this);
            final StatementInfo body = this.stack.pop(maxStackSize, StatementInfo.class);
            if (body != null) {
                synchronizedBlock.setStatement(body);
                text.append(body.getText());
            }

            synchronizedBlock.setText(text.toString());
        }

        return false;
    }

    @Override
    public boolean visit(final ThrowStatement node) {
        if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {
            final int startLine = this.getStartLineNumber(node);
            final int endLine = this.getEndLineNumber(node);
            final ProgramElementInfo ownerBlock = this.stack.peek();
            final StatementInfo throwStatement = new StatementInfo(ownerBlock, StatementInfo.CATEGORY.Throw,
					node, startLine, endLine);
            int maxStackSize = this.stack.push(throwStatement);

            final StringBuilder text = new StringBuilder();
            
            node.getExpression().accept(this);
            final ProgramElementInfo expression = this.stack.pop(maxStackSize, ProgramElementInfo.class);
            text.append("throw ");
            if (expression != null) {
                throwStatement.addExpression(expression);
                text.append(expression.getText());
            }
            text.append(";");
            
            throwStatement.setText(text.toString());
        }

        return false;
    }

    @Override
    public boolean visit(final TryStatement node) {
        if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {
            final int startLine = this.getStartLineNumber(node);
            final int endLine = this.getEndLineNumber(node);
            final ProgramElementInfo ownerBlock = this.stack.peek();
            final StatementInfo tryBlock = new StatementInfo(ownerBlock, StatementInfo.CATEGORY.Try,
					node, startLine, endLine);
            int maxStackSize = this.stack.push(tryBlock);

            node.getBody().accept(this);

            final StringBuilder text = new StringBuilder();
            text.append("try ");

            final StatementInfo body = this.stack.pop(maxStackSize, StatementInfo.class);
            if (body != null) {
                tryBlock.setStatement(body);
                text.append(body.getText());
            }

            for (final Object o : node.catchClauses()) {
                ((ASTNode) o).accept(this);
                final StatementInfo catchBlock = this.stack.pop(maxStackSize, StatementInfo.class);
                if (catchBlock != null) {
                    tryBlock.addCatchStatement(catchBlock);
                    text.append(catchBlock.getText());
                }
            }

            if (null != node.getFinally()) {
                node.getFinally().accept(this);
                final StatementInfo finallyBlock = this.stack.pop(maxStackSize, StatementInfo.class);
                if (finallyBlock != null) {
                    tryBlock.setFinallyStatement(finallyBlock);
                    text.append(finallyBlock.getText());
                }
            }

            tryBlock.setText(text.toString());
        }

        return false;
    }

    @Override
    public boolean visit(final WhileStatement node) {
        if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {
            final int startLine = this.getStartLineNumber(node);
            final int endLine = this.getEndLineNumber(node);
            final ProgramElementInfo ownerBlock = this.stack.peek();
            final StatementInfo whileBlock = new StatementInfo(ownerBlock, StatementInfo.CATEGORY.While,
					node, startLine, endLine);
            int maxStackSize = this.stack.push(whileBlock);

            final StringBuilder text = new StringBuilder();
            
            node.getExpression().accept(this);
            final ProgramElementInfo condition = this.stack.pop(maxStackSize, ProgramElementInfo.class);
            text.append("while (");
            if (condition != null) {
                whileBlock.setCondition(condition);
                condition.setOwnerConditinalBlock(whileBlock);
                text.append(condition.getText());
                condition.setText("while " + condition.getText());
            }
            text.append(") ");

            node.getBody().accept(this);
            StatementInfo body = this.stack.pop(maxStackSize, StatementInfo.class);
            if (body != null) {
                whileBlock.setStatement(body);
                text.append(body.getText());
            }

            //hj
            whileBlock.setText(text.toString());
        }

        return false;
    }

    @Override
    public boolean visit(final SwitchCase node) {
        if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {
            final int startLine = this.getStartLineNumber(node);
            final int endLine = this.getEndLineNumber(node);
            final ProgramElementInfo ownerBlock = this.stack.peek();
            final StatementInfo switchCase = new StatementInfo(ownerBlock,
                    StatementInfo.CATEGORY.Case, node, startLine, endLine);
            int maxStackSize = this.stack.push(switchCase);

            final StringBuilder text = new StringBuilder();

            for (Object exprObj : node.expressions()) {
                if (exprObj instanceof ASTNode expr) {
                    expr.accept(this);
                    final ProgramElementInfo expression = this.stack.pop(maxStackSize, ProgramElementInfo.class);
                    if (expression != null) {
                        switchCase.addExpression(expression);

                        text.append("case ");
                        text.append(expression.getText());
                    }
                } else {
                    text.append("default");
                }
            }


            text.append(":");
            switchCase.setText(text.toString());
        }

        return false;
    }

    @Override
    public boolean visit(final BreakStatement node) {
        if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {
            final int startLine = this.getStartLineNumber(node);
            final int endLine = this.getEndLineNumber(node);
            final ProgramElementInfo ownerBlock = this.stack.peek();
            final StatementInfo breakStatement = new StatementInfo(ownerBlock,
                    StatementInfo.CATEGORY.Break, node, startLine, endLine);
            int maxStackSize = this.stack.push(breakStatement);

            final StringBuilder text = new StringBuilder();
            text.append("break");

            if (null != node.getLabel()) {
                node.getLabel().accept(this);
                final ProgramElementInfo label = this.stack.pop(maxStackSize, ProgramElementInfo.class);
                if (label != null) {
                    breakStatement.addExpression(label);

                    text.append(" ");
                    text.append(label.getText());
                }
            }

            text.append(";");
            breakStatement.setText(text.toString());
        }

        return false;
    }

    @Override
    public boolean visit(final ContinueStatement node) {
        if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {
            final int startLine = this.getStartLineNumber(node);
            final int endLine = this.getEndLineNumber(node);
            final ProgramElementInfo ownerBlock = this.stack.peek();
            final StatementInfo continuekStatement = new StatementInfo(
                    ownerBlock, StatementInfo.CATEGORY.Continue, node, startLine, endLine);
            int maxStackSize = this.stack.push(continuekStatement);

            final StringBuilder text = new StringBuilder();
            text.append("continue");

            if (null != node.getLabel()) {
                node.getLabel().accept(this);
                final ProgramElementInfo label = this.stack.pop(maxStackSize, ProgramElementInfo.class);
                if (label != null) {
                    continuekStatement.addExpression(label);

                    text.append(" ");
                    text.append(label.getText());
                }
            }

            text.append(";");
            continuekStatement.setText(text.toString());
        }
        return false;
    }

    @Override
    public boolean visit(final LabeledStatement node) {
        node.getBody().accept(this);
        final StatementInfo statement = (StatementInfo) this.stack.peek();

        final String label = node.getLabel().toString();
        statement.setLabel(label);

        return false;
    }

    @Override
    public boolean visit(final Block node) {
        if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {
            final int startLine = this.getStartLineNumber(node);
            final int endLine = this.getEndLineNumber(node);
            final ProgramElementInfo ownerBlock = this.stack.peek();
            final StatementInfo simpleBlock = new StatementInfo(ownerBlock,
                    StatementInfo.CATEGORY.SimpleBlock, node, startLine, endLine);
            int maxStackSize = this.stack.push(simpleBlock);

            final StringBuilder text = new StringBuilder();
            text.append("{");
            text.append(lineSeparator);

            for (final Object o : node.statements()) {
                ((ASTNode) o).accept(this);
                final StatementInfo statement = this.stack.pop(maxStackSize, StatementInfo.class);
                if (statement != null) {
                    simpleBlock.addStatement(statement);
                    text.append(statement.getText());
                    text.append(lineSeparator);
                }
            }

            text.append("}");
            simpleBlock.setText(text.toString());
        }
        return false;
    }

    // catch block
    @Override
    public boolean visit(final CatchClause node) {
        if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {
            final int startLine = this.getStartLineNumber(node);
            final int endLine = this.getEndLineNumber(node);
            final ProgramElementInfo ownerBlock = this.stack.peek();
            final StatementInfo catchBlock = new StatementInfo(ownerBlock,
                    StatementInfo.CATEGORY.Catch, node, startLine, endLine);
            int maxStackSize = this.stack.push(catchBlock);

            final StringBuilder text = new StringBuilder();
            text.append("catch (");
            
            node.getException().accept(this);
            final ProgramElementInfo exception = this.stack.pop(maxStackSize, ProgramElementInfo.class);
            if (exception != null) {
                exception.setOwnerConditinalBlock(catchBlock);
                catchBlock.setCondition(exception);
                text.append(exception.getText());
            }
            
            text.append(") ");

            node.getBody().accept(this);
            final StatementInfo body = this.stack.pop(maxStackSize, StatementInfo.class);
            if (body != null) {
                catchBlock.setStatement(body);
            }
            
            text.append(catchBlock.getText());
            catchBlock.setText(text.toString());
        }

        return false;
    }

    @Override
    public boolean visit(final SingleVariableDeclaration node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final TypeInfo type = new TypeInfo(node.getType().toString(),
                node.getType(), startLine, endLine);
        final String name = node.getName().toString();
        final VariableInfo variable = new VariableInfo(
                VariableInfo.CATEGORY.LOCAL, type, name, node, startLine, endLine);
        int maxStackSize = this.stack.push(variable);

        final StringBuilder text = new StringBuilder();
        for (final Object modifier : node.modifiers()) {
            variable.addModifier(modifier.toString());
            text.append(modifier);
            text.append(" ");
        }
        //hj 加
        if (node.getParent() instanceof CatchClause) {
            text.append("catch ( ");
            text.append(type.getText());
            text.append(" ");
            text.append(name);
            text.append(" )");
            variable.setText(text.toString());
        } else {
            text.append(type.getText());
            text.append(" ");
            text.append(name);
            variable.setText(text.toString());
        }
        return false;
    }

    @Override
    public boolean visit(final EmptyStatement node) {
        if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {
            final int startLine = this.getStartLineNumber(node);
            final int endLine = this.getEndLineNumber(node);
            final ProgramElementInfo ownerBlock = this.stack.peek();
            final StatementInfo emptyStatement = new StatementInfo(ownerBlock,
                    StatementInfo.CATEGORY.Empty, node, startLine, endLine);
            int maxStackSize = this.stack.push(emptyStatement);
            emptyStatement.setText(";");
        }

        return false;
    }

}
