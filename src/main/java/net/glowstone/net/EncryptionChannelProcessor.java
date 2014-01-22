package net.glowstone.net;

import com.flowpowered.networking.processor.simple.SimpleMessageProcessor;
import org.bouncycastle.crypto.BufferedBlockCipher;

public class EncryptionChannelProcessor extends SimpleMessageProcessor {

    private final BufferedBlockCipher cipherEncode;
    private final BufferedBlockCipher cipherDecode;

    private final byte[] processedEncode;
    private int storedEncode = 0;
    private int positionEncode = 0;

    private final byte[] processedDecode;
    private int storedDecode = 0;
    private int positionDecode = 0;

    public EncryptionChannelProcessor(BufferedBlockCipher cipherEncode, BufferedBlockCipher cipherDecode, int capacity) {
        super(capacity);
        this.cipherDecode = cipherDecode;
        this.cipherEncode = cipherEncode;


        processedEncode = new byte[capacity * 2];
        processedDecode = new byte[capacity * 2];
    }

    @Override
    protected void writeEncode(byte[] buf, int length) {
        if (storedEncode > positionEncode) {
            throw new IllegalStateException("Stored data must be completely read before writing more data");
        }
        storedEncode = cipherEncode.processBytes(buf, 0, length, processedEncode, 0);
        positionEncode = 0;
    }

    @Override
    protected int readEncode(byte[] buf) {
        if (positionEncode >= storedEncode) {
            return 0;
        } else {
            int toRead = Math.min(buf.length, storedEncode - positionEncode);
            for (int i = 0; i < toRead; i++) {
                buf[i] = processedEncode[positionEncode + i];
            }
            positionEncode += toRead;
            return toRead;
        }
    }

    @Override
    protected void writeDecode(byte[] buf, int length) {
        if (storedDecode > positionDecode) {
            throw new IllegalStateException("Stored data must be completely read before writing more data");
        }
        storedDecode = cipherDecode.processBytes(buf, 0, length, processedDecode, 0);
        positionDecode = 0;
    }

    @Override
    protected int readDecode(byte[] buf) {
        if (positionDecode >= storedDecode) {
            return 0;
        } else {
            int toRead = Math.min(buf.length, storedDecode - positionDecode);
            for (int i = 0; i < toRead; i++) {
                buf[i] = processedEncode[positionDecode + i];
            }
            positionDecode += toRead;
            return toRead;
        }
    }
}