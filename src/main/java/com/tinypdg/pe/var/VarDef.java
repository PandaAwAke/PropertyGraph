package com.tinypdg.pe.var;

import lombok.Data;
import lombok.ToString;

import java.util.Collection;

/**
 * Record the information of defs of the variables in the ProgramElement.
 */
@ToString(callSuper = true)
@Data
public class VarDef extends Var {

    protected Type type = Type.UNKNOWN;

    /**
     * Def types.
     */
    public enum Type {
        // Levels:
        // - UNKNOWN < NO_DEF < MAY_DEF < DEF < DECLARE < DECLARE_AND_DEF
        UNKNOWN(0), NO_DEF(1), MAY_DEF(2),
        DEF(3), DECLARE(4), DECLARE_AND_DEF(5);

        Type(int level) {
            this.level = level;
        }

        public final int level;

        /**
         * Return whether this is at least MAY_DEF.
         * @return True if it is, otherwise return false
         */
        public boolean isAtLeastMayDef() {
            return this.level >= MAY_DEF.level;
        }

        /**
         * Return whether this is at least a DECLARE.
         * @return True if it is, otherwise return false
         */
        public boolean isAtLeastDeclare() {
            return this.level >= DECLARE.level;
        }

    }

    public VarDef(Scope scope, String variableName, Type type) {
        super(scope, variableName);
        this.type = type;
    }

    public VarDef(Scope scope, String mainVariableName, Collection<String> variableNameAliases, Type type) {
        super(scope, mainVariableName, variableNameAliases);
        this.type = type;
    }

    public VarDef(Scope scope, VarDef o) {
        super(scope, o.mainVariableName, o.variableNameAliases);
        this.type = o.type;
    }

    public VarDef(VarDef o) {
        super(o.scope, o.mainVariableName, o.variableNameAliases);
        this.type = o.type;
    }

    /**
     * Return a promoted var def with at least the specified type.
     * @param type Type
     * @return Cloned var def with at least the specified type
     */
    public VarDef promote(Type type) {
        VarDef result = new VarDef(this);
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
        VarDef def = (VarDef) o;
        return type == def.type;
    }

}
