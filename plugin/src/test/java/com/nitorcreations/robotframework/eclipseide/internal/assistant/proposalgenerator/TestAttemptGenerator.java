/**
 * Copyright 2013 Nitor Creations Oy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nitorcreations.robotframework.eclipseide.internal.assistant.proposalgenerator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.InOrder;

import com.nitorcreations.junit.runners.NicelyParameterized;
import com.nitorcreations.robotframework.eclipseide.structure.ParsedString;

@RunWith(Enclosed.class)
public class TestAttemptGenerator {

    @Ignore
    public static abstract class Base {
        final AttemptGenerator generator = new AttemptGenerator();

        static final String argumentText = "1234567890";
        final RobotCompletionProposalSet SET_1 = new RobotCompletionProposalSet();
        final RobotCompletionProposalSet SET_2 = new RobotCompletionProposalSet();
        final RobotCompletionProposalSet SET_3 = new RobotCompletionProposalSet();
        final AttemptVisitor attemptVisitor = mock(AttemptVisitor.class, "attemptVisitor");

        final List<RobotCompletionProposalSet> proposalSets = new ArrayList<RobotCompletionProposalSet>();

        @After
        public final void checks() {
            verifyNoMoreInteractions(attemptVisitor);
        }
    }

    public static class Static_behaviour extends Base {
        static final int argumentArgCharPos = 37;
        static final Integer argumentIndex = 3;
        static final int documentOffset = argumentArgCharPos + 4;
        final ParsedString argument = new ParsedString(argumentText, argumentArgCharPos, argumentIndex);
        static final String matchArgument = "1234444";

        @Test
        public void should_pass_region_of_given_argument_as_replacementRegion_to_visitor() {
            when(attemptVisitor.visitAttempt(anyString(), any(IRegion.class))).thenReturn(SET_1);
            RobotCompletionProposal rcp = new RobotCompletionProposal(matchArgument, null, null, null, null, null, null);
            SET_1.getProposals().addAll(Arrays.asList(rcp));

            generator.acceptAttempts(argument, documentOffset, proposalSets, attemptVisitor);

            verify(attemptVisitor).visitAttempt(anyString(), eq(new Region(argument.getArgCharPos(), argument.getValue().length())));
        }

        @Test
        public void should_fail_on_null_return_value_from_visitor() {

            try {
                generator.acceptAttempts(argument, documentOffset, proposalSets, attemptVisitor);
                fail("Should throw NPE");
            } catch (NullPointerException e) {
                // expected
            }

            verify(attemptVisitor).visitAttempt(anyString(), any(IRegion.class));
        }

        @SuppressWarnings("unchecked")
        @Test
        public void should_not_filter_exception_from_visitor() {
            when(attemptVisitor.visitAttempt(anyString(), any(IRegion.class))).thenThrow(IllegalStateException.class);

            try {
                generator.acceptAttempts(argument, documentOffset, proposalSets, attemptVisitor);
            } catch (RuntimeException e) {
                // expected; the original IllegalStateException might be wrapped in some other
                // RuntimeException-extending
                // exception
            }

            verify(attemptVisitor).visitAttempt(anyString(), any(IRegion.class));
        }
    }

    @RunWith(NicelyParameterized.class)
    public static class Attempt_generation extends Base {

        enum AttemptResponse {
            // _ means "not expected to be used"
            EXACT, INEXACT, NONE, SINGLE, MULTIPLE, MULTIPLE_WITH_EXACT, MULTIPLE_WITH_INEXACT, _
        }

        private static final int ARGUMENT_POS = 33;

        public static final AttemptResponse[] allExpected;
        public static final AttemptResponse[] allExpectedForEmpty;
        public static final AttemptResponse[] notExpected = { AttemptResponse._ };

        static {
            Set<AttemptResponse> set = new LinkedHashSet<AttemptResponse>(Arrays.asList(AttemptResponse.values()));
            set.remove(AttemptResponse._);
            allExpected = set.toArray(new AttemptResponse[set.size()]);
            set.remove(AttemptResponse.EXACT);
            set.remove(AttemptResponse.INEXACT);
            set.remove(AttemptResponse.MULTIPLE_WITH_EXACT);
            set.remove(AttemptResponse.MULTIPLE_WITH_INEXACT);
            allExpectedForEmpty = set.toArray(new AttemptResponse[set.size()]);
        }

        @Parameters
        public static List<Object[]> parameters() {
            List<Object[]> p = new ArrayList<Object[]>();
            for (boolean isGivenListPopulated : new boolean[] { false, true }) {
                for (String input : new String[] { "", "abc" }) {
                    for (int argOff = 0; argOff <= input.length() + 1; ++argOff) {
                        int baseAmount = input.isEmpty() ? 1 : argOff > 0 && argOff < input.length() ? 3 : 2;
                        for (AttemptResponse attemptResp1 : baseAmount > 1 ? allExpected : allExpectedForEmpty) {
                            boolean isAttemptResp1Final = baseAmount < 2 || attemptResp1 == AttemptResponse.SINGLE || attemptResp1 == AttemptResponse.MULTIPLE;
                            for (AttemptResponse attemptResp2 : !isAttemptResp1Final ? baseAmount > 2 ? allExpected : allExpectedForEmpty : notExpected) {
                                boolean isAttemptResp1Or2Final = isAttemptResp1Final || baseAmount < 3 || attemptResp2 == AttemptResponse.SINGLE || attemptResp2 == AttemptResponse.MULTIPLE;
                                for (AttemptResponse attemptResp3 : !isAttemptResp1Or2Final ? allExpectedForEmpty : notExpected) {
                                    p.add(new Object[] { isGivenListPopulated, input, argOff, attemptResp1, attemptResp2, attemptResp3 });
                                }
                            }
                        }
                    }
                }
            }
            return p;
        }

        private final boolean isGivenListPopulated;
        private final String input;
        private final int argOff;
        private final AttemptResponse attemptResp1;
        private final AttemptResponse attemptResp2;
        private final AttemptResponse attemptResp3;

        private boolean isFinal1;
        private boolean isFinal2;
        private boolean isFinal3;

        public Attempt_generation(boolean isGivenListPopulated, String input, int argOff, AttemptResponse attemptResp1, AttemptResponse attemptResp2, AttemptResponse attemptResp3) {
            this.isGivenListPopulated = isGivenListPopulated;
            this.input = input;
            this.argOff = argOff;
            this.attemptResp1 = attemptResp1;
            this.attemptResp2 = attemptResp2;
            this.attemptResp3 = attemptResp3;
        }

        @SuppressWarnings("unchecked")
        @Before
        public void setup() {
            // OngoingStubbing<RobotCompletionProposalSet> ongoingStubbing =
            // when(attemptVisitor.visitAttempt(anyString(), any(IRegion.class)));
            // //.thenReturn(SET_1, SET_2, SET_3).thenThrow(IllegalStateException.class);
            when(attemptVisitor.visitAttempt(anyString(), any(IRegion.class))).thenReturn(SET_1, SET_2, SET_3).thenThrow(IllegalStateException.class);
            isFinal1 = setupAttemptResponse(SET_1, attemptResp1);
            isFinal2 = setupAttemptResponse(SET_2, attemptResp2);
            isFinal3 = setupAttemptResponse(SET_3, attemptResp3);
        }

        /**
         * @return true if the attempt is expected to be the final attempt
         */
        private boolean setupAttemptResponse(RobotCompletionProposalSet set, AttemptResponse attemptResp) {
            switch (attemptResp) {
                case EXACT:
                    assertFalse("Test setup fail", input.isEmpty());
                    set.getProposals().add(new RobotCompletionProposal(input, null, null, null, null, null, null));
                    return false; // single exact match - should ignore and continue
                case INEXACT:
                    assertFalse("Test setup fail", input.isEmpty());
                    set.getProposals().add(new RobotCompletionProposal(input.toUpperCase(), null, null, null, null, null, null));
                    return true;
                case NONE:
                case _:
                    return false; // no matches - should continue
                case SINGLE:
                    set.getProposals().add(new RobotCompletionProposal("single", null, null, null, null, null, null));
                    return true;
                case MULTIPLE:
                    set.getProposals().add(new RobotCompletionProposal("multi1", null, null, null, null, null, null));
                    set.getProposals().add(new RobotCompletionProposal("multi2", null, null, null, null, null, null));
                    set.getProposals().add(new RobotCompletionProposal("multi3", null, null, null, null, null, null));
                    return true;
                case MULTIPLE_WITH_EXACT:
                    assertFalse("Test setup fail", input.isEmpty());
                    set.getProposals().add(new RobotCompletionProposal("multi1", null, null, null, null, null, null));
                    set.getProposals().add(new RobotCompletionProposal("multi2", null, null, null, null, null, null));
                    set.getProposals().add(new RobotCompletionProposal("multi3", null, null, null, null, null, null));
                    set.getProposals().add(new RobotCompletionProposal(input, null, null, null, null, null, null));
                    return true;
                case MULTIPLE_WITH_INEXACT:
                    assertFalse("Test setup fail", input.isEmpty());
                    set.getProposals().add(new RobotCompletionProposal("multi1", null, null, null, null, null, null));
                    set.getProposals().add(new RobotCompletionProposal("multi2", null, null, null, null, null, null));
                    set.getProposals().add(new RobotCompletionProposal("multi3", null, null, null, null, null, null));
                    set.getProposals().add(new RobotCompletionProposal(input.toUpperCase(), null, null, null, null, null, null));
                    return true;
                default:
                    throw new IllegalStateException(attemptResp.toString());
            }
        }

        @Test
        public void test() {
            RobotCompletionProposalSet preExistingProposalSet = new RobotCompletionProposalSet();
            List<RobotCompletionProposalSet> expectedProposalSets = new ArrayList<RobotCompletionProposalSet>(2);
            if (isGivenListPopulated) {
                proposalSets.add(preExistingProposalSet);
                expectedProposalSets.add(preExistingProposalSet);
            }

            ParsedString argument = new ParsedString(input, ARGUMENT_POS);

            generator.acceptAttempts(argument, ARGUMENT_POS + argOff, proposalSets, attemptVisitor);

            String inputLC = input.toLowerCase();
            InOrder inOrder = inOrder(attemptVisitor);
            inOrder.verify(attemptVisitor).visitAttempt(eq(inputLC), any(IRegion.class));
            if (!isFinal1 && !inputLC.isEmpty()) {
                String partialInput = argOff > 0 && argOff < inputLC.length() ? inputLC.substring(0, argOff) : "";
                inOrder.verify(attemptVisitor).visitAttempt(eq(partialInput), any(IRegion.class));
                if (!isFinal2 && !partialInput.isEmpty()) {
                    assertNotSame("Test setup fail", "", partialInput);
                    inOrder.verify(attemptVisitor).visitAttempt(eq(""), any(IRegion.class));
                }
            }

            if (isFinal1) {
                expectedProposalSets.add(SET_1);
            } else if (isFinal2) {
                expectedProposalSets.add(SET_2);
            } else if (isFinal3) {
                expectedProposalSets.add(SET_3);
            }
            assertEquals(expectedProposalSets, proposalSets);
        }
    }
}