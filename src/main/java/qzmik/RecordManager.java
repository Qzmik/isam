package qzmik;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

public class RecordManager {

    public static final int BLOCKING_FACTOR = 4;
    private double ALPHA;
    private double OVERFLOW_TO_MAIN_RATIO;
    private ByteBuffer mainBuffer;
    private RecordFile mainFile;
    private RecordFile reorganizationMainFile;
    private int mainPagesCount = 0;
    public int mainRecordsCount = 0;
    private int currentMainPageLoaded = -1;
    private int mainPageReads = 0;
    private int mainPageWrites = 0;

    private String dodger = "0";

    // this points at the first record in overflow that COULD NOT fit into main page
    // due to its key being the smallest
    private int specialOverflowPointer = -1;

    private ByteBuffer overflowBuffer;
    private RecordFile overflowFile;
    private RecordFile reorganizationOverflowFile;
    private int overflowPagesCount = 0;
    public int overflowRecordsCount = 0;
    private int currentOverflowPageLoaded = -1;
    private int overflowPageReads = 0;
    private int overflowPageWrites = 0;

    public RecordManager(double alpha, double ratio) throws FileNotFoundException, IOException {
        ALPHA = alpha;
        OVERFLOW_TO_MAIN_RATIO = ratio;
        mainFile = new RecordFile("mainFile" + dodger);
        overflowFile = new RecordFile("overflowFile" + dodger);
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

    public int[] determineOverflowPosition(int overflow) {
        // pageNumber, position on page
        int overflowPosition[] = { overflow / BLOCKING_FACTOR, overflow % BLOCKING_FACTOR };
        return overflowPosition;
    }

    public int[] giveReadWriteData() {
        int readWriteData[] = { mainPageReads, mainPageWrites, overflowPageReads, overflowPageWrites };
        return readWriteData;
    }

    public Record readRecord(int pageNumber, int key) throws IOException {
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
                int recordKey = overflowBuffer.getInt();
                // EOF
                if (recordKey == 0) {
                    mainBuffer.position(0);
                    overflowBuffer.position(0);
                    break;
                }
                Double recordVoltage = overflowBuffer.getDouble();
                Double recordCurrent = overflowBuffer.getDouble();
                overflow = overflowBuffer.getInt();

                if (recordKey == key) {
                    mainBuffer.position(0);
                    overflowBuffer.position(0);
                    return new Record(key, recordVoltage, recordCurrent, overflow);
                }
            }
            return null;
        }

        readMainPageIntoBuffer(pageNumber); // this is after looking for record in current buffer

        return checkForRecordInCurrentBuffer(key);
    }

    public Record checkForRecordInCurrentBuffer(int key) throws IOException {
        mainBuffer.position(0);
        overflowBuffer.position(0);
        while (mainBuffer.hasRemaining()) {
            int recordKey = mainBuffer.getInt();
            Double recordVoltage = mainBuffer.getDouble();
            Double recordCurrent = mainBuffer.getDouble();
            int overflow = mainBuffer.getInt();
            if (recordKey == key) {
                mainBuffer.position(0);
                return new Record(key, recordVoltage, recordCurrent, overflow);
            }
            if (recordKey > key) {
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
            mainBuffer.position(0);
            return null;
        }
        while (overflow != -1) {
            int overflowPosition[] = determineOverflowPosition(overflow);
            readOverflowPageIntoBuffer(overflowPosition[0]);
            overflowBuffer.position(overflowPosition[1] * Record.RECORD_SIZE_ON_DISK);
            int recordKey = overflowBuffer.getInt();
            Double recordVoltage = overflowBuffer.getDouble();
            Double recordCurrent = overflowBuffer.getDouble();
            overflow = overflowBuffer.getInt();

            // EOF
            if (recordKey == 0) {
                mainBuffer.position(0);
                overflowBuffer.position(0);
                break;
            }

            if (recordKey == key) {
                mainBuffer.position(0);
                overflowBuffer.position(0);
                return new Record(key, recordVoltage, recordCurrent, overflow);
            }
        }
        mainBuffer.position(0);
        overflowBuffer.position(0);
        return null;
    }

    public void writeRecord(Record record, int pageNumber) throws IOException {
        // directed to special overflow
        if (pageNumber == -1) {
            int recordKey = 0;
            int overflow = specialOverflowPointer;
            int prevOverflow = specialOverflowPointer;
            if (overflow == -1) {
                specialOverflowPointer = overflowRecordsCount;
            } else {
                while (overflow != -1) {
                    int overflowPosition[] = determineOverflowPosition(overflow);
                    readOverflowPageIntoBuffer(overflowPosition[0]);
                    overflowBuffer.position(overflowPosition[1] * Record.RECORD_SIZE_ON_DISK);

                    recordKey = overflowBuffer.getInt();
                    if (recordKey == 0 || recordKey > record.getKey()) {
                        mainBuffer.position(0);
                        overflowBuffer.position(0);
                        break;
                    }
                    overflowBuffer.getDouble();
                    overflowBuffer.getDouble();
                    prevOverflow = overflow;
                    overflow = overflowBuffer.getInt();
                }
                if (overflow == specialOverflowPointer) {
                    specialOverflowPointer = overflowRecordsCount;
                } else {
                    if (recordKey != 0) {
                        int overflowPosition[] = determineOverflowPosition(prevOverflow);
                        readOverflowPageIntoBuffer(overflowPosition[0]);
                        overflowBuffer.position(overflowPosition[1] * Record.RECORD_SIZE_ON_DISK);
                        overflowBuffer.position(overflowBuffer.position() + 20);
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
                    mainBuffer.position(0);
                    overflowBuffer.position(0);
                    return;
                }
            }
            return;
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
                mainBuffer.putInt(-1);
                writeBufferToMainFile(pageNumber);
                mainRecordsCount++;
                mainBuffer.position(0);
                overflowBuffer.position(0);
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
            writeBufferToMainFile(currentMainPageLoaded);
        } else {
            int recordKey = 0;
            while (overflow != -1) {
                int overflowPosition[] = determineOverflowPosition(overflow);
                readOverflowPageIntoBuffer(overflowPosition[0]);
                overflowBuffer.position(overflowPosition[1] * Record.RECORD_SIZE_ON_DISK);

                recordKey = overflowBuffer.getInt();
                if (recordKey == 0 || recordKey > record.getKey()) {
                    mainBuffer.position(0);
                    overflowBuffer.position(0);
                    break;
                }
                overflowBuffer.getDouble();
                overflowBuffer.getDouble();
                prevOverflow = overflow;
                overflow = overflowBuffer.getInt();
            }
            // record belongs to beginning of chain
            if (overflow == prevOverflow) {
                mainBuffer.position(mainBuffer.position() + 20);
                mainBuffer.putInt(overflowRecordsCount);
                writeBufferToMainFile(currentMainPageLoaded);
            } else if (recordKey != 0) {
                int overflowPosition[] = determineOverflowPosition(prevOverflow);
                readOverflowPageIntoBuffer(overflowPosition[0]);
                overflowBuffer.position(overflowPosition[1] * Record.RECORD_SIZE_ON_DISK);
                recordKey = overflow; // save the pointer (even if it is end of chain)
                overflowBuffer.position(overflowBuffer.position() + 20);
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
                mainBuffer.position(0);
                overflowBuffer.position(0);
                return;
            }
        }
        mainBuffer.position(0);
        overflowBuffer.position(0);
    }

    public void updateRecord(int pageNumber, Record record, boolean fromWrite) throws IOException {
        mainBuffer.position(0);
        overflowBuffer.position(0);
        if (pageNumber == -1) {
            int overflow = specialOverflowPointer;
            if (overflow == -1) {
                System.out.printf("Record does not exist!");
                return;
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
                if (recordIndex == record.getKey()) {
                    if (overflowBuffer.getDouble() == 0 && !fromWrite) {
                        System.out.printf("Record does not exist!");
                        overflowBuffer.position(0);
                        return;
                    }
                    overflowBuffer.position(overflowBuffer.position() - 8);
                    overflowBuffer.putDouble(record.getVoltage());
                    overflowBuffer.putDouble(record.getCurrent());
                    writeBufferToOverflowFile(currentOverflowPageLoaded);
                    return;
                }
                overflow = overflowBuffer.getInt();
            }
            return;
        }

        readMainPageIntoBuffer(pageNumber); // this is after looking for record in current buffer

        while (mainBuffer.hasRemaining()) {
            int recordKey = mainBuffer.getInt();

            if (recordKey == record.getKey()) {
                if (mainBuffer.getDouble() == 0 && !fromWrite) {
                    System.out.printf("Record does not exist!");
                    mainBuffer.position(0);
                    return;
                }
                mainBuffer.position(mainBuffer.position() - 8);
                mainBuffer.putDouble(record.getVoltage());
                mainBuffer.putDouble(record.getCurrent());
                writeBufferToMainFile(recordKey);
                return;
            }
            mainBuffer.getDouble();
            mainBuffer.getDouble();
            mainBuffer.getInt();

            if (recordKey > record.getKey()) {
                mainBuffer.position(mainBuffer.position() - 24);
                break;
            }

        }
        // assuming we are at a correct page, this means the record belongs to the
        // overflow if it exists

        // if after reading just the first record of current buffer it was already too
        // big, then it is not in the buffer, nor in corresponding overflow
        if (mainBuffer.position() == 0 && !fromWrite) {
            System.out.printf("Record does not exist!");
            return;
        }

        mainBuffer.position(mainBuffer.position() - 4);
        int overflow = mainBuffer.getInt();

        // no overflow chain found - no record
        if (overflow == -1) {
            System.out.printf("Record does not exist!");
            return;
        }
        while (overflow != -1) {
            int overflowPosition[] = determineOverflowPosition(overflow);
            readOverflowPageIntoBuffer(overflowPosition[0]);
            overflowBuffer.position(overflowPosition[1] * Record.RECORD_SIZE_ON_DISK);
            int recordKey = overflowBuffer.getInt();

            // EOF
            if (recordKey == 0) {
                mainBuffer.position(0);
                overflowBuffer.position(0);
                break;
            }

            if (recordKey == record.getKey()) {
                if (overflowBuffer.getDouble() == 0 && !fromWrite) {
                    System.out.printf("Record does not exist!");
                    overflowBuffer.position(0);
                    return;
                }
                overflowBuffer.position(overflowBuffer.position() - 8);
                overflowBuffer.putDouble(record.getVoltage());
                overflowBuffer.putDouble(record.getCurrent());
                writeBufferToOverflowFile(currentOverflowPageLoaded);
                mainBuffer.position(0);
                return;
            }
            overflowBuffer.getDouble();
            overflowBuffer.getDouble();
            overflow = overflowBuffer.getInt();
        }
        System.out.printf("Record does not exist!");
        return;
    }

    public void printRecordFile() throws IOException {
        mainBuffer.position(0);
        overflowBuffer.position(0);
        System.out.printf("----------------------\nRECORD FILE:\n");
        int specialOverflow = specialOverflowPointer;
        while (specialOverflow != -1) {
            System.out.printf("SPECIAL OVERFLOW CHAIN: ");
            int overflowPosition[] = determineOverflowPosition(specialOverflow);
            readOverflowPageIntoBuffer(overflowPosition[0]);
            overflowBuffer.position(overflowPosition[1] * Record.RECORD_SIZE_ON_DISK);

            int key = overflowBuffer.getInt();
            double voltage = overflowBuffer.getDouble();
            double current = overflowBuffer.getDouble();
            specialOverflow = overflowBuffer.getInt();

            if (voltage != 0) {
                System.out.printf("Record: Key - %d Voltage - %f Current - %f\n", key, voltage, current);
            } else {
                System.out.printf("Record: Key - %d DELETED\n", key);
            }
            mainBuffer.position(0);
            overflowBuffer.position(0);
        }

        for (int i = 0; i < mainPagesCount; i++) {
            System.out.printf("----------------------\nPAGE %d:\n", i + 1);
            int recordCounter = 0;
            readMainPageIntoBuffer(i);

            while (mainBuffer.hasRemaining()) {
                int key = mainBuffer.getInt();
                double voltage = mainBuffer.getDouble();
                double current = mainBuffer.getDouble();
                int overflow = mainBuffer.getInt();
                if (key != 0) {
                    recordCounter++;
                    if (voltage != 0) {
                        System.out.printf("Record: Key - %d Voltage - %f Current - %f\n", key, voltage, current);
                    } else {
                        System.out.printf("Record: Key - %d DELETED\n", key);
                    }
                    while (overflow != -1) {
                        System.out.printf("OVERFLOW CHAIN: ");
                        int overflowPosition[] = determineOverflowPosition(overflow);
                        readOverflowPageIntoBuffer(overflowPosition[0]);
                        overflowBuffer.position(overflowPosition[1] * Record.RECORD_SIZE_ON_DISK);

                        key = overflowBuffer.getInt();
                        voltage = overflowBuffer.getDouble();
                        current = overflowBuffer.getDouble();
                        overflow = overflowBuffer.getInt();

                        if (voltage != 0) {
                            System.out.printf("Record: Key - %d Voltage - %f Current - %f\n", key, voltage, current);
                        } else {
                            System.out.printf("Record: Key - %d DELETED\n", key);
                        }
                    }
                } else
                    System.out.printf("-\n");
            }
            System.out.printf("Filling: %f%%\n", 100.0 * recordCounter / BLOCKING_FACTOR);
            mainBuffer.position(0);
            overflowBuffer.position(0);
        }
    }

    public void printOverflowFile() throws IOException {
        mainBuffer.position(0);
        overflowBuffer.position(0);
        System.out.printf("----------------------\nOVERFLOW FILE:\n");
        for (int i = 0; i < overflowPagesCount; i++) {
            System.out.printf("----------------------\nPAGE %d:\n", i + 1);
            int recordCounter = 0;
            readOverflowPageIntoBuffer(i);

            while (overflowBuffer.hasRemaining()) {
                int key = overflowBuffer.getInt();
                double voltage = overflowBuffer.getDouble();
                double current = overflowBuffer.getDouble();
                overflowBuffer.getInt();
                if (key != 0) {
                    recordCounter++;
                    if (voltage != 0) {
                        System.out.printf("Record: Key - %d Voltage - %f Current - %f\n", key, voltage, current);
                    } else {
                        System.out.printf("Record: Key - %d DELETED\n", key);
                    }
                } else
                    System.out.printf("-\n");
            }
            System.out.printf("Filling: %f%%\n", 100.0 * recordCounter / BLOCKING_FACTOR);
            overflowBuffer.position(0);
        }
    }

    public void beginReorganization(long amountOfPagesToAlloc) throws IOException {

        dodger = dodger == "1" ? "0" : "1";
        reorganizationMainFile = new RecordFile("mainFile" + dodger);
        reorganizationOverflowFile = new RecordFile("overflowFile" + dodger);

        reorganizationMainFile
                .preallocate(amountOfPagesToAlloc * Record.RECORD_SIZE_ON_DISK * BLOCKING_FACTOR);
        reorganizationOverflowFile.preallocate(
                (long) (Math.ceil(amountOfPagesToAlloc * OVERFLOW_TO_MAIN_RATIO) * Record.RECORD_SIZE_ON_DISK
                        * BLOCKING_FACTOR));
    }

    public void reorganize(IndexManager indexManager) throws IOException {
        mainBuffer.position(0);
        overflowBuffer.position(0);

        ByteBuffer reorganizationBuffer = ByteBuffer.allocate(BLOCKING_FACTOR * Record.RECORD_SIZE_ON_DISK);
        ByteBuffer indexBuffer = ByteBuffer.allocate(IndexManager.BLOCKING_FACTOR * IndexRecord.RECORD_SIZE_ON_DISK);
        int pageCounter = 0;
        int recordCounter = 0;
        int fullRecordCounter = 0;
        int indexPageCounter = 0;

        int specialOverflow = specialOverflowPointer;
        while (specialOverflow != -1) {
            int overflowPosition[] = determineOverflowPosition(specialOverflow);
            readOverflowPageIntoBuffer(overflowPosition[0]);
            overflowBuffer.position(overflowPosition[1] * Record.RECORD_SIZE_ON_DISK);

            int key = overflowBuffer.getInt();
            double voltage = overflowBuffer.getDouble();
            double current = overflowBuffer.getDouble();
            specialOverflow = overflowBuffer.getInt();

            if (voltage != 0) {
                recordCounter++;
                fullRecordCounter++;
                reorganizationBuffer.putInt(key);
                reorganizationBuffer.putDouble(voltage);
                reorganizationBuffer.putDouble(current);
                reorganizationBuffer.putInt(-1);
            }
            if (recordCounter >= (int) (ALPHA * BLOCKING_FACTOR)) {
                reorganizationBuffer.position(0);
                indexBuffer.putInt(reorganizationBuffer.getInt());
                indexBuffer.putInt(pageCounter);
                if (!indexBuffer.hasRemaining()) {
                    indexPageCounter++;
                    indexManager.putIndexReorg(indexBuffer);
                    indexBuffer = ByteBuffer.allocate(BLOCKING_FACTOR * IndexRecord.RECORD_SIZE_ON_DISK);
                }
                reorganizationMainFile.position(pageCounter * BLOCKING_FACTOR * Record.RECORD_SIZE_ON_DISK);
                pageCounter++;
                reorganizationMainFile.writePageOfRecords(reorganizationBuffer.array());
                mainPageWrites++;
                reorganizationBuffer = ByteBuffer.allocate(BLOCKING_FACTOR * Record.RECORD_SIZE_ON_DISK);
                recordCounter = 0;
            }
            mainBuffer.position(0);
            overflowBuffer.position(0);
        }

        for (int i = 0; i < mainPagesCount; i++) {
            readMainPageIntoBuffer(i);

            while (mainBuffer.hasRemaining()) {
                int key = mainBuffer.getInt();
                double voltage = mainBuffer.getDouble();
                double current = mainBuffer.getDouble();
                int overflow = mainBuffer.getInt();
                if (key != 0) {
                    if (voltage != 0) {
                        recordCounter++;
                        fullRecordCounter++;
                        reorganizationBuffer.putInt(key);
                        reorganizationBuffer.putDouble(voltage);
                        reorganizationBuffer.putDouble(current);
                        reorganizationBuffer.putInt(-1);
                    }
                    if (recordCounter >= (int) (ALPHA * BLOCKING_FACTOR)) {
                        reorganizationBuffer.position(0);
                        indexBuffer.putInt(reorganizationBuffer.getInt());
                        indexBuffer.putInt(pageCounter);
                        if (!indexBuffer.hasRemaining()) {
                            indexPageCounter++;
                            indexManager.putIndexReorg(indexBuffer);
                            indexBuffer = ByteBuffer.allocate(BLOCKING_FACTOR * IndexRecord.RECORD_SIZE_ON_DISK);
                        }
                        reorganizationMainFile.position(pageCounter * BLOCKING_FACTOR * Record.RECORD_SIZE_ON_DISK);
                        pageCounter++;
                        reorganizationMainFile.writePageOfRecords(reorganizationBuffer.array());
                        mainPageWrites++;
                        reorganizationBuffer = ByteBuffer.allocate(BLOCKING_FACTOR * Record.RECORD_SIZE_ON_DISK);
                        recordCounter = 0;
                    }
                    while (overflow != -1) {
                        int overflowPosition[] = determineOverflowPosition(overflow);
                        readOverflowPageIntoBuffer(overflowPosition[0]);
                        overflowBuffer.position(overflowPosition[1] * Record.RECORD_SIZE_ON_DISK);

                        key = overflowBuffer.getInt();
                        voltage = overflowBuffer.getDouble();
                        current = overflowBuffer.getDouble();
                        overflow = overflowBuffer.getInt();

                        if (voltage != 0) {
                            recordCounter++;
                            fullRecordCounter++;
                            reorganizationBuffer.putInt(key);
                            reorganizationBuffer.putDouble(voltage);
                            reorganizationBuffer.putDouble(current);
                            reorganizationBuffer.putInt(-1);
                        }
                        if (recordCounter >= (int) (ALPHA * BLOCKING_FACTOR)) {
                            reorganizationBuffer.position(0);
                            indexBuffer.putInt(reorganizationBuffer.getInt());
                            indexBuffer.putInt(pageCounter);
                            if (!indexBuffer.hasRemaining()) {
                                indexPageCounter++;
                                indexManager.putIndexReorg(indexBuffer);
                                indexBuffer = ByteBuffer.allocate(BLOCKING_FACTOR * IndexRecord.RECORD_SIZE_ON_DISK);
                            }
                            reorganizationMainFile.position(pageCounter * BLOCKING_FACTOR * Record.RECORD_SIZE_ON_DISK);
                            pageCounter++;
                            reorganizationMainFile.writePageOfRecords(reorganizationBuffer.array());
                            mainPageWrites++;
                            reorganizationBuffer = ByteBuffer.allocate(BLOCKING_FACTOR * Record.RECORD_SIZE_ON_DISK);
                            recordCounter = 0;
                        }
                    }
                }
            }
            mainBuffer.position(0);
            overflowBuffer.position(0);
        }
        if (reorganizationBuffer.position() != 0) {
            reorganizationBuffer.position(0);
            indexBuffer.putInt(reorganizationBuffer.getInt());
            indexBuffer.putInt(pageCounter);

            indexPageCounter++;
            indexManager.putIndexReorg(indexBuffer);
            indexBuffer = ByteBuffer.allocate(BLOCKING_FACTOR * IndexRecord.RECORD_SIZE_ON_DISK);

            reorganizationMainFile.position(pageCounter * BLOCKING_FACTOR * Record.RECORD_SIZE_ON_DISK);
            pageCounter++;
            reorganizationMainFile.writePageOfRecords(reorganizationBuffer.array());
            mainPageWrites++;
            reorganizationBuffer = ByteBuffer.allocate(BLOCKING_FACTOR * Record.RECORD_SIZE_ON_DISK);
            recordCounter = 0;
        } else if (indexBuffer.position() != 0) {
            indexPageCounter++;
            indexManager.putIndexReorg(indexBuffer);
            indexBuffer = ByteBuffer.allocate(BLOCKING_FACTOR * IndexRecord.RECORD_SIZE_ON_DISK);
        }

        mainPagesCount = pageCounter;
        overflowPagesCount = (int) Math.ceil(pageCounter * OVERFLOW_TO_MAIN_RATIO);
        currentMainPageLoaded = -1;
        currentOverflowPageLoaded = -1;
        mainRecordsCount = fullRecordCounter;
        overflowRecordsCount = 0;

        dodger = dodger == "1" ? "0" : "1";

        mainBuffer = ByteBuffer.allocate(BLOCKING_FACTOR * Record.RECORD_SIZE_ON_DISK);
        overflowBuffer = ByteBuffer.allocate(BLOCKING_FACTOR * Record.RECORD_SIZE_ON_DISK);

        indexManager.resetIndex(indexPageCounter);
        specialOverflowPointer = -1;

        mainFile = reorganizationMainFile;

        reorganizationMainFile = null;
        overflowFile = reorganizationOverflowFile;
        reorganizationOverflowFile = null;

        File main = new File("workspace/mainFile" + dodger);
        main.delete();

        File overflow = new File("workspace/overflowFile" + dodger);
        overflow.delete();

        File index = new File("workspace/indexFile" + dodger);
        index.delete();

        dodger = dodger == "1" ? "0" : "1";

    }
}
