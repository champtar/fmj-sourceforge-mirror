package net.sf.fmj.media.rtp;

import javax.media.rtp.*;

public class RTPStats implements ReceptionStats
{
    public static final int PDULOST = 0;
    public static final int PDUPROCSD = 1;
    public static final int PDUMISORD = 2;
    public static final int PDUINVALID = 3;
    public static final int PDUDUP = 4;
    public static final int PAYLOAD = 5;
    public static final int ENCODE = 6;
    public static final int QSIZE = 7;
    public static final int PDUDROP = 8;
    public static final int ADUDROP = 9;
    private int numLost;
    private int numProc;
    private int numMisord;
    private int numInvalid;
    private int numDup;
    private int payload;
    private String encodeName;
    private int qSize;
    private int PDUDrop;
    private int ADUDrop;

    public RTPStats()
    {
        numLost = 0;
        numProc = 0;
        numMisord = 0;
        numInvalid = 0;
        numDup = 0;
        qSize = 0;
        PDUDrop = 0;
        ADUDrop = 0;
    }

    public int getADUDrop()
    {
        return ADUDrop;
    }

    public int getBufferSize()
    {
        return qSize;
    }

    public String getEncodingName()
    {
        return encodeName;
    }

    public int getPayloadType()
    {
        return payload;
    }

    public int getPDUDrop()
    {
        return PDUDrop;
    }

    public int getPDUDuplicate()
    {
        return numDup;
    }

    public int getPDUInvalid()
    {
        return numInvalid;
    }

    public int getPDUlost()
    {
        return numLost;
    }

    public int getPDUMisOrd()
    {
        return numMisord;
    }

    public int getPDUProcessed()
    {
        return numProc;
    }

    @Override
    public String toString()
    {
        String s = "PDULost " + getPDUlost() + "\nPDUProcessed "
                + getPDUProcessed() + "\nPDUMisord " + getPDUMisOrd()
                + "\nPDUInvalid " + getPDUInvalid() + "\nPDUDuplicate "
                + getPDUDuplicate();
        return s;
    }

    public synchronized void update(int which)
    {
        switch (which)
        {
        case PDULOST: // '\0'
            numLost++;
            break;

        case PDUPROCSD: // '\001'
            numProc++;
            break;

        case PDUMISORD: // '\002'
            numMisord++;
            break;

        case PDUINVALID: // '\003'
            numInvalid++;
            break;

        case PDUDUP: // '\004'
            numDup++;
            break;

        case PDUDROP:
            PDUDrop++;
            break;
        }
    }

    public synchronized void update(int which, int amount)
    {
        switch (which)
        {
        case PDULOST: // '\0'
            numLost = numLost + amount;
            break;

        case PAYLOAD: // '\005'
            payload = amount;
            break;

        case QSIZE: // '\007'
            qSize = amount;
            break;

        case PDUDROP: // '\b'
            PDUDrop = amount;
            break;

        case ADUDROP: // '\t'
            ADUDrop = amount;
            break;
        }
    }

    public synchronized void update(int which, String name)
    {
        if (which == ENCODE)
            encodeName = name;
    }
}
