package com.tinypdg.pe.var;

import com.tinypdg.pe.ProgramElementInfo;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

/**
 * The variable scope. Each scope correspond to a block in the ast.
 * A scope could contain a set of scopes and variables (although only parent scope is recorded).
 */
@Getter
@EqualsAndHashCode(of = "block")
@ToString
public class Scope {

    /**
     * The corresponding block of this scope.
     */
    protected ProgramElementInfo block = null;

    /**
     * The parent scope of this scope. This can be null.
     */
    @Setter
    protected Scope parent = null;

    /**
     * The variables in this scope.
     */
    protected Set<Var> variables = new HashSet<>();

    public Scope(final ProgramElementInfo block) {
        this(block, null);
    }

    public Scope(final ProgramElementInfo block, final Scope parent) {
        this(block, parent, Set.of());
    }

    public Scope(final ProgramElementInfo block, final Scope parent, final Set<Var> variables) {
        this.block = block;
        this.parent = parent;
        this.variables.addAll(variables);
    }

    /**
     * Add a variable to this scope.
     * @param var The variable to add
     * @return True if it was added
     */
    public boolean addVariable(final Var var) {
        if (hasVariableDirectly(var.getMainVariableName())) {
            return false;
        }
        return variables.add(var);
    }

    /**
     * Judge whether this scope contains the var.
     * @param varName The variable name
     * @return True if this scope directly contains this var
     */
    public boolean hasVariableDirectly(String varName) {
        return variables.stream()
                .flatMap(var -> var.getVariableNameAliases().stream())
                .anyMatch(name -> name.equals(varName));
    }

    /**
     * Judge whether this scope or its ancestor scopes contains the var.
     * @param varName The variable name
     * @return True if this scope or its ancestor scopes contain this var
     */
    public boolean hasVariable(String varName) {
        if (hasVariableDirectly(varName)) {
            return true;
        }
        if (parent != null) {
            return parent.hasVariable(varName);
        }
        return false;
    }

}