package net.glowstone.util.nbt;

import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * This class writes NBT, or Named Binary Tag, {@link Tag} objects to an
 * underlying {@link OutputStream}.
 * <p />
 * The NBT format was created by Markus Persson, and the specification may
 * be found at <a href="http://www.minecraft.net/docs/NBT.txt">
 * http://www.minecraft.net/docs/NBT.txt</a>.
 * @author Graham Edgecombe
 */
public final class NBTOutputStream implements Closeable {

    /**
     * The output stream.
     */
    private final DataOutputStream os;

    /**
     * Creates a new {@link NBTOutputStream}, which will write data to the
     * specified underlying output stream. This assumes the output stream
     * should be compressed with GZIP.
     * @param os The output stream.
     * @throws IOException if an I/O error occurs.
     */
    public NBTOutputStream(OutputStream os) throws IOException {
        this(os, true);
    }

    /**
     * Creates a new {@link NBTOutputStream}, which will write data to the
     * specified underlying output stream. A flag indicates if the output
     * should be compressed with GZIP or not.
     * @param os The output stream.
     * @param compressed A flag that indicates if the output should be compressed.
     * @throws IOException if an I/O error occurs.
     */
    public NBTOutputStream(OutputStream os, boolean compressed) throws IOException {
        this.os = new DataOutputStream(compressed ? new GZIPOutputStream(os) : os);
    }

    /**
     * Writes a tag.
     * @param tag The tag to write.
     * @throws IOException if an I/O error occurs.
     */
    public void writeTag(Tag tag) throws IOException {
        TagType type = tag.getType();
        String name = tag.getName();
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);

        if (type == TagType.END) {
            throw new IOException("Named TAG_End not permitted.");
        }

        os.writeByte(type.getId());
        os.writeShort(nameBytes.length);
        os.write(nameBytes);

        writeTagPayload(tag);
    }

    /**
     * Writes tag payload.
     * @param tag The tag.
     * @throws IOException if an I/O error occurs.
     */
    private void writeTagPayload(Tag tag) throws IOException {
        TagType type = tag.getType();
        switch (type) {
        case END:
            writeEndTagPayload((EndTag) tag);
            break;

        case BYTE:
            writeByteTagPayload((ByteTag) tag);
            break;

        case SHORT:
            writeShortTagPayload((ShortTag) tag);
            break;

        case INT:
            writeIntTagPayload((IntTag) tag);
            break;

        case LONG:
            writeLongTagPayload((LongTag) tag);
            break;

        case FLOAT:
            writeFloatTagPayload((FloatTag) tag);
            break;

        case DOUBLE:
            writeDoubleTagPayload((DoubleTag) tag);
            break;

        case BYTE_ARRAY:
            writeByteArrayTagPayload((ByteArrayTag) tag);
            break;

        case STRING:
            writeStringTagPayload((StringTag) tag);
            break;

        case LIST:
            writeListTagPayload((ListTag<?>) tag);
            break;

        case COMPOUND:
            writeCompoundTagPayload((CompoundTag) tag);
            break;

        case INT_ARRAY:
            writeIntArrayTagPayload((IntArrayTag) tag);
            break;

        default:
            throw new IOException("Invalid tag type: " + type + ".");
        }
    }

    /**
     * Writes a {@code TAG_Byte} tag.
     * @param tag The tag.
     * @throws IOException if an I/O error occurs.
     */
    private void writeByteTagPayload(ByteTag tag) throws IOException {
        os.writeByte(tag.getValue());
    }

    /**
     * Writes a {@code TAG_Byte_Array} tag.
     * @param tag The tag.
     * @throws IOException if an I/O error occurs.
     */
    private void writeByteArrayTagPayload(ByteArrayTag tag) throws IOException {
        byte[] bytes = tag.getValue();
        os.writeInt(bytes.length);
        os.write(bytes);
    }

    /**
     * Writes a {@code TAG_Int_Array} tag.
     * @param tag The tag.
     * @throws IOException if an I/O error occurs.
     */
    private void writeIntArrayTagPayload(IntArrayTag tag) throws IOException {
        int[] ints = tag.getValue();
        os.writeInt(ints.length);
        for (int value : ints) {
            os.writeInt(value);
        }
    }

    /**
     * Writes a {@code TAG_Compound} tag.
     * @param tag The tag.
     * @throws IOException if an I/O error occurs.
     */
    private void writeCompoundTagPayload(CompoundTag tag) throws IOException {
        for (Tag childTag : tag.getValue().values()) {
            writeTag(childTag);
        }
        os.writeByte((byte) 0); // end tag - better way?
    }

    /**
     * Writes a {@code TAG_List} tag.
     * @param tag The tag.
     * @throws IOException if an I/O error occurs.
     */
    @SuppressWarnings("unchecked")
    private void writeListTagPayload(ListTag<?> tag) throws IOException {
        List<Tag> tags = (List<Tag>) tag.getValue();
        int size = tags.size();

        os.writeByte(tag.getChildType().getId());
        os.writeInt(size);
        for (Tag child : tags) {
            writeTagPayload(child);
        }
    }

    /**
     * Writes a {@code TAG_String} tag.
     * @param tag The tag.
     * @throws IOException if an I/O error occurs.
     */
    private void writeStringTagPayload(StringTag tag) throws IOException {
        byte[] bytes = tag.getValue().getBytes(StandardCharsets.UTF_8);
        os.writeShort(bytes.length);
        os.write(bytes);
    }

    /**
     * Writes a {@code TAG_Double} tag.
     * @param tag The tag.
     * @throws IOException if an I/O error occurs.
     */
    private void writeDoubleTagPayload(DoubleTag tag) throws IOException {
        os.writeDouble(tag.getValue());
    }

    /**
     * Writes a {@code TAG_Float} tag.
     * @param tag The tag.
     * @throws IOException if an I/O error occurs.
     */
    private void writeFloatTagPayload(FloatTag tag) throws IOException {
        os.writeFloat(tag.getValue());
    }

    /**
     * Writes a {@code TAG_Long} tag.
     * @param tag The tag.
     * @throws IOException if an I/O error occurs.
     */
    private void writeLongTagPayload(LongTag tag) throws IOException {
        os.writeLong(tag.getValue());
    }

    /**
     * Writes a {@code TAG_Int} tag.
     * @param tag The tag.
     * @throws IOException if an I/O error occurs.
     */
    private void writeIntTagPayload(IntTag tag) throws IOException {
        os.writeInt(tag.getValue());
    }

    /**
     * Writes a {@code TAG_Short} tag.
     * @param tag The tag.
     * @throws IOException if an I/O error occurs.
     */
    private void writeShortTagPayload(ShortTag tag) throws IOException {
        os.writeShort(tag.getValue());
    }

    /**
     * Writes a {@code TAG_Empty} tag.
     * @param tag The tag.
     */
    private void writeEndTagPayload(EndTag tag) {
        /* empty */
    }

    @Override
    public void close() throws IOException {
        os.close();
    }

}

