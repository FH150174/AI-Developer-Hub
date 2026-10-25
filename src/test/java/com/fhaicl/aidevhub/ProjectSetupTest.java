package com.fhaicl.aidevhub;

import javafx.application.Application;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectSetupTest {

    @Test
    void applicationEntryPointIsAvailableWithJavaFxOnTheTestClasspath() throws ClassNotFoundException {
        Class<?> entryPoint = Class.forName(
                "com.fhaicl.aidevhub.AiDeveloperHubApplication", false,
                getClass().getClassLoader());

        assertTrue(Application.class.isAssignableFrom(entryPoint));
    }
}
