package net.sf.fmj.media.rtp;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;
import javax.media.protocol.*;

import net.sf.fmj.media.*;
import net.sf.fmj.media.protocol.*;
import net.sf.fmj.media.protocol.rtp.DataSource;
import net.sf.fmj.media.rtp.util.*;

public class RTPSourceStream
    extends BasicSourceStream
    implements PushBufferStream, Runnable
{
    private class PktQue
    {
        private final int FUDGE = 5;
        private final int DEFAULT_AUD_PKT_SIZE = 256;

        /**
         * The default duration in milliseconds of an audio RTP packet. The
         * default value expresses an expectation only. A value of <tt>20</tt>
         * seems more reasonable than, for example, <tt>30</tt> because it is
         * more commonly used in specifications.
         */
        private final int DEFAULT_MS_PER_PKT = 20;

        // damencho: The original value was 30 and we increased it.
        /**
         * The default number of RTP packets to buffer in the case of video.
         */
        private final int DEFAULT_PKTS_TO_BUFFER = 90;
        private final int MIN_BUF_CHECK = 10000;
        private final int BUF_CHECK_INTERVAL = 7000;

        int pktsEst;

        int framesEst = 0;

        int fps = 15;

        int pktsPerFrame = DEFAULT_VIDEO_RATE;

        /**
         * The average approximation of the size in bytes of an RTP packet.
         */
        private int sizePerPkt = DEFAULT_AUD_PKT_SIZE;

        /**
         * The average approximation of the duration in milliseconds of an RTP
         * packet. Used for audio only at the time of this writing. It sounds
         * reasonable to introduce such a value for the duration since there is
         * one for the size in bytes already (i.e. <tt>sizePerPkt</tt>).
         */
        private long msPerPkt = DEFAULT_MS_PER_PKT;

        int maxPktsToBuffer = 0;

        private int sockBufSize = 0;

        //unused?
        int tooMuchBufferingCount = 0;

        long lastPktSeq = 0L;

        long lastCheckTime = 0L;

        /**
         * The <tt>Buffer</tt>s which represent/contain the RTP packets which
         * have been added into this <tt>PktQue</tt> and which may be read out
         * of it.
         */
        private Buffer fill[];

        /**
         * The <tt>Buffer</tt>s which are pooled by this <tt>PktQue</tt> in
         * order to reduce memory allocations and to achieve a better garbage
         * collection profile. A <tt>Buffer</tt> goes out of <tt>free</tt> when
         * it is to be used for an actual RTP packet i.e. placed into
         * <tt>fill</tt> and comes back into <tt>free</tt> when it is read out
         * of <tt>fill</tt>.
         */
        private Buffer free[];

        // Used as pointers in the 'fill' and 'free' arrays.
        private int headFill;
        private int tailFill;
        private int headFree;
        private int tailFree;

        protected int size;

        public PktQue(int size)
        {
            allocBuffers(size);
        }

        /**
         * Inserts <tt>buffer</tt> in its proper place in this queue according
         * to its sequence number. The elements are always kept in ascending
         * order by sequence number.
         *
         * @param buffer the <tt>Buffer</tt> to insert in this queue
         * @see #insert(Buffer)
         */
        public synchronized void addPkt(Buffer buffer)
        {
            long firstSN = NOT_SPECIFIED;
            long lastSN = NOT_SPECIFIED;
            long bufferSN = buffer.getSequenceNumber();
            if (fillNotEmpty())
            {
                firstSN = fill[headFill].getSequenceNumber();
                int i = tailFill - 1;
                if (i < 0)
                    i = size - 1;
                lastSN = fill[i].getSequenceNumber();
            }

            if (firstSN == NOT_SPECIFIED && lastSN == NOT_SPECIFIED)
                append(buffer);
            else if (bufferSN < firstSN)
                prepend(buffer);
            else if (firstSN < bufferSN && bufferSN < lastSN)
                insert(buffer);
            else if (bufferSN > lastSN)
                append(buffer);
            else //only if (bufferSN == firstSN) || (bufferSN == lastSN)?
                returnFree(buffer);
        }

        /**
         * Initialises the <tt>Buffer</tt> arrays {@link #free} and
         * {@link #fill}.
         *
         * @param i the size of the arrays to be initialised
         */
        private void allocBuffers(int i)
        {
            fill = new Buffer[i];
            free = new Buffer[i];
            for (int j = 0; j < i - 1; j++)
                free[j] = new Buffer();

            size = i;
            headFill = tailFill = 0;
            headFree = 0;
            tailFree = size - 1;
        }

        /**
         * Adds <tt>buffer</tt> to the end of this queue.
         *
         * @param buffer the <tt>Buffer</tt> to be added to the end of this
         * queue
         */
        private synchronized void append(Buffer buffer)
        {
            nbAppend++;
            fill[tailFill] = buffer;
            tailFill++;
            if (tailFill >= size)
                tailFill = 0;
        }

        private synchronized void cutByHalf()
        {
            nbCutByHalf++;
            int i = size / 2;
            if (i <= 0)
                return;
            Buffer abuffer[] = new Buffer[size / 2];
            Buffer abuffer1[] = new Buffer[size / 2];
            int j = totalPkts();
            int k;
            for (k = 0; k < i && k < j; k++)
                abuffer[k] = get();

            j = i - k - (size - j - totalFree());
            headFill = 0;
            tailFill = k;
            for (int l = 0; l <= j; l++)
                abuffer1[l] = new Buffer();

            headFree = 0;
            tailFree = j;
            fill = abuffer;
            free = abuffer1;
            size = i;
        }

        /**
         * Removes the first element (the one with the least sequence number)
         * from <tt>fill</tt> and releases it to be reused (adds it to
         * <tt>free</tt>)
         */
        private synchronized void dropFirstPkt()
        {
            // System.out.println("Drop first packet!");
            Buffer buffer = get();
            lastSeqSent = buffer.getSequenceNumber();
            returnFree(buffer);
        }

        /**
         * Removes an element from the queue and releases it to be reused. The
         * element is chosen in a way specific to mpeg.
         */
        private synchronized void dropMpegPkt()
        {
            int i = headFill;
            int j = -1;
            int k = -1;
            while (i != tailFill)
            {
                Buffer buffer = fill[i];
                byte abyte0[] = (byte[]) buffer.getData();
                int l = buffer.getOffset();
                int i1 = abyte0[l + 2] & 7;
                if (i1 > 2)
                {
                    k = i;
                    break;
                }
                if (i1 == 2 && j == -1)
                    j = i;
                if (++i >= size)
                    i = 0;
            }
            if (k == -1)
                i = j != -1 ? j : headFill;
            Buffer buffer1 = fill[i];
            if (i == 0)
                lastSeqSent = buffer1.getSequenceNumber();
            removeAt(i);
        }

        /**
         * Removes an element from the queue and releases it to be reused. Also
         * increases the number of discarded packets in <tt>stats</tt>.
         *
         * Note that it blocks until the queue is non-empty.
         */
        public void dropPkt()
        {
            while (!fillNotEmpty())
                try
                {
                    wait();
                } catch (Exception exception)
                {
                }
            //boris grozev
            if(stats != null)
                stats.update(RTPStats.PDUDROP);
            if (format instanceof AudioFormat)
                dropFirstPkt();
            else if (RTPSourceStream.mpegVideo.matches(format))
                dropMpegPkt();
            else
                dropFirstPkt();
        }

        /**
         * Pops the element/<tt>Buffer</tt> at the head of this queue.
         *
         * @return the element/<tt>Buffer</tt> which was at the head of this
         * queue
         */
        private synchronized Buffer get()
        {
            Buffer buffer = fill[headFill];
            fill[headFill] = null;
            headFill++;
            if (headFill >= size)
                headFill = 0;
            return buffer;
        }

        /**
         * Gets the sequence number of the element/<tt>Buffer</tt> at the head
         * of this queue or <tt>-1</tt> if this queue is empty.
         *
         * @return the sequence number of the element/<tt>Buffer</tt> at the
         * head of this queue or <tt>-1</tt> if this queue is empty.
         */
        public synchronized long getFirstSeq()
        {
            if (!fillNotEmpty())
                return NOT_SPECIFIED;
            else
                return fill[headFill].getSequenceNumber();
        }

        /**
         * Returns one of the saved 'free' (spare) <tt>Buffer</tt>s.
         */
        public synchronized Buffer getFree()
        {
            Buffer buffer = free[headFree];
            free[headFree] = null;
            headFree++;
            if (headFree >= size)
                headFree = 0;
            return buffer;
        }

        /**
         * Returns the first element of the queue.
         *
         * Note that it blocks until the queue is not empty.
         *
         * @return the first element of the queue.
         */
        public synchronized Buffer getPkt()
        {
            while (!fillNotEmpty())
                try
                {
                    wait();
                } catch (Exception exception)
                {
                }
            return get();
        }

        /**
         * Resizes the queue to <tt>newSize</tt>. Creates new arrays and copies
         * the necessary elements from the old ones.
         *
         * @param newSize Resizes the queue to <tt>newSize</tt>
         */
        private synchronized void grow(int newSize)
        {
            nbGrow++;
            Buffer newFill[] = new Buffer[newSize];
            Buffer newFree[] = new Buffer[newSize];
            int j1 = totalPkts();
            int k1 = totalFree();
            int j = headFill;
            for (int l = 0; j != tailFill; l++)
            {
                newFill[l] = fill[j];
                if (++j >= size)
                    j = 0;
            }

            headFill = 0;
            tailFill = j1;
            fill = newFill;
            j = headFree;
            for (int i1 = 0; j != tailFree; i1++)
            {
                newFree[i1] = free[j];
                if (++j >= size)
                    j = 0;
            }

            headFree = 0;
            tailFree = k1;
            for (int k = newSize - size; k > 0; k--)
            {
                newFree[tailFree] = new Buffer();
                tailFree++;
            }

            free = newFree;
            size = newSize;
        }

        /**
         * Inserts <tt>buffer</tt> in the correct place in the queue, so that
         * the order is preserved. The order is by ascending sequence numbers.
         *
         * Note: This could potentially be slow, since all the elements 'bigger'
         * than <tt>buffer</tt> are moved.
         *
         * @param buffer the <tt>Buffer</tt> to insert
         */
        private synchronized void insert(Buffer buffer)
        {
            nbInsert++;
            int i;
            for (i = headFill; i != tailFill;)
            {
                if (fill[i].getSequenceNumber() > buffer.getSequenceNumber())
                    break;
                if (++i >= size)
                    i = 0;
            }

            if (i != tailFill)
            {
                tailFill++;
                if (tailFill >= size)
                    tailFill = 0;
                int k;
                int j = k = tailFill;
                do
                {
                    if (--k < 0)
                        k = size - 1;
                    fill[j] = fill[k];
                    j = k;
                } while (j != i);
                fill[i] = buffer;
            }
        }

        /**
         * This method is called every time before a <tt>Buffer</tt> is added to
         * the queue. It decides whether the queue should be resized and by how
         * much, and does it (by calling either <tt>grow</tt> or
         * <tt>cutByHalf</tt>).
         *
         * @param buffer the <tt>Buffer</tt> which is about to be added
         * @param rtprawreceiver used to access the 'socket buffer'?
         */
        public void monitorQueueSize(
                Buffer buffer,
                RTPRawReceiver rtprawreceiver)
        {
            sizePerPkt = (sizePerPkt + buffer.getLength()) / 2;
            if (format instanceof VideoFormat)
            {
                if (lastPktSeq + 1L == buffer.getSequenceNumber())
                    pktsEst++;
                else
                    pktsEst = 1;
                lastPktSeq = buffer.getSequenceNumber();
                if (RTPSourceStream.mpegVideo.matches(format))
                {
                    byte abyte0[] = (byte[]) buffer.getData();
                    int k = buffer.getOffset();
                    int k1 = abyte0[k + 2] & 7;
                    if (k1 < 3 && (buffer.getFlags() & Buffer.FLAG_RTP_MARKER) != 0)
                    {
                        pktsPerFrame = (pktsPerFrame + pktsEst) / 2;
                        pktsEst = 0;
                    }
                    fps = 30;
                    // damencho
                } else if (RTPSourceStream.h264Video.matches(format))
                {
                    pktsPerFrame = 300;// 800;
                    fps = 15;
                }
                if ((buffer.getFlags() & Buffer.FLAG_RTP_MARKER) != 0)
                {
                    pktsPerFrame = (pktsPerFrame + pktsEst) / 2;
                    pktsEst = 0;
                    framesEst++;
                    long l = System.currentTimeMillis();
                    if (l - lastCheckTime >= 1000L)
                    {
                        lastCheckTime = l;
                        fps = (fps + framesEst) / 2;
                        framesEst = 0;
                        if (fps > 30)
                            fps = 30;
                    }
                }
                int i;
                if (bc != null)
                {
                    i = (int) ((bc.getBufferLength() * fps) / 1000L);
                    if (i <= 0)
                        i = 1;
                    i = pktsPerFrame * i;
//                    threshold = (int) (((bc.getMinimumThreshold() * fps) / 1000L) * pktsPerFrame);
//                    if (threshold <= i / 2)
//                        ;
                    threshold = i / 2;
                } else
                {
                    i = DEFAULT_PKTS_TO_BUFFER;
                }

                // damencho: We need bigger buffers for H.264.
                if (RTPSourceStream.h264Video.matches(format))
                {
                    maxPktsToBuffer = 200;
                } else
                {
                    if (maxPktsToBuffer > 0)
                        maxPktsToBuffer = (maxPktsToBuffer + i) / 2;
                    else
                        maxPktsToBuffer = i;
                }

                int i1 = totalPkts();
                if (size > MIN_BUF_CHECK && i1 < size / 4)
                {
                    if (!prebuffering
                            && tooMuchBufferingCount++ > pktsPerFrame * fps
                                    * BUF_CHECK_INTERVAL)
                    {
                        cutByHalf();
                        tooMuchBufferingCount = 0;
                    }
                } else if (i1 >= size / 2 && size < maxPktsToBuffer)
                {
                    i = size + size / 2;
                    if (i > maxPktsToBuffer)
                        i = maxPktsToBuffer;
                    grow(i + FUDGE);

                    Log.comment("RTP video buffer size: " + size + " pkts, "
                            + i * sizePerPkt + " bytes.\n");
                    tooMuchBufferingCount = 0;
                } else
                {
                    tooMuchBufferingCount = 0;
                }
                int l1 = (i * sizePerPkt) / 2;
                if (rtprawreceiver != null && l1 > sockBufSize)
                {
                    rtprawreceiver.setRecvBufSize(l1);
                    if (rtprawreceiver.getRecvBufSize() < l1)
                        sockBufSize = 0x7fffffff /* BufferControlImpl.NOT_SPECIFIED? */;
                    else
                        sockBufSize = l1;

                    Log.comment(
                            "RTP video socket receive buffer size: "
                                + rtprawreceiver.getRecvBufSize()
                                + " bytes.\n");
                }
            }
            else if (format instanceof AudioFormat)
            {
                if (sizePerPkt <= 0)
                    sizePerPkt = DEFAULT_AUD_PKT_SIZE;
                if (bc != null)
                {
                    long ms;
                    if (RTPSourceStream.mpegAudio.matches(format))
                        ms = sizePerPkt / 4;
                    else
                    {
                        ms = DEFAULT_MS_PER_PKT;
                        try
                        {
                            long ns = buffer.getDuration();

                            if (ns <= 0)
                            {
                                ns
                                    = ((AudioFormat) format).computeDuration(
                                            buffer.getLength());
                                if (ns > 0)
                                    ms = ns / 1000L;
                            }
                            else
                                ms = ns / 1000L;
                        }
                        catch (Throwable t)
                        {
                            if (t instanceof ThreadDeath)
                                throw (ThreadDeath) t;
                        }
                    }
                    msPerPkt = (msPerPkt + ms) / 2;
                    ms = (msPerPkt == 0) ? DEFAULT_MS_PER_PKT : msPerPkt;
                    int aprxBufferLengthInPkts
                        = (int) (bc.getBufferLength() / ms);
//                    threshold = (int) (bc.getMinimumThreshold() / ms);
//                    if (threshold <= bcMs / 2)
//                        ;
                    threshold = aprxBufferLengthInPkts / 2;
                    if (aprxBufferLengthInPkts > size)
                    {
                        grow(aprxBufferLengthInPkts);
                        Log.comment(
                                "Grew audio RTP packet queue to: "
                                    + size + " pkts, "
                                    + size * sizePerPkt + " bytes.\n");
                    }

                    /*
                     * There was no comment and the variables did not use
                     * meaningful names at the time the following code was
                     * initially written. Consequently, it is not immediately
                     * obvious why it is necessary at all and it may be hard to
                     * understand. A possible explanation may be that, since
                     * the threshold value will force a delay with a specific
                     * duration/byte size, we should better be able to hold on
                     * to that much in the socket so that it does not throw the
                     * delayed data away.
                     */
                    int aprxThresholdInBytes = (aprxBufferLengthInPkts * sizePerPkt) / 2;
                    if (rtprawreceiver != null && aprxThresholdInBytes > sockBufSize)
                    {
                        rtprawreceiver.setRecvBufSize(aprxThresholdInBytes);
                        if (rtprawreceiver.getRecvBufSize() < aprxThresholdInBytes)
                            sockBufSize = 0x7fffffff /* BufferControlImpl.NOT_SPECIFIED? */;
                        else
                            sockBufSize = aprxThresholdInBytes;
                        Log.comment(
                                "RTP audio socket receive buffer size: "
                                    + rtprawreceiver.getRecvBufSize()
                                    + " bytes.\n");
                    }
                }
            }
        }

        /**
         * Determines whether this queue is not empty.
         *
         * @return <tt>true</tt> if this queue is not empty i.e. it contains
         * elements/<tt>Buffer</tt>s; otherwise, <tt>false</tt>
         */
        private boolean fillNotEmpty()
        {
            return headFill != tailFill;
        }

        /**
         * Determines whether there are no more free elements/<tt>Buffer</tt>s
         * in this queue.
         *
         * @return <tt>true</tt> if there are no more free
         * elements/<tt>Buffer</tt>s in this queue; otherwise, <tt>false</tt>
         */
        private boolean noMoreFree()
        {
            return headFree == tailFree;
        }

        /**
         * Adds <tt>buffer</tt> to the beginning of this queue.
         *
         * @param buffer the <tt>Buffer</tt> to add to the beginning of this
         * queue
         */
        private synchronized void prepend(Buffer buffer)
        {
            nbPrepend++;
            if (headFill == tailFill)
                return;
            headFill--;
            if (headFill < 0)
                headFill = size - 1;
            fill[headFill] = buffer;
        }

        private void removeAt(int i)
        {
            nbRemoveAt++;
            Buffer buffer = fill[i];
            if (i == headFill)
            {
                headFill++;
                if (headFill >= size)
                    headFill = 0;
            } else if (i == tailFill)
            {
                tailFill--;
                if (tailFill < 0)
                    tailFill = size - 1;
            } else
            {
                int j = i;
                do
                {
                    if (--j < 0)
                        j = size - 1;
                    fill[i] = fill[j];
                    i = j;
                } while (i != headFill);
                headFill++;
                if (headFill >= size)
                    headFill = 0;
            }
            returnFree(buffer);
        }

        /**
         * Empties the queue, effectively dropping all packets
         */
        public synchronized void reset()
        {
            nbReset++;
            for (; fillNotEmpty(); returnFree(get()))
            {
                //consider packets dropped
                if(stats != null)
                    stats.update(RTPStats.PDUDROP);
            }
            tooMuchBufferingCount = 0;
            notifyAll();
        }

        /**
         * Returns (releases) <tt>buffer</tt> to the <tt>free</tt> queue.
         *
         * @param buffer the <tt>Buffer</tt> to return
         */
        private synchronized void returnFree(Buffer buffer)
        {
            free[tailFree] = buffer;
            tailFree++;
            if (tailFree >= size)
                tailFree = 0;
        }

        /**
         * Returns the number of element in the <tt>free</tt> queue
         *
         * @return the number of element in the <tt>free</tt> queue
         */
        public int totalFree()
        {
            return tailFree < headFree ? size - (headFree - tailFree)
                    : tailFree - headFree;
        }

        /**
         * Returns the number of elements in the queue.
         *
         * @return the number of elements in the queue.
         */
        public int totalPkts()
        {
            return tailFill < headFill ? size - (headFill - tailFill)
                    : tailFill - headFill;
        }
    }

    //boris grozev: These are added temporary to help with debugging
    private int nbAdd = 0;
    private int nbReset = 0;
    private int nbAppend = 0;
    private int nbInsert = 0;
    private int nbCutByHalf = 0;
    private int nbGrow = 0;
    private int nbPrepend = 0;
    private int nbRemoveAt = 0;
    private int nbDrop = 0;
    private int nbReplenishFinished = 0;
    private int nbReadWhileEmpty = 0;
    private int nbReplenishStart = 0;

    private void printStats()
    {
        String cn = this.getClass().getCanonicalName()+" ";
        Log.info(cn+"Total packets added: " + nbAdd);
        Log.info(cn+"Times reset() called: " + nbReset);
        Log.info(cn+"Times append() called: " + nbAppend);
        Log.info(cn+"Times insert() called: " + nbInsert);
        Log.info(cn+"Times cutByHalf() called: " + nbCutByHalf);
        Log.info(cn+"Times grow() called: " + nbGrow);
        Log.info(cn+"Times prepend() called: " + nbPrepend);
        Log.info(cn+"Times removeAt() called: " + nbRemoveAt);
        Log.info(cn+"Packets dropped: " + nbDrop);
        Log.info(cn+"Times replenish finished:" + nbReplenishFinished);
        Log.info(cn+"Times read() while empty:" + nbReadWhileEmpty);
        Log.info(cn+"Times replenish started:" + nbReplenishStart);
        //Log.comment(this);
    }

    private static final int DEFAULT_AUDIO_RATE = 8000;
    private static final int DEFAULT_VIDEO_RATE = 15;
    private static final int NOT_SPECIFIED = -1;

    private DataSource dsource;

    private Format format = null;

    BufferTransferHandler handler = null;

    boolean started = false;

    boolean killed = false;

    boolean replenish = true;

    PktQue pktQ;

    Object startReq;

    private RTPMediaThread thread = null;

    private boolean hasRead = false;

    private BufferControlImpl bc = null;

    private long lastSeqRecv = NOT_SPECIFIED;

    private long lastSeqSent = NOT_SPECIFIED;

    private BufferListener listener = null;

    private int threshold = 0;

    private boolean prebuffering = false;

    private boolean prebufferNotice = false;

    private boolean bufferWhenStopped = true;
    static AudioFormat mpegAudio = new AudioFormat("mpegaudio/rtp");
    static VideoFormat mpegVideo = new VideoFormat("mpeg/rtp");
    // damencho
    static VideoFormat h264Video = new VideoFormat("h264/rtp");

    //boris grozev: log discarded packets here
    private RTPStats stats = null;

    public RTPSourceStream(DataSource datasource)
    {
        startReq = new Object();
        dsource = datasource;
        datasource.setSourceStream(this);
        pktQ = new PktQue(4);
        createThread();
    }

    /**
     * Adds <tt>buffer</tt> to the queue.
     *
     * In case the queue is full: if <tt>buffer</tt>'s sequence number comes
     * before the sequence numbers of the <tt>Buffer</tt>s in the queue, nothing
     * is done. Otherwise, a packet is dropped using PktQue.dropPkt()
     *
     * @param buffer the buffer to add
     * @param flag unused
     * @param rtprawreceiver used to access the 'socket buffer'?
     */
    public void add(Buffer buffer, boolean flag, RTPRawReceiver rtprawreceiver)
    {
        if (!started && !bufferWhenStopped)
            return;

        nbAdd++;
        if (lastSeqRecv - buffer.getSequenceNumber() > 256L)
            pktQ.reset();
        lastSeqRecv = buffer.getSequenceNumber();
        boolean almostFull = false;
        synchronized (pktQ)
        {
            pktQ.monitorQueueSize(buffer, rtprawreceiver);
            if (pktQ.noMoreFree())
            {
                nbDrop++;
                long l = pktQ.getFirstSeq();
                if (l != NOT_SPECIFIED && buffer.getSequenceNumber() < l)
                {
                    if(stats != null)
                        stats.update(RTPStats.PDUDROP);
                    return;
                }
                pktQ.dropPkt();
            }
        }
        if (pktQ.totalFree() <= 1)
            almostFull = true;
        Buffer freeBuffer = pktQ.getFree();

        byte bufferData[] = (byte[]) buffer.getData();
        byte freeBufferData[] = (byte[]) freeBuffer.getData();
        if (freeBufferData == null || freeBufferData.length < bufferData.length)
            freeBufferData = new byte[bufferData.length];
        System.arraycopy(bufferData, buffer.getOffset(), freeBufferData,
                buffer.getOffset(), buffer.getLength());
        freeBuffer.copy(buffer);
        freeBuffer.setData(freeBufferData);
        if (almostFull) //with this packet added, the queue will be full
            freeBuffer.setFlags(freeBuffer.getFlags() |
                    Buffer.FLAG_BUF_OVERFLOWN | Buffer.FLAG_NO_DROP);
        else
            freeBuffer.setFlags(freeBuffer.getFlags() | Buffer.FLAG_NO_DROP);

        pktQ.addPkt(freeBuffer);
        synchronized (pktQ)
        {
            if (started && prebufferNotice && listener != null
                    && pktQ.totalPkts() >= threshold)
            {
                listener.minThresholdReached(dsource);
                prebufferNotice = false;
                prebuffering = false;
                synchronized (startReq)
                {
                    startReq.notifyAll();
                }
            }
            if (replenish && (format instanceof AudioFormat))
            {
                //delay the call to notifyAll until the queue is 'replenished'
                if (pktQ.totalPkts() >= pktQ.size / 2)
                {
                    nbReplenishFinished++;
                    replenish = false;
                    pktQ.notifyAll();
                }
            } else
            {
                pktQ.notifyAll();
            }
        }
    }

    public void close()
    {
        if (killed)
            return;
        printStats();
        stop();
        killed = true;
        synchronized (startReq)
        {
            startReq.notifyAll();
        }
        synchronized (pktQ)
        {
            pktQ.notifyAll();
        }
        thread = null;
        if (bc != null)
            bc.removeSourceStream(this);
    }

    public void connect()
    {
        killed = false;
        createThread();
    }

    private void createThread()
    {
        if (thread != null)
            return;
        thread = new RTPMediaThread(this, "RTPStream");
        thread.useControlPriority();
        thread.start();
    }

    public Format getFormat()
    {
        return format;
    }

    public void prebuffer()
    {
        synchronized (pktQ)
        {
            prebuffering = true;
            prebufferNotice = true;
        }
    }

    /**
     * Pops an element off the queue and copies it to <tt>buffer</tt>. The data
     * and header arrays of <tt>buffer</tt> are reused.
     *
     * @param buffer The <tt>Buffer</tt> object to copy an element of the queue
     * to.
     */
    public void read(Buffer buffer)
    {
        if (pktQ.totalPkts() == 0)
        {
            nbReadWhileEmpty++;
            buffer.setDiscard(true);
            return;
        }
        Buffer bufferFromQueue = pktQ.getPkt();
        lastSeqSent = bufferFromQueue.getSequenceNumber();
        Object bufferData = buffer.getData();
        Object bufferHeader = buffer.getHeader();
        buffer.copy(bufferFromQueue);
        bufferFromQueue.setData(bufferData);
        bufferFromQueue.setHeader(bufferHeader);
        pktQ.returnFree(bufferFromQueue);
        synchronized (pktQ)
        {
            hasRead = true;
            if (format instanceof AudioFormat)
            {
                if (pktQ.totalPkts() > 0)
                    pktQ.notifyAll();
                else
                {
                    nbReplenishStart++;
                    replenish = true; //start to replenish when the queue empties
                }
            } else
            {
                pktQ.notifyAll();
            }
        }
    }

    /**
     * Resets the queue, dropping all packets.
     */
    public void reset()
    {
        pktQ.reset();
        lastSeqSent = NOT_SPECIFIED;
    }

    public void run()
    {
        while (true)
            try
            {
                synchronized (startReq)
                {
                    while ((!started || prebuffering) && !killed)
                        startReq.wait();
                }
                synchronized (pktQ)
                {
                    do
                    {
                        if (!hasRead && !killed)
                            pktQ.wait();
                        hasRead = false;
                    } while (pktQ.totalPkts() <= 0 && !killed);
                }
                if (killed)
                    break;
                if (handler != null)
                    handler.transferData(this);
            } catch (InterruptedException interruptedexception)
            {
                Log.error("Thread " + interruptedexception.getMessage());
            }
    }

    public void setBufferControl(BufferControl buffercontrol)
    {
        bc = (BufferControlImpl) buffercontrol;
        updateBuffer(bc.getBufferLength());
        updateThreshold(bc.getMinimumThreshold());
    }

    public void setBufferListener(BufferListener bufferlistener)
    {
        listener = bufferlistener;
    }

    public void setBufferWhenStopped(boolean flag)
    {
        bufferWhenStopped = flag;
    }

    void setContentDescriptor(String s)
    {
        super.contentDescriptor = new ContentDescriptor(s);
    }

    protected void setFormat(Format format1)
    {
        format = format1;
    }

    public void setTransferHandler(BufferTransferHandler buffertransferhandler)
    {
        handler = buffertransferhandler;
    }

    public void start()
    {
        synchronized (startReq)
        {
            started = true;
            startReq.notifyAll();
        }
    }

    public void stop()
    {
        synchronized (startReq)
        {
            started = false;
            prebuffering = false;
            if (!bufferWhenStopped)
                reset();
        }
    }

    public long updateBuffer(long l)
    {
        return l;
    }

    public long updateThreshold(long l)
    {
        return l;
    }

    public void setStats(RTPStats rtpStats)
    {
        stats = rtpStats;
    }
}