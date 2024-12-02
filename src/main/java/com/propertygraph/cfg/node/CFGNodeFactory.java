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

package com.propertygraph.cfg.node;

import com.propertygraph.pe.ExpressionInfo;
import com.propertygraph.pe.ProgramElementInfo;
import com.propertygraph.pe.StatementInfo;

import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class CFGNodeFactory {

    private final ConcurrentMap<ProgramElementInfo, CFGNode<? extends ProgramElementInfo>> elementToNodeMap;

    public CFGNodeFactory() {
        this.elementToNodeMap = new ConcurrentHashMap<>();
    }

    //非catch、for、Do、foreach、if之类的结点，处理break、continue、case以及其他结点
    public synchronized CFGNode<? extends ProgramElementInfo> makeNormalNode(final ProgramElementInfo element) {
        if (null == element) {
            return new CFGPseudoNode();
        }

        CFGNormalNode<?> node = (CFGNormalNode<?>) this.elementToNodeMap.get(element);
        if (null == node) {
            if (element instanceof StatementInfo) {
                node = switch (((StatementInfo) element).getCategory()) {
                    case Break -> new CFGBreakStatementNode((StatementInfo) element);
                    case Continue -> new CFGContinueStatementNode((StatementInfo) element);
                    case Case -> new CFGSwitchCaseNode((StatementInfo) element);
                    default -> new CFGStatementNode((StatementInfo) element);
                };
                this.elementToNodeMap.put(element, node);
            } else if (element instanceof ExpressionInfo) {
                node = new CFGExpressionNode((ExpressionInfo) element);
            } else {
                assert false : "\"element\" must be StatementInfo.";
                return null;
            }
        }

        return node;
    }

    public synchronized CFGNode<? extends ProgramElementInfo> makeControlNode(final ProgramElementInfo expression) {
        if (null == expression) {
            return new CFGPseudoNode();
        }

        CFGControlNode node = (CFGControlNode) this.elementToNodeMap.get(expression);
        if (null == node) {
            node = new CFGControlNode(expression);
            this.elementToNodeMap.put(expression, node);
        }
        return node;
    }

    public CFGNode<? extends ProgramElementInfo> getNode(final ProgramElementInfo element) {
        assert null != element : "\"element\" is null.";
        return this.elementToNodeMap.get(element);
    }

    public synchronized boolean removeNode(final ProgramElementInfo element) {
        return null != this.elementToNodeMap.remove(element);
    }

    public SortedSet<CFGNode<? extends ProgramElementInfo>> getAllNodes() {
        return new TreeSet<>(this.elementToNodeMap.values());
    }
}
