package qzmik;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

public class IndexManager {

    public static final int BLOCKING_FACTOR = 4;
    private ByteBuffer buffer;
    private IndexFile indexFile;
    private int indexPagesCount = 0;
    private int currentPageLoaded = 0;

    private int pageReads = 0;
    private int pageWrites = 0;

    public IndexManager() throws FileNotFoundException {
        indexFile = new IndexFile();
        buffer = ByteBuffer.allocate(BLOCKING_FACTOR * IndexRecord.RECORD_SIZE_ON_DISK);
    }

    private void readPageIntoBuffer() throws IOException, EOFException {
        byte[] recordArray = new byte[BLOCKING_FACTOR * IndexRecord.RECORD_SIZE_ON_DISK];
        int numberOfBytesRead = indexFile.readPageToBuffer(recordArray);
        if (numberOfBytesRead < 0) {
            throw new EOFException("Chosen tape has no more data on it");
        }
        buffer = ByteBuffer.wrap(recordArray, 0, numberOfBytesRead);
        pageReads++;
    }

    private void writeBufferToFile(int targetTapeIndex) throws IOException {
        indexFile.writePageOfRecords(buffer.array());
        pageWrites++;
        buffer = ByteBuffer
                .allocate(IndexRecord.RECORD_SIZE_ON_DISK * BLOCKING_FACTOR);
    }

    public int findPageNumber(int index) {

        // check if key already in buffer
        int leftIndex = buffer.getInt();
        int leftPage = buffer.getInt();

        for (int i = 1; i < BLOCKING_FACTOR; i++) {
            int rightIndex = buffer.getInt();
            if (rightIndex > index && index >= leftIndex) {
                return leftPage;
            }

            if (index < leftIndex) {
                // its not in the buffer - its in pages "below"
                buffer.position(0);
                break;
            }

            leftPage = buffer.getInt(); // rightPage
            leftIndex = rightIndex;
        }
        // if quitted the loop, its not in the buffer - its in pages "above"
    }

}
