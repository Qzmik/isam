package qzmik;

import java.io.FileReader;
import java.io.IOException;
import java.io.BufferedReader;
import java.util.Scanner;

public class ISAMMain {
    public static final double RATIO = 0.2f;

    public static void main(String[] args) throws IOException {

        ISAMManager isamManager = new ISAMManager(0.5, RATIO);

        Scanner scanner = new Scanner(System.in);
        char scannedOption = 'z';
        boolean firstWrite = true;
        int key;
        Record record;
        System.out.printf("ISAM Mikołaj Kuźmicz s198291\n");
        System.out.printf("Press l to load from file then go into interactive mode\n");
        System.out.printf("Press i to go into interactive mode\n");

        while (scannedOption != 'i') {
            scannedOption = scanner.next().charAt(0);
            if (scannedOption == 'l') {
                System.out.printf("Type in the name of the file: ");
                String fileName = scanner.next();
                try {
                    FileReader fr = new FileReader(fileName);
                    BufferedReader br = new BufferedReader(fr);
                    String line;

                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        char option = line.charAt(0);
                        switch (option) {
                            case 'i':
                                fileInsert(isamManager, line, firstWrite);
                                firstWrite = false;
                                break;
                            case 'u':
                                fileUpdate(isamManager, line, firstWrite);
                                break;
                            case 'd':
                                fileDelete(isamManager, line);
                                break;
                        }
                    }
                    br.close();
                    scannedOption = 'i';
                } catch (Exception e) {
                    System.out.printf("INVALID FILE, ABORTING\n");
                    System.out.println(e.getMessage());
                    scanner.close();
                    return;
                }
            }
        }

        System.out.printf("q - quit\ni - insert\nd - delete\nr - read \np - print \nu - update\no - (re)organize\n");
        scannedOption = 'z';
        while (scannedOption != 'q') {
            scannedOption = scanner.next().charAt(0);
            switch (scannedOption) {
                case 'i':
                    record = createRecord(scanner);
                    isamManager.writeRecord(firstWrite, record, true);
                    firstWrite = false;
                    performReorgIfNeeded(isamManager);
                    break;
                case 'd':
                    key = getKeyFromUser(scanner);
                    isamManager.deleteRecord(key, true);
                    break;
                case 'r':
                    key = getKeyFromUser(scanner);
                    isamManager.readRecord(key, true);
                    break;
                case 'u':
                    key = getKeyFromUser(scanner);
                    record = createRecord(scanner);
                    isamManager.updateRecord(key, record, true, false);
                    performReorgIfNeeded(isamManager);
                    break;
                case 'p':
                    isamManager.printISAM();
                    break;
                case 'o':
                    isamManager.reorganize();
                    break;
            }
        }
        scanner.close();
    }

    private static Record createRecord(Scanner scanner) {
        System.out.printf("Both double fields are limited from 0 to 100 (0 excluded, 100 included)\n");
        System.out.printf(
                "A record consists of a natural number key and 2 double values, representing voltage and current\n");
        System.out.printf("Type in the key of the record: ");
        int key = scanner.nextInt();
        System.out.printf("Type in the voltage of the record: ");
        double voltage = scanner.nextDouble();
        System.out.printf("Type in the current of the record: ");
        double current = scanner.nextDouble();
        return new Record(key, voltage, current, -1);
    }

    private static int getKeyFromUser(Scanner scanner) {
        System.out.printf("Type in the key of the record you're looking for: ");
        int key = scanner.nextInt();
        return key;
    }

    private static void performReorgIfNeeded(ISAMManager isamManager) throws IOException {
        if (isamManager.recordManager.overflowRecordsCount / RATIO >= isamManager.recordManager.mainRecordsCount) {
            System.out.printf("Performing mandatory reorganization\n");
            isamManager.reorganize();
        }
    }

    private static void fileInsert(ISAMManager isamManager, String line, boolean firstWrite) throws IOException {
        String tokens[] = line.split(" ");
        int key = Integer.parseInt(tokens[1]);
        double voltage = Double.parseDouble(tokens[2]);
        double current = Double.parseDouble(tokens[3]);
        Record record = new Record(key, voltage, current, -1);
        isamManager.writeRecord(firstWrite, record, false);
        performReorgIfNeeded(isamManager);
    }

    private static void fileUpdate(ISAMManager isamManager, String line, boolean firstWrite) throws IOException {
        String tokens[] = line.split(" ");
        int origkey = Integer.parseInt(tokens[1]);
        int newKey = Integer.parseInt(tokens[2]);
        double voltage = Double.parseDouble(tokens[3]);
        double current = Double.parseDouble(tokens[4]);
        Record record = new Record(newKey, voltage, current, -1);
        isamManager.updateRecord(origkey, record, false, false);
        performReorgIfNeeded(isamManager);
    }

    private static void fileDelete(ISAMManager isamManager, String line) throws IOException {
        String tokens[] = line.split(" ");
        int key = Integer.parseInt(tokens[1]);
        isamManager.deleteRecord(key, false);
        performReorgIfNeeded(isamManager);
    }

}