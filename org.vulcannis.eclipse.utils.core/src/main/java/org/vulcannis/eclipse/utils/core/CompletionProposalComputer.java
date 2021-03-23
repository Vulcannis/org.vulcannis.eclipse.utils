package org.vulcannis.eclipse.utils.core;

import static java.util.Collections.emptyList;
import static org.vulcannis.eclipse.utils.core.NewInitializingConstructorProcessor.getInitializingRewrite;

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.manipulation.SharedASTProviderCore;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.text.java.*;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposal;
import org.eclipse.jface.text.contentassist.*;

public class CompletionProposalComputer implements IJavaCompletionProposalComputer
{
    @Override
    public List< ICompletionProposal > computeCompletionProposals( final ContentAssistInvocationContext context, final IProgressMonitor monitor )
    {
        if( context instanceof JavaContentAssistInvocationContext ) {
            final JavaContentAssistInvocationContext javaContext = (JavaContentAssistInvocationContext)context;
            final CompilationUnit ast = SharedASTProviderCore.getAST( javaContext.getCompilationUnit( ), SharedASTProviderCore.WAIT_NO, monitor );
            if( ast != null ) {
                final ASTNode coveringNode = NodeFinder.perform( ast, javaContext.getInvocationOffset( ), 0 );
                if( coveringNode instanceof TypeDeclaration ) {
                    return getInitializingRewrite( (TypeDeclaration)coveringNode, javaContext.getProject( ) )
                        .map( rewrite -> (ICompletionProposal)new ASTRewriteCorrectionProposal(
                            "New initializing constructor",
                            javaContext.getCompilationUnit( ),
                            rewrite,
                            1000,
                            JavaUI.getSharedImages( ).getImage( Util.IMG_CORRECTION_ADD )
                        ) )
                        .stream( ).toList( );
                }
            }
        }
        return emptyList( );
    }

    @Override
    public List< IContextInformation > computeContextInformation( final ContentAssistInvocationContext context, final IProgressMonitor monitor )
    {
        return emptyList( );
    }

    @Override
    public String getErrorMessage( )
    {
        return null;
    }

    @Override
    public void sessionStarted( )
    {
    }

    @Override
    public void sessionEnded( )
    {
    }
}
