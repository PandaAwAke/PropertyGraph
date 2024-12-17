package com.tinypdg.pe.var;

import com.tinypdg.pe.StatementInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Collection;
import java.util.Set;

/**
 * Record the information of uses of the variables in the ProgramElement.
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class VarUse extends Var {

    protected Type type = Type.UNKNOWN;

    /**
     * The relevant stmt of this use. Set by StatementInfo.
     */
    protected StatementInfo relevantStmt = null;

    /**
     * Use types.
     */
    public enum Type {
        // Levels:
        // - UNKNOWN < NO_USE < MAY_USE < USE
        UNKNOWN(0), NO_USE(1), MAY_USE(2), USE(3);

        Type(int level) {
            this.level = level;
        }

        public final int level;

        /**
         * Return whether this is at least MAY_USE.
         * @return True if it is, otherwise return false
         */
        public boolean isAtLeastMayUse() {
            return this.level >= MAY_USE.level;
        }
    }

    public VarUse(Scope scope, String variableName, VarUse.Type type) {
        this(scope, variableName, Set.of(variableName), type, null);
    }

    public VarUse(Scope scope, String mainVariableName, Collection<String> variableNameAliases, VarUse.Type type) {
        this(scope, mainVariableName, variableNameAliases, type, null);
    }
    
    public VarUse(Scope scope, String mainVariableName, Collection<String> variableNameAliases,
                  VarUse.Type type, StatementInfo relevantStmt) {
        super(scope, mainVariableName, variableNameAliases);
        this.type = type;
        this.relevantStmt = relevantStmt;
    }

    public VarUse(Scope scope, VarUse o) {
        this(scope, o.mainVariableName, o.variableNameAliases, o.type, o.relevantStmt);
    }

    public VarUse(VarUse o) {
        this(o.scope, o.mainVariableName, o.variableNameAliases, o.type, o.relevantStmt);
    }

    /**
     * Return a promoted var use with at least the specified type.
     * @param type Type
     * @return Cloned var use with at least the specified type
     */
    public VarUse promote(Type type) {
        VarUse result = new VarUse(this);
        if (this.type.level < type.level) {
            result.type = type;
        }
        return result;
    }

}
