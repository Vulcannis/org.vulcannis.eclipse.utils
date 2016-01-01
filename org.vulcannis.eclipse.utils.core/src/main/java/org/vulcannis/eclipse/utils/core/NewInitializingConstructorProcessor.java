package org.vulcannis.eclipse.utils.core;

import static java.util.Collections.emptyList;

import java.util.*;

import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.*;
import org.eclipse.jdt.internal.corext.dom.*;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.ui.text.java.*;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposal;
import org.eclipse.swt.graphics.Image;

@SuppressWarnings( { "restriction", "unchecked" } )
public class NewInitializingConstructorProcessor implements IQuickAssistProcessor
{
    @Override
    public boolean hasAssists( final IInvocationContext context )
    {
        final ASTNode coveringNode = context.getCoveringNode( );
        return context.getSelectionLength( ) == 0 && coveringNode instanceof TypeDeclaration;
    }

    @Override
    public IJavaCompletionProposal[ ] getAssists( final IInvocationContext context, final IProblemLocation[ ] locations )
    {
        final ASTNode coveringNode = context.getCoveringNode( );
        if( context.getSelectionLength( ) == 0 && coveringNode instanceof TypeDeclaration ) {
            final TypeDeclaration declaration = (TypeDeclaration)coveringNode;

            final Image image = JavaPluginImages.get( JavaPluginImages.IMG_CORRECTION_ADD );
            final ICompilationUnit compilationUnit = context.getCompilationUnit( );

            // TODO if initializing is empty, don't include
            return new ASTRewriteCorrectionProposal[ ] {
                new ASTRewriteCorrectionProposal( "New empty constructor", compilationUnit, getEmptyRewrite( declaration ), 1, image ),
                new ASTRewriteCorrectionProposal( "New initializing constructor", compilationUnit, getInitializingRewrite( declaration, compilationUnit.getJavaProject( ) ), 2, image ),
            };
        }
        return null;
    }

    public static ASTRewrite getEmptyRewrite( final TypeDeclaration declaration )
    {
        return new ConstructorRewriter( declaration ).getRewrite( );
    }

    public static ASTRewrite getInitializingRewrite( final TypeDeclaration declaration, final IJavaProject project )
    {
        return new ConstructorRewriter( declaration ) {
            @Override
            protected void createConstructor( )
            {
                assert declaration != null;
                assert project != null;

                final List< SingleVariableDeclaration > minimalSuperConstructor = getMinimalSuperConstructorArguments( declaration, project );
                final List< Statement > statements = body.statements( );
                if( !minimalSuperConstructor.isEmpty( ) ) {
                    final List< SingleVariableDeclaration > parameters = constructor.parameters( );
                    parameters.addAll( minimalSuperConstructor );
                    final SuperConstructorInvocation superInvocation = ast.newSuperConstructorInvocation( );
                    statements.add( superInvocation );
                    final List< Expression > arguments = superInvocation.arguments( );
                    for( final SingleVariableDeclaration argumentDeclaration: minimalSuperConstructor ) {
                        arguments.add( ast.newSimpleName( argumentDeclaration.getName( ).getIdentifier( ) ) );
                    }
                }

                final List< SingleVariableDeclaration > uninitializedFinals = getUnitinitializedFinalFields( declaration );
                if( !uninitializedFinals.isEmpty( ) ) {
                    final List< SingleVariableDeclaration > parameters = constructor.parameters( );
                    parameters.addAll( uninitializedFinals );
                    for( final SingleVariableDeclaration argumentDeclaration: uninitializedFinals ) {
                        statements.add( Util.createFieldAssignmentStatement( ast, argumentDeclaration.getName( ) ) );
                    }
                }
            }
        }.getRewrite( );
    }

    private static List< SingleVariableDeclaration > getUnitinitializedFinalFields( final TypeDeclaration declaration )
    {
        // TODO screen out fields initialized in static initializers.
        final FieldDeclaration[ ] fields = declaration.getFields( );
        final List< SingleVariableDeclaration > uninitializedFields = new ArrayList< >( );
        final AST ast = declaration.getAST( );
        for( final FieldDeclaration fieldDeclaration: fields ) {
            if( Modifier.isFinal( fieldDeclaration.getModifiers( ) ) ) {
                for( final VariableDeclarationFragment fragment: (List< VariableDeclarationFragment >)fieldDeclaration.fragments( ) ) {
                    if( fragment.getInitializer( ) == null ) {
                        final SingleVariableDeclaration argumentDeclaration = ast.newSingleVariableDeclaration( );
                        argumentDeclaration.modifiers( ).addAll( ast.newModifiers( Modifier.FINAL ) );
                        argumentDeclaration.setName( ast.newSimpleName( fragment.getName( ).getIdentifier( ) ) );
                        argumentDeclaration.setType( Util.newType( ast, fieldDeclaration, fragment ) );
                        uninitializedFields.add( argumentDeclaration );
                    }
                }
            }
        }
        return uninitializedFields;
    }

    private static List< SingleVariableDeclaration > getMinimalSuperConstructorArguments( final TypeDeclaration declaration, final IJavaProject project )
    {
        try {
            final ITypeBinding superclass = declaration.resolveBinding( ).getSuperclass( );
            //            final IType superType = Bindings.findType( superclass, project );
            final IJavaElement foundTypeContainer = JavaModelUtil.findTypeContainer( project, superclass.getQualifiedName( ) );
            final IType superType = (IType)foundTypeContainer;
            final IMethodBinding[ ] methods = superclass.getDeclaredMethods( );

            String[ ] minimalParameterNames = null;
            ITypeBinding[ ] minimalParameterTypes = null;
            for( final IMethodBinding method2: methods ) {
                @SuppressWarnings( "deprecation" )
                final IMethod method = Bindings.findMethod( method2, superType );
                if( method != null && method.isConstructor( ) && isVisible( method.getFlags( ) ) ) {
                    if( minimalParameterNames == null || method.getNumberOfParameters( ) < minimalParameterNames.length ) {
                        minimalParameterNames = method.getParameterNames( );
                        minimalParameterTypes = method2.getParameterTypes( );
                    }
                }
            }
            if( minimalParameterNames == null ) {
                return emptyList( );
            } else {
                final AST ast = declaration.getAST( );
                final List< SingleVariableDeclaration > parameters = new ArrayList< >( );
                for( int loop = 0; loop < minimalParameterNames.length; loop++ ) {
                    final String name = minimalParameterNames[ loop ];
                    final ITypeBinding type = Bindings.normalizeTypeBinding( minimalParameterTypes[ loop ] );
                    final SingleVariableDeclaration parameter = ast.newSingleVariableDeclaration( );
                    parameter.modifiers( ).addAll( ast.newModifiers( Modifier.FINAL ) );
                    parameter.setName( ast.newSimpleName( name ) );
                    //                    parameter.setType( ASTNodeFactory.newType( ast, type, false ) );
                    parameter.setType( ASTNodeFactory.newType( ast, type.getQualifiedName( ) ) );
                    parameters.add( parameter );
                }
                return parameters;
            }
        }
        catch( final JavaModelException e ) {
            return emptyList( );
        }
    }

    private static boolean isVisible( final int modifiers )
    {
        // TODO package access
        return Flags.isPublic( modifiers ) || Flags.isProtected( modifiers );
    }

    private static int getInsertIndex( final List< ? extends ASTNode > declarations )
    {
        int insertPosition = 0;
        for( int loop = declarations.size( ) - 1; loop >= 0; loop-- ) {
            final ASTNode curr = declarations.get( loop );
            if( curr instanceof MethodDeclaration ) {
                if( ( (MethodDeclaration)curr ).isConstructor( ) ) {
                    return loop + 1;
                }
                insertPosition = loop;
            } else if( curr instanceof FieldDeclaration ) {
                return loop + 1;
            }
        }
        return insertPosition;
    }

    private static class ConstructorRewriter
    {
        protected final ASTRewrite rewrite;
        protected final MethodDeclaration constructor;
        protected final AST ast;
        protected final Block body;

        public ConstructorRewriter( final TypeDeclaration declaration )
        {
            ast = declaration.getAST( );
            rewrite = ASTRewrite.create( ast );
            constructor = ast.newMethodDeclaration( );
            constructor.setConstructor( true );
            constructor.setName( ast.newSimpleName( declaration.getName( ).getIdentifier( ) ) );
            constructor.modifiers( ).addAll( ast.newModifiers( Modifier.PUBLIC ) );
            body = ast.newBlock( );
            constructor.setBody( body );

            createConstructor( );

            final int insertIndex = getInsertIndex( declaration.bodyDeclarations( ) );
            final ListRewrite constructorRewrite = rewrite.getListRewrite( declaration, TypeDeclaration.BODY_DECLARATIONS_PROPERTY );
            constructorRewrite.insertAt( constructor, insertIndex, null );
        }

        protected void createConstructor( )
        {
        }

        public ASTRewrite getRewrite( )
        {
            return rewrite;
        }
    }
}
