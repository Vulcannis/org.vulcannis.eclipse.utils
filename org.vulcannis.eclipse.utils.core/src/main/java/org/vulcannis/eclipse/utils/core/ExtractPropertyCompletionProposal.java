package org.vulcannis.eclipse.utils.core;

import java.io.IOException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.*;
import org.eclipse.jface.text.contentassist.*;
import org.eclipse.jface.text.link.*;
import org.eclipse.m2e.core.ui.internal.editing.PomEdits;
import org.eclipse.m2e.core.ui.internal.editing.PomEdits.*;
import org.eclipse.m2e.editor.xml.MvnImages;
import org.eclipse.swt.graphics.*;
import org.eclipse.ui.texteditor.link.EditorLinkedModeUI;
import org.eclipse.wst.xml.core.internal.provisional.document.*;
import org.w3c.dom.*;
import org.w3c.dom.Document;

@SuppressWarnings( "restriction" )
public class ExtractPropertyCompletionProposal implements ICompletionProposal
{
    private static final String pomNamespace = "http://maven.apache.org/POM/4.0.0";

    private final ITextViewer viewer;
    private final int offset, length;
    private final IDOMText textNode;

    public ExtractPropertyCompletionProposal( final ITextViewer viewer, final int offset, final int length, final IDOMText textNode )
    {
        this.viewer = viewer;
        this.offset = offset;
        this.length = length;
        this.textNode = textNode;
    }

    @Override
    public void apply( final IDocument document )
    {
        try {
            final Point[ ] selections = { null, null, null };
            PomEdits.performOnDOMDocument( new OperationTuple( document, (Operation)doc -> {
                final int start = offset - firstSibling( textNode ).getStartOffset( );
                final String content = textNode.getWholeText( );
                final String propertyValue = content.substring( start, start + length );

                final IDOMElement element = (IDOMElement)textNode.getParentNode( );
                String guessedName = null;
                if( element.getLocalName( ).equals( PomEdits.VERSION ) && element.getParentNode( ).getLocalName( ).equals( PomEdits.DEPENDENCY ) ) {
                    final NodeList artifactNodes = ( (Element)element.getParentNode( ) ).getElementsByTagName( PomEdits.ARTIFACT_ID );
                    if( artifactNodes.getLength( ) == 1 ) {
                        final String rawArtifactId = artifactNodes.item( 0 ).getTextContent( );
                        // Should interpolate here, but doing it seems painful.
                        guessedName = rawArtifactId + ".version";
                    }
                }
                final IDOMElement propertyElement = createNewProperty( doc, propertyValue, guessedName );
                final String propertyName = propertyElement.getLocalName( );
                final String newText = content.substring( 0, start ) + "${" + propertyName + "}" + content.substring( start + length );
                PomEdits.setText( element, newText );

                selections[ 0 ] = new Point( element.getFirstStructuredDocumentRegion( ).getEndOffset( ) + start + 2, propertyName.length( ) );
                selections[ 1 ] = new Point( propertyElement.getFirstStructuredDocumentRegion( ).getStartOffset( ) + 1, propertyName.length( ) );
                selections[ 2 ] = new Point( propertyElement.getLastStructuredDocumentRegion( ).getStartOffset( ) + 2, propertyName.length( ) );
            } ) );

            startRename( document, selections );
        }
        catch( IOException | CoreException e ) {
            Bundle.getLog( ).error( "Error creating new property", e );
        }
    }

    private void startRename( final IDocument document, final Point... selections )
    {
        try {
            final LinkedPositionGroup group = new LinkedPositionGroup( );
            for( final Point selection: selections ) {
                group.addPosition( new LinkedPosition( document, selection.x, selection.y ) );
            }

            final LinkedModeModel model = new LinkedModeModel( );
            model.addGroup( group );
            model.forceInstall( );

            final LinkedModeUI ui = new EditorLinkedModeUI( model, viewer );
            ui.setExitPosition( viewer, offset, 0, Integer.MAX_VALUE );
            ui.enter( );
        }
        catch( final BadLocationException e ) {
        }
    }

    private static IDOMElement createNewProperty( final Document doc, final String propertyValue, String propertyName )
    {
        final NodeList list = doc.getElementsByTagNameNS( pomNamespace, PomEdits.PROPERTIES );
        Node toFormat = null;
        final Element propertiesNode;
        if( list.getLength( ) == 0 ) {
            propertiesNode = PomEdits.createElement( doc.getDocumentElement( ), PomEdits.PROPERTIES );
            toFormat = propertiesNode;
        } else {
            propertiesNode = (Element)list.item( 0 );
        }
        if( propertyName == null ) {
            propertyName = "new.property";
        }
        int count = 0;
        while( propertiesNode.getElementsByTagName( propertyName ).getLength( ) > 0 ) {
            propertyName = "new.property" + count++;
        }
        final Element newProperty = PomEdits.createElementWithText( propertiesNode, propertyName, propertyValue );
        if( toFormat == null ) {
            toFormat = newProperty;
        }
        PomEdits.format( toFormat );
        return (IDOMElement)newProperty;
    }

    private static IDOMNode firstSibling( IDOMNode node )
    {
        while( node.getPreviousSibling( ) != null ) {
            node = (IDOMNode)node.getPreviousSibling( );
        }
        return node;
    }

    @Override
    public Point getSelection( final IDocument document )
    {
        return null;
    }

    @Override
    public String getAdditionalProposalInfo( )
    {
        return null;
    }

    @Override
    public String getDisplayString( )
    {
        return "Extract selected text into new property";
    }

    @Override
    public Image getImage( )
    {
        return MvnImages.IMG_PROPERTY;
    }

    @Override
    public IContextInformation getContextInformation( )
    {
        return null;
    }
}
