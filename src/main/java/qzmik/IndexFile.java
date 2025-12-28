package qzmik;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class IndexFile {

    private RandomAccessFile fileHook;

    public IndexFile(String name) throws FileNotFoundException {
        fileHook = new RandomAccessFile(String.format("workspace/%1$s", name), "rw");
    }

    public void writeRecord(IndexRecord record) throws IOException {
        fileHook.writeInt(record.getKey());
        fileHook.writeInt(record.getPageNumber());
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
