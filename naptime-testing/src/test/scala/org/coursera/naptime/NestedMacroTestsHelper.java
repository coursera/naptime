/*
 * Copyright 2016 Coursera Inc.
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

package org.coursera.naptime;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NestedMacroTestsHelper {

    // Use Java to get access to Scala's (hidden) generated methods
    static void setupMockDefaults(FriendsResource mock, String defaultValue) {
        when(mock.withDefaults$default$3()).thenReturn(defaultValue);
    }

    static void setupMockDefaults(PersonResource mock, ComplexEmailType defaultValue) {
        when(mock.complexWithDefault$default$1()).thenReturn(defaultValue);
    }

    static void verifyDefaultNotTaken(PersonResource mock) {
        verify(mock, never()).complexWithDefault$default$1();
    }
}
