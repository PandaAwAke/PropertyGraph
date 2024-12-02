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
import lombok.Setter;
import org.eclipse.jdt.core.dom.ASTNode;

import java.util.*;

public class StatementInfo extends ProgramElementInfo implements BlockInfo {

    @Getter
    private ProgramElementInfo ownerBlock;
    @Getter
    private CATEGORY category;

    private List<ProgramElementInfo> expressions;

    //hj
    //hj
    //如果是变量的声明  则记录变量的类型 hj
    @Setter
    @Getter
    private String Instance;
    //hj
    @Setter
    private boolean isArray;

    final private List<ProgramElementInfo> initializers;

    @Getter
    private ProgramElementInfo condition;
    final private List<ProgramElementInfo> updaters;

    final private List<StatementInfo> statements;
    final private List<StatementInfo> elseStatements;
    final private List<StatementInfo> catchStatements;

    @Getter
    private StatementInfo finallyStatement;

    @Setter
    @Getter
    private String label;

    public StatementInfo(final ProgramElementInfo ownerBlock,
                         final CATEGORY category,
                         final ASTNode node,
                         final int startLine,
                         final int endLine) {
        super(node, startLine, endLine);

        this.ownerBlock = ownerBlock;
        this.category = category;
        this.expressions = new ArrayList<>();

        //hj
        this.Instance = null;
        this.isArray = false;

        this.initializers = new ArrayList<>();
        this.condition = null;
        this.updaters = new ArrayList<>();

        this.statements = new ArrayList<>();
        this.elseStatements = new ArrayList<>();
        this.catchStatements = new ArrayList<>();
        this.finallyStatement = null;

        this.label = null;
    }

    public enum CATEGORY {
        Assert,
        Break,
        Case,
        Catch,
        Continue,
        Do,
        Empty,
        Expression,
        If,
        For,
        Foreach,
        Return,
        SimpleBlock,
        Synchronized,
        Switch,
        Throw,
        Try,
        TypeDeclaration,
        VariableDeclaration,
        While,
    }

    public void setOwnerBlock(final ProgramElementInfo ownerBlock) {
        assert null != ownerBlock : "\"ownerBlock\" is null.";
        this.ownerBlock = ownerBlock;
    }

    //hj
    public boolean getIsArray() {
        return isArray;
    }

    public void setCategory(final CATEGORY category) {
        assert null != category : "\"category\" is null.";
        this.category = category;
    }

    public void addInitializer(final ProgramElementInfo initializer) {
        assert null != initializer : "\"initializer\" is null.";
        this.initializers.add(initializer);
    }

    public void setCondition(final ProgramElementInfo condition) {
        assert null != condition : "\"condition\" is null.";
        this.condition = condition;
    }

    public void addUpdater(final ProgramElementInfo updater) {
        assert null != updater : "\"updater\" is null.";
        this.updaters.add(updater);
    }

    public List<ProgramElementInfo> getInitializers() {
        return new ArrayList<>(this.initializers);
    }

    public List<ProgramElementInfo> getUpdaters() {
        return new ArrayList<>(this.updaters);
    }

    @Override
    public void setStatement(final StatementInfo statement) {
        assert null != statement : "\"statement\" is null.";
        this.statements.clear();
        if (CATEGORY.SimpleBlock == statement.getCategory()) {
            if (statement.getStatements().isEmpty()) {
                this.statements.add(statement);
            } else {
                this.statements.addAll(statement.getStatements());
            }
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

    public void setElseStatement(final StatementInfo elseBody) {
        assert null != elseBody : "\"elseStatement\" is null.";
        this.elseStatements.clear();
        if (CATEGORY.SimpleBlock == elseBody.getCategory()) {
            this.elseStatements.addAll(elseBody.getStatements());
        } else {
            this.elseStatements.add(elseBody);
        }
    }

    public List<StatementInfo> getElseStatements() {
        return Collections.unmodifiableList(this.elseStatements);
    }

    public void addCatchStatement(final StatementInfo catchStatement) {
        assert null != catchStatement : "\"catchStatement\" is null.";
        this.catchStatements.add(catchStatement);
    }

    public List<StatementInfo> getCatchStatements() {
        return Collections.unmodifiableList(this.catchStatements);
    }

    public void setFinallyStatement(final StatementInfo finallyStatement) {
        assert null != finallyStatement : "\"finallyStatement\" is null.";
        this.finallyStatement = finallyStatement;
    }

    public void addExpression(final ProgramElementInfo element) {
        assert null != element : "\"element\" is null.";
        this.expressions.add(element);
    }

    public List<ProgramElementInfo> getExpressions() {
        return new ArrayList<>(this.expressions);
    }

    @Override
    public SortedSet<String> getAssignedVariables() {
        final SortedSet<String> variables = new TreeSet<String>();

        for (final ProgramElementInfo expression : this.expressions) {
            variables.addAll(expression.getAssignedVariables());
        }

        for (final ProgramElementInfo initializer : this.initializers) {
            variables.addAll(initializer.getAssignedVariables());
        }

        if (null != this.condition) {
            variables.addAll(this.condition.getAssignedVariables());
        }

        for (final ProgramElementInfo updater : this.updaters) {
            variables.addAll(updater.getAssignedVariables());
        }

        for (final StatementInfo statement : this.statements) {
            variables.addAll(statement.getAssignedVariables());
        }

        for (final StatementInfo statement : this.elseStatements) {
            variables.addAll(statement.getAssignedVariables());
        }

        for (final StatementInfo catchStatement : this.catchStatements) {
            variables.addAll(catchStatement.getAssignedVariables());
        }

        if (null != this.finallyStatement) {
            variables.addAll(this.finallyStatement.getAssignedVariables());
        }

        return variables;
    }

    @Override
    public SortedSet<String> getReferencedVariables() {
        final SortedSet<String> variables = new TreeSet<>();

        for (final ProgramElementInfo expression : this.expressions) {
            variables.addAll(expression.getReferencedVariables());
        }

        for (final ProgramElementInfo initializer : this.initializers) {
            variables.addAll(initializer.getReferencedVariables());
        }

        if (null != this.condition) {
            variables.addAll(this.condition.getReferencedVariables());
        }

        for (final ProgramElementInfo updater : this.updaters) {
            variables.addAll(updater.getReferencedVariables());
        }

        for (final StatementInfo statement : this.statements) {
            variables.addAll(statement.getReferencedVariables());
        }

        for (final StatementInfo statement : this.elseStatements) {
            variables.addAll(statement.getReferencedVariables());
        }

        for (final StatementInfo catchStatement : this.catchStatements) {
            variables.addAll(catchStatement.getReferencedVariables());
        }

        if (null != this.finallyStatement) {
            variables.addAll(this.finallyStatement.getReferencedVariables());
        }

        return variables;
    }

    public String getJumpToLabel() {
        if (this.expressions.isEmpty()) {
            return null;
        } else {
            return this.expressions.get(0).getText();
        }
    }
}
