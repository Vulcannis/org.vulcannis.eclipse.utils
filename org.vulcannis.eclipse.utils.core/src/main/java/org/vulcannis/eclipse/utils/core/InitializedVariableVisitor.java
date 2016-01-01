package org.vulcannis.eclipse.utils.core;

import java.util.*;

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.internal.corext.dom.*;
import org.vulcannis.eclipse.utils.core.InitializeFieldsInConstructorProcessor.ConstructorEntry;

import com.google.common.collect.*;

@SuppressWarnings( "restriction" )
public class InitializedVariableVisitor extends ASTVisitor
{
    private final List< VariableDeclarationFragment > variables;
    private final List< ConstructorEntry > entries;
    private final List< IVariableBinding > bindings;
    private final SetMultimap< ConstructorEntry, VariableDeclarationFragment > uninitializedList = HashMultimap.create( );
    private final Map< MethodDeclaration, ConstructorEntry > methodEntryMap;

    private MethodDeclaration currentMethod = null;

    public InitializedVariableVisitor( final List< VariableDeclarationFragment > variables, final List< ConstructorEntry > entries )
    {
        this.variables = variables;
        this.entries = entries;

        bindings = Lists.newArrayListWithExpectedSize( variables.size( ) );
        for( final VariableDeclarationFragment fragment: variables ) {
            bindings.add( ASTNodes.getVariableBinding( fragment.getName( ) ) );
            for( final ConstructorEntry constructor: entries ) {
                uninitializedList.put( constructor, fragment );
            }
        }
        methodEntryMap = Maps.uniqueIndex( entries, x -> x.declaration );
    }

    public SetMultimap< ConstructorEntry, VariableDeclarationFragment > getConstructors( )
    {
        return uninitializedList;
    }

    @Override
    public boolean visit( final MethodDeclaration method )
    {
        if( method.isConstructor( ) && currentMethod == null ) {
            currentMethod = method;
            return true;
        }
        return false;
    }

    @Override
    public void endVisit( final MethodDeclaration method )
    {
        if( method == currentMethod ) {
            currentMethod = null;
        }
    }

    @Override
    public boolean visit( final Initializer initializer )
    {
        return !Modifier.isStatic( initializer.getModifiers( ) );
    }

    @Override
    public boolean visit( final Assignment visiting )
    {
        doVisit( visiting.getLeftHandSide( ) );
        return true;
    }

    @Override
    public boolean visit( final PostfixExpression visiting )
    {
        doVisit( visiting.getOperand( ) );
        return true;
    }

    @Override
    public boolean visit( final PrefixExpression visiting )
    {
        doVisit( visiting.getOperand( ) );
        return true;
    }

    private void doVisit( final Expression expression )
    {
        final IVariableBinding assignedVariable = Util.getAssignedVariable( expression );
        final VariableDeclarationFragment variable = findVariable( assignedVariable );
        if( variable != null ) {
	        if( currentMethod == null ) {
	            // Must be in an initializer...
	            for( final ConstructorEntry constructor: entries ) {
                    uninitializedList.remove( constructor, variable );
                }
	        } else {
                uninitializedList.remove( methodEntryMap.get( currentMethod ), variable );
	        }
        }
    }

    private VariableDeclarationFragment findVariable( final IVariableBinding assignedVariable )
    {
        for( int loop = 0; loop < bindings.size( ); loop++ ) {
            if( Bindings.equals( assignedVariable, bindings.get( loop ) ) ) {
                return variables.get( loop );
            }
        }
        return null;
    }
}
