package org.opensearch.ml.engine;

import org.mockito.MockSettings;
import org.mockito.MockedStatic;
import org.mockito.internal.creation.MockSettingsImpl;

/**
 * This class provides a way to use Mockito's MockedStatic with inline mock maker enabled.
 * It can be used as a base class for other test classes that require mocking static methods.
 *
 * Note: before using this class to mock static function, think twice if your function really has to be
 * static as static functions are tightly coupled with coding using them and have bad testability.
 *
 * Example usage:
 *
 * public class MyClassTest extends MLStaticMockBase {
 *     // Test methods go here
 * }
 *
 * It's to overcome the issue described in https://github.com/opensearch-project/OpenSearch/issues/14420
 */
public class MLStaticMockBase {
    private static final String inlineMockMaker = "org.mockito.internal.creation.bytebuddy.InlineByteBuddyMockMaker";

    private MockSettings mockSettingsWithInlineMockMaker = new MockSettingsImpl().mockMaker(inlineMockMaker);

    protected <T> MockedStatic<T> mockStatic(Class<T> classToMock) {
        return org.mockito.Mockito.mockStatic(classToMock, mockSettingsWithInlineMockMaker);
    }

    protected <T> MockedStatic<T> mockStatic(Class<T> classToMock, MockSettings settings) {
        MockSettings newSettings = settings.mockMaker(inlineMockMaker);
        return org.mockito.Mockito.mockStatic(classToMock, newSettings);
    }
}
