package org.vulcannis.eclipse.utils.core;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.swt.graphics.Point;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegion;
import org.eclipse.wst.sse.ui.contentassist.CompletionProposalInvocationContext;
import org.eclipse.wst.sse.ui.internal.contentassist.ContentAssistUtils;
import org.eclipse.wst.xml.core.internal.provisional.document.*;
import org.eclipse.wst.xml.ui.internal.contentassist.*;
import org.w3c.dom.Node;

@SuppressWarnings( "restriction" )
public class PomContentAssistProcessor extends DefaultXMLCompletionProposalComputer
{
    @Override
    protected ContentAssistRequest computeCompletionProposals( final String matchString, final ITextRegion completionRegion, final IDOMNode treeNode,
        final IDOMNode xmlnode, final CompletionProposalInvocationContext context )
    {
        final ContentAssistRequest request = super.computeCompletionProposals( matchString, completionRegion, treeNode, xmlnode, context );

        final ITextViewer viewer = context.getViewer( );
        final Point selectedRange = viewer.getSelectedRange( );
        if( treeNode instanceof IDOMText && selectedRange.y > 0 && viewer instanceof IAdaptable ) {
            final int offset = selectedRange.x, length = selectedRange.y;
            final Node endRegion = (Node)ContentAssistUtils.getNodeAt( viewer, offset + length - 1 );
            if( treeNode == endRegion ) {
                request.addProposal( new ExtractPropertyCompletionProposal( viewer, offset, length, (IDOMText)treeNode ) );
            }
        }
        return request;
    }
}
