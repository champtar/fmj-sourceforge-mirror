package javax.media.rtp;

import javax.media.rtp.event.*;

/**
 * Standard JMF class -- see <a href=
 * "http://java.sun.com/products/java-media/jmf/2.1.1/apidocs/javax/media/rtp/ReceiveStreamListener.html"
 * target="_blank">this class in the JMF Javadoc</a>. Complete.
 * 
 * @author Ken Larson
 * 
 */
public interface ReceiveStreamListener extends java.util.EventListener
{
    public void update(ReceiveStreamEvent event);
}
