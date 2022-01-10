/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.fir.components.typeCreator;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.util.KtTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.analysis.api.impl.barebone.test.FrontendApiTestConfiguratorService;
import org.jetbrains.kotlin.analysis.api.fir.FirFrontendApiTestConfiguratorService;
import org.jetbrains.kotlin.analysis.api.impl.base.test.components.typeCreator.AbstractTypeParameterTypeTest;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link GenerateNewCompilerTests.kt}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("analysis/analysis-api/testData/components/typeCreator/typeParameter")
@TestDataPath("$PROJECT_ROOT")
public class FirTypeParameterTypeTestGenerated extends AbstractTypeParameterTypeTest {
    @NotNull
    @Override
    public FrontendApiTestConfiguratorService getConfigurator() {
        return FirFrontendApiTestConfiguratorService.INSTANCE;
    }

    @Test
    public void testAllFilesPresentInTypeParameter() throws Exception {
        KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("analysis/analysis-api/testData/components/typeCreator/typeParameter"), Pattern.compile("^(.+)\\.kt$"), null, true);
    }

    @Test
    @TestMetadata("multipleBounds.kt")
    public void testMultipleBounds() throws Exception {
        runTest("analysis/analysis-api/testData/components/typeCreator/typeParameter/multipleBounds.kt");
    }

    @Test
    @TestMetadata("regular.kt")
    public void testRegular() throws Exception {
        runTest("analysis/analysis-api/testData/components/typeCreator/typeParameter/regular.kt");
    }

    @Test
    @TestMetadata("reified.kt")
    public void testReified() throws Exception {
        runTest("analysis/analysis-api/testData/components/typeCreator/typeParameter/reified.kt");
    }
}
