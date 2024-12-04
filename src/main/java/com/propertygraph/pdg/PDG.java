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

package com.propertygraph.pdg;

import com.propertygraph.cfg.CFG;
import com.propertygraph.cfg.edge.CFGEdge;
import com.propertygraph.cfg.node.CFGNode;
import com.propertygraph.cfg.node.CFGNodeFactory;
import com.propertygraph.pdg.edge.PDGControlDependenceEdge;
import com.propertygraph.pdg.edge.PDGDataDependenceEdge;
import com.propertygraph.pdg.edge.PDGEdge;
import com.propertygraph.pdg.edge.PDGExecutionDependenceEdge;
import com.propertygraph.pdg.node.*;
import com.propertygraph.pe.*;

import java.util.*;

/**
 * The Program Dependency Graph of a method.
 */
public class PDG implements Comparable<PDG> {

    /**
     * The factory to generate PDG nodes.
     */
    final private PDGNodeFactory pdgNodeFactory;

    /**
     * The factory to generate CFG nodes.
     */
    final private CFGNodeFactory cfgNodeFactory;

    /**
	 * The enter node of the PDG.
     * Note that unlike CFG, the enterNode here is usually a fake entry node.
	 */
    final public PDGMethodEnterNode enterNode;

    /**
     * The exit nodes of the PDG. This is the same as the CFG.
     */
    final private SortedSet<PDGNode<?>> exitNodes;

    /**
     * The parameter nodes of the PDG. Usually represents parameters of the method.
     */
    final private List<PDGParameterNode> parameterNodes;

    /**
     * The MethodInfo related to this PDG (like CFG.core).
     */
    final public MethodInfo unit;

    // ------------------- Control dependency edges -------------------
    /**
     * Whether PDG should build control dependency edges, such as if-then-else condition edges.
     */
    final public boolean buildControlDependence;
    /**
     * If this is true, then for every node, there'll be a "true" control edge from the enter node to it.
     */
    final public boolean buildControlDependenceFromEnterToAllNodes = false;
    /**
     * If this is true, then there'll be control edges from the enter node to parameter nodes.
     */
    final public boolean buildControlDependenceFromEnterToParameterNodes = false;


    // ------------------- Data dependency edges -------------------
    /**
     * Whether PDG should build data dependency edges.
     */
    final public boolean buildDataDependence;

    /**
     * If this is true, then the dependency propagation will be short-circuited,
     * i.e. new defs will override the old defs, thereby avoiding the continued propagation of the old defs.
     */
    final public boolean avoidDefPropagationWhenBuildingDataDependence = true;

    // ------------------- Execution dependency edges -------------------
    /**
     * Whether PDG should build execution dependency edges, such as CFG normal edges.
     */
    final public boolean buildExecutionDependence;


    /**
     * The CFG object used for building PDG.
     */
    private CFG cfg;

    public PDG(final MethodInfo unit,
			   final PDGNodeFactory pdgNodeFactory,
               final CFGNodeFactory cfgNodeFactory,
               final boolean buildControlDependence,
               final boolean buildDataDependence,
               final boolean buildExecutionDependence) {

        assert null != unit : "\"unit\" is null";
        assert null != pdgNodeFactory : "\"pdgNodeFactory\" is null";
        assert null != cfgNodeFactory : "\"cfgNodeFactory\" is null";

        this.unit = unit; //一个方法总的信息
        this.pdgNodeFactory = pdgNodeFactory;
        this.cfgNodeFactory = cfgNodeFactory;

        this.enterNode = (PDGMethodEnterNode) this.pdgNodeFactory.makeControlNode(unit);
        this.exitNodes = new TreeSet<>();
        this.parameterNodes = new ArrayList<>();

        for (final VariableInfo variable : unit.getParameters()) {
            final PDGParameterNode parameterNode = (PDGParameterNode) this.pdgNodeFactory.makeNormalNode(variable);
            this.parameterNodes.add(parameterNode);
        }

        this.buildControlDependence = buildControlDependence;
        this.buildDataDependence = buildDataDependence;
        this.buildExecutionDependence = buildExecutionDependence;
    }

    public PDG(final MethodInfo unit,
			   final PDGNodeFactory pdgNodeFactory,
			   final CFGNodeFactory cfgNodeFactory) {
        this(unit, pdgNodeFactory, cfgNodeFactory, true, true, true);
    }

    public PDG(final MethodInfo unit) {
        this(unit, new PDGNodeFactory(), new CFGNodeFactory());
    }

    public PDG(final MethodInfo unit, final boolean buildControlDependency,
               final boolean buildDataDependency,
               final boolean buildExecutionDependency) {
        this(unit, new PDGNodeFactory(), new CFGNodeFactory(),
                buildControlDependency, buildDataDependency,
                buildExecutionDependency);
    }

    @Override
    public int compareTo(final PDG o) {
        assert null != o : "\"o\" is null.";
        return this.unit.compareTo(o.unit);
    }

    public final SortedSet<PDGNode<?>> getExitNodes() {
        final SortedSet<PDGNode<?>> nodes = new TreeSet<>();
        nodes.addAll(this.exitNodes);
        return nodes;
    }

    public final List<PDGParameterNode> getParameterNodes() {
        return new ArrayList<>(this.parameterNodes);
    }

    public final SortedSet<PDGNode<?>> getAllNodes() {
        final SortedSet<PDGNode<?>> nodes = new TreeSet<>();
        this.getAllNodes(this.enterNode, nodes);
        return nodes;
    }

    private void getAllNodes(final PDGNode<?> node,
                             final SortedSet<PDGNode<?>> nodes) {
        assert null != node : "\"node\" is null.";
        assert null != nodes : "\"nodes\" is null.";

        if (nodes.contains(node)) {
            return;
        }

        nodes.add(node);
        for (final PDGEdge edge : node.getBackwardEdges()) {
            this.getAllNodes(edge.fromNode, nodes);
        }
        for (final PDGEdge edge : node.getForwardEdges()) {
            this.getAllNodes(edge.toNode, nodes);
        }
    }

    public final SortedSet<PDGEdge> getAllEdges() {
        final SortedSet<PDGEdge> edges = new TreeSet<>();

        final SortedSet<PDGNode<?>> nodes = this.getAllNodes();
        for (final PDGNode<?> node : nodes) {
            edges.addAll(node.getForwardEdges());
            edges.addAll(node.getBackwardEdges());
        }

        return edges;
    }

    /**
     * Build the PDG.
     */
    public void build() {
        this.cfg = new CFG(this.unit, this.cfgNodeFactory);
        this.cfg.build();
//        this.cfg.removeSwitchCases(); //switch结点
//        this.cfg.removeJumpStatements(); //Jump结点的打扰

        if (this.buildControlDependence) { //控制依赖
            if (this.buildControlDependenceFromEnterToAllNodes) {
                // Add control edges from "entry" to all nodes (why?)
                this.buildControlDependence(this.enterNode, unit); // enter与各大结点之间的关系
            }

            if (this.buildControlDependenceFromEnterToParameterNodes) {
                // Add control edges from "entry" to all parameter nodes (why?)
    			for (final PDGParameterNode parameterNode : this.parameterNodes) {
    				final PDGControlDependenceEdge edge = new PDGControlDependenceEdge(
    						this.enterNode, parameterNode, true);
    				this.enterNode.addForwardEdge(edge);
    				parameterNode.addBackwardEdge(edge);
    			}
            }
        }

        // ---------------- Build dependency edges for "entry" ----------------
        if (this.buildExecutionDependence) {
            if (!this.cfg.isEmpty()) {
                final PDGNode<?> node = this.pdgNodeFactory.makeNode(this.cfg.getEnterNode());
                final PDGExecutionDependenceEdge edge = new PDGExecutionDependenceEdge(this.enterNode, node);
                this.enterNode.addForwardEdge(edge);
                node.addBackwardEdge(edge);
            }
        }

        if (this.buildDataDependence) {
            // Dependency edges: parameters to "CFG entry" (the first statement of the method)
            for (final PDGParameterNode parameterNode : this.parameterNodes) {
                if (!this.cfg.isEmpty()) {
                    this.buildDataDependence(this.cfg.getEnterNode(), parameterNode, parameterNode.core.name, new HashSet<>());
                }
            }

            // Dependency edges: from "PDG entry" (a fake node) to parameters
            for (final PDGParameterNode parameterNode : this.parameterNodes) {
                final PDGDataDependenceEdge edge = new PDGDataDependenceEdge(this.enterNode, parameterNode, parameterNode.core.name);
                this.enterNode.addForwardEdge(edge);
                parameterNode.addBackwardEdge(edge);
            }
        }

        // ---------------- Build dependency edges by CFG ----------------
        // This set is used to avoid revisiting the same node
        final Set<CFGNode<?>> checkedNodes = new HashSet<>();

        // Build the dependency edges: reachable from the "CFG entry" (the first statement of the method)
        if (!this.cfg.isEmpty()) {
            this.buildDependence(this.cfg.getEnterNode(), checkedNodes);
        }

        // Set PDG exitNodes from CFG exitNodes
        for (final CFGNode<?> cfgExitNode : this.cfg.getExitNodes()) {
            final PDGNode<?> pdgExitNode = this.pdgNodeFactory.makeNode(cfgExitNode);
            this.exitNodes.add(pdgExitNode);
        }

        // Build the dependency edges: unreachable in the CFG
        if (!this.cfg.isEmpty()) {
            final Set<CFGNode<?>> unreachableNodes = new HashSet<>(this.cfg.getAllNodes());
            unreachableNodes.removeAll(this.cfg.getReachableNodes());
            for (final CFGNode<?> unreachableNode : unreachableNodes) {
                this.buildDependence(unreachableNode, checkedNodes);
            }
        }
    }

    /**
     * Build all dependency edges from a CFG node.
     * @param cfgNode CFGNode
     * @param checkedNodes The visited nodes set used for avoiding revisiting
     */
    private void buildDependence(final CFGNode<?> cfgNode,
                                 final Set<CFGNode<?>> checkedNodes) {
        assert null != cfgNode : "\"cfgNode\" is null.";
        assert null != checkedNodes : "\"checkedNodes\" is null.";

        if (checkedNodes.contains(cfgNode)) {
            return;
        } else {
            checkedNodes.add(cfgNode);
        }

        final PDGNode<?> pdgNode = this.pdgNodeFactory.makeNode(cfgNode); // PDGNode
        if (this.buildDataDependence) {
            Set<String> variables = pdgNode.core.getAssignedVariables();
            for (final String variable : variables) {
                for (final CFGEdge edge : cfgNode.getForwardEdges()) {
                    final Set<CFGNode<?>> checkedNodesForDefinedVariables = new HashSet<>();
                    this.buildDataDependence(edge.toNode, pdgNode, variable, checkedNodesForDefinedVariables);
                }
            }
        }
        if (this.buildControlDependence) {
            if (pdgNode instanceof PDGControlNode) {
                final ProgramElementInfo condition = pdgNode.core;
                this.buildControlDependence((PDGControlNode) pdgNode, condition.getOwnerConditionalBlock());
            }
        }

        if (this.buildExecutionDependence) {
            for (final CFGNode<?> toCFGNode : cfgNode.getForwardNodes()) {
                final PDGNode<?> toPDGNode = this.pdgNodeFactory.makeNode(toCFGNode);
                final PDGExecutionDependenceEdge edge = new PDGExecutionDependenceEdge(pdgNode, toPDGNode);
                pdgNode.addForwardEdge(edge);
                toPDGNode.addBackwardEdge(edge);
            }
        }

        for (final CFGNode<?> forwardNode : cfgNode.getForwardNodes()) {
            this.buildDependence(forwardNode, checkedNodes);
        }
    }

    /**
     * Try to build a data dependency edge (associated with a variable) from "fromPDGNode" to PDG node of "cfgNode".
     * If "cfgNode" uses the variable defined by "fromPDGNode", then the edge will be added.
     * @param cfgNode The CFG node which might use the variable
     * @param fromPDGNode The PDG node which defined the variable
     * @param variable Defined variable (originally defined in "fromPDGNode")
     * @param checkedCFGNodes The visited nodes set used for avoiding revisiting
     */
    private void buildDataDependence(final CFGNode<?> cfgNode,
                                     final PDGNode<?> fromPDGNode, final String variable,
                                     final Set<CFGNode<?>> checkedCFGNodes) {
        assert null != cfgNode : "\"cfgNode\" is null.";
        assert null != fromPDGNode : "\"fromPDGNode\" is null.";
        assert null != variable : "\"variable\" is null.";
        assert null != checkedCFGNodes : "\"checkedCFGnodes\" is null.";

        if (checkedCFGNodes.contains(cfgNode)) {
            return;
        } else {
            checkedCFGNodes.add(cfgNode);
        }

        // If "cfgNode" uses the variable, add the edge
        if (cfgNode.core.getReferencedVariables().contains(variable)) {
            final PDGNode<?> toPDGNode = this.pdgNodeFactory.makeNode(cfgNode);
            final PDGDataDependenceEdge edge = new PDGDataDependenceEdge(fromPDGNode, toPDGNode, variable);
            fromPDGNode.addForwardEdge(edge);
            toPDGNode.addBackwardEdge(edge);
        }

        if (this.avoidDefPropagationWhenBuildingDataDependence) {
            // Short-circuit: avoid the old defs continuing to propagate forward
            if (cfgNode.core.getAssignedVariables().contains(variable)) {
                return;
            }
        }

        for (final CFGNode<?> forwardNode : cfgNode.getForwardNodes()) {
            this.buildDataDependence(forwardNode, fromPDGNode, variable, checkedCFGNodes);
        }
    }

    /**
     * Try to build a control dependency edge from "fromPDGNode" to statements in the "block".
     * @param fromPDGNode The source PDG node
     * @param block The target block
     */
    private void buildControlDependence(final PDGControlNode fromPDGNode,
                                        final BlockInfo block) {
        for (final StatementInfo statement : block.getStatements()) {
            this.buildControlDependence(fromPDGNode, statement, true);
        }

        if (block instanceof StatementInfo) {
            for (final StatementInfo statement : ((StatementInfo) block).getElseStatements()) {
                this.buildControlDependence(fromPDGNode, statement, false);
            }

            for (final ProgramElementInfo updater : ((StatementInfo) block).getUpdaters()) {
                final PDGNode<?> toPDGNode = this.pdgNodeFactory.makeNormalNode(updater);
                final PDGControlDependenceEdge edge = new PDGControlDependenceEdge(fromPDGNode, toPDGNode, true);
                fromPDGNode.addForwardEdge(edge);
                toPDGNode.addBackwardEdge(edge);
            }
        }
    }

    /**
     * Try to build a control dependency edge from "fromPDGNode" to the statement with a boolean value on the edge.
     * @param fromPDGNode The source PDG node
     * @param statement The target statement
     * @param type The value of the edge, such as "true" for "if-then", "false" for "if-else"
     */
    private void buildControlDependence(final PDGControlNode fromPDGNode,
                                        final StatementInfo statement, final boolean type) {
        switch (statement.getCategory()) {
            case Catch, Do, For, Foreach, If, SimpleBlock, Synchronized, Switch, Try, While -> {
                final ProgramElementInfo condition = statement.getCondition();
                if (null != condition) {
                    final PDGNode<?> toPDGNode = this.pdgNodeFactory.makeControlNode(condition);
                    final PDGControlDependenceEdge edge = new PDGControlDependenceEdge(fromPDGNode, toPDGNode, type);
                    fromPDGNode.addForwardEdge(edge);
                    toPDGNode.addBackwardEdge(edge);
                } else {
                    this.buildControlDependence(fromPDGNode, statement);
                }

                for (final ProgramElementInfo initializer : statement.getInitializers()) {
                    final PDGNode<?> toPDGNode = this.pdgNodeFactory.makeNormalNode(initializer);
                    final PDGControlDependenceEdge edge = new PDGControlDependenceEdge(fromPDGNode, toPDGNode, type);
                    fromPDGNode.addForwardEdge(edge);
                    toPDGNode.addBackwardEdge(edge);
                }
            }
            case Assert, Break, Case, Continue, Expression, Return, Throw, VariableDeclaration -> {
                final CFGNode<?> cfgNode = this.cfgNodeFactory.getNode(statement);
                if ((null != cfgNode) && (this.cfg.getAllNodes().contains(cfgNode))) {
                    final PDGNode<?> toPDGNode = this.pdgNodeFactory.makeNormalNode(statement);
                    final PDGControlDependenceEdge edge = new PDGControlDependenceEdge(fromPDGNode, toPDGNode, type);
                    fromPDGNode.addForwardEdge(edge);
                    toPDGNode.addBackwardEdge(edge);
                }
            }
            default -> {}
        }
    }
}
