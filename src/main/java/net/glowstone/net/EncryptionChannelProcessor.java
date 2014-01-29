package net.glowstone.net;

import com.flowpowered.networking.processor.simple.SimpleMessageProcessor;
import net.glowstone.GlowServer;
import org.bouncycastle.crypto.BufferedBlockCipher;

import java.util.Arrays;

public class EncryptionChannelProcessor extends SimpleMessageProcessor {

    private CryptBuf encodeBuf;
    private CryptBuf decodeBuf;

    public EncryptionChannelProcessor(BufferedBlockCipher cipherEncode, BufferedBlockCipher cipherDecode, int capacity) {
        super(capacity);
        this.encodeBuf = new CryptBuf(cipherDecode, capacity * 2);
        this.decodeBuf = new CryptBuf(cipherEncode, capacity * 2);
    }

    @Override
    protected void writeEncode(byte[] buf, int length) {
        GlowServer.logger.info("writeEncode: " + Arrays.toString(buf));
        encodeBuf.write(buf, length);
    }

    @Override
    protected int readEncode(byte[] buf) {
        GlowServer.logger.info("readEncode");
        return encodeBuf.read(buf);
    }

    @Override
    protected void writeDecode(byte[] buf, int length) {
        GlowServer.logger.info("writeDecode");
        decodeBuf.write(buf, length);
    }

    @Override
    protected int readDecode(byte[] buf) {
        GlowServer.logger.info("readDecode");
        return decodeBuf.read(buf);
    }

    private static class CryptBuf {
        private final BufferedBlockCipher cipher;
        private final byte[] buffer;
        private int writePosition;
        private int readPosition;

        private CryptBuf(BufferedBlockCipher cipher, int bufSize) {
            this.cipher = cipher;
            this.buffer = new byte[bufSize];
        }

        private int read(byte[] dest) {
            if (readPosition >= writePosition) {
                return 0;
            } else {
                int amount = Math.min(dest.length, writePosition - readPosition);
                System.arraycopy(buffer, readPosition, dest, 0, amount);
                readPosition += amount;
                return amount;
            }
        }

        private void write(byte[] src, int length) {
            if (readPosition < writePosition) {
                throw new IllegalStateException("Stored data must be completely read before writing more data");
            }
            writePosition = cipher.processBytes(src, 0, length, buffer, 0);
            readPosition = 0;
        }
    }
}