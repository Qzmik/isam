package qzmik;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class RecordFile {

    private RandomAccessFile fileHook;

    public RecordFile(boolean overflow) throws FileNotFoundException {
        if (!overflow) {
            fileHook = new RandomAccessFile("workspace/mainFile", "rw");
        } else {
            fileHook = new RandomAccessFile("workspace/overflowFile", "rw");
        }
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
}
