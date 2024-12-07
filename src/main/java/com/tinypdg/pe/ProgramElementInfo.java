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

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

abstract public class ProgramElementInfo implements Comparable<ProgramElementInfo> {

	/**
	 * The ID generator to generate a unique id for every ProgramElementInfo (thread safely).
	 */
	final static private AtomicInteger ID_GENERATOR = new AtomicInteger(0);

	/**
	 * The unique id of the program element.
	 */
	@Getter
	final public int id;

	/**
	 * The original ast node from the parser.
	 * Usually used for debugging, or getting additional information from the ast.
	 */
	@Getter
	final public Object node;

	/**
	 * The start line number of the program element (ast).
	 */
	@Getter
	final public int startLine;

	/**
	 * The end line number of the program element (ast).
	 */
	@Getter
	final public int endLine;

	/**
	 * One type of the text representation for the program element.
	 */
	@Getter
	private String text;

	/**
	 * The modifiers of the program element (ast) (if exists).
	 */
	@Getter
	final private List<String> modifiers;

	/**
	 * Used to mark whether this program element is the "condition" of another element
	 * (such as the condition of an "If" statement).
	 * If so, this is the entire block of the StatementInfo (such as the whole "If" StatementInfo)
	 */
	@Getter
    protected BlockInfo ownerConditionalBlock;

	public ProgramElementInfo(final Object node, final int startLine, final int endLine) {
		this.node = node;
		this.startLine = startLine;
		this.endLine = endLine;
		this.id = ID_GENERATOR.getAndIncrement();
		this.text = "";

		this.modifiers = new ArrayList<>();

		this.ownerConditionalBlock = null;
	}

	@Override
	final public int hashCode() {
		return this.id;
	}

	@Override
	final public boolean equals(final Object o) {
		if (!(o instanceof ProgramElementInfo target)) {
			return false;
		}

        return this.id == target.id;
	}

	@Override
	final public int compareTo(final ProgramElementInfo element) {
		assert null != element : "\"element\" is null.";
        return Integer.compare(this.id, element.id);
	}

	final public void setText(final String text) {
		assert null != text : "\"text\" is null.";
		this.text = text;
	}

	final public void addModifier(final String modifier) {
		assert null != modifier : "\"modifier\" is null.";
		this.modifiers.add(modifier);
	}


	// --------------------- Variable Uses & Defs ---------------------

	private SortedSet<VarDef> defVariables = null;
	private SortedSet<VarUse> useVariables = null;

	/**
     * Analyze the defined variables (i.e. defs) in this program element.
     * @return The variable defs in the program element
     */
	public SortedSet<VarDef> getDefVariables() {
		if (null == defVariables) {
			defVariables = new TreeSet<>();
			doCalcDefVariables();
		}
		return defVariables;
	}

	/**
     * Analyze the used variables (i.e. uses) in this program element.
     * @return The variable uses in the program element
     */
	public SortedSet<VarUse> getUseVariables() {
		if (null == useVariables) {
			useVariables = new TreeSet<>();
			doCalcUseVariables();
		}
		return useVariables;
	}

	/**
	 * Add a variable def. This should be the only interface for the subclasses to use.
	 * @param variableName Variable name
	 * @param type Def type, can be one of UNKNOWN, DEF, MAY_DEF
	 */
	protected void addVarDef(String variableName, VarDef.Type type) {
		this.defVariables.add(new VarDef(variableName, type));
	}

	/**
	 * Add a variable use. This should be the only interface for the subclasses to use.
	 * @param variableName Variable name
	 * @param type Use type, can be one of UNKNOWN, USE, MAY_USE
	 */
	protected void addVarUse(String variableName, VarUse.Type type) {
		this.useVariables.add(new VarUse(variableName, type));
	}

	/**
	 * Add a variable def. This should be the only interface for the subclasses to use.
	 * @param varDef Var def
	 */
	protected void addVarDef(VarDef varDef) {
		this.defVariables.add(new VarDef(varDef).atLeast(VarDef.Type.MAY_DEF));
	}

	/**
	 * Add a variable use. This should be the only interface for the subclasses to use.
	 * @param varUse Var use
	 */
	protected void addVarUse(VarUse varUse) {
		this.useVariables.add(new VarUse(varUse).atLeast(VarUse.Type.MAY_USE));
	}

	/**
	 * The real place to calculate defs.
	 */
	protected void doCalcDefVariables() {
		// Do nothing by default, implement it in your subclass
	}

	/**
	 * The real place to calculate uses.
	 */
	protected void doCalcUseVariables() {
		// Do nothing by default, implement it in your subclass
	}


	public void setOwnerConditionalBlock(final BlockInfo ownerConditionalBlock) {
		assert null != ownerConditionalBlock : "\"ownerConditionalBlock\" is null.";
		this.ownerConditionalBlock = ownerConditionalBlock;
	}

	@Override
	public String toString() {
		return text;
//		return node.toString();
	}


	/**
	 * Record the information of uses of the variables in the ProgramElement.
	 */
	@Data
	@NoArgsConstructor
	public static class VarUse implements Comparable<VarUse> {
		protected Object scope = null;
		protected String variableName = null;
		protected Type type = Type.UNKNOWN;

		/**
		 * Use types.
		 */
		public enum Type {
			// Levels:
			// - UNKNOWN < MAY_USE < USE
			UNKNOWN(0), MAY_USE(1), USE(2);

			Type(int level) {
				this.level = level;
			}

			public final int level;
		}

		VarUse(String variableName, Type type) {
			this.variableName = variableName;
			this.type = type;
		}

		VarUse(VarUse o) {
			this.scope = o.scope;
			this.variableName = o.variableName;
			this.type = o.type;
		}

		/**
		 * Return a cloned var use with at least the specified type.
		 * @param type Type
		 * @return Cloned var use with at least the specified type
		 */
		public VarUse atLeast(Type type) {
			VarUse result = new VarUse(variableName, type);
			if (this.type.level < type.level) {
				result.type = type;
			}
			return result;
		}

		@Override
		public int compareTo(@NotNull VarUse o) {
			int compare = Objects.compare(variableName, o.variableName, String::compareTo);
			if (compare == 0) {
				compare = Objects.compare(type, o.type, Type::compareTo);
			}
			return compare;
		}

	}
	
	/**
	 * Record the information of defs of the variables in the ProgramElement.
	 */
	@Data
	@NoArgsConstructor
	public static class VarDef implements Comparable<VarDef> {
		protected Object scope = null;
		protected String variableName = null;
		protected Type type = Type.UNKNOWN;

		/**
		 * Def types.
		 */
		public enum Type {
			// Levels:
			// - UNKNOWN < MAY_DEF < DEF
			UNKNOWN(0), MAY_DEF(1), DEF(2);

			Type(int level) {
				this.level = level;
			}

			public final int level;
		}

		VarDef(String variableName, Type type) {
			this.variableName = variableName;
			this.type = type;
		}

		VarDef(VarDef o) {
			this.scope = o.scope;
			this.variableName = o.variableName;
			this.type = o.type;
		}

		/**
		 * Return a cloned var def with at least the specified type.
		 * @param type Type
		 * @return Cloned var def with at least the specified type
		 */
		public VarDef atLeast(Type type) {
			VarDef result = new VarDef(variableName, type);
			if (this.type.level < type.level) {
				result.type = type;
			}
			return result;
		}

		@Override
		public int compareTo(@NotNull VarDef o) {
			int compare = Objects.compare(variableName, o.variableName, String::compareTo);
			if (compare == 0) {
				compare = Objects.compare(type, o.type, Type::compareTo);
			}
			return compare;
		}

	}

}
