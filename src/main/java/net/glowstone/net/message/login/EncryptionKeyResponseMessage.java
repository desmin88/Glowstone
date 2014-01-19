package net.glowstone.net.message.login;

import com.flowpowered.networking.process.ChannelProcessor;
import com.flowpowered.networking.process.ProcessorHandler;
import com.flowpowered.networking.process.ProcessorSetupMessage;

public final class EncryptionKeyResponseMessage implements ProcessorSetupMessage {

    private ChannelProcessor processor;

    private final byte[] sharedSecret;
    private final byte[] verifyToken;

    public EncryptionKeyResponseMessage(byte[] sharedSecret, byte[] verifyToken) {
        this.sharedSecret = sharedSecret;
        this.verifyToken = verifyToken;
    }

    public byte[] getSharedSecret() {
        return sharedSecret;
    }

    public byte[] getVerifyToken() {
        return verifyToken;
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    public void setProcessor(ChannelProcessor processor) {
        this.processor = processor;
    }

    @Override
    public ChannelProcessor getProcessor() {
        return processor;
    }

    @Override
    public boolean isChannelLocking() {
        return false;
    }

    @Override
    public void setProcessorHandler(ProcessorHandler handler) {
        //F-N garbage
    }
}
