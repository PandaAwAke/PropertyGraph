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

package com.propertygraph.pe;

import lombok.Getter;

import java.util.*;

public class MethodInfo extends ProgramElementInfo implements BlockInfo {

	@Getter
	final public boolean lambda;
	@Getter
	private ExpressionInfo lambdaExpression;

	final public String name;
	final private List<VariableInfo> parameters;
	final private List<StatementInfo> statements;

	public MethodInfo(final boolean lambda, final String name, final Object node, final int startLine, final int endLine) {
		super(node, startLine, endLine);
		this.lambda = lambda;
		this.name = name;
		this.parameters = new ArrayList<>();
		this.statements = new ArrayList<>();
	}

	public void addParameter(final VariableInfo parameter) {
		assert null != parameter : "\"variable\" is null.";
		this.parameters.add(parameter);
	}

	public SortedSet<VariableInfo> getParameters() {
        return new TreeSet<>(this.parameters);
	}

	@Override
	public void setStatement(final StatementInfo statement) {
		assert null != statement : "\"statement\" is null.";
		this.statements.clear();
		if (StatementInfo.CATEGORY.SimpleBlock == statement.getCategory()) {
			this.statements.addAll(statement.getStatements());
		} else {
			this.statements.add(statement);
		}
	}

	@Override
	public void addStatement(final StatementInfo statement) {
		assert null != statement : "\"statement\" is null.";
		this.statements.add(statement);
	}

	@Override
	public void addStatements(final Collection<StatementInfo> statements) {
		assert null != statements : "\"statements\" is null.";
		this.statements.addAll(statements);
	}

	@Override
	public List<StatementInfo> getStatements() {
		return Collections.unmodifiableList(this.statements);
	}

	public void setLambdaExpression(ExpressionInfo lambdaExpression) {
		assert lambda : "\"lambda\" is false.";
		this.lambdaExpression = lambdaExpression;
	}

	@Override
	public SortedSet<String> getAssignedVariables() {
		final SortedSet<String> variables = new TreeSet<>();
		for (final StatementInfo statement : this.statements) {
			variables.addAll(statement.getAssignedVariables());
		}
		if (lambda && lambdaExpression != null) {
			variables.addAll(lambdaExpression.getAssignedVariables());
		}
		return variables;
	}

	@Override
	public SortedSet<String> getReferencedVariables() {
		final SortedSet<String> variables = new TreeSet<>();
		for (final StatementInfo statement : this.statements) {
			variables.addAll(statement.getReferencedVariables());
		}
		if (lambda && lambdaExpression != null) {
			variables.addAll(lambdaExpression.getReferencedVariables());
		}
		return variables;
	}

}
