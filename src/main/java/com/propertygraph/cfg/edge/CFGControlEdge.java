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

package com.propertygraph.cfg.edge;

import com.propertygraph.cfg.node.CFGNode;

public class CFGControlEdge extends CFGEdge {

	final public boolean control;

	CFGControlEdge(CFGNode<?> fromNode, final CFGNode<?> toNode, final boolean control) {
		super(fromNode, toNode);
		this.control = control;
	}

	@Override
	public String getDependenceString() {
		return Boolean.toString(this.control);
	}

	@Override
	public String getDependenceTypeString() {
		return "control";
	}

}
