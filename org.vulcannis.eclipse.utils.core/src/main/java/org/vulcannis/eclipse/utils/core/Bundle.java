package org.vulcannis.eclipse.utils.core;

import org.eclipse.e4.core.contexts.*;
import org.eclipse.e4.core.services.log.Logger;
import org.eclipse.e4.ui.internal.workbench.WorkbenchLogger;
import org.osgi.framework.FrameworkUtil;

@SuppressWarnings( "restriction" )
public class Bundle
{
    public static Logger getLog( )
    {
        final org.osgi.framework.Bundle bundle = FrameworkUtil.getBundle( Bundle.class );
        final IEclipseContext context = EclipseContextFactory.getServiceContext( bundle.getBundleContext( ) );
        final Logger logger = ContextInjectionFactory.make( WorkbenchLogger.class, context );
        return logger;
    }
}
