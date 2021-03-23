package org.vulcannis.eclipse.utils.core;

import java.util.List;

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.*;

public class Util
{
    private static final String NAME_PREFIX = "org.eclipse.jdt.ui.";
    public static final String IMG_CORRECTION_ADD = NAME_PREFIX + "add_correction.gif";

    public static IVariableBinding getAssignedVariable( final Expression expression )
    {
        switch( expression.getNodeType( ) ) {
            case ASTNode.SIMPLE_NAME: {
                return (IVariableBinding)( (SimpleName)expression ).resolveBinding( );
            }

            case ASTNode.QUALIFIED_NAME: {
                return (IVariableBinding)( (QualifiedName)expression ).getName( ).resolveBinding( );
            }

            case ASTNode.FIELD_ACCESS: {
                return ( (FieldAccess)expression ).resolveFieldBinding( );
            }

            case ASTNode.SUPER_FIELD_ACCESS: {
                return ( (SuperFieldAccess)expression ).resolveFieldBinding( );
            }

            case ASTNode.PARENTHESIZED_EXPRESSION: {
                return getAssignedVariable( ( (ParenthesizedExpression)expression ).getExpression( ) );
            }

            default: {
                return null;
            }
        }
    }

    public static ExpressionStatement createFieldAssignmentStatement( final AST ast, final SimpleName fieldName )
    {
        final Assignment assignment = ast.newAssignment( );
        final FieldAccess access = ast.newFieldAccess( );
        access.setExpression( ast.newThisExpression( ) );
        access.setName( ast.newSimpleName( fieldName.getIdentifier( ) ) );
        assignment.setLeftHandSide( access );
        assignment.setRightHandSide( ast.newSimpleName( fieldName.getIdentifier( ) ) );
        return ast.newExpressionStatement( assignment );
    }

    public static Type newType( final AST ast, final FieldDeclaration declaration, final VariableDeclaration fragment )
    {
        final int extraDim = fragment.getExtraDimensions( );

        Type type = (Type)ASTNode.copySubtree( ast, declaration.getType( ) );
        for( int i = 0; i < extraDim; i++ ) {
            type = ast.newArrayType( type );
        }
        return type;
    }

    public static void addModifiers( final ASTNode node, final ChildListPropertyDescriptor property, final int modifiers, final ASTRewrite rewrite )
    {
        final AST ast = rewrite.getAST( );
        final ListRewrite listRewrite = rewrite.getListRewrite( node, property );
        @SuppressWarnings( "unchecked" )
        final List< Modifier > modifierNodes = ast.newModifiers( modifiers );
        for( final Modifier modifier: modifierNodes ) {
            listRewrite.insertLast( modifier, null );
        }
    }

    public static void removeModifiers( final ASTNode node, final ChildListPropertyDescriptor property, final int modifiers, final ASTRewrite rewrite )
    {
        final ListRewrite listRewrite = rewrite.getListRewrite( node, property );
        @SuppressWarnings( "unchecked" )
        final List< ASTNode > modifierNodes = (List< ASTNode >)node.getStructuralProperty( property );
        for( final ASTNode element: modifierNodes ) {
            final Modifier modifier = (Modifier)element;
            if( modifier.isModifier( ) && ( modifier.getKeyword( ).toFlagValue( ) & modifiers ) != 0 ) {
                listRewrite.remove( modifier, null );
            }
        }
    }

    public static int getModifiers( final ASTNode node, final ChildListPropertyDescriptor property )
    {
        int modifiers = 0;
        @SuppressWarnings( "unchecked" )
        final List< ASTNode > modifierNodes = (List< ASTNode >)node.getStructuralProperty( property );
        for( final ASTNode element: modifierNodes ) {
            final Modifier modifier = (Modifier)element;
            if( modifier.isModifier( ) ) {
                modifiers |= modifier.getKeyword( ).toFlagValue( );
            }
        }
        return modifiers;
    }
}
