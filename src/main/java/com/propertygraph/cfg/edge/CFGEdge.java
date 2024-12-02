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

import com.propertygraph.cfg.node.CFGBreakStatementNode;
import com.propertygraph.cfg.node.CFGContinueStatementNode;
import com.propertygraph.cfg.node.CFGControlNode;
import com.propertygraph.cfg.node.CFGNode;
import com.propertygraph.cfg.node.CFGPseudoNode;
import com.propertygraph.pe.ProgramElementInfo;

public abstract class CFGEdge implements Comparable<CFGEdge> {

    static public CFGEdge makeEdge(final CFGNode<?> fromNode, final CFGNode<?> toNode, boolean control) {

        assert null != fromNode : "\"fromNode\" is null.";
        assert null != toNode : "\"toNode\" is null.";

        assert fromNode instanceof CFGControlNode
                || fromNode instanceof CFGPseudoNode : "\"fromNode\" is neither CFGControlNode nor CFGPseudoNode.";

        if (fromNode instanceof CFGControlNode) {
            return new CFGControlEdge(fromNode, toNode, control);
        }

        if (fromNode instanceof CFGPseudoNode) {
            return new CFGNormalEdge(fromNode, toNode);
        }

        return null;
    }

    static public CFGEdge makeEdge(final CFGNode<?> fromNode, final CFGNode<?> toNode) {
        assert null != fromNode : "\"fromNode\" is null.";
        assert null != toNode : "\"toNode\" is null.";

        if (fromNode instanceof CFGControlNode) {
            return makeEdge(fromNode, toNode, false);
        } else if (fromNode instanceof CFGBreakStatementNode
                || fromNode instanceof CFGContinueStatementNode) {
            return new CFGJumpEdge(fromNode, toNode);
        } else {
            return new CFGNormalEdge(fromNode, toNode);
        }
    }

    static public CFGEdge makeJumpEdge(final CFGNode<?> fromNode, final CFGNode<?> toNode) {
        assert null != fromNode : "\"fromNode\" is null.";
        assert null != toNode : "\"toNode\" is null.";

        return new CFGJumpEdge(fromNode, toNode);
    }

    public final CFGNode<? extends ProgramElementInfo> fromNode;
    public final CFGNode<? extends ProgramElementInfo> toNode;

    CFGEdge(final CFGNode<?> fromNode, final CFGNode<?> toNode) {
        assert null != fromNode : "\"fromNode\" is null.";
        assert null != toNode : "\"toNode\" is null.";

        this.fromNode = fromNode;
        this.toNode = toNode;
    }

    public abstract String getDependenceTypeString();

    public abstract String getDependenceString();

    @Override
    public boolean equals(final Object arg) {
        if (this.getClass().equals(arg.getClass())) {
            final CFGEdge edge = (CFGEdge) arg;
            return this.fromNode.equals(edge.fromNode)
                    && this.toNode.equals(edge.toNode);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        final int fromHash = this.fromNode.hashCode() * 10;
        final int toHash = this.toNode.hashCode();
        return fromHash + toHash;
    }

    @Override
    public int compareTo(final CFGEdge edge) {
        if (null == edge) {
            throw new IllegalArgumentException();
        }
        //hj add
//		if (null == edge.fromNode){
//			throw new NullPointerException();
//		}
        //
        final int fromOrder = this.fromNode.compareTo(edge.fromNode);
        if (0 != fromOrder) {
            return fromOrder;
        }
        //hj add


        final int toOrder = this.toNode.compareTo(edge.toNode);
        if (0 != toOrder) {
            return toOrder;
        }

        return this.getDependenceTypeString().compareTo(edge.getDependenceTypeString());
    }
}
