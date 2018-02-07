package org.rapla.client.gwt;

import io.reactivex.functions.Action;
import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsType;

@JsType
public class GwtActionWrapper implements Action
{
    @JsFunction
    @FunctionalInterface
    interface Runnable
    {
        void run();
    }

    public Runnable getRunnable()
    {
        return runnable;
    }

    public void setRunnable(Runnable runnable)
    {
        this.runnable = runnable;
    }

    private Runnable runnable;

    @Override
    public void run() throws Exception
    {
        if ( runnable != null)
        {
            runnable.run();
        }
    }
}
