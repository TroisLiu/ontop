package it.unibz.inf.ontop.owlrefplatform.core.optimization;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import it.unibz.inf.ontop.model.ImmutableSubstitution;
import it.unibz.inf.ontop.model.ImmutableTerm;
import it.unibz.inf.ontop.model.Variable;
import it.unibz.inf.ontop.owlrefplatform.core.Quest;
import it.unibz.inf.ontop.owlrefplatform.core.basicoperations.ImmutableSubstitutionImpl;
import it.unibz.inf.ontop.owlrefplatform.core.optimization.QueryNodeNavigationTools.NextNodeAndQuery;
import it.unibz.inf.ontop.pivotalrepr.*;
import it.unibz.inf.ontop.pivotalrepr.proposal.NodeCentricOptimizationResults;
import it.unibz.inf.ontop.pivotalrepr.proposal.SubstitutionPropagationProposal;
import it.unibz.inf.ontop.pivotalrepr.proposal.UnionLiftProposal;
import it.unibz.inf.ontop.pivotalrepr.proposal.impl.SubstitutionPropagationProposalImpl;
import it.unibz.inf.ontop.pivotalrepr.proposal.impl.UnionLiftProposalImpl;
import it.unibz.inf.ontop.utils.ImmutableCollectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static it.unibz.inf.ontop.owlrefplatform.core.optimization.QueryNodeNavigationTools.getDepthFirstNextNode;
import static it.unibz.inf.ontop.pivotalrepr.NonCommutativeOperatorNode.ArgumentPosition.LEFT;
import static it.unibz.inf.ontop.pivotalrepr.NonCommutativeOperatorNode.ArgumentPosition.RIGHT;

/**
 * Optimizer to extract and propagate bindings in the query up and down the tree.
 * Uses {@link UnionFriendlyBindingExtractor}, {@link SubstitutionPropagationProposal} and {@link UnionLiftProposal}
 *
 */
public class TopDownSubstitutionLiftOptimizer implements SubstitutionLiftOptimizer {

    private final Logger log = LoggerFactory.getLogger(Quest.class);
    private final SimpleUnionNodeLifter lifter = new SimpleUnionNodeLifter();
    private final UnionFriendlyBindingExtractor extractor = new UnionFriendlyBindingExtractor();

    @Override
    public IntermediateQuery optimize(IntermediateQuery query) throws EmptyQueryException {
        // Non-final
        NextNodeAndQuery nextNodeAndQuery = new NextNodeAndQuery(
                query.getFirstChild(query.getRootConstructionNode()),
                query);

        //explore the tree lifting the bindings when it is possible
        while (nextNodeAndQuery.getOptionalNextNode().isPresent()) {
            nextNodeAndQuery = liftBindings(nextNodeAndQuery.getNextQuery(),
                    nextNodeAndQuery.getOptionalNextNode().get());

            log.debug(String.valueOf(nextNodeAndQuery.getNextQuery()));

        }

        return nextNodeAndQuery.getNextQuery();
    }

    private NextNodeAndQuery liftBindings(IntermediateQuery currentQuery, QueryNode currentNode)
            throws EmptyQueryException {

        if (currentNode instanceof ConstructionNode) {
            return liftBindingsFromConstructionNode(currentQuery, (ConstructionNode) currentNode);
        }
        else if (currentNode instanceof CommutativeJoinNode) {
            return liftBindingsFromCommutativeJoinNode(currentQuery, (CommutativeJoinNode) currentNode);
        }
        else if (currentNode instanceof LeftJoinNode) {
            return liftBindingsFromLeftJoinNode(currentQuery, (LeftJoinNode) currentNode);
        }
        else if (currentNode instanceof UnionNode) {
            return liftBindingsAndUnion(currentQuery, (UnionNode) currentNode);
        }
        /**
         * Other nodes: does nothing
         */
        else {
            return new NextNodeAndQuery(getDepthFirstNextNode(currentQuery, currentNode), currentQuery);
        }
    }

    /* Lift the bindings of the union to see if it is possible to simplify the tree.
      Otherwise try to lift the union to an ancestor with useful projected variables between its children
      (common with the conflicting bindings of the union).
      */
    private NextNodeAndQuery liftBindingsAndUnion(IntermediateQuery currentQuery, UnionNode initialUnionNode) throws EmptyQueryException {
        QueryNode currentNode = initialUnionNode;


        //extract bindings (liftable bindings and conflicting one) from the union node
        final BindingExtractor.Extraction extraction = extractor.extractInSubTree(
                currentQuery, currentNode);

        //get liftable bindings
        Optional<ImmutableSubstitution<ImmutableTerm>> optionalSubstitution = extraction.getOptionalSubstitution();

        if (optionalSubstitution.isPresent()) {

            //try to lift the bindings up and down the tree
            SubstitutionPropagationProposal<QueryNode> proposal =
                    new SubstitutionPropagationProposalImpl<>(currentNode, optionalSubstitution.get());

            NodeCentricOptimizationResults<QueryNode> results = currentQuery.applyProposal(proposal);
            currentQuery = results.getResultingQuery();
            currentNode = results.getNewNodeOrReplacingChild()
                    .orElseThrow(() -> new IllegalStateException(
                            "The focus was expected to be kept or replaced, not removed"));


        }


        //if the union node has not been removed
        if (currentNode instanceof UnionNode) {

            //variables of bindings that could not be returned because conflicting or not common in the subtree
            ImmutableSet<Variable> irregularVariables = extraction.getVariablesWithConflictingBindings();

            if(!irregularVariables.isEmpty()) {
                UnionNode currentUnionNode = (UnionNode) currentNode;
                return liftUnionToMatchingVariable(currentQuery, currentUnionNode, irregularVariables);
            }


        }
        return new NextNodeAndQuery(getDepthFirstNextNode(currentQuery, currentNode), currentQuery);



    }


    /*  Lift the union to an ancestor with useful projected variables between its children,
       These variables are common with the bindings of the union. */
    private NextNodeAndQuery liftUnionToMatchingVariable(IntermediateQuery currentQuery, UnionNode currentUnionNode, ImmutableSet<Variable> unionVariables) throws EmptyQueryException {


        Optional<QueryNode> parentNode = lifter.chooseLevelLift(currentQuery, currentUnionNode, unionVariables);

        if(parentNode.isPresent()){

            UnionLiftProposal proposal = new UnionLiftProposalImpl(currentUnionNode, parentNode.get());
            NodeCentricOptimizationResults<UnionNode> results = currentQuery.applyProposal(proposal);
            currentQuery = results.getResultingQuery();
            currentUnionNode = results.getOptionalNewNode().orElseThrow(() -> new IllegalStateException(
                    "The focus node has to be a union node and be present"));

            return liftBindingsAndUnion(currentQuery, currentUnionNode);
        }

        //no parent with the given variable, I don't lift for the moment

        return new NextNodeAndQuery(getDepthFirstNextNode(currentQuery, currentUnionNode), currentQuery);



    }


    private NextNodeAndQuery liftBindingsFromConstructionNode(IntermediateQuery initialQuery,
                                                              ConstructionNode initialConstrNode)
            throws EmptyQueryException {

        IntermediateQuery currentQuery = initialQuery;
        QueryNode currentNode = initialConstrNode;

        //extract substitution from the construction node
        Optional<ImmutableSubstitution<ImmutableTerm>> optionalSubstitution = extractor.extractInSubTree(
                currentQuery, currentNode).getOptionalSubstitution();

        //propagate substitution up and down
        if (optionalSubstitution.isPresent()) {
            SubstitutionPropagationProposal<QueryNode> proposal =
                    new SubstitutionPropagationProposalImpl<>(currentNode, optionalSubstitution.get());

            NodeCentricOptimizationResults<QueryNode> results = currentQuery.applyProposal(proposal);
            currentQuery = results.getResultingQuery();
            currentNode = results.getNewNodeOrReplacingChild()
                    .orElseThrow(() -> new IllegalStateException(
                            "The focus was expected to be kept or replaced, not removed"));

        }

        return new NextNodeAndQuery(getDepthFirstNextNode(currentQuery, currentNode), currentQuery);
    }

    private NextNodeAndQuery liftBindingsFromCommutativeJoinNode(IntermediateQuery initialQuery,
                                                                 CommutativeJoinNode initialJoinNode)
            throws EmptyQueryException {

        // Non-final
        Optional<QueryNode> optionalCurrentChild = initialQuery.getFirstChild(initialJoinNode);
        IntermediateQuery currentQuery = initialQuery;
        QueryNode currentJoinNode = initialJoinNode;

        //apply directly to join

        while (optionalCurrentChild.isPresent()) {
            QueryNode currentChild = optionalCurrentChild.get();

            Optional<ImmutableSubstitution<ImmutableTerm>> optionalSubstitution = extractor.extractInSubTree(
                    currentQuery, currentChild).getOptionalSubstitution();

            /**
             * Applies the substitution to the child
             */
            if (optionalSubstitution.isPresent()) {
                SubstitutionPropagationProposal<QueryNode> proposal =
                        new SubstitutionPropagationProposalImpl<>(currentChild, optionalSubstitution.get());

                NodeCentricOptimizationResults<QueryNode> results = currentQuery.applyProposal(proposal);
                currentQuery = results.getResultingQuery();
                optionalCurrentChild = results.getOptionalNextSibling();
                currentJoinNode = currentQuery.getParent(
                        results.getNewNodeOrReplacingChild()
                                .orElseThrow(() -> new IllegalStateException(
                                        "The focus was expected to be kept or replaced, not removed")))
                        .orElseThrow(() -> new IllegalStateException(
                                "The focus node should still have a parent (a Join node)"));



            }
            else {
                optionalCurrentChild = currentQuery.getNextSibling(currentChild);
            }
        }


        return new NextNodeAndQuery(getDepthFirstNextNode(currentQuery, currentJoinNode), currentQuery);

    }


    //lift bindings from left node checking first the left part,
    // lift from the right only the bindings with variables that are not common with the left
    private NextNodeAndQuery liftBindingsFromLeftJoinNode(IntermediateQuery initialQuery, LeftJoinNode initialLeftJoinNode) throws EmptyQueryException {
        // Non-final
        Optional<QueryNode> optionalLeftChild = initialQuery.getChild(initialLeftJoinNode, LEFT);
        IntermediateQuery currentQuery = initialQuery;
        QueryNode currentJoinNode = initialLeftJoinNode;
        Optional<QueryNode> optionalRightChild = currentQuery.getChild(currentJoinNode, RIGHT);

        //check bindings of the right side if there are some that are not projected in the second, they can be already pushed
        //substitution coming from the left have more importance than the one coming from the right
        if (optionalLeftChild.isPresent()) {
            QueryNode leftChild = optionalLeftChild.get();
            Optional<ImmutableSubstitution<ImmutableTerm>> optionalSubstitution = extractor.extractInSubTree(
                    currentQuery, leftChild).getOptionalSubstitution();

            /**
             * Applies the substitution to the child
             */
            if (optionalSubstitution.isPresent()) {
                SubstitutionPropagationProposal<QueryNode> proposal =
                        new SubstitutionPropagationProposalImpl<>(leftChild, optionalSubstitution.get());

                NodeCentricOptimizationResults<QueryNode> results = currentQuery.applyProposal(proposal);
                currentQuery = results.getResultingQuery();
                optionalRightChild = results.getOptionalNextSibling();
                currentJoinNode = currentQuery.getParent(
                        results.getNewNodeOrReplacingChild()
                                .orElseThrow(() -> new IllegalStateException(
                                        "The focus was expected to be kept or replaced, not removed")))
                        .orElseThrow(() -> new IllegalStateException(
                                "The focus node should still have a parent (a Join node)"));
            }
        }

        if (optionalRightChild.filter(rightChild -> !(rightChild instanceof EmptyNode)).isPresent()) {
            QueryNode rightChild = optionalRightChild.get();

            Optional<ImmutableSubstitution<ImmutableTerm>> optionalSubstitution = extractor.extractInSubTree(
                    currentQuery, rightChild).getOptionalSubstitution();
            Set<Variable> onlyRightVariables = new HashSet<>();
            onlyRightVariables.addAll(currentQuery.getVariables(rightChild));
            if(optionalLeftChild.isPresent()){
                onlyRightVariables.removeAll(currentQuery.getVariables(optionalLeftChild.get()));
            }
            Map<Variable, ImmutableTerm> substitutionMap = new HashMap<>();
            onlyRightVariables.forEach(v ->
                    optionalSubstitution.ifPresent(s -> {
                        ImmutableMap<Variable, ImmutableTerm> immutableMap = s.getImmutableMap();
                        if (immutableMap.containsKey(v)) {
                            substitutionMap.put(v, immutableMap.get(v));
                        }
                    })
            );
            ImmutableMap<Variable, ImmutableTerm> immutableMap = substitutionMap.entrySet().stream().collect(ImmutableCollectors.toMap());

            Optional<ImmutableSubstitutionImpl<ImmutableTerm>> substitutionRightMap = Optional.of(immutableMap)
                    .filter(m -> !m.isEmpty())
                    .map(ImmutableSubstitutionImpl::new);

            /**
             * Applies the substitution to the child
             */
            if (substitutionRightMap.isPresent()) {
                SubstitutionPropagationProposal<QueryNode> proposal =
                        new SubstitutionPropagationProposalImpl<>(rightChild, substitutionRightMap.get());

                NodeCentricOptimizationResults<QueryNode> results = currentQuery.applyProposal(proposal);
                currentQuery = results.getResultingQuery();
                currentJoinNode = currentQuery.getParent(
                        results.getNewNodeOrReplacingChild()
                                .orElseThrow(() -> new IllegalStateException(
                                        "The focus was expected to be kept or replaced, not removed")))
                        .orElseThrow(() -> new IllegalStateException(
                                "The focus node should still have a parent (a Join node)"));
            }

        }


        return new NextNodeAndQuery(getDepthFirstNextNode(currentQuery, currentJoinNode), currentQuery);


    }


}
