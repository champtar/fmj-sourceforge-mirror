/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.media.codec.audio.ilbc;

import javax.media.*;
import javax.media.format.*;

import net.java.sip.communicator.impl.media.codec.*;
import net.java.sip.communicator.impl.media.codec.audio.*;

/**
 * iLbc to PCM java decoder
 *
 * @author Damian Minkov
 */
public class JavaDecoder extends com.ibm.media.codec.audio.AudioCodec
{
    // kenlars99: TODO: input format endianness is hard-coded, but not present
    // in input format.
    private Format lastFormat = null;

    /**
     * The decoder
     */
    private ilbc_decoder dec = null;

    public JavaDecoder()
    {
        inputFormats = new Format[] { new AudioFormat(Constants.ILBC_RTP, 8000,
                16, 1, Format.NOT_SPECIFIED, Format.NOT_SPECIFIED) };

        supportedInputFormats = new AudioFormat[] { new AudioFormat(
                Constants.ILBC_RTP, 8000, 16, 1, Format.NOT_SPECIFIED,
                Format.NOT_SPECIFIED) };

        defaultOutputFormats = new AudioFormat[] { new AudioFormat(
                AudioFormat.LINEAR) };

        PLUGIN_NAME = "iLbc Decoder";
    }

    @Override
    public void close()
    {
    }

    @Override
    public java.lang.Object[] getControls()
    {
        if (controls == null)
        {
            controls = new Control[1];
            controls[0] = new com.sun.media.controls.SilenceSuppressionAdapter(
                    this, false, false);
        }
        return controls;
    }

    @Override
    protected Format[] getMatchingOutputFormats(Format in)
    {
        AudioFormat af = (AudioFormat) in;

        supportedOutputFormats = new AudioFormat[] { new AudioFormat(
                AudioFormat.LINEAR, af.getSampleRate(), 16, 1,
                AudioFormat.LITTLE_ENDIAN, // isBigEndian(),
                AudioFormat.SIGNED // isSigned());
        ) };

        return supportedOutputFormats;

    }

    private void initConverter(AudioFormat inFormat, int inputLength)
    {
        lastFormat = inFormat;

        if (inputLength == ilbc_constants.NO_OF_BYTES_20MS)
            dec = new ilbc_decoder(20, 1);
        else if (inputLength == ilbc_constants.NO_OF_BYTES_30MS)
            dec = new ilbc_decoder(30, 1);
    }

    @Override
    public void open()
    {
    }

    public int process(Buffer inputBuffer, Buffer outputBuffer)
    {
        if (!checkInputBuffer(inputBuffer))
        {
            return BUFFER_PROCESSED_FAILED;
        }

        if (isEOM(inputBuffer))
        {
            propagateEOM(outputBuffer);
            return BUFFER_PROCESSED_OK;
        }

        byte[] inData = (byte[]) inputBuffer.getData();

        int inpLength = inputBuffer.getLength();
        int inOffset = inputBuffer.getOffset();

        Format newFormat = inputBuffer.getFormat();

        if (lastFormat != newFormat)
        {
            initConverter((AudioFormat) newFormat, inpLength);
        }

        if (inpLength <= 0)
            return OUTPUT_BUFFER_NOT_FILLED; // Jun 19 2007 kenlars99 - not sure
                                             // if caller is supposed to take
                                             // care of this and simply not
                                             // call..

        short[] data = Utils.byteToShortArray(inData, inOffset, inpLength,
                false);

        short[] decodedData = new short[dec.ULP_inst.blockl];
        dec.decode(decodedData, data, (short) 1);
        int outLength = dec.ULP_inst.blockl * 2;
        byte[] outData = validateByteArraySize(outputBuffer, outLength);

        Utils.shortArrToByteArr(decodedData, outData, true);

        updateOutput(outputBuffer, outputFormat, outLength, 0);

        return BUFFER_PROCESSED_OK;
    }
}
