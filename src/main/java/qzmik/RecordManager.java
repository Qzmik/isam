package qzmik;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

public class RecordManager {

    public static final int BLOCKING_FACTOR = 4;
    private ByteBuffer mainBuffer;
    private RecordFile mainFile;
    private int mainPagesCount = 0;
    private int currentMainPageLoaded = -1;
    private int mainPageReads = 0;
    private int mainPageWrites = 0;

    // this points at the first record in overflow that COULD NOT fit into main page
    // due to its key being the smallest
    private int specialOverflowPointer = -1;

    private ByteBuffer overflowBuffer;
    private RecordFile overflowFile;
    private int overflowPagesCount = 0;
    private int overflowRecordsCount = 0;
    private int currentOverflowPageLoaded = -1;
    private int overflowPageReads = 0;
    private int overflowPageWrites = 0;

    public RecordManager() throws FileNotFoundException, IOException {
        mainFile = new RecordFile(false);
        overflowFile = new RecordFile(true);
        mainBuffer = ByteBuffer.allocate(BLOCKING_FACTOR * Record.RECORD_SIZE_ON_DISK);
        writeBufferToMainFile(0);
        mainPagesCount++;
        overflowBuffer = ByteBuffer.allocate(BLOCKING_FACTOR * Record.RECORD_SIZE_ON_DISK);
        writeBufferToOverflowFile(0);
        overflowPagesCount++;
    }

    private void readMainPageIntoBuffer(int targetPage) throws IOException, EOFException {
        if (targetPage == currentMainPageLoaded) {
            return;
        }
        byte[] recordArray = new byte[BLOCKING_FACTOR * Record.RECORD_SIZE_ON_DISK];
        mainFile.position(targetPage * BLOCKING_FACTOR * Record.RECORD_SIZE_ON_DISK);
        int numberOfBytesRead = mainFile.readPageToBuffer(recordArray);
        if (numberOfBytesRead < 0) {
            throw new EOFException("Chosen file has no more data on it");
        }
        mainBuffer = ByteBuffer.wrap(recordArray, 0, numberOfBytesRead);
        currentMainPageLoaded = targetPage;
        mainPageReads++;
    }

    private void writeBufferToMainFile(int targetPage) throws IOException {
        mainFile.position(targetPage * BLOCKING_FACTOR * Record.RECORD_SIZE_ON_DISK);
        mainFile.writePageOfRecords(mainBuffer.array());
        mainPageWrites++;
        mainBuffer.position(0);
    }

    private void readOverflowPageIntoBuffer(int targetPage) throws IOException, EOFException {
        if (targetPage == currentOverflowPageLoaded) {
            return;
        }
        byte[] recordArray = new byte[BLOCKING_FACTOR * Record.RECORD_SIZE_ON_DISK];
        overflowFile.position(targetPage * BLOCKING_FACTOR * Record.RECORD_SIZE_ON_DISK);
        int numberOfBytesRead = overflowFile.readPageToBuffer(recordArray);
        if (numberOfBytesRead < 0) {
            throw new EOFException("Chosen file has no more data on it");
        }
        overflowBuffer = ByteBuffer.wrap(recordArray, 0, numberOfBytesRead);
        currentOverflowPageLoaded = targetPage;
        overflowPageReads++;
    }

    private void writeBufferToOverflowFile(int targetPage) throws IOException {
        overflowFile.position(targetPage * BLOCKING_FACTOR * Record.RECORD_SIZE_ON_DISK);
        overflowFile.writePageOfRecords(mainBuffer.array());
        overflowPageWrites++;
        overflowBuffer.position(0);
    }

    private int[] determineOverflowPosition(int overflow) {
        // pageNumber, position on page
        int overflowPosition[] = { overflow / BLOCKING_FACTOR, overflow % BLOCKING_FACTOR };
        return overflowPosition;
    }

    public int[] giveReadWriteData() {
        int readWriteData[] = { mainPageReads, mainPageWrites, overflowPageReads, overflowPageWrites };
        return readWriteData;
    }

    public Record readRecord(int pageNumber, int index) throws IOException {

        // directed to special overflow
        if (pageNumber == -1) {
            int overflow = specialOverflowPointer;
            if (overflow == -1) {
                return null;
            }
            while (overflow != -1) {
                int overflowPosition[] = determineOverflowPosition(overflow);
                readOverflowPageIntoBuffer(overflowPosition[0]);
                overflowBuffer.position(overflowPosition[1] * Record.RECORD_SIZE_ON_DISK);
                int recordIndex = overflowBuffer.getInt();
                // EOF
                if (recordIndex == 0) {
                    mainBuffer.position(0);
                    overflowBuffer.position(0);
                    break;
                }
                Double recordVoltage = overflowBuffer.getDouble();
                Double recordCurrent = overflowBuffer.getDouble();
                overflow = overflowBuffer.getInt();

                if (recordIndex == index) {
                    mainBuffer.position(0);
                    overflowBuffer.position(0);
                    return new Record(index, recordVoltage, recordCurrent, overflow);
                }
            }
            return null;
        }

        readMainPageIntoBuffer(pageNumber); // this is after looking for record in current buffer

        return checkForRecordInCurrentBuffer(index);
    }

    public Record checkForRecordInCurrentBuffer(int index) throws IOException {
        while (mainBuffer.hasRemaining()) {
            int recordIndex = mainBuffer.getInt();
            Double recordVoltage = mainBuffer.getDouble();
            Double recordCurrent = mainBuffer.getDouble();
            int overflow = mainBuffer.getInt();
            if (recordIndex == index) {
                mainBuffer.position(0);
                return new Record(index, recordVoltage, recordCurrent, overflow);
            }
            if (recordIndex > index) {
                mainBuffer.position(mainBuffer.position() - 24);
                break;
            }
        }
        // assuming we are at a correct page, this means the record belongs to the
        // overflow if it exists

        // if after reading just the first record of current buffer it was already too
        // big, then it is not in the buffer, nor in corresponding overflow
        if (mainBuffer.position() == 0) {
            return null;
        }

        mainBuffer.position(mainBuffer.position() - 4);
        int overflow = mainBuffer.getInt();

        // no overflow chain found - no record
        if (overflow == -1) {
            return null;
        }
        while (overflow != -1) {
            int overflowPosition[] = determineOverflowPosition(overflow);
            readOverflowPageIntoBuffer(overflowPosition[0]);
            overflowBuffer.position(overflowPosition[1] * Record.RECORD_SIZE_ON_DISK);
            int recordIndex = overflowBuffer.getInt();
            Double recordVoltage = overflowBuffer.getDouble();
            Double recordCurrent = overflowBuffer.getDouble();
            overflow = overflowBuffer.getInt();

            // EOF
            if (recordIndex == 0) {
                mainBuffer.position(0);
                overflowBuffer.position(0);
                break;
            }

            if (recordIndex == index) {
                mainBuffer.position(0);
                overflowBuffer.position(0);
                return new Record(index, recordVoltage, recordCurrent, overflow);
            }
        }
        return null;
    }

    public void writeRecord(Record record, int pageNumber) throws IOException {

        // directed to special overflow
        if (pageNumber == -1) {
            int recordIndex = 0;
            int overflow = specialOverflowPointer;
            int prevOverflow = specialOverflowPointer;
            if (overflow == -1) {
                specialOverflowPointer = overflowRecordsCount;
            } else {
                while (overflow != -1) {
                    int overflowPosition[] = determineOverflowPosition(overflow);
                    readOverflowPageIntoBuffer(overflowPosition[0]);
                    overflowBuffer.position(overflowPosition[1] * Record.RECORD_SIZE_ON_DISK);

                    recordIndex = overflowBuffer.getInt();
                    if (recordIndex == 0 || recordIndex < record.getKey()) {
                        mainBuffer.position(0);
                        overflowBuffer.position(0);
                        break;
                    }
                    Double recordVoltage = overflowBuffer.getDouble();
                    Double recordCurrent = overflowBuffer.getDouble();
                    prevOverflow = overflow;
                    overflow = overflowBuffer.getInt();
                }
                if (overflow == specialOverflowPointer) {
                    specialOverflowPointer = overflowRecordsCount;
                } else {
                    if (recordIndex != 0) {
                        int overflowPosition[] = determineOverflowPosition(prevOverflow);
                        readOverflowPageIntoBuffer(overflowPosition[0]);
                        overflowBuffer.position(overflowPosition[1] * Record.RECORD_SIZE_ON_DISK);
                        recordIndex = overflow; // save the pointer (even if it is end of chain)
                        overflowBuffer.position(overflowBuffer.position() - 4);
                        overflowBuffer.putInt(overflowRecordsCount);
                        writeBufferToOverflowFile(currentOverflowPageLoaded);
                    }
                }
            }

            int overflowPosition[] = determineOverflowPosition(overflowRecordsCount);
            readOverflowPageIntoBuffer(overflowPosition[0]);

            Record currRecord;
            while (overflowBuffer.hasRemaining()) {
                currRecord = new Record(overflowBuffer.getInt(), overflowBuffer.getDouble(),
                        overflowBuffer.getDouble(),
                        overflowBuffer.getInt());
                if (currRecord.getKey() == 0) {
                    overflowBuffer.position(overflowBuffer.position() - Record.RECORD_SIZE_ON_DISK);
                    overflowBuffer.putInt(record.getKey());
                    overflowBuffer.putDouble(record.getVoltage());
                    overflowBuffer.putDouble(record.getCurrent());
                    overflowBuffer.putInt(overflow); // put correct pointer to preserve sorted linked list
                    overflowRecordsCount++;
                    writeBufferToOverflowFile(currentOverflowPageLoaded);
                    return;
                }
            }
        }

        if (pageNumber != currentMainPageLoaded) {
            readMainPageIntoBuffer(pageNumber);
        }

        // LEGACY CHECK
        /*
         * if (checkForRecordInCurrentBuffer(record.getKey()) != null) {
         * System.out.printf("Record already exists!\n");
         * return;
         * }
         */

        Record currRecord;

        while (mainBuffer.hasRemaining()) {
            currRecord = new Record(mainBuffer.getInt(), mainBuffer.getDouble(), mainBuffer.getDouble(),
                    mainBuffer.getInt());

            // found viable place for the record in main area
            if (currRecord.getKey() == 0) {
                mainBuffer.position(mainBuffer.position() - Record.RECORD_SIZE_ON_DISK);
                mainBuffer.putInt(record.getKey());
                mainBuffer.putDouble(record.getVoltage());
                mainBuffer.putDouble(record.getCurrent());
                mainBuffer.putInt(record.getOverflow());
                writeBufferToMainFile(pageNumber);
                mainBuffer.position(0);
                return;
            }

            // record has to be in overflow
            if (currRecord.compareTo(record) == 1) {
                mainBuffer.position(mainBuffer.position() - Record.RECORD_SIZE_ON_DISK);
                break;
            }
        }

        mainBuffer.position(mainBuffer.position() - 4);
        int overflow = mainBuffer.getInt();
        int prevOverflow = overflow;
        // no chain
        if (overflow == -1) {
            mainBuffer.position(mainBuffer.position() - 4);
            mainBuffer.putInt(overflowRecordsCount);
            mainBuffer.position(0);
            writeBufferToMainFile(currentMainPageLoaded);
        } else {
            int recordIndex = 0;
            while (overflow != -1) {
                int overflowPosition[] = determineOverflowPosition(overflow);
                readOverflowPageIntoBuffer(overflowPosition[0]);
                overflowBuffer.position(overflowPosition[1] * Record.RECORD_SIZE_ON_DISK);

                recordIndex = overflowBuffer.getInt();
                if (recordIndex == 0 || recordIndex < record.getKey()) {
                    mainBuffer.position(0);
                    overflowBuffer.position(0);
                    break;
                }
                Double recordVoltage = overflowBuffer.getDouble();
                Double recordCurrent = overflowBuffer.getDouble();
                prevOverflow = overflow;
                overflow = overflowBuffer.getInt();
            }
            // record belongs to beginning of chain
            if (overflow == prevOverflow) {
                mainBuffer.position(mainBuffer.position() - 4);
                mainBuffer.putInt(overflowRecordsCount);
                mainBuffer.position(0);
                writeBufferToMainFile(currentMainPageLoaded);
            } else if (recordIndex != 0) {
                int overflowPosition[] = determineOverflowPosition(prevOverflow);
                readOverflowPageIntoBuffer(overflowPosition[0]);
                overflowBuffer.position(overflowPosition[1] * Record.RECORD_SIZE_ON_DISK);
                recordIndex = overflow; // save the pointer (even if it is end of chain)
                overflowBuffer.position(overflowBuffer.position() - 4);
                overflowBuffer.putInt(overflowRecordsCount);
                writeBufferToOverflowFile(currentOverflowPageLoaded);
            }
        }

        int overflowPosition[] = determineOverflowPosition(overflowRecordsCount);
        readOverflowPageIntoBuffer(overflowPosition[0]);

        while (overflowBuffer.hasRemaining()) {
            currRecord = new Record(overflowBuffer.getInt(), overflowBuffer.getDouble(),
                    overflowBuffer.getDouble(),
                    overflowBuffer.getInt());
            if (currRecord.getKey() == 0) {
                overflowBuffer.position(overflowBuffer.position() - Record.RECORD_SIZE_ON_DISK);
                overflowBuffer.putInt(record.getKey());
                overflowBuffer.putDouble(record.getVoltage());
                overflowBuffer.putDouble(record.getCurrent());
                overflowBuffer.putInt(overflow);
                overflowRecordsCount++;
                writeBufferToOverflowFile(currentOverflowPageLoaded);
                return;
            }
        }
    }
}
