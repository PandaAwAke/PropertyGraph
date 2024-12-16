package com.tinypdg.pe.var;

import com.tinypdg.pe.ProgramElementInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Collection;

/**
 * Record the information of uses of the variables in the ProgramElement.
 */
@EqualsAndHashCode(callSuper = true)
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
         * @return True if is, otherwise return false
         */
        public boolean isAtLeastMayUse() {
            return this.level >= MAY_USE.level;
        }
    }

    VarUse(ProgramElementInfo scope, String variableName, Type type) {
        super(scope, variableName);
        this.type = type;
    }

    public VarUse(ProgramElementInfo scope, String mainVariableName, Collection<String> variableNameAliases, Type type) {
        super(scope, mainVariableName, variableNameAliases);
        this.type = type;
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

}
