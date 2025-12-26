package qzmik;

import java.util.Random;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Record {

    private Double voltage;
    private Double current;
    private Integer key;
    private Integer overflow;
    public static final int MIN_RANGE = 1;
    public static final int MAX_RANGE = 100;
    public static final int RECORD_SIZE_ON_DISK = 24;

    public Record(int keyToSet) {
        Double[] randomRecordValues = generateRandomRecordValues();
        voltage = randomRecordValues[0];
        current = randomRecordValues[1];
        key = keyToSet;
        overflow = 0;
    }

    public Record(int keyToSet, double voltageToSet, double currentToSet, int overflowToSet) {
        key = keyToSet;
        voltage = voltageToSet;
        current = currentToSet;
        overflow = overflowToSet;
    }

    public static Double[] generateRandomRecordValues() {
        Random r = new Random();
        return new Double[] { r.nextDouble() * (MAX_RANGE - MIN_RANGE) + MIN_RANGE,
                r.nextDouble() * (MAX_RANGE - MIN_RANGE) + MIN_RANGE };
    }

    public int compareTo(Record other) {
        if (key < other.getKey())
            return -1;
        else
            return 1;
    }
}