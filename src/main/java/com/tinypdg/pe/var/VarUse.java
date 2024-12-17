package com.tinypdg.pe.var;

import lombok.Data;
import lombok.ToString;

import java.util.Collection;

/**
 * Record the information of uses of the variables in the ProgramElement.
 */
@ToString(callSuper = true)
@Data
public class VarUse extends Var {

    protected Type type = Type.UNKNOWN;

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

    public VarUse(Scope scope, String variableName, Type type) {
        super(scope, variableName);
        this.type = type;
    }

    public VarUse(Scope scope, String mainVariableName, Collection<String> variableNameAliases, Type type) {
        super(scope, mainVariableName, variableNameAliases);
        this.type = type;
    }

    public VarUse(Scope scope, VarUse o) {
        super(scope, o.mainVariableName, o.variableNameAliases);
        this.type = o.type;
    }

    public VarUse(VarUse o) {
        super(o.scope, o.mainVariableName, o.variableNameAliases);
        this.type = o.type;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        VarUse varUse = (VarUse) o;
        return type == varUse.type;
    }

}
