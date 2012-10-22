package net.sf.fmj.media.rtp;

import java.io.*;

public class RTCPSRPacket extends RTCPPacket
{
    int ssrc;
    long ntptimestampmsw;
    long ntptimestamplsw;
    long rtptimestamp;
    long packetcount;
    long octetcount;
    RTCPReportBlock reports[];

    RTCPSRPacket(int ssrc, RTCPReportBlock reports[])
    {
        this.ssrc = ssrc;
        this.reports = reports;
        if (reports.length > 31)
            throw new IllegalArgumentException("Too many reports");
        else
            return;
    }

    RTCPSRPacket(RTCPPacket parent)
    {
        super(parent);
        super.type = 200;
    }

    @Override
    void assemble(DataOutputStream out) throws IOException
    {
        out.writeByte(128 + reports.length);
        out.writeByte(200);
        out.writeShort(6 + reports.length * 6);
        out.writeInt(ssrc);
        out.writeInt((int) ntptimestampmsw);
        out.writeInt((int) ntptimestamplsw);
        out.writeInt((int) rtptimestamp);
        out.writeInt((int) packetcount);
        out.writeInt((int) octetcount);
        for (int i = 0; i < reports.length; i++)
        {
            out.writeInt(reports[i].ssrc);
            out.writeInt((reports[i].packetslost & 0xffffff)
                    + (reports[i].fractionlost << 24));
            out.writeInt((int) reports[i].lastseq);
            out.writeInt(reports[i].jitter);
            out.writeInt((int) reports[i].lsr);
            out.writeInt((int) reports[i].dlsr);
        }

    }

    @Override
    public int calcLength()
    {
        return 28 + reports.length * 24;
    }

    @Override
    public String toString()
    {
        return "\tRTCP SR (sender report) packet for sync source " + ssrc
                + "\n\t\tNTP timestampMSW: " + ntptimestampmsw
                + "\n\t\tNTP timestampLSW: " + ntptimestamplsw
                + "\n\t\tRTP timestamp: " + rtptimestamp
                + "\n\t\tnumber of packets sent: " + packetcount
                + "\n\t\tnumber of octets (bytes) sent: " + octetcount + "\n"
                + RTCPReportBlock.toString(reports);
    }
}
