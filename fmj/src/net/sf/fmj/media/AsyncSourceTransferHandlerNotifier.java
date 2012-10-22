package net.sf.fmj.media;

import java.util.logging.*;

import javax.media.protocol.*;

import net.sf.fmj.utility.*;

import com.lti.utils.synchronization.*;

/**
 * Helper class to do SourceTransferHandler notifications in a background
 * thread.
 * 
 * @author Ken Larson
 * 
 */
public class AsyncSourceTransferHandlerNotifier
{
    // doing the transfer notifications in a different thread keeps the
    // capture thread from being tied up. Seems to avoid some deadlocks when
    // JMF is ahead in the classpath as well.
    class NotifyTransferHandlerThread extends CloseableThread
    {
        private final ProducerConsumerQueue<Boolean> q = new ProducerConsumerQueue<Boolean>();

        public NotifyTransferHandlerThread(String threadName)
        {
            super(threadName);
            setDaemon(true);
        }

        public void notifyTransferHandlerAsync() throws InterruptedException
        {
            q.put(Boolean.TRUE);
        }

        @Override
        public void run()
        {
            try
            {
                while (!isClosing())
                {
                    if (q.get() == null)
                        break;

                    notifyTransferHandlerSync();
                }
            } catch (InterruptedException e)
            {
            } finally
            {
                setClosed();
            }
        }
    }

    private static final Logger logger = LoggerSingleton.logger;
    private final PushSourceStream stream;

    private final SynchronizedObjectHolder<SourceTransferHandler> transferHandlerHolder = new SynchronizedObjectHolder<SourceTransferHandler>();

    private NotifyTransferHandlerThread notifyTransferHandlerThread;

    public AsyncSourceTransferHandlerNotifier(PushSourceStream stream)
    {
        super();
        this.stream = stream;
    }

    public void dispose()
    {
        if (notifyTransferHandlerThread != null)
        {
            notifyTransferHandlerThread.close();
            try
            {
                notifyTransferHandlerThread.waitUntilClosed();
            } catch (InterruptedException e)
            {
                logger.log(Level.WARNING, "" + e, e);
            } finally
            {
                notifyTransferHandlerThread = null;
            }
        }
    }

    public void disposeAsync()
    {
        if (notifyTransferHandlerThread != null)
        {
            notifyTransferHandlerThread.close();
            notifyTransferHandlerThread = null;
        }
    }

    public void notifyTransferHandlerAsync() throws InterruptedException
    {
        if (notifyTransferHandlerThread == null)
        {
            notifyTransferHandlerThread = new NotifyTransferHandlerThread(
                    "NotifyTransferHandlerThread for " + stream);
            notifyTransferHandlerThread.start();
        }

        notifyTransferHandlerThread.notifyTransferHandlerAsync();
    }

    public void notifyTransferHandlerSync()
    {
        final SourceTransferHandler handler = transferHandlerHolder.getObject();
        if (handler != null)
            handler.transferData(stream);
    }

    public void setTransferHandler(SourceTransferHandler transferHandler)
    {
        transferHandlerHolder.setObject(transferHandler);
    }
}
