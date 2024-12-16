package com.tinypdg.pe.var;

import com.tinypdg.pe.ProgramElementInfo;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

@Data
@NoArgsConstructor
public class Var {

    protected ProgramElementInfo scope = null;

    /**
     * The main variable name.
     */
    protected String mainVariableName = null;

    /**
     * Aliases of the same variable. Such as "this.source" and "source".
     * Note that the mainVariableName should also in it.
     */
    protected Set<String> variableNameAliases = new TreeSet<>();

    public Var(ProgramElementInfo scope, String variableName) {
        this.scope = scope;
        this.mainVariableName = variableName;
        this.variableNameAliases.add(variableName);
    }

    public Var(ProgramElementInfo scope, String mainVariableName, Collection<String> variableNameAliases) {
        this.scope = scope;
        this.mainVariableName = mainVariableName;
        this.variableNameAliases.addAll(variableNameAliases);
    }


}
