package qzmik;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IndexRecord {

    private Integer key;
    private Integer pageNumber;
    public static final int MIN_RANGE = 1;
    public static final int MAX_RANGE = 100;
    public static final int RECORD_SIZE_ON_DISK = 8;

    public IndexRecord(int keyToSet, int pageNumberToSet) {
        key = keyToSet;
        pageNumber = pageNumberToSet;
    }
}
