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

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
abstract public class ProgramElementInfo implements Comparable<ProgramElementInfo> {

	/**
	 * The ID generator to generate a unique id for every ProgramElementInfo (thread safely).
	 */
	final static private AtomicInteger ID_GENERATOR = new AtomicInteger(0);

	/**
	 * The unique id of the program element.
	 */
	final public int id;

	/**
	 * The original ast node from the parser.
	 * Usually used for debugging, or getting additional information from the ast.
	 */
	final public Object node;

	/**
	 * The start line number of the program element (ast).
	 */
	final public int startLine;

	/**
	 * The end line number of the program element (ast).
	 */
	final public int endLine;

	/**
	 * One type of the text representation for the program element.
	 */
	private String text;

	/**
	 * The modifiers of the program element (ast) (if exists).
	 */
	final private List<String> modifiers;

	/**
	 * Used to mark whether this program element is the "condition" of another element
	 * (such as the condition of an "If" statement).
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

	/**
     * Analyze the defined variables (i.e. defs) in this program element.
     * @return The variable defs in the program element
     */
	public SortedSet<String> getAssignedVariables() {
		return new TreeSet<>();
	}

	/**
     * Analyze the used variables (i.e. uses) in this program element.
     * @return The variable uses in the program element
     */
	public SortedSet<String> getReferencedVariables() {
		return new TreeSet<>();
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

}
