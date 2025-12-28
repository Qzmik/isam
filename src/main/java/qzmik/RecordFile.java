package qzmik;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class RecordFile {

    private RandomAccessFile fileHook;

    public RecordFile(String name) throws FileNotFoundException {
        fileHook = new RandomAccessFile(String.format("workspace/%1$s", name), "rw");
    }

    public void writeRecord(Record record) throws IOException {
        fileHook.writeInt(record.getKey());
        fileHook.writeDouble(record.getVoltage());
        fileHook.writeDouble(record.getCurrent());
        fileHook.writeInt(record.getOverflow());
    }

    public int readPageToBuffer(byte[] buffer) throws IOException {
        return fileHook.read(buffer);
    }

    public void writePageOfRecords(byte[] blockOfRecords) throws IOException {
        fileHook.write(blockOfRecords);
    }

    public void position(long pos) throws IOException {
        fileHook.seek(pos);
    }

    public void preallocate(long length) throws IOException {
        fileHook.setLength(length);
    }
}
