package qzmik;

import java.io.IOException;
import java.util.Scanner;

public class ISAMMain {
    public static void main(String[] args) throws IOException {
        ISAMManager isamManager = new ISAMManager();

        Scanner scanner = new Scanner(System.in);
        char scannedOption = 'z';
        boolean firstWrite = true;
        while (scannedOption != 'q') {
            scannedOption = scanner.next().charAt(0);
            switch (scannedOption) {
                case 'i':
                    Record record = createRecord(scanner);
                    isamManager.writeRecord(firstWrite, record);
                    firstWrite = false;
                    break;
                case 'r':
                    int key = getKeyFromUser(scanner);
                    isamManager.readRecord(key);
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
}