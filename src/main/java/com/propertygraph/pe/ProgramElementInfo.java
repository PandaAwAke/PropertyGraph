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

abstract public class ProgramElementInfo implements Comparable<ProgramElementInfo> {

	final static private AtomicInteger ID_GENERATOR = new AtomicInteger(0);

	final public Object node;
	final public int startLine;
	final public int endLine;
	final public int id;

	private String text;

	final private List<String> modifiers;

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

	final public String getText() {
		return this.text;
	}

	final public void setText(final String text) {
		assert null != text : "\"text\" is null.";
		this.text = text;
	}

	@Override
	final public int compareTo(final ProgramElementInfo element) {
		assert null != element : "\"element\" is null.";
        return Integer.compare(this.id, element.id);
	}

	final public void addModifier(final String modifier) {
		assert null != modifier : "\"modifier\" is null.";
		this.modifiers.add(modifier);
	}

	final public List<String> getModifiers() {
        return new ArrayList<>(this.modifiers);
	}

	public SortedSet<String> getAssignedVariables() {
		return new TreeSet<>();
	}

	public SortedSet<String> getReferencedVariables() {
		return new TreeSet<>();
	}

	public void setOwnerConditinalBlock(final BlockInfo ownerConditionalBlock) {
		assert null != ownerConditionalBlock : "\"ownerConditionalBlock\" is null.";
		this.ownerConditionalBlock = ownerConditionalBlock;
	}

}
