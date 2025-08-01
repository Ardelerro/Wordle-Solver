import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class Filter{


    public static void main(String[] args) throws IOException {
        File file = new File("./words_alpha.txt");
        File file2 = new File("./words_alpha_size_5.txt");
        BufferedReader br = new BufferedReader(new FileReader(file));
        BufferedWriter  bw = new BufferedWriter(new FileWriter(file2));

        String st;
        while ((st = br.readLine()) != null){
            if (st.length() == 5){
                bw.write(st);
                bw.newLine();
            }      
        }
        bw.flush();
        bw.close();
        br.close();
        
    }
}