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
    private int currentPageLoaded = -1;

    private int pageReads = 0;
    private int pageWrites = 0;

    public IndexManager() throws FileNotFoundException, IOException {
        indexFile = new IndexFile();
        buffer = ByteBuffer.allocate(BLOCKING_FACTOR * IndexRecord.RECORD_SIZE_ON_DISK);
        writeBufferToFile(0);
    }

    private void readPageIntoBuffer(int targetPage) throws IOException, EOFException {
        if (targetPage == currentPageLoaded) {
            return;
        }
        byte[] recordArray = new byte[BLOCKING_FACTOR * IndexRecord.RECORD_SIZE_ON_DISK];
        indexFile.position(targetPage * BLOCKING_FACTOR * IndexRecord.RECORD_SIZE_ON_DISK);
        int numberOfBytesRead = indexFile.readPageToBuffer(recordArray);
        if (numberOfBytesRead < 0) {
            throw new EOFException("Chosen file has no more data on it");
        }
        buffer = ByteBuffer.wrap(recordArray, 0, numberOfBytesRead);
        currentPageLoaded = targetPage;
        pageReads++;
    }

    private void writeBufferToFile(int targetPage) throws IOException {
        indexFile.position(targetPage * BLOCKING_FACTOR * IndexRecord.RECORD_SIZE_ON_DISK);
        indexFile.writePageOfRecords(buffer.array());
        pageWrites++;
        buffer.position(0);
    }

    public int[] giveReadWriteData() {
        int readWriteData[] = { pageReads, pageWrites };
        return readWriteData;
    }

    public int findPageNumber(int index) throws IOException {

        // check if key already in buffer
        int rightIndex;
        int leftPage = 0;
        int rightPage = indexPagesCount;
        int leftIndex = buffer.getInt();
        int potentiallyFoundPage = buffer.getInt();
        int middle;
        int nextPotentialPage;

        if (index < leftIndex) {
            buffer.position(0);
            return -1;
        }

        while (leftPage <= rightPage) {
            if (index >= leftIndex) {
                while (buffer.hasRemaining()) {
                    rightIndex = buffer.getInt();
                    nextPotentialPage = buffer.getInt();

                    if (rightIndex == 0) {
                        buffer.position(0);
                        return potentiallyFoundPage; // reading placeholder data, the file has ended logically
                    }

                    if (rightIndex > index && index >= leftIndex) {
                        buffer.position(0);
                        return potentiallyFoundPage;
                    }

                    potentiallyFoundPage = nextPotentialPage;
                    leftIndex = rightIndex;
                }
            }
            // if quitted the loop, its not in the buffer - its in pages "above" or "below"
            // setting up for bisection
            if (index < leftIndex) {
                rightPage = currentPageLoaded - 1;
            } else {
                leftPage = currentPageLoaded + 1;
            }
            middle = (leftPage + rightPage) / 2;
            readPageIntoBuffer(middle);
            leftIndex = buffer.getInt();
            potentiallyFoundPage = buffer.getInt();
        }
        buffer.position(0);
        return -1;
    }

    public void createStartingIndexPage(int startingIndex) throws IOException {

        buffer.putInt(startingIndex);
        buffer.putInt(indexPagesCount);

        writeBufferToFile(indexPagesCount);

        currentPageLoaded = indexPagesCount;
        indexPagesCount++;
    }

    public void printIndexFile() throws IOException {
        System.out.printf("INDEX FILE:\n");
        for (int i = 0; i < indexPagesCount; i++) {
            System.out.printf("----------------------\nPAGE %d:\n", i + 1);
            int recordCounter = 0;
            readPageIntoBuffer(i);

            while (buffer.hasRemaining()) {
                int key = buffer.getInt();
                int pageNumber = buffer.getInt();
                if (key != 0) {
                    recordCounter++;
                    System.out.printf("Index %d: Main file page %d\n", key, pageNumber + 1);
                } else
                    System.out.printf("-\n");

            }
            System.out.printf("Filling: %f%%\n", 100.0 * recordCounter / BLOCKING_FACTOR);
        }

        buffer.position(0);
    }
}
