import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.util.Scanner;

class DataParser {

    static int[] xs;
    static int[] ys;
    static int[] open_times;
    static int[] close_times;

    public static int getNodeCount(String filePath) {
        File file = new File(filePath);
        if (!file.exists() || file.length() == 0)
            return 0;

        try (RandomAccessFile fileRandom = new RandomAccessFile(file, "r")) {
            long length = fileRandom.length();
            long lastIndex = length - 1;
            StringBuilder lastLine = new StringBuilder();

            while (lastIndex >= 0) {
                fileRandom.seek(lastIndex);
                char ch = (char) fileRandom.readByte();
                if (ch == '\n' && lastLine.toString().trim().length() > 0)
                    break;

                lastLine.insert(0, ch);
                lastIndex--;
            }
            String line = lastLine.toString().trim();
            if (line.isEmpty())
                return 0;

            int firstSpaceIndex = line.indexOf(' ');

            if (firstSpaceIndex == -1)
                return 0;
            return (Integer.parseInt(line.substring(0, firstSpaceIndex)) + 1);

        } catch (IOException e) {
            return 0;
        }
    }

    public static void init_arrays(String filePath) {
        int number_of_nodes = getNodeCount(filePath);
        xs = new int[number_of_nodes];
        ys = new int[number_of_nodes];
        open_times = new int[number_of_nodes];
        close_times = new int[number_of_nodes];

        File file = new File(filePath);
        if (!file.exists() || file.length() == 0)
            return;

        try (Scanner input = new Scanner(file)) {
            while (input.hasNextInt()) {
                int id = input.nextInt();
                xs[id] = input.nextInt();
                ys[id] = input.nextInt();
                open_times[id] = input.nextInt();
                close_times[id] = input.nextInt();
            }
        } catch (FileNotFoundException e) {
            return;
        }
    }
}