package qzmik;

import java.io.IOException;

public class ISAMManager {

    private IndexManager indexManager;
    private RecordManager recordManager;

    public ISAMManager() throws IOException {
        indexManager = new IndexManager();
        recordManager = new RecordManager();
    }

    private void displayOperationPerformance(int oldIndexRWD[], int oldRecordRWD[], int newIndexRWD[],
            int newRecordRWD[]) {
        System.out.printf("Operation stats:\nIR: %d\nIW: %d\nMR: %d\nMW: %d\nOR: %d\nOW: %d\n",
                newIndexRWD[0] - oldIndexRWD[0],
                newIndexRWD[1] - oldIndexRWD[1], newRecordRWD[0] - oldRecordRWD[0], newRecordRWD[1] - oldRecordRWD[1],
                newRecordRWD[2] - oldRecordRWD[2], newRecordRWD[3] - oldRecordRWD[3]);
    }

    private Record readRecordSilent(int key) throws IOException {

        Record potentialRecord = recordManager.checkForRecordInCurrentBuffer(key);
        if (potentialRecord != null) {
            return potentialRecord;
        }
        int pageNumber = indexManager.findPageNumber(key);
        potentialRecord = recordManager.readRecord(pageNumber, key);
        return potentialRecord;
    }

    public void readRecord(int key, boolean displayData) throws IOException {

        int oldIndexReadWriteData[] = indexManager.giveReadWriteData();
        int oldRecordReadWriteData[] = recordManager.giveReadWriteData();

        Record potentialRecord = recordManager.checkForRecordInCurrentBuffer(key);

        if (potentialRecord != null && potentialRecord.getVoltage() != 0) {
            int newIndexReadWriteData[] = indexManager.giveReadWriteData();
            int newRecordReadWriteData[] = recordManager.giveReadWriteData();

            if (displayData) {
                System.out.printf("Record found\nKey: %d Voltage: %f Current: %f\n", potentialRecord.getKey(),
                        potentialRecord.getVoltage(), potentialRecord.getCurrent());

                displayOperationPerformance(oldIndexReadWriteData, oldRecordReadWriteData, newIndexReadWriteData,
                        newRecordReadWriteData);
            }
            return;
        }

        int pageNumber = indexManager.findPageNumber(key);
        potentialRecord = recordManager.readRecord(pageNumber, key);

        int newIndexReadWriteData[] = indexManager.giveReadWriteData();
        int newRecordReadWriteData[] = recordManager.giveReadWriteData();
        if (displayData) {
            if (potentialRecord != null && potentialRecord.getVoltage() != 0) {
                System.out.printf("Record found\n Key: %d Voltage: %f Current: %f\n", potentialRecord.getKey(),
                        potentialRecord.getVoltage(), potentialRecord.getCurrent());
            } else {
                System.out.printf("Record not found\n");
            }
            displayOperationPerformance(oldIndexReadWriteData, oldRecordReadWriteData, newIndexReadWriteData,
                    newRecordReadWriteData);
        }

        return;
    }

    public boolean writeRecord(boolean firstWrite, Record record, boolean displayData) throws IOException {
        int oldIndexReadWriteData[] = indexManager.giveReadWriteData();
        int oldRecordReadWriteData[] = recordManager.giveReadWriteData();

        if (firstWrite) {
            indexManager.createStartingIndexPage(record.getKey());
            recordManager.writeRecord(record, 0);
            return true;
        }

        Record potentialRecord = readRecordSilent(record.getKey());

        if (potentialRecord != null) {
            if (potentialRecord.getVoltage() != 0) {
                if (displayData) {
                    System.out.printf("Record already exists!\n");
                    int newIndexReadWriteData[] = indexManager.giveReadWriteData();
                    int newRecordReadWriteData[] = recordManager.giveReadWriteData();
                    displayOperationPerformance(oldIndexReadWriteData, oldRecordReadWriteData, newIndexReadWriteData,
                            newRecordReadWriteData);
                }
                return false;
            }
            if (potentialRecord.getVoltage() == 0) {
                updateRecord(record.getKey(), record, false);
                if (displayData) {
                    int newIndexReadWriteData[] = indexManager.giveReadWriteData();
                    int newRecordReadWriteData[] = recordManager.giveReadWriteData();
                    displayOperationPerformance(oldIndexReadWriteData, oldRecordReadWriteData, newIndexReadWriteData,
                            newRecordReadWriteData);
                }
                return true;
            }
        }

        int pageNumber = indexManager.findPageNumber(record.getKey());
        recordManager.writeRecord(record, pageNumber);
        int newIndexReadWriteData[] = indexManager.giveReadWriteData();
        int newRecordReadWriteData[] = recordManager.giveReadWriteData();
        if (displayData)
            displayOperationPerformance(oldIndexReadWriteData, oldRecordReadWriteData, newIndexReadWriteData,
                    newRecordReadWriteData);
        return true;
    }

    public void deleteRecord(int key, boolean displayData) throws IOException {
        int oldIndexReadWriteData[] = indexManager.giveReadWriteData();
        int oldRecordReadWriteData[] = recordManager.giveReadWriteData();

        int pageNumber = indexManager.findPageNumber(key);
        recordManager.updateRecord(pageNumber, new Record(key, 0, 0, -1));
        int newIndexReadWriteData[] = indexManager.giveReadWriteData();
        int newRecordReadWriteData[] = recordManager.giveReadWriteData();
        if (displayData)
            displayOperationPerformance(oldIndexReadWriteData, oldRecordReadWriteData, newIndexReadWriteData,
                    newRecordReadWriteData);
        return;
    }

    public void updateRecord(int originalKey, Record record, boolean displayData) throws IOException {
        int oldIndexReadWriteData[] = indexManager.giveReadWriteData();
        int oldRecordReadWriteData[] = recordManager.giveReadWriteData();

        if (record.getKey() != originalKey) {
            if (writeRecord(false, record, false)) {
                deleteRecord(originalKey, displayData);
            } else {
                if (displayData) {
                    System.out.printf("Record with specified key already exists!");
                    int newIndexReadWriteData[] = indexManager.giveReadWriteData();
                    int newRecordReadWriteData[] = recordManager.giveReadWriteData();
                    if (displayData)
                        displayOperationPerformance(oldIndexReadWriteData, oldRecordReadWriteData,
                                newIndexReadWriteData,
                                newRecordReadWriteData);
                }
            }
        } else {
            int pageNumber = indexManager.findPageNumber(record.getKey());
            recordManager.updateRecord(pageNumber, record);
            int newIndexReadWriteData[] = indexManager.giveReadWriteData();
            int newRecordReadWriteData[] = recordManager.giveReadWriteData();
            if (displayData)
                displayOperationPerformance(oldIndexReadWriteData, oldRecordReadWriteData,
                        newIndexReadWriteData,
                        newRecordReadWriteData);
        }
    }

    public void printISAM() throws IOException {
        indexManager.printIndexFile();
        recordManager.printRecordFile();
        recordManager.printOverflowFile();
    }

    public void reorganize() throws IOException {

    }
}
