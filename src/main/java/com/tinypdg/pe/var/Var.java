package com.tinypdg.pe.var;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@ToString(exclude = "scope")
@NoArgsConstructor
@Getter
@Setter
public class Var {

    protected Scope scope = null;

    /**
     * The main variable name.
     */
    protected String mainVariableName = null;

    /**
     * Aliases of the same variable. Such as "this.source" and "source".
     * Note that the mainVariableName should also in it.
     */
    protected Set<String> variableNameAliases = new TreeSet<>();

    public Var(Scope scope, String variableName) {
        this.scope = scope;
        this.mainVariableName = variableName;
        this.variableNameAliases.add(variableName);

        if (scope != null) {
            scope.addVariable(this);
        }
    }

    public Var(Scope scope, String mainVariableName, Collection<String> variableNameAliases) {
        this.scope = scope;
        this.mainVariableName = mainVariableName;
        this.variableNameAliases.addAll(variableNameAliases);

        if (scope != null) {
            scope.addVariable(this);
        }
    }

    /**
     * Judge whether a variable name matches this Var.
     * @return True if the name matches this var.
     */
    public boolean matchName(String name) {
        return variableNameAliases.contains(name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Var var = (Var) o;
        return Objects.equals(scope, var.scope) && Objects.equals(mainVariableName, var.mainVariableName) && Objects.equals(variableNameAliases, var.variableNameAliases);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scope, mainVariableName, variableNameAliases);
    }

}
