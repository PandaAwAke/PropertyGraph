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

import com.tinypdg.pe.var.Scope;
import com.tinypdg.pe.var.ScopeManager;
import com.tinypdg.pe.var.VarDef;
import com.tinypdg.pe.var.VarUse;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.jdt.core.dom.ASTNode;

import java.util.*;

/**
 * Describe the information of a statement (in the ast).
 */
@Getter
public class StatementInfo extends ProgramElementInfo implements BlockInfo {

    /**
     * The scope manager used for creating scopes.
     */
    private final ScopeManager scopeManager;

    /**
     * The ownerBlock of this statement (if exists).
     */
    private final ProgramElementInfo ownerBlock;

    /**
     * The category of this statement.
     */
    public final CATEGORY category;

    /**
     * If this is a conditional block (such as If or While),
     * here is the condition element.
     */
    private ProgramElementInfo condition;

    /**
     * All children expressions in this statement.
     * In fact this list might contain many types of ProgramElementInfo (except BlockInfo)
     */
    private final List<ProgramElementInfo> expressions = new ArrayList<>();

    /**
     * Used for "For" and "Foreach" statement.
     * Records the initializer expressions in the "for" loop.
     */
    private final List<ProgramElementInfo> initializers = new ArrayList<>();

    /**
     * Used for "For" and "Foreach" statement.
     * Records the updater expressions in the "for" loop.
     */
    private final List<ProgramElementInfo> updaters = new ArrayList<>();


    /**
     * All children statements in this statement.
     * In fact this list might contain many types of ProgramElementInfo (except BlockInfo)
     */
    private final List<StatementInfo> statements = new ArrayList<>();

    /**
     * Used for "If" statement.
     * Records the statements in "else" block.
     */
    private final List<StatementInfo> elseStatements = new ArrayList<>();

    /**
     * Used for "Try" statement.
     * Records the "catch" blocks.
     */
    private final List<StatementInfo> catchStatements = new ArrayList<>();

    /**
     * Used for "Try" statement.
     * Records the "finally" block.
     */
    private StatementInfo finallyStatement;

    /**
     * If this is a LabeledStatement, here is the label of it.
     * @see org.eclipse.jdt.core.dom.LabeledStatement
     */
    @Setter
    private String label;

    public StatementInfo(final ScopeManager scopeManager,
                         final ProgramElementInfo ownerBlock,
                         final CATEGORY category,
                         final ASTNode node,
                         final int startLine,
                         final int endLine) {
        super(node, startLine, endLine);

        this.scopeManager = scopeManager;

        this.ownerBlock = ownerBlock;
        this.category = category;

        this.condition = null;
        this.finallyStatement = null;

        this.label = null;
    }

    /**
     * Used for Continue and Break statement.
     * Get the label to jump (if exists), such as "jumpOut" in "break jumpOut".
     * @return The label to jump (if exists)
     */
    public String getJumpToLabel() {
        if (this.category != CATEGORY.Break && this.category != CATEGORY.Continue) {
            return null;
        }
        if (this.expressions.isEmpty()) {
            return null;
        } else {
            return this.expressions.get(0).getText();
        }
    }

    /**
     * All supported statement types.
     */
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

    public void setElseStatement(final StatementInfo elseBody) {
        assert null != elseBody : "\"elseStatement\" is null.";
        this.elseStatements.clear();
        if (CATEGORY.SimpleBlock == elseBody.getCategory()) {
            this.elseStatements.addAll(elseBody.getStatements());
        } else {
            this.elseStatements.add(elseBody);
        }
    }

    public void addCatchStatement(final StatementInfo catchStatement) {
        assert null != catchStatement : "\"catchStatement\" is null.";
        this.catchStatements.add(catchStatement);
    }

    public void setFinallyStatement(final StatementInfo finallyStatement) {
        assert null != finallyStatement : "\"finallyStatement\" is null.";
        this.finallyStatement = finallyStatement;
    }

    public void addExpression(final ProgramElementInfo element) {
        assert null != element : "\"element\" is null.";
        this.expressions.add(element);
    }


    /**
     * Add a var def with our "ownerBlock" scope.
     * @param varDef Var def
     */
    @Override
    protected void addVarDef(VarDef varDef) {
        VarDef defWithScope;
        Scope ourScope = scopeManager.getScope(this.ownerBlock);

        if (varDef.getType().isAtLeastDeclare()) {
            // Case 1: it is a DECLARE
            if (varDef.getScope() == null) {
                // It was not declared in any scope
                defWithScope = new VarDef(ourScope, varDef);
            } else {
                defWithScope = new VarDef(varDef);
            }
        } else {
            // Case 2: it is not a DECLARE
            Scope matchedScope = ourScope.searchVariable(varDef.getMainVariableName());
            if (matchedScope != null) {
                // Found a matched scope (including myself), set it
                defWithScope = new VarDef(matchedScope, varDef);
            } else {
                // This varDef was not declared
                // It is most likely a this.xxx def, set scope to null
                defWithScope = new VarDef(null, varDef);
            }
        }

        defWithScope.updateScope();
        super.addVarDef(defWithScope);
    }

    /**
     * Add a var use with our "ownerBlock" scope.
     * @param varUse Var use
     */
    @Override
    protected void addVarUse(VarUse varUse) {
        VarUse useWithScope;
        Scope ourScope = scopeManager.getScope(this.ownerBlock);

        Scope matchedScope = ourScope.searchVariable(varUse.getMainVariableName());
        if (matchedScope != null) {
            // Found a matched scope (including myself), set it
            useWithScope = new VarUse(matchedScope, varUse);
        } else {
            // This varDef was not declared
            // It is most likely a this.xxx def, set scope to null
            useWithScope = new VarUse(null, varUse);
        }

        useWithScope.updateScope();
        super.addVarUse(useWithScope);
    }

    @Override
    protected void doCalcDefVariables() {
        for (final ProgramElementInfo expression : this.expressions) {
            expression.getDefVariables().forEach(this::addVarDef);
        }

        for (final ProgramElementInfo initializer : this.initializers) {
            initializer.getDefVariables().forEach(this::addVarDef);
        }

        if (null != this.condition) {
            this.condition.getDefVariables().forEach(this::addVarDef);
        }

        for (final ProgramElementInfo updater : this.updaters) {
            updater.getDefVariables().forEach(this::addVarDef);
        }

        for (final StatementInfo statement : this.statements) {
            statement.getDefVariables().forEach(this::addVarDef);
        }

        for (final StatementInfo statement : this.elseStatements) {
            statement.getDefVariables().forEach(this::addVarDef);
        }

        for (final StatementInfo catchStatement : this.catchStatements) {
            catchStatement.getDefVariables().forEach(this::addVarDef);
        }

        if (null != this.finallyStatement) {
            this.finallyStatement.getDefVariables().forEach(this::addVarDef);
        }
    }

    @Override
    protected void doCalcUseVariables() {
        for (final ProgramElementInfo expression : this.expressions) {
            expression.getUseVariables().forEach(this::addVarUse);
        }

        for (final ProgramElementInfo initializer : this.initializers) {
            initializer.getUseVariables().forEach(this::addVarUse);
        }

        if (null != this.condition) {
            this.condition.getUseVariables().forEach(this::addVarUse);
        }

        for (final ProgramElementInfo updater : this.updaters) {
            updater.getUseVariables().forEach(this::addVarUse);
        }

        for (final StatementInfo statement : this.statements) {
            statement.getUseVariables().forEach(this::addVarUse);
        }

        for (final StatementInfo statement : this.elseStatements) {
            statement.getUseVariables().forEach(this::addVarUse);
        }

        for (final StatementInfo catchStatement : this.catchStatements) {
            catchStatement.getUseVariables().forEach(this::addVarUse);
        }

        if (null != this.finallyStatement) {
            this.finallyStatement.getUseVariables().forEach(this::addVarUse);
        }
    }

}
