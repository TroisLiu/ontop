package it.unibz.inf.ontop.reformulation.tests;

import com.google.common.collect.ImmutableMap;
import it.unibz.inf.ontop.model.*;
import it.unibz.inf.ontop.model.impl.AtomPredicateImpl;
import it.unibz.inf.ontop.model.impl.URITemplatePredicateImpl;
import it.unibz.inf.ontop.owlrefplatform.core.basicoperations.ImmutableSubstitutionImpl;
import it.unibz.inf.ontop.pivotalrepr.*;
import it.unibz.inf.ontop.pivotalrepr.equivalence.IQSyntacticEquivalenceChecker;
import it.unibz.inf.ontop.pivotalrepr.impl.*;
import it.unibz.inf.ontop.pivotalrepr.proposal.impl.InnerJoinOptimizationProposalImpl;
import it.unibz.inf.ontop.sql.*;
import org.junit.Test;

import java.sql.Types;
import java.util.Optional;

import static it.unibz.inf.ontop.model.Predicate.COL_TYPE.INTEGER;
import static junit.framework.TestCase.assertTrue;

import static it.unibz.inf.ontop.OptimizationTestingTools.*;

/**
 * Optimizations for inner joins based on foreign keys
 */
public class RedundantJoinFKTest {

    private final static AtomPredicate TABLE1_PREDICATE;
    private final static AtomPredicate TABLE2_PREDICATE;
    private final static AtomPredicate TABLE3_PREDICATE;
    private final static AtomPredicate TABLE4_PREDICATE;
    private final static AtomPredicate ANS1_PREDICATE = new AtomPredicateImpl("ans1", 1);
    private final static Variable X = DATA_FACTORY.getVariable("X");
    private final static Variable A = DATA_FACTORY.getVariable("A");
    private final static Variable B = DATA_FACTORY.getVariable("B");
    private final static Variable C = DATA_FACTORY.getVariable("C");
    private final static Variable D = DATA_FACTORY.getVariable("D");
    private final static Variable E = DATA_FACTORY.getVariable("E");
    private final static Variable F = DATA_FACTORY.getVariable("F");
    private final static Variable P1 = DATA_FACTORY.getVariable("P");

    private static Constant ONE = DATA_FACTORY.getConstantLiteral("1", INTEGER);

    private final static ImmutableExpression EXPRESSION = DATA_FACTORY.getImmutableExpression(
            ExpressionOperation.EQ, B, ONE);

    private static final DBMetadata DB_METADATA;

    private static URITemplatePredicate URI_PREDICATE_ONE_VAR =  new URITemplatePredicateImpl(2);
    private static Constant URI_TEMPLATE_STR_1 =  DATA_FACTORY.getConstantLiteral("http://example.org/ds1/{}");
    private static Constant URI_TEMPLATE_STR_2 =  DATA_FACTORY.getConstantLiteral("http://example.org/ds2/{}");

    static {

        /**
         * build the FKs
         */
        BasicDBMetadata dbMetadata = DBMetadataTestingTools.createDummyMetadata();
        QuotedIDFactory idFactory = dbMetadata.getQuotedIDFactory();

        DatabaseRelationDefinition table1Def = dbMetadata.createDatabaseRelation(idFactory.createRelationID(null, "TABLE1"));
        Attribute pk1 = table1Def.addAttribute(idFactory.createAttributeID("col1"), Types.INTEGER, null, false);
        table1Def.addAttribute(idFactory.createAttributeID("col2"), Types.INTEGER, null, false);
        TABLE1_PREDICATE = Relation2DatalogPredicate.createAtomPredicateFromRelation(table1Def);

        DatabaseRelationDefinition table2Def = dbMetadata.createDatabaseRelation(idFactory.createRelationID(null, "TABLE2"));
        table2Def.addAttribute(idFactory.createAttributeID("col1"), Types.INTEGER, null, false);
        Attribute table2Col2 = table2Def.addAttribute(idFactory.createAttributeID("col2"), Types.INTEGER, null, false);
        table2Def.addForeignKeyConstraint(ForeignKeyConstraint.of("fk2-1", table2Col2, pk1));
        TABLE2_PREDICATE = Relation2DatalogPredicate.createAtomPredicateFromRelation(table2Def);

        DatabaseRelationDefinition table3Def = dbMetadata.createDatabaseRelation(idFactory.createRelationID(null, "TABLE3"));
        Attribute pk2 = table3Def.addAttribute(idFactory.createAttributeID("col1"), Types.INTEGER, null, false);
        Attribute pk3 = table3Def.addAttribute(idFactory.createAttributeID("col2"), Types.INTEGER, null, false);
        table3Def.addAttribute(idFactory.createAttributeID("col3"), Types.INTEGER, null, false);
        TABLE3_PREDICATE = Relation2DatalogPredicate.createAtomPredicateFromRelation(table3Def);

        DatabaseRelationDefinition table4Def = dbMetadata.createDatabaseRelation(idFactory.createRelationID(null, "TABLE4"));
        table4Def.addAttribute(idFactory.createAttributeID("col1"), Types.INTEGER, null, false);
        Attribute table4Col2 = table4Def.addAttribute(idFactory.createAttributeID("col2"), Types.INTEGER, null, false);
        Attribute table4Col3 = table4Def.addAttribute(idFactory.createAttributeID("col3"), Types.INTEGER, null, false);

        table4Def.addForeignKeyConstraint(new ForeignKeyConstraint.Builder(table4Def, table3Def)
                .add(table4Col2, pk2)
                .add(table4Col3, pk3)
                .build("fk2-1"));
        TABLE4_PREDICATE = Relation2DatalogPredicate.createAtomPredicateFromRelation(table4Def);

        dbMetadata.freeze();

        DB_METADATA = dbMetadata;
    }


    @Test
    public void testForeignKeyOptimization() throws EmptyQueryException {

        IntermediateQueryBuilder queryBuilder = createQueryBuilder(DB_METADATA);
        DistinctVariableOnlyDataAtom projectionAtom = DATA_FACTORY.getDistinctVariableOnlyDataAtom(ANS1_PREDICATE, A);
        ConstructionNode constructionNode = IQ_FACTORY.createConstructionNode(projectionAtom.getVariables(),
                new ImmutableSubstitutionImpl<>(ImmutableMap.of(P1, generateURI1(A), C, generateURI1(D))), Optional.empty());
        queryBuilder.init(projectionAtom, constructionNode);

        InnerJoinNode joinNode = IQ_FACTORY.createInnerJoinNode();
        queryBuilder.addChild(constructionNode, joinNode);
        ExtensionalDataNode dataNode1 =  IQ_FACTORY.createExtensionalDataNode(DATA_FACTORY.getDataAtom(TABLE1_PREDICATE, A, B));
        ExtensionalDataNode dataNode2 =  IQ_FACTORY.createExtensionalDataNode(DATA_FACTORY.getDataAtom(TABLE2_PREDICATE, D, A));

        queryBuilder.addChild(joinNode, dataNode1);
        queryBuilder.addChild(joinNode, dataNode2);

        IntermediateQuery query = queryBuilder.build();
        System.out.println("\nBefore optimization: \n" +  query);

        query.applyProposal(new InnerJoinOptimizationProposalImpl(joinNode));

        System.out.println("\n After optimization: \n" +  query);

        IntermediateQueryBuilder expectedQueryBuilder = createQueryBuilder(DB_METADATA);
        DistinctVariableOnlyDataAtom projectionAtom1 = DATA_FACTORY.getDistinctVariableOnlyDataAtom(ANS1_PREDICATE, A);
        ConstructionNode constructionNode1 = IQ_FACTORY.createConstructionNode(projectionAtom.getVariables(),
                new ImmutableSubstitutionImpl<>(ImmutableMap.of(P1, generateURI1(A), C, generateURI1(D))), Optional.empty());
        expectedQueryBuilder.init(projectionAtom1, constructionNode1);
        expectedQueryBuilder.addChild(constructionNode1, dataNode2);

        IntermediateQuery expectedQuery = expectedQueryBuilder.build();

        System.out.println("\n Expected query: \n" +  expectedQuery);

        assertTrue(IQSyntacticEquivalenceChecker.areEquivalent(query, expectedQuery));
    }


    @Test
    public void testForeignKeyNonOptimization() throws EmptyQueryException {

        IntermediateQueryBuilder queryBuilder = createQueryBuilder(DB_METADATA);
        DistinctVariableOnlyDataAtom projectionAtom = DATA_FACTORY.getDistinctVariableOnlyDataAtom(ANS1_PREDICATE, A);
        ConstructionNode constructionNode = IQ_FACTORY.createConstructionNode(projectionAtom.getVariables(),
                new ImmutableSubstitutionImpl<>(ImmutableMap.of(
                        P1, generateURI1(A),
                        C, generateURI1(D),
                        X, generateURI1(B))), Optional.empty());
        queryBuilder.init(projectionAtom, constructionNode);

        InnerJoinNode joinNode = IQ_FACTORY.createInnerJoinNode();
        queryBuilder.addChild(constructionNode, joinNode);
        ExtensionalDataNode dataNode1 =  IQ_FACTORY.createExtensionalDataNode(DATA_FACTORY.getDataAtom(TABLE1_PREDICATE, A, B));
        ExtensionalDataNode dataNode2 =  IQ_FACTORY.createExtensionalDataNode(DATA_FACTORY.getDataAtom(TABLE2_PREDICATE, D, A));

        queryBuilder.addChild(joinNode, dataNode1);
        queryBuilder.addChild(joinNode, dataNode2);

        IntermediateQuery query = queryBuilder.build();
        System.out.println("\nBefore optimization: \n" +  query);

        IntermediateQuery expectedQuery = query.createSnapshot();
        System.out.println("\n Expected query: \n" +  expectedQuery);

        query.applyProposal(new InnerJoinOptimizationProposalImpl(joinNode));

        System.out.println("\n After optimization: \n" +  query);

        assertTrue(IQSyntacticEquivalenceChecker.areEquivalent(query, expectedQuery));
    }


    @Test
    public void testForeignKeyNonOptimization1() throws EmptyQueryException {

        IntermediateQueryBuilder queryBuilder = createQueryBuilder(DB_METADATA);
        DistinctVariableOnlyDataAtom projectionAtom = DATA_FACTORY.getDistinctVariableOnlyDataAtom(ANS1_PREDICATE, A);
        ConstructionNode constructionNode = IQ_FACTORY.createConstructionNode(projectionAtom.getVariables(),
                new ImmutableSubstitutionImpl<>(ImmutableMap.of(
                        P1, generateURI1(A))), Optional.empty());
        queryBuilder.init(projectionAtom, constructionNode);

        InnerJoinNode joinNode = IQ_FACTORY.createInnerJoinNode();
        queryBuilder.addChild(constructionNode, joinNode);
        ExtensionalDataNode dataNode1 =  IQ_FACTORY.createExtensionalDataNode(DATA_FACTORY.getDataAtom(TABLE1_PREDICATE, A, ONE));
        ExtensionalDataNode dataNode2 =  IQ_FACTORY.createExtensionalDataNode(DATA_FACTORY.getDataAtom(TABLE2_PREDICATE, B, A));

        queryBuilder.addChild(joinNode, dataNode1);
        queryBuilder.addChild(joinNode, dataNode2);

        IntermediateQuery query = queryBuilder.build();
        System.out.println("\nBefore optimization: \n" +  query);

        IntermediateQuery expectedQuery = query.createSnapshot();
        System.out.println("\n Expected query: \n" +  expectedQuery);

        query.applyProposal(new InnerJoinOptimizationProposalImpl(joinNode));

        System.out.println("\n After optimization: \n" +  query);

        assertTrue(IQSyntacticEquivalenceChecker.areEquivalent(query, expectedQuery));
    }

    @Test
    public void testForeignKeyNonOptimization2() throws EmptyQueryException {

        IntermediateQueryBuilder queryBuilder = createQueryBuilder(DB_METADATA);
        DistinctVariableOnlyDataAtom projectionAtom = DATA_FACTORY.getDistinctVariableOnlyDataAtom(ANS1_PREDICATE, A);
        ConstructionNode constructionNode = IQ_FACTORY.createConstructionNode(projectionAtom.getVariables(),
                new ImmutableSubstitutionImpl<>(ImmutableMap.of(
                        P1, generateURI1(A),
                        C, generateURI1(D))), Optional.empty());
        queryBuilder.init(projectionAtom, constructionNode);

        FilterNode filterNode = IQ_FACTORY.createFilterNode(EXPRESSION);
        queryBuilder.addChild(constructionNode, filterNode);
        InnerJoinNode joinNode = IQ_FACTORY.createInnerJoinNode();
        queryBuilder.addChild(filterNode, joinNode);

        ExtensionalDataNode dataNode1 =  IQ_FACTORY.createExtensionalDataNode(DATA_FACTORY.getDataAtom(TABLE1_PREDICATE, A, B));
        ExtensionalDataNode dataNode2 =  IQ_FACTORY.createExtensionalDataNode(DATA_FACTORY.getDataAtom(TABLE2_PREDICATE, D, A));

        queryBuilder.addChild(joinNode, dataNode1);
        queryBuilder.addChild(joinNode, dataNode2);

        IntermediateQuery query = queryBuilder.build();
        System.out.println("\nBefore optimization: \n" +  query);

        IntermediateQuery expectedQuery = query.createSnapshot();
        System.out.println("\n Expected query: \n" +  expectedQuery);

        query.applyProposal(new InnerJoinOptimizationProposalImpl(joinNode));

        System.out.println("\n After optimization: \n" +  query);

        assertTrue(IQSyntacticEquivalenceChecker.areEquivalent(query, expectedQuery));
    }

    @Test
    public void testForeignKeyNonOptimization3() throws EmptyQueryException {

        IntermediateQueryBuilder queryBuilder = createQueryBuilder(DB_METADATA);
        DistinctVariableOnlyDataAtom projectionAtom = DATA_FACTORY.getDistinctVariableOnlyDataAtom(ANS1_PREDICATE, A);
        ConstructionNode constructionNode = IQ_FACTORY.createConstructionNode(projectionAtom.getVariables(),
                new ImmutableSubstitutionImpl<>(ImmutableMap.of(
                        P1, generateURI1(A))), Optional.empty());
        queryBuilder.init(projectionAtom, constructionNode);

        InnerJoinNode joinNode = IQ_FACTORY.createInnerJoinNode();
        queryBuilder.addChild(constructionNode, joinNode);
        ExtensionalDataNode dataNode1 =  IQ_FACTORY.createExtensionalDataNode(DATA_FACTORY.getDataAtom(TABLE1_PREDICATE, A, A));
        ExtensionalDataNode dataNode2 =  IQ_FACTORY.createExtensionalDataNode(DATA_FACTORY.getDataAtom(TABLE2_PREDICATE, B, A));

        queryBuilder.addChild(joinNode, dataNode1);
        queryBuilder.addChild(joinNode, dataNode2);

        IntermediateQuery query = queryBuilder.build();
        System.out.println("\nBefore optimization: \n" +  query);

        IntermediateQuery expectedQuery = query.createSnapshot();
        System.out.println("\n Expected query: \n" +  expectedQuery);

        query.applyProposal(new InnerJoinOptimizationProposalImpl(joinNode));

        System.out.println("\n After optimization: \n" +  query);

        assertTrue(IQSyntacticEquivalenceChecker.areEquivalent(query, expectedQuery));
    }

    @Test
    public void testForeignKeyOptimization1() throws EmptyQueryException {

        IntermediateQueryBuilder queryBuilder = createQueryBuilder(DB_METADATA);
        DistinctVariableOnlyDataAtom projectionAtom = DATA_FACTORY.getDistinctVariableOnlyDataAtom(ANS1_PREDICATE, A);
        ConstructionNode constructionNode = IQ_FACTORY.createConstructionNode(projectionAtom.getVariables(),
                new ImmutableSubstitutionImpl<>(ImmutableMap.of(P1, generateURI1(A), C, generateURI1(D))), Optional.empty());
        queryBuilder.init(projectionAtom, constructionNode);

        InnerJoinNode joinNode = IQ_FACTORY.createInnerJoinNode();
        queryBuilder.addChild(constructionNode, joinNode);
        ExtensionalDataNode dataNode1_1 =  IQ_FACTORY.createExtensionalDataNode(DATA_FACTORY.getDataAtom(TABLE1_PREDICATE, A, A));
        ExtensionalDataNode dataNode1_2 =  IQ_FACTORY.createExtensionalDataNode(DATA_FACTORY.getDataAtom(TABLE1_PREDICATE, A, B));
        ExtensionalDataNode dataNode1_3 =  IQ_FACTORY.createExtensionalDataNode(DATA_FACTORY.getDataAtom(TABLE1_PREDICATE, C, E));
        ExtensionalDataNode dataNode1_4 =  IQ_FACTORY.createExtensionalDataNode(DATA_FACTORY.getDataAtom(TABLE1_PREDICATE, A, F));
        ExtensionalDataNode dataNode2_1 =  IQ_FACTORY.createExtensionalDataNode(DATA_FACTORY.getDataAtom(TABLE2_PREDICATE, D, A));
        ExtensionalDataNode dataNode2_2 =  IQ_FACTORY.createExtensionalDataNode(DATA_FACTORY.getDataAtom(TABLE2_PREDICATE, B, A));
        ExtensionalDataNode dataNode2_3 =  IQ_FACTORY.createExtensionalDataNode(DATA_FACTORY.getDataAtom(TABLE2_PREDICATE, D, A));
        ExtensionalDataNode dataNode2_4 =  IQ_FACTORY.createExtensionalDataNode(DATA_FACTORY.getDataAtom(TABLE2_PREDICATE, D, C));

        queryBuilder.addChild(joinNode, dataNode1_1);
        queryBuilder.addChild(joinNode, dataNode1_2);
        queryBuilder.addChild(joinNode, dataNode1_3);
        queryBuilder.addChild(joinNode, dataNode1_4);
        queryBuilder.addChild(joinNode, dataNode2_1);
        queryBuilder.addChild(joinNode, dataNode2_2);
        queryBuilder.addChild(joinNode, dataNode2_3);
        queryBuilder.addChild(joinNode, dataNode2_4);

        IntermediateQuery query = queryBuilder.build();
        System.out.println("\nBefore optimization: \n" +  query);

        query.applyProposal(new InnerJoinOptimizationProposalImpl(joinNode));

        System.out.println("\n After optimization: \n" +  query);

        IntermediateQueryBuilder expectedQueryBuilder = createQueryBuilder(DB_METADATA);
        DistinctVariableOnlyDataAtom projectionAtom1 = DATA_FACTORY.getDistinctVariableOnlyDataAtom(ANS1_PREDICATE, A);
        ConstructionNode constructionNode1 = IQ_FACTORY.createConstructionNode(projectionAtom.getVariables(),
                new ImmutableSubstitutionImpl<>(ImmutableMap.of(P1, generateURI1(A), C, generateURI1(D))), Optional.empty());
        expectedQueryBuilder.init(projectionAtom1, constructionNode1);
        InnerJoinNode joinNode1 = IQ_FACTORY.createInnerJoinNode();
        expectedQueryBuilder.addChild(constructionNode1, joinNode1);
        expectedQueryBuilder.addChild(joinNode1, dataNode1_1);
        expectedQueryBuilder.addChild(joinNode1, dataNode1_2);
        expectedQueryBuilder.addChild(joinNode1, dataNode2_1);
        expectedQueryBuilder.addChild(joinNode1, dataNode2_2);
        expectedQueryBuilder.addChild(joinNode1, dataNode2_3);
        expectedQueryBuilder.addChild(joinNode1, dataNode2_4);

        IntermediateQuery expectedQuery = expectedQueryBuilder.build();

        System.out.println("\n Expected query: \n" +  expectedQuery);

        assertTrue(IQSyntacticEquivalenceChecker.areEquivalent(query, expectedQuery));
    }

    @Test
    public void testForeignKeyOptimization2() throws EmptyQueryException {

        IntermediateQueryBuilder queryBuilder = createQueryBuilder(DB_METADATA);
        DistinctVariableOnlyDataAtom projectionAtom = DATA_FACTORY.getDistinctVariableOnlyDataAtom(ANS1_PREDICATE, A);
        ConstructionNode constructionNode = IQ_FACTORY.createConstructionNode(projectionAtom.getVariables(),
                new ImmutableSubstitutionImpl<>(ImmutableMap.of(P1, generateURI1(A))), Optional.empty());
        queryBuilder.init(projectionAtom, constructionNode);

        InnerJoinNode joinNode = IQ_FACTORY.createInnerJoinNode();
        queryBuilder.addChild(constructionNode, joinNode);
        ExtensionalDataNode dataNode1 =  IQ_FACTORY.createExtensionalDataNode(DATA_FACTORY.getDataAtom(TABLE3_PREDICATE, A, B, C));
        ExtensionalDataNode dataNode2 =  IQ_FACTORY.createExtensionalDataNode(DATA_FACTORY.getDataAtom(TABLE4_PREDICATE, D, A, B));

        queryBuilder.addChild(joinNode, dataNode1);
        queryBuilder.addChild(joinNode, dataNode2);

        IntermediateQuery query = queryBuilder.build();
        System.out.println("\nBefore optimization: \n" +  query);

        query.applyProposal(new InnerJoinOptimizationProposalImpl(joinNode));

        System.out.println("\n After optimization: \n" +  query);

        IntermediateQueryBuilder expectedQueryBuilder = createQueryBuilder(DB_METADATA);
        DistinctVariableOnlyDataAtom projectionAtom1 = DATA_FACTORY.getDistinctVariableOnlyDataAtom(ANS1_PREDICATE, A);
        ConstructionNode constructionNode1 = IQ_FACTORY.createConstructionNode(projectionAtom.getVariables(),
                new ImmutableSubstitutionImpl<>(ImmutableMap.of(P1, generateURI1(A))), Optional.empty());
        expectedQueryBuilder.init(projectionAtom1, constructionNode1);
        expectedQueryBuilder.addChild(constructionNode1, dataNode2);

        IntermediateQuery expectedQuery = expectedQueryBuilder.build();

        System.out.println("\n Expected query: \n" +  expectedQuery);

        assertTrue(IQSyntacticEquivalenceChecker.areEquivalent(query, expectedQuery));
    }

    @Test
    public void testForeignKeyNonOptimization4() throws EmptyQueryException {

        IntermediateQueryBuilder queryBuilder = createQueryBuilder(DB_METADATA);
        DistinctVariableOnlyDataAtom projectionAtom = DATA_FACTORY.getDistinctVariableOnlyDataAtom(ANS1_PREDICATE, A);
        ConstructionNode constructionNode = IQ_FACTORY.createConstructionNode(projectionAtom.getVariables(),
                new ImmutableSubstitutionImpl<>(ImmutableMap.of(
                        P1, generateURI1(A))), Optional.empty());
        queryBuilder.init(projectionAtom, constructionNode);

        InnerJoinNode joinNode = IQ_FACTORY.createInnerJoinNode();
        queryBuilder.addChild(constructionNode, joinNode);
        ExtensionalDataNode dataNode1 =  IQ_FACTORY.createExtensionalDataNode(DATA_FACTORY.getDataAtom(TABLE3_PREDICATE, A, B, C));
        ExtensionalDataNode dataNode2 =  IQ_FACTORY.createExtensionalDataNode(DATA_FACTORY.getDataAtom(TABLE4_PREDICATE, D, B, A));

        queryBuilder.addChild(joinNode, dataNode1);
        queryBuilder.addChild(joinNode, dataNode2);

        IntermediateQuery query = queryBuilder.build();
        System.out.println("\nBefore optimization: \n" +  query);

        IntermediateQuery expectedQuery = query.createSnapshot();
        System.out.println("\n Expected query: \n" +  expectedQuery);

        query.applyProposal(new InnerJoinOptimizationProposalImpl(joinNode));

        System.out.println("\n After optimization: \n" +  query);

        assertTrue(IQSyntacticEquivalenceChecker.areEquivalent(query, expectedQuery));
    }

    @Test
    public void testForeignKeyNonOptimization5() throws EmptyQueryException {

        IntermediateQueryBuilder queryBuilder = createQueryBuilder(DB_METADATA);
        DistinctVariableOnlyDataAtom projectionAtom = DATA_FACTORY.getDistinctVariableOnlyDataAtom(ANS1_PREDICATE, A);
        ConstructionNode constructionNode = IQ_FACTORY.createConstructionNode(projectionAtom.getVariables(),
                new ImmutableSubstitutionImpl<>(ImmutableMap.of(
                        P1, generateURI1(A))), Optional.empty());
        queryBuilder.init(projectionAtom, constructionNode);

        InnerJoinNode joinNode = IQ_FACTORY.createInnerJoinNode();
        queryBuilder.addChild(constructionNode, joinNode);
        ExtensionalDataNode dataNode1 =  IQ_FACTORY.createExtensionalDataNode(DATA_FACTORY.getDataAtom(TABLE3_PREDICATE, A, A, C));
        ExtensionalDataNode dataNode2 =  IQ_FACTORY.createExtensionalDataNode(DATA_FACTORY.getDataAtom(TABLE4_PREDICATE, A, A, B));

        queryBuilder.addChild(joinNode, dataNode1);
        queryBuilder.addChild(joinNode, dataNode2);

        IntermediateQuery query = queryBuilder.build();
        System.out.println("\nBefore optimization: \n" +  query);

        IntermediateQuery expectedQuery = query.createSnapshot();
        System.out.println("\n Expected query: \n" +  expectedQuery);

        query.applyProposal(new InnerJoinOptimizationProposalImpl(joinNode));

        System.out.println("\n After optimization: \n" +  query);

        assertTrue(IQSyntacticEquivalenceChecker.areEquivalent(query, expectedQuery));
    }

    private static ImmutableFunctionalTerm generateURI1(VariableOrGroundTerm argument) {
        return DATA_FACTORY.getImmutableFunctionalTerm(URI_PREDICATE_ONE_VAR, URI_TEMPLATE_STR_1, argument);
    }

    private static ImmutableFunctionalTerm generateURI2(VariableOrGroundTerm argument) {
        return DATA_FACTORY.getImmutableFunctionalTerm(URI_PREDICATE_ONE_VAR, URI_TEMPLATE_STR_2, argument);
    }
}
