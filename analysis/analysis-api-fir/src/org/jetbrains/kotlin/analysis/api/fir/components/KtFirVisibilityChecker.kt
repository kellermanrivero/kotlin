/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.fir.components

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.components.KtVisibilityChecker
import org.jetbrains.kotlin.analysis.api.fir.KtFirAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.symbols.KtFirFileSymbol
import org.jetbrains.kotlin.analysis.api.fir.symbols.KtFirSymbol
import org.jetbrains.kotlin.analysis.api.impl.barebone.parentsOfType
import org.jetbrains.kotlin.analysis.api.symbols.KtFileSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeToken
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.collectDesignation
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.getOrBuildFirSafe
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.resolve.calls.ExpressionReceiverValue
import org.jetbrains.kotlin.fir.visibilityChecker
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

internal class KtFirVisibilityChecker(
    override val analysisSession: KtFirAnalysisSession,
    override val token: KtLifetimeToken
) : KtVisibilityChecker(), KtFirAnalysisSessionComponent {

    override fun isVisible(
        candidateSymbol: KtSymbolWithVisibility,
        useSiteFile: KtFileSymbol,
        position: PsiElement,
        receiverExpression: KtExpression?
    ): Boolean {
        require(candidateSymbol is KtFirSymbol<*>)
        require(useSiteFile is KtFirFileSymbol)

        val nonLocalContainingDeclaration = findContainingNonLocalDeclaration(position)
        val useSiteFirFile = useSiteFile.firSymbol.fir
        val containers = nonLocalContainingDeclaration
            ?.getOrBuildFirSafe<FirCallableDeclaration>(analysisSession.firResolveSession)
            ?.collectDesignation()
            ?.path
            .orEmpty()

        val explicitDispatchReceiver = receiverExpression
            ?.getOrBuildFirSafe<FirExpression>(analysisSession.firResolveSession)
            ?.let { ExpressionReceiverValue(it) }

        val candidateFirSymbol = candidateSymbol.firSymbol.fir as FirMemberDeclaration

        return rootModuleSession.visibilityChecker.isVisible(
            candidateFirSymbol,
            rootModuleSession,
            useSiteFirFile,
            containers,
            explicitDispatchReceiver
        )
    }

    private fun findContainingNonLocalDeclaration(element: PsiElement): KtCallableDeclaration? {
        return element
            .parentsOfType<KtCallableDeclaration>()
            .firstOrNull { it.isNotFromLocalClass }
    }

    private val KtCallableDeclaration.isNotFromLocalClass
        get() = this is KtNamedFunction && (isTopLevel || containingClassOrObject?.isLocal == false) ||
                this is KtProperty && (isTopLevel || containingClassOrObject?.isLocal == false)
}
