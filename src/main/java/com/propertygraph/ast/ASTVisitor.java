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

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

@Slf4j
public class ASTVisitor extends NaiveASTFlattener {

    private static final String lineSeparator = System.lineSeparator();

    public static CompilationUnit createAST(final String source) {
        final ASTParser parser = ASTParser.newParser(AST.JLS19);
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

    final private Stack<ProgramElementInfo> stack = new Stack<>();

    public ASTVisitor(final CompilationUnit root) {
        this.root = root;
    }

    //class
    @Override
    public boolean visit(final TypeDeclaration node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ClassInfo typeDeclaration = new ClassInfo(node.getName().toString(), node, startLine, endLine);
        this.stack.push(typeDeclaration);

        final StringBuilder text = new StringBuilder();
        text.append("class ");
        text.append(node.getName().toString());
        text.append("{");
        text.append(lineSeparator);
        for (final Object o : node.bodyDeclarations()) {
            if (o instanceof MethodDeclaration) {
                ((ASTNode) o).accept(this);
                final ProgramElementInfo method = this.stack.pop();
                this.methods.add((MethodInfo) method);
                typeDeclaration.addMethod((MethodInfo) method);
                text.append(method.getText());
                text.append(lineSeparator);
            }
        }
        text.append("}");
        typeDeclaration.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final TypeDeclarationStatement node) {
        if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {
            final int startLine = this.getStartLineNumber(node);
            final int endLine = this.getEndLineNumber(node);
            final ProgramElementInfo ownerBlock = this.stack.peek();
            final StatementInfo statement = new StatementInfo(ownerBlock,
                    StatementInfo.CATEGORY.TypeDeclaration, node, startLine, endLine);
            this.stack.push(statement);

            node.getDeclaration().accept(this);
            final ProgramElementInfo typeDeclaration = this.stack.pop();
            statement.addExpression(typeDeclaration);

            statement.setText(typeDeclaration.getText());
        }

        return false;
    }

    @Override
    public boolean visit(final AnnotationTypeDeclaration node) {
        for (final Object o : node.bodyDeclarations()) {
            ((ASTNode) o).accept(this);
            final ProgramElementInfo method = this.stack.pop();
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
        this.stack.push(anonymousClass);

        for (final Object o : node.bodyDeclarations()) {
            if (o instanceof MethodDeclaration) {
                ((ASTNode) o).accept(this); // class 到 method
                final ProgramElementInfo method = this.stack.pop();
                anonymousClass.addMethod((MethodInfo) method);
                text.append(method.getText());
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
        final MethodInfo method = new MethodInfo(name, node, startLine, endLine);
        this.stack.push(method);

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
            final VariableInfo parameter = (VariableInfo) this.stack.pop();
            parameter.setCategory(VariableInfo.CATEGORY.PARAMETER);
            method.addParameter(parameter);
            text.append(parameter.getText());
            text.append(" ,");
        }
        if (!node.parameters().isEmpty()) {
            text.deleteCharAt(text.length() - 1);
        }
        text.append(" ) ");

        if (null != node.getBody()) {
            node.getBody().accept(this);
            final ProgramElementInfo body = this.stack.pop();
            method.setStatement((StatementInfo) body);
            text.append(body.getText());
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
            node.getExpression().accept(this);
            final ProgramElementInfo expression = this.stack.pop();

            node.getMessage().accept(this);
            final ProgramElementInfo message = this.stack.pop();

            final int startLine = this.getStartLineNumber(node);
            final int endLine = this.getEndLineNumber(node);
            final ProgramElementInfo ownerBlock = this.stack.peek();
            final StatementInfo statement = new StatementInfo(ownerBlock,
                    StatementInfo.CATEGORY.Assert, node, startLine, endLine);
            statement.addExpression(expression);
            statement.addExpression(message);
            this.stack.push(statement);
        }

        return false;
    }

    @Override
    public boolean visit(final ArrayAccess node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo expression = new ExpressionInfo(ExpressionInfo.CATEGORY.ArrayAccess,
                node, startLine, endLine);
        this.stack.push(expression);

        node.getArray().accept(this);
        final ProgramElementInfo array = this.stack.pop();
        expression.addExpression(array);

        node.getIndex().accept(this);
        final ProgramElementInfo index = this.stack.pop();
        expression.addExpression(index);

        final StringBuilder text = new StringBuilder();
        text.append(array.getText());
        text.append("[");
        text.append(index.getText());
        text.append("]");
        expression.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final ArrayType node) {
        final StringBuffer text = new StringBuffer();
        text.append(node.getElementType().toString());
        text.append("[]".repeat(Math.max(0, node.getDimensions())));
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final TypeInfo type = new TypeInfo(text.toString(), node, startLine, endLine);
        this.stack.push(type);

        return false;
    }

    @Override
    public boolean visit(final NullLiteral node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ProgramElementInfo expression = new ExpressionInfo(ExpressionInfo.CATEGORY.Null,
                node, startLine, endLine);
        expression.setText("null");
        this.stack.push(expression);

        return false;
    }

    @Override
    public boolean visit(final NumberLiteral node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ProgramElementInfo expression = new ExpressionInfo(ExpressionInfo.CATEGORY.Number,
                node, startLine, endLine);
        expression.setText(node.getToken());
        this.stack.push(expression);

        return false;
    }

    @Override
    public boolean visit(final PostfixExpression node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo postfixExpression = new ExpressionInfo(ExpressionInfo.CATEGORY.Postfix,
                node, startLine, endLine);
        this.stack.push(postfixExpression);

        node.getOperand().accept(this);
        final ProgramElementInfo operand = this.stack.pop();
        postfixExpression.addExpression(operand);

        final OperatorInfo operator = new OperatorInfo(node.getOperator().toString(),
                node, startLine, endLine);
        postfixExpression.addExpression(operator);

        final StringBuilder text = new StringBuilder();
        text.append(operand.getText());
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
        this.stack.push(prefixExpression);

        final OperatorInfo operator = new OperatorInfo(node.getOperator().toString(),
                node.getOperator(), startLine, endLine);
        prefixExpression.addExpression(operator);

        node.getOperand().accept(this);
        final ProgramElementInfo operand = this.stack.pop();
        prefixExpression.addExpression(operand);

        final StringBuilder text = new StringBuilder();
        text.append(operator.getText());
        text.append(operand.getText());
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
        this.stack.push(expression);

        return false;
    }

    @Override
    public boolean visit(final SuperFieldAccess node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo superFieldAccess = new ExpressionInfo(ExpressionInfo.CATEGORY.SuperFieldAccess,
				node, startLine, endLine);
        this.stack.push(superFieldAccess);

        node.getName().accept(this);
        final ProgramElementInfo name = this.stack.pop();
        superFieldAccess.addExpression(name);

        final StringBuilder text = new StringBuilder();
        text.append("super.");
        text.append(name.getText());
        superFieldAccess.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final SuperMethodInvocation node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo superMethodInvocation = new ExpressionInfo(ExpressionInfo.CATEGORY.SuperMethodInvocation,
				node, startLine, endLine);
        this.stack.push(superMethodInvocation);

        node.getName().accept(this);
        final ProgramElementInfo name = this.stack.pop();
        superMethodInvocation.addExpression(name);

        final StringBuilder text = new StringBuilder();
        text.append("super.");
        text.append(name);
        for (final Object argument : node.arguments()) {
            ((ASTNode) argument).accept(this);
            final ProgramElementInfo argumentExpression = this.stack.pop();
            superMethodInvocation.addExpression(argumentExpression);
            text.append(argumentExpression.getText());
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
        this.stack.push(expression);

        return false;
    }

    @Override
    public boolean visit(final QualifiedName node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo qualifiedName = new ExpressionInfo(ExpressionInfo.CATEGORY.QualifiedName,
                node, startLine, endLine);
        this.stack.push(qualifiedName);

        node.getQualifier().accept(this);
        final ProgramElementInfo qualifier = this.stack.pop();
        qualifiedName.setQualifier(qualifier);

        node.getName().accept(this);
        final ProgramElementInfo name = this.stack.pop();
        qualifiedName.addExpression(name);

        final StringBuilder text = new StringBuilder();
        text.append(qualifier.getText());
        text.append(".");
        text.append(name.getText());
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
        this.stack.push(simpleName);

        return false;
    }

    @Override
    public boolean visit(final CharacterLiteral node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ProgramElementInfo expression = new ExpressionInfo(ExpressionInfo.CATEGORY.Character,
                node, startLine, endLine);
        expression.setText("'" + node.charValue() + "'");
        this.stack.push(expression);

        return false;
    }

    @Override
    public boolean visit(final FieldAccess node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo fieldAccess = new ExpressionInfo(ExpressionInfo.CATEGORY.FieldAccess,
                node, startLine, endLine);
        this.stack.push(fieldAccess);

        node.getExpression().accept(this);
        final ProgramElementInfo expression = this.stack.pop();
        fieldAccess.addExpression(expression);

        node.getName().accept(this);
        final ProgramElementInfo name = this.stack.pop();
        fieldAccess.addExpression(name);

        final StringBuilder text = new StringBuilder();
        text.append(expression.getText());
        text.append(".");
        text.append(name.getText());
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
        this.stack.push(infixExpression);

        node.getLeftOperand().accept(this);
        final ProgramElementInfo left = this.stack.pop();
        infixExpression.addExpression(left);

        final OperatorInfo operator = new OperatorInfo(node.getOperator().toString(),
                node.getOperator(), startLine, endLine);
        infixExpression.addExpression(operator);

        node.getRightOperand().accept(this);
        final ProgramElementInfo right = this.stack.pop();
        infixExpression.addExpression(right);

        final StringBuilder text = new StringBuilder();
        //text.append("if");
        text.append(" ( ");
        text.append(left.getText());
        text.append(" ");
        text.append(operator.getText());
        text.append(" ");
        text.append(right.getText());
        text.append(" )");

        if (node.hasExtendedOperands()) {
            for (final Object operand : node.extendedOperands()) {
                ((ASTNode) operand).accept(this);
                final ProgramElementInfo operandExpression = this.stack.pop();
                infixExpression.addExpression(operator);
                infixExpression.addExpression(operandExpression);
                text.append(" ");
                text.append(operator.getText());
                text.append(" ");
                text.append(operandExpression.getText());
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
        this.stack.push(arrayCreation);

        node.getType().accept(this);
        final ProgramElementInfo type = this.stack.pop();
        arrayCreation.addExpression(type);

        final StringBuilder text = new StringBuilder();
        text.append("new ");
        text.append(type.getText());
        text.append("[]");

        if (null != node.getInitializer()) {
            node.getInitializer().accept(this);
            final ProgramElementInfo initializer = this.stack.pop();
            arrayCreation.addExpression(initializer);
            text.append(arrayCreation);
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
        this.stack.push(initializer);

        final StringBuilder text = new StringBuilder();
        text.append("{");
        for (final Object expression : node.expressions()) {
            ((ASTNode) expression).accept(this);
            final ProgramElementInfo subexpression = this.stack.pop();
            initializer.addExpression(subexpression);
            text.append(subexpression.getText());
            text.append(",");
        }
        if (!node.expressions().isEmpty()) {
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
        this.stack.push(expression);
        expression.setText(node.toString());

        return false;
    }

    @Override
    public boolean visit(final Assignment node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo assignment = new ExpressionInfo(ExpressionInfo.CATEGORY.Assignment,
                node, startLine, endLine);
        this.stack.push(assignment);

        node.getLeftHandSide().accept(this);
        final ProgramElementInfo left = this.stack.pop();
        assignment.addExpression(left);

        final OperatorInfo operator = new OperatorInfo(node.getOperator().toString(),
                node.getOperator(), startLine, endLine);
        assignment.addExpression(operator);

        node.getRightHandSide().accept(this);
        final ProgramElementInfo right = this.stack.pop();
        assignment.addExpression(right);

        final StringBuilder text = new StringBuilder();
        text.append(left.getText());
        text.append(" ");
        text.append(operator.getText());
        text.append(" ");
        text.append(right.getText());
        assignment.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final CastExpression node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo cast = new ExpressionInfo(ExpressionInfo.CATEGORY.Cast,
                node, startLine, endLine);
        this.stack.push(cast);

        final TypeInfo type = new TypeInfo(node.getType().toString(), node.getType(), startLine, endLine);
        cast.addExpression(type);

        node.getExpression().accept(this);
        final ProgramElementInfo expression = this.stack.pop();
        cast.addExpression(expression);

        final StringBuilder text = new StringBuilder();
        text.append("(");
        text.append(type.getText());
        text.append(")");
        text.append(expression.getText());
        cast.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final ClassInstanceCreation node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo classInstanceCreation = new ExpressionInfo(
                ExpressionInfo.CATEGORY.ClassInstanceCreation, node, startLine, endLine);
        this.stack.push(classInstanceCreation);

        final TypeInfo type = new TypeInfo(node.getType().toString(), node.getType(), startLine, endLine);
        classInstanceCreation.addExpression(type);

        final StringBuilder text = new StringBuilder();
        text.append("new ");
        text.append(type.getText());
        text.append("(");
        for (final Object argument : node.arguments()) {
            ((ASTNode) argument).accept(this);
            final ProgramElementInfo argumentExpression = this.stack.pop();
            classInstanceCreation.addExpression(argumentExpression);

            text.append(argumentExpression.getText());
            text.append(",");
        }
        if (!node.arguments().isEmpty()) {
            text.deleteCharAt(text.length() - 1);
        }
        text.append(")");

        if (null != node.getExpression()) {
            node.getExpression().accept(this);
            final ProgramElementInfo expression = this.stack.pop();
            classInstanceCreation.addExpression(expression);
            text.append(expression.getText());
        }

        if (null != node.getAnonymousClassDeclaration()) {
            node.getAnonymousClassDeclaration().accept(this);
            final ProgramElementInfo anonymousClass = this.stack.pop();
            classInstanceCreation.setAnonymousClassDeclaration((ClassInfo) anonymousClass);
            text.append(anonymousClass.getText());
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
        this.stack.push(trinomial);

        node.getExpression().accept(this);
        final ProgramElementInfo expression = this.stack.pop();
        trinomial.addExpression(expression);

        node.getThenExpression().accept(this);
        final ProgramElementInfo thenExpression = this.stack.pop();
        trinomial.addExpression(thenExpression);

        node.getElseExpression().accept(this);
        final ProgramElementInfo elseExpression = this.stack.pop();
        trinomial.addExpression(elseExpression);

        final StringBuilder text = new StringBuilder();
        text.append(expression.getText());
        text.append("? ");
        text.append(thenExpression.getText());
        text.append(": ");
        text.append(elseExpression.getText());
        trinomial.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final ConstructorInvocation node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);

        final ExpressionInfo invocation = new ExpressionInfo(
                ExpressionInfo.CATEGORY.ConstructorInvocation, node, startLine, endLine);
        this.stack.push(invocation);

        final StringBuilder text = new StringBuilder();
        text.append("this(");
        for (final Object argument : node.arguments()) {
            ((ASTNode) argument).accept(this);
            final ProgramElementInfo argumentExpression = this.stack.pop();
            invocation.addExpression(argumentExpression);
            text.append(argumentExpression.getText());
            text.append(",");
        }
        if (!node.arguments().isEmpty()) {
            text.deleteCharAt(text.length() - 1);
        }
        text.append(")");
        invocation.setText(text.toString());

        this.stack.pop();
        final ProgramElementInfo ownerBlock = this.stack.peek();
        final StatementInfo statement = new StatementInfo(ownerBlock, StatementInfo.CATEGORY.Expression,
				node, startLine, endLine);
        this.stack.push(statement);

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
            this.stack.push(statement);

            node.getExpression().accept(this);
            final ProgramElementInfo expression = this.stack.pop();
            statement.addExpression(expression);

            final StringBuilder text = new StringBuilder();
            text.append(expression.getText());
            text.append(";");
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
        this.stack.push(instanceofExpression);

        node.getLeftOperand().accept(this);
        final ProgramElementInfo left = this.stack.pop();
        instanceofExpression.addExpression(left);

        node.getRightOperand().accept(this);
        final ProgramElementInfo right = this.stack.pop();
        instanceofExpression.addExpression(right);

        final StringBuilder text = new StringBuilder();
        text.append(left.getText());
        text.append(" instanceof ");
        text.append(right.getText());
        instanceofExpression.setText(text.toString());

        return false;
    }

    @Override
    public boolean visit(final MethodInvocation node) {
        final int startLine = this.getStartLineNumber(node);
        final int endLine = this.getEndLineNumber(node);
        final ExpressionInfo methodInvocation = new ExpressionInfo(ExpressionInfo.CATEGORY.MethodInvocation,
				node, startLine, endLine);
        this.stack.push(methodInvocation);

        final StringBuilder text = new StringBuilder();

        if (null != node.getExpression()) {
            node.getExpression().accept(this);
            final ProgramElementInfo expression = this.stack.pop();
            methodInvocation.setQualifier(expression);

            text.append(expression.getText());
            text.append(".");
        }

        node.getName().accept(this);
        final ProgramElementInfo name = this.stack.pop();
        methodInvocation.addExpression(name);

        text.append(name.getText());
        text.append("(");
        for (final Object argument : node.arguments()) {
            ((ASTNode) argument).accept(this);
            final ProgramElementInfo argumentExpression = this.stack.pop();
            methodInvocation.addExpression(argumentExpression);

            text.append(argumentExpression.getText());
            text.append(",");
        }
        if (!node.arguments().isEmpty()) {
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
        this.stack.push(parenthesizedExpression);

        node.getExpression().accept(this);
        final ProgramElementInfo expression = this.stack.pop();
        parenthesizedExpression.addExpression(expression);

        final StringBuilder text = new StringBuilder();
        text.append("(");
        text.append(expression.getText());
        text.append(")");
        parenthesizedExpression.setText(text.toString());

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
            this.stack.push(returnStatement);

            final StringBuilder text = new StringBuilder();
            text.append("return");

            if (null != node.getExpression()) {
                node.getExpression().accept(this);
                final ProgramElementInfo expression = this.stack.pop();
                returnStatement.addExpression(expression);
                text.append(" ");
                text.append(expression.getText());
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
        this.stack.push(superConstructorInvocation);

        final StringBuilder text = new StringBuilder();

        if (null != node.getExpression()) {
            node.getExpression().accept(this);
            final ProgramElementInfo qualifier = this.stack.pop();
            superConstructorInvocation.setQualifier(qualifier);
            text.append(qualifier.getText());
            text.append(".super(");
        } else {
            text.append("super(");
        }

        for (final Object argument : node.arguments()) {
            ((ASTNode) argument).accept(this);
            final ProgramElementInfo argumentExpression = this.stack.pop();
            superConstructorInvocation.addExpression(argumentExpression);
            text.append(argumentExpression.getText());
            text.append(",");
        }
        if (!node.arguments().isEmpty()) {
            text.deleteCharAt(text.length() - 1);
        }
        text.append(")");
        superConstructorInvocation.setText(text.toString());

        this.stack.pop();
        final ProgramElementInfo ownerBlock = this.stack.peek();
        final StatementInfo statement = new StatementInfo(ownerBlock,
                StatementInfo.CATEGORY.Expression, node, startLine, endLine);
        this.stack.push(statement);

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
        this.stack.push(expression);
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
        this.stack.push(vdExpression);

        final TypeInfo type = new TypeInfo(node.getType().toString(), node.getType(), startLine, endLine);
        vdExpression.addExpression(type);

        final StringBuilder text = new StringBuilder();
        text.append(type.getText());
        text.append(" ");

        for (final Object fragment : node.fragments()) {
            ((ASTNode) fragment).accept(this);
            final ProgramElementInfo fragmentExpression = this.stack.pop();
            vdExpression.addExpression(fragmentExpression);
            text.append(fragmentExpression.getText());
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
            this.stack.push(vdStatement);

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
                final ProgramElementInfo fragmentExpression = this.stack.pop();
                vdStatement.addExpression(fragmentExpression);
                text.append(fragmentExpression.getText()).append(",");
            }
            if (anyExpression) {
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
        this.stack.push(vdFragment);

        node.getName().accept(this);
        final ProgramElementInfo name = this.stack.pop();
        vdFragment.addExpression(name);

        final StringBuilder text = new StringBuilder();
        text.append(name.getText());

        if (null != node.getInitializer()) {
            node.getInitializer().accept(this);
            final ProgramElementInfo expression = this.stack.pop();
            vdFragment.addExpression(expression);

            text.append(" = ");
            text.append(expression.getText());
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
            this.stack.push(doBlock);

            node.getBody().accept(this);
            final StatementInfo body = (StatementInfo) this.stack.pop();
            doBlock.setStatement(body);

            node.getExpression().accept(this);
            final ProgramElementInfo condition = this.stack.pop();
            doBlock.setCondition(condition);
            condition.setOwnerConditinalBlock(doBlock);

            final StringBuilder text = new StringBuilder();
            text.append("do ");
            text.append(body.getText());
            text.append("while (");
            text.append(condition.getText());
            text.append(");");
        }

        return false;
    }

    @Override
    public boolean visit(final EnhancedForStatement node) {
        if (!this.stack.isEmpty() && this.stack.peek() instanceof BlockInfo) {
            node.getParameter().accept(this);
            final ProgramElementInfo parameter = this.stack.pop();

            node.getExpression().accept(this);
            final ProgramElementInfo expression = this.stack.pop();

            final int startLine = this.getStartLineNumber(node);
            final int endLine = this.getEndLineNumber(node);
            final ProgramElementInfo ownerBlock = this.stack.peek();
            final StatementInfo foreachBlock = new StatementInfo(ownerBlock, StatementInfo.CATEGORY.Foreach,
					node, startLine, endLine);
            foreachBlock.addInitializer(parameter);
            foreachBlock.addInitializer(expression);
            this.stack.push(foreachBlock);

            node.getBody().accept(this);
            final StatementInfo body = (StatementInfo) this.stack.pop();
            foreachBlock.setStatement(body);

            final StringBuilder text = new StringBuilder();
            text.append("for (");
            text.append(parameter.getText());
            text.append(" : ");
            text.append(expression.getText());
            text.append(")");
            text.append(body.getText());
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
            this.stack.push(forBlock);

            final StringBuilder text = new StringBuilder();
            text.append("for (");

            for (final Object o : node.initializers()) {
                ((ASTNode) o).accept(this);
                final ExpressionInfo initializer = (ExpressionInfo) this.stack.pop();
                forBlock.addInitializer(initializer);
                text.append(initializer.getText());
                text.append(",");
            }
            if (!node.initializers().isEmpty()) {
                text.deleteCharAt(text.length() - 1);
            }

            text.append("; ");

            if (null != node.getExpression()) {
                node.getExpression().accept(this);
                final ProgramElementInfo condition = this.stack.pop();
                forBlock.setCondition(condition);
                condition.setOwnerConditinalBlock(forBlock);
                text.append(condition.getText());
            }

            text.append("; ");

            for (final Object o : node.updaters()) {
                ((ASTNode) o).accept(this);
                final ExpressionInfo updater = (ExpressionInfo) this.stack.pop();
                forBlock.addUpdater(updater);
                text.append(updater.getText());
                text.append(",");
            }
            if (!node.updaters().isEmpty()) {
                text.deleteCharAt(text.length() - 1);
            }

            text.append(")");

            node.getBody().accept(this);
            final StatementInfo body = (StatementInfo) this.stack.pop();
            forBlock.setStatement(body);
            text.append(body.getText());
            forBlock.setText(text.toString());
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
            this.stack.push(ifBlock);

            node.getExpression().accept(this);
            final ProgramElementInfo condition = this.stack.pop();
            ifBlock.setCondition(condition);
            condition.setOwnerConditinalBlock(ifBlock);

            final StringBuilder text = new StringBuilder();
            text.append("if (");
            text.append(condition.getText());
            text.append(") ");

            condition.setText("if " + condition.getText()); //hj 加

            if (null != node.getThenStatement()) {
                node.getThenStatement().accept(this);
                final StatementInfo thenBody = (StatementInfo) this.stack.pop();
                ifBlock.setStatement(thenBody);
                text.append(thenBody.getText());
            }

            if (null != node.getElseStatement()) {
                node.getElseStatement().accept(this);
                final StatementInfo elseBody = (StatementInfo) this.stack.pop();
                ifBlock.setElseStatement(elseBody);
                text.append(elseBody.getText());
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
            this.stack.push(switchBlock);

            node.getExpression().accept(this);
            final ProgramElementInfo condition = this.stack.pop();
            switchBlock.setCondition(condition);
            condition.setOwnerConditinalBlock(switchBlock);

            final StringBuilder text = new StringBuilder();
            text.append("switch (");
            text.append(condition.getText());
            text.append(") {");
            text.append(lineSeparator);

            for (final Object o : node.statements()) {
                ((ASTNode) o).accept(this);
                final StatementInfo statement = (StatementInfo) this.stack.pop();
                switchBlock.addStatement(statement);
                text.append(statement.getText());
                text.append(lineSeparator);
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
            this.stack.push(synchronizedBlock);

            node.getExpression().accept(this);
            final ProgramElementInfo condition = this.stack.pop();
            synchronizedBlock.setCondition(condition);
            condition.setOwnerConditinalBlock(synchronizedBlock);

            node.getBody().accept(this);
            final StatementInfo body = (StatementInfo) this.stack.pop();
            synchronizedBlock.setStatement(body);

            final StringBuilder text = new StringBuilder();
            text.append("synchronized (");
            text.append(condition.getText());
            text.append(") ");
            text.append(body.getText());
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
            this.stack.push(throwStatement);

            node.getExpression().accept(this);
            final ProgramElementInfo expression = this.stack.pop();
            throwStatement.addExpression(expression);

            final StringBuilder text = new StringBuilder();
            text.append("throw ");
            text.append(expression.getText());
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
            this.stack.push(tryBlock);

            node.getBody().accept(this);
            final StatementInfo body = (StatementInfo) this.stack.pop();
            tryBlock.setStatement(body);

            final StringBuilder text = new StringBuilder();
            text.append("try ");
            text.append(body.getText());

            for (final Object o : node.catchClauses()) {
                ((ASTNode) o).accept(this);
                final StatementInfo catchBlock = (StatementInfo) this.stack.pop();
                tryBlock.addCatchStatement(catchBlock);
                text.append(catchBlock.getText());
            }

            if (null != node.getFinally()) {
                node.getFinally().accept(this);
                final StatementInfo finallyBlock = (StatementInfo) this.stack.pop();
                tryBlock.setFinallyStatement(finallyBlock);
                text.append(finallyBlock.getText());
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
            this.stack.push(whileBlock);

            node.getExpression().accept(this);
            final ProgramElementInfo condition = this.stack.pop();
            whileBlock.setCondition(condition);
            condition.setOwnerConditinalBlock(whileBlock);

            node.getBody().accept(this);
            StatementInfo body = (StatementInfo) this.stack.pop();
            whileBlock.setStatement(body);

            final StringBuilder text = new StringBuilder();
            text.append("while (");
            text.append(condition.getText());
            text.append(") ");
            text.append(body.getText());

            //hj
            condition.setText("while " + condition.getText());
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
            this.stack.push(switchCase);

            final StringBuilder text = new StringBuilder();

            if (null != node.getExpression()) {
                node.getExpression().accept(this);
                final ProgramElementInfo expression = this.stack.pop();
                switchCase.addExpression(expression);

                text.append("case ");
                text.append(expression.getText());
            } else {
                text.append("default");
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
            this.stack.push(breakStatement);

            final StringBuilder text = new StringBuilder();
            text.append("break");

            if (null != node.getLabel()) {
                node.getLabel().accept(this);
                final ProgramElementInfo label = this.stack.pop();
                breakStatement.addExpression(label);

                text.append(" ");
                text.append(label.getText());
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
            this.stack.push(continuekStatement);

            final StringBuilder text = new StringBuilder();
            text.append("continue");

            if (null != node.getLabel()) {
                node.getLabel().accept(this);
                final ProgramElementInfo label = this.stack.pop();
                continuekStatement.addExpression(label);

                text.append(" ");
                text.append(label.getText());
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
            this.stack.push(simpleBlock);

            final StringBuilder text = new StringBuilder();
            text.append("{");
            text.append(lineSeparator);

            for (final Object o : node.statements()) {
                ((ASTNode) o).accept(this);
                final ProgramElementInfo statement = this.stack.pop();
                simpleBlock.addStatement((StatementInfo) statement);
                text.append(statement.getText());
                text.append(lineSeparator);
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
            this.stack.push(catchBlock);

            node.getException().accept(this);
            final ProgramElementInfo exception = this.stack.pop();
            exception.setOwnerConditinalBlock(catchBlock);
            catchBlock.setCondition(exception);

            node.getBody().accept(this);
            final StatementInfo body = (StatementInfo) this.stack.pop();
            catchBlock.setStatement(body);

            final StringBuilder text = new StringBuilder();
            text.append("catch (");
            text.append(exception.getText());
            text.append(") ");
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
        this.stack.push(variable);

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
            this.stack.push(emptyStatement);
            emptyStatement.setText(";");
        }

        return false;
    }

}
