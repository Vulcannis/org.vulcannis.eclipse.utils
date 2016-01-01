package org.vulcannis.eclipse.utils.core;

import static java.util.Collections.emptyList;

import java.util.*;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.*;
import org.eclipse.jdt.internal.corext.dom.*;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.ui.text.java.*;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposal;
import org.eclipse.swt.graphics.Image;

import com.google.common.collect.*;

@SuppressWarnings( { "restriction", "unchecked" } )
public class InitializeFieldsInConstructorProcessor implements IQuickFixProcessor
{
    @Override
    public boolean hasCorrections( final ICompilationUnit unit, final int problemId )
    {
        return canFix( problemId );
    }

    @Override
    public IJavaCompletionProposal[ ] getCorrections( final IInvocationContext context, final IProblemLocation[ ] locations )
    {
        boolean canHandle = false;
        for( int loop = 0; !canHandle && loop < locations.length; loop++ ) {
            canHandle = canFix( locations[ loop ].getProblemId( ) );
        }
        if( !canHandle ) {
            return null;
        }

        final ASTNode coveringNode = context.getCoveringNode( );
        final TypeDeclaration typeDeclaration = (TypeDeclaration)ASTNodes.getParent( coveringNode, ASTNode.TYPE_DECLARATION );
        if( typeDeclaration == null ) {
            return null;
        }

        final List< VariableDeclarationFragment > finalFields = getUninitializedFinalFields( typeDeclaration );
        final SetMultimap< ConstructorEntry, VariableDeclarationFragment > constructors = getConstructors( typeDeclaration, finalFields );

        if( constructors.isEmpty( ) ) {
            return new IJavaCompletionProposal[ 0 ];
        } else {
            final String label = "Add parameters to constructors";
            final Image image = JavaPluginImages.get( JavaPluginImages.IMG_CORRECTION_ADD );
            return new IJavaCompletionProposal[ ] {
                new ASTRewriteCorrectionProposal( label, context.getCompilationUnit( ), null, 1, image ) {
                    private ASTRewrite rewrite = null;

                    @Override
                    protected ASTRewrite getRewrite( )
                    {
                        if( rewrite == null ) {
                            final AST ast = typeDeclaration.getAST( );
                            rewrite = ASTRewrite.create( ast );

                            for( final ConstructorEntry constructor: constructors.keySet( ) ) {
                                rewriteConstructor( rewrite, constructor, order( constructors.get( constructor ), finalFields ) );
                            }
                        }
                        return rewrite;
                    }
                }
            };
        }
    }

    private void rewriteConstructor( final ASTRewrite rewrite, final ConstructorEntry entry, final List< VariableDeclarationFragment > variables )
    {
        final AST ast = rewrite.getAST( );
        for( final VariableDeclarationFragment variable: variables ) {
            final SingleVariableDeclaration paramNode = addParameter( rewrite, entry.declaration, variable );

            final List< Statement > bodyStatements = entry.declaration.getBody( ).statements( );
            final int insertIndex = findAssignmentInsertIndex( bodyStatements, paramNode, entry.declaration );

            final ExpressionStatement assignmentStatement = Util.createFieldAssignmentStatement( ast, variable.getName( ) );
            final ListRewrite statementRewrite = rewrite.getListRewrite( entry.declaration.getBody( ), Block.STATEMENTS_PROPERTY );
            statementRewrite.insertAt( assignmentStatement, insertIndex, null );
        }

        for( final ConstructorEntry element: entry.children ) {
            rewriteChainConstructor( rewrite, element, variables );
        }
    }

    private void rewriteChainConstructor( final ASTRewrite rewrite, final ConstructorEntry entry, final List< VariableDeclarationFragment > variables )
    {
        final AST ast = rewrite.getAST( );
        for( final VariableDeclarationFragment variable: variables ) {
            final SingleVariableDeclaration paramNode = addParameter( rewrite, entry.declaration, variable );

            final ConstructorInvocation superCall = (ConstructorInvocation)getFirstStatement( entry.declaration.getBody( ) );
            final SimpleName superArgument = ast.newSimpleName( paramNode.getName( ).getIdentifier( ) );
            final ListRewrite argumentRewrite = rewrite.getListRewrite( superCall, ConstructorInvocation.ARGUMENTS_PROPERTY );
            argumentRewrite.insertLast( superArgument, null );
        }
        for( final ConstructorEntry element: entry.children ) {
            rewriteChainConstructor( rewrite, element, variables );
        }
    }

    private SetMultimap< ConstructorEntry, VariableDeclarationFragment > getConstructors( final TypeDeclaration typeDeclaration, final List< VariableDeclarationFragment > finalFields )
    {
        final MethodDeclaration[ ] methods = typeDeclaration.getMethods( );
        final List< MethodDeclaration > baseConstructors = Lists.newArrayList( );
        final SetMultimap< String, MethodDeclaration > childConstructors = HashMultimap.create( );
        for( final MethodDeclaration method: methods ) {
            if( method.isConstructor( ) ) {
                final Statement firstStatement = getFirstStatement( method.getBody( ) );
                if( firstStatement instanceof ConstructorInvocation ) {
                    final ConstructorInvocation invocation = (ConstructorInvocation)firstStatement;
                    childConstructors.put( invocation.resolveConstructorBinding( ).getKey( ), method );
                } else {
                    baseConstructors.add( method );
                }
            }
        }
        final InitializedVariableVisitor visitor = new InitializedVariableVisitor( finalFields, resolveConstructors( baseConstructors, childConstructors ) );
        typeDeclaration.accept( visitor );
        return visitor.getConstructors( );
    }

    private List< ConstructorEntry > resolveConstructors( final Collection< ? extends MethodDeclaration > constructors, final SetMultimap< String, MethodDeclaration > childMap )
    {
        final List< ConstructorEntry > entries = Lists.newArrayList( );
        for( final MethodDeclaration constructor: constructors ) {
            final IMethodBinding binding = constructor.resolveBinding( );
            final Set< MethodDeclaration >set = childMap.get( binding.getKey( ) );
            final List< ConstructorEntry > children;
            if( set == null || set.isEmpty( ) ) {
                children = emptyList( );
            } else {
                children = resolveConstructors( set, childMap );
            }
            entries.add( new ConstructorEntry( constructor, children ) );
        }
        return entries;
    }

    private List< VariableDeclarationFragment > getUninitializedFinalFields( final TypeDeclaration typeDeclaration )
    {
        final FieldDeclaration[ ] allFields = typeDeclaration.getFields( );
        final List< VariableDeclarationFragment > finalFields = Lists.newArrayList( );
        for( final FieldDeclaration field: allFields ) {
            final int modifiers = field.getModifiers( );
            if( Modifier.isFinal( modifiers ) && !Modifier.isStatic( modifiers ) ) {
                for( final VariableDeclarationFragment fragment: (List< VariableDeclarationFragment >)field.fragments( ) ) {
                    if( fragment.getInitializer( ) == null ) {
                        finalFields.add( fragment );
                    }
                }
            }
        }
        return finalFields;
    }

    private static List< VariableDeclarationFragment > order( final Set< VariableDeclarationFragment > variables, final List< VariableDeclarationFragment > order )
    {
        final List< VariableDeclarationFragment > orderedVariables = Lists.newArrayListWithExpectedSize( variables.size( ) );
        for( final VariableDeclarationFragment fragment: order ) {
            if( variables.contains( fragment ) ) {
                orderedVariables.add( fragment );
            }
        }
        return orderedVariables;
    }

    private static Statement getFirstStatement( final Block statement )
    {
        final List< Statement > list = statement.statements( );
        if( list.isEmpty( ) ) {
            return null;
        } else {
            return list.get( 0 );
        }
    }

	private static int findAssignmentInsertIndex( final List< ? extends Statement > statements, final ASTNode paramNode, final MethodDeclaration method )
    {
        final Set< String > paramsBefore = Sets.newHashSet( );
        final List< SingleVariableDeclaration > params = method.parameters( );
        for( int i = 0; i < params.size( ) && params.get( i ) != paramNode; i++ ) {
            final SingleVariableDeclaration declaration = params.get( i );
            paramsBefore.add( declaration.getName( ).getIdentifier( ) );
        }

        int insertIndex;
        for( insertIndex = 0; insertIndex < statements.size( ); insertIndex++ ) {
            final Statement curr = statements.get( insertIndex );
            switch( curr.getNodeType( ) ) {
                case ASTNode.CONSTRUCTOR_INVOCATION:
                case ASTNode.SUPER_CONSTRUCTOR_INVOCATION: {
                    break;
                }

                case ASTNode.EXPRESSION_STATEMENT: {
                    final Expression expr = ( (ExpressionStatement)curr ).getExpression( );
                    if( expr instanceof Assignment ) {
                        final Assignment assignment = (Assignment)expr;
                        final Expression rightHand = assignment.getRightHandSide( );
                        if( rightHand instanceof SimpleName && paramsBefore.contains( ( (SimpleName)rightHand ).getIdentifier( ) ) ) {
                            final IVariableBinding binding = Bindings.getAssignedVariable( assignment );
                            if( binding == null || binding.isField( ) ) {
                                break;
                            }
                        }
                    }
                    return insertIndex;
                }

                default: {
                    return insertIndex;
                }
            }
        }
        return insertIndex;
    }

    private static SingleVariableDeclaration addParameter( final ASTRewrite rewrite, final MethodDeclaration declaration, final VariableDeclarationFragment variable )
    {
        final AST ast = rewrite.getAST( );
        final SingleVariableDeclaration paramNode = ast.newSingleVariableDeclaration( );
        paramNode.modifiers( ).addAll( ast.newModifiers( Modifier.FINAL ) );
        paramNode.setName( ast.newSimpleName( variable.getName( ).getIdentifier( ) ) );
        paramNode.setType( Util.newType( ast, (FieldDeclaration)variable.getParent( ), variable ) );

        final ListRewrite parameterRewrite = rewrite.getListRewrite( declaration, MethodDeclaration.PARAMETERS_PROPERTY );
        parameterRewrite.insertLast( paramNode, null );
        return paramNode;
    }

    private static boolean canFix( final int problemId )
    {
        return problemId == IProblem.UninitializedBlankFinalField;
    }

    public static class ConstructorEntry
    {
        public final MethodDeclaration declaration;
        public final List< ConstructorEntry > children;

        public ConstructorEntry( final MethodDeclaration declaration, final List< ConstructorEntry > children )
        {
            this.declaration = declaration;
            this.children = children;
        }
    }
}
