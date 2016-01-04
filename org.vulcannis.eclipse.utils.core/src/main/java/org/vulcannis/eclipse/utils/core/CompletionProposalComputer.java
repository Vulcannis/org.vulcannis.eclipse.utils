package org.vulcannis.eclipse.utils.core;

import static java.util.Collections.*;

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.ui.SharedASTProvider;
import org.eclipse.jdt.ui.text.java.*;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposal;
import org.eclipse.jface.text.contentassist.*;

@SuppressWarnings( "restriction" )
public class CompletionProposalComputer implements IJavaCompletionProposalComputer
{
    public CompletionProposalComputer( )
    {
    }

    @Override
    public List< ICompletionProposal > computeCompletionProposals( final ContentAssistInvocationContext context, final IProgressMonitor monitor )
    {
        if( context instanceof JavaContentAssistInvocationContext ) {
            final JavaContentAssistInvocationContext javaContext = (JavaContentAssistInvocationContext)context;
            final CompilationUnit ast = SharedASTProvider.getAST( javaContext.getCompilationUnit( ), SharedASTProvider.WAIT_NO, monitor );
            if( ast != null ) {
                final ASTNode coveringNode = NodeFinder.perform( ast, javaContext.getInvocationOffset( ), 0 );
                if( coveringNode instanceof TypeDeclaration ) {
                    final ICompletionProposal proposal = new ASTRewriteCorrectionProposal( "New initializing constructor", javaContext.getCompilationUnit( ), NewInitializingConstructorProcessor.getInitializingRewrite( (TypeDeclaration)coveringNode, javaContext.getProject( ) ), 1000, JavaPluginImages.get( JavaPluginImages.IMG_CORRECTION_ADD ) );
                    return singletonList( proposal );
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
