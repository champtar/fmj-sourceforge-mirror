package net.sf.fmj.ejmf.toolkit.gui.controls;

import java.awt.*;
import java.util.*;

import javax.media.*;

/**
 * An AbstractListenerControl provides a Component-Listener model for Controls,
 * allowing the two elements to be changed independently of each other.
 * <p>
 * This is the root class from which all EJMF control panel controls extend.
 * <p>
 * Subclasses must implement these methods:
 * <ul>
 * <li>createControlListener()
 * <li>createControlComponent()
 * <li>addControlListener(EventListener listener)
 * <li>removeControlListener(EventListener listener)
 * </ul>
 */
public abstract class AbstractListenerControl implements Control
{
    private EventListener currListener;
    private EventListener defaultListener;

    private Controller controller;
    private Component component;
    protected final Skin skin;

    private boolean isOperational = true;

    /**
     * Create an AbstractListenerControl
     */
    protected AbstractListenerControl(Skin skin)
    {
        this.skin = skin;
        component = createControlComponent(skin);
        initListener(createControlListener());
    }

    /**
     * Create an AbstractListenerControl
     * 
     * @param controller
     *            Controller with which this control is associated.
     */
    protected AbstractListenerControl(Skin skin, Controller controller)
    {
        this(skin);
        setController(controller);
    }

    /**
     * Since different controls may have different types of listeners, each
     * subclass should define addControlListener to register the correct type of
     * listener with component.
     * 
     * @param listener
     *            Listener object to be added.
     */
    protected abstract void addControlListener(EventListener listener);

    /**
     * Subclasses defined this method to supply their GUI component. Subclasses
     * must not return a null component.
     * <p>
     * Listener registration is handled by AbstractListenerControl.
     * 
     * @return a Component
     */
    protected abstract Component createControlComponent(Skin skin);

    /**
     * Subclasses of AbstractListenerControl must define this method to supply
     * an EventListener to handle events originating from control's component.
     * <p>
     * Subclasses must not return a null listener.
     * 
     * @return an EventListener
     */
    protected abstract EventListener createControlListener();

    /**
     * Return Control Component as required by Control inteface.
     * 
     * @return Control component
     */
    public Component getControlComponent()
    {
        return component;
    }

    /**
     * @return associated Controller.
     */
    public Controller getController()
    {
        return controller;
    }

    /**
     * @return the default listener for this Control.
     */
    protected EventListener getDefaultControlListener()
    {
        return defaultListener;
    }

    /**
     * Initializes listener, establishes it as default and registers it with
     * Control Compoent.
     * 
     * @param listener
     *            Listener object for this control
     */
    protected void initListener(EventListener listener)
    {
        defaultListener = listener;
        currListener = defaultListener;
        addControlListener(defaultListener);
    }

    /**
     * Return the operational state of this Control.
     * 
     * @return true if control is operational
     */
    public boolean isOperational()
    {
        return isOperational;
    }

    /**
     * Since different controls may have different types of listeners, each
     * subclass should define removeControlListener to remove listener from
     * component listener list in a type-safe way.
     * 
     * @param listener
     *            Listener object to be removed.
     */
    protected abstract void removeControlListener(EventListener listener);

    /**
     * Set the GUI component associated with this Control.
     * <p>
     * Subclasses should override this method to provide a type-safe public
     * version.
     * 
     * @param component
     *            Component to associate with this control.
     */
    protected void setComponent(Component component)
    {
        setComponentAndListener(component, getDefaultControlListener());
    }

    /**
     * Set both GUI component and control listener associated with this Control.
     * <p>
     * Subclasses should override this method to provide a type-safe public
     * version.
     * 
     * @param component
     *            Component to associate with this control.
     * @param listener
     *            Listener object for this control
     */
    protected void setComponentAndListener(Component component,
            EventListener listener)
    {
        this.component = component;
        setControlListener(listener);
    }

    /**
     * Associates Controller with this Control.
     * 
     * @throws IllegalArgumentException
     *             is thrown if attempt is made to set Controller a second time.
     * @param controller
     *            Controller with which this control is associated.
     */
    public void setController(Controller controller)
    {
        // Do not allow Controller to be set a second time.
        if (this.controller != null)
        {
            throw new IllegalArgumentException(
                    "Controller already set on Control");
        }

        if (controller.getState() < Controller.Realized)
        {
            throw new NotRealizedError("Control requires realized Controller");
        }
        this.controller = controller;
        setControllerHook(controller);
    }

    /**
     * Subclasses override and provide the guts to setControllerHook if they
     * want to augment the the work of setController.
     * 
     * @param controller
     *            Controller with which this control is associated.
     */
    protected void setControllerHook(Controller controller)
    {
    }

    /**
     * Set the control listener associated with this Control.
     * <p>
     * Subclasses should override this method to provide a type-safe public
     * version.
     * 
     * @param listener
     *            Listener object for this control
     */
    protected void setControlListener(EventListener listener)
    {
        if (currListener != null)
            removeControlListener(currListener);
        addControlListener(listener);
        currListener = listener;
    }

    /**
     * Set the operational state of this Control.
     * 
     * @param flag
     *            Set to true to make control operational.
     */
    protected void setOperational(boolean flag)
    {
        isOperational = flag;
    }

    /**
     * @return fully-qualified class name. This value can be used as search key
     *         in response to Controller.getControl(name)
     */
    @Override
    public String toString()
    {
        return getClass().getName();
    }
}
