package org.rapla.client.gwt;

import com.google.gwt.inject.client.GinModules;
import com.google.gwt.inject.client.Ginjector;

@GinModules(value= { RaplaGwtModule.class },properties="extra.ginModules")
public interface MainInjector extends Ginjector {
//    public Application getApplication();
    public Bootstrap getBootstrap();
}
