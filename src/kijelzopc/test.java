/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package kijelzopc;

import gui.kijelzoGUI;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jaraics <jaraics+code at google.com>
 */
public class test {
public test(){
    final kijelzoGUI gui = new gui.kijelzoGUI();
    String[] books = readBooks("konyvek.txt");
    gui.setBook(books[0]);
    gui.setNumber("123");
    gui.show();
}
     private String[] readBooks(String filename) {
        ArrayList<String> result = new ArrayList<String>();
        try {
            BufferedReader input = new BufferedReader(new FileReader(filename));
            try {
                String line = null;

                while ((line = input.readLine()) != null) {
                    result.add(line);
                }

            } finally {
                input.close();
            }
        } catch (FileNotFoundException ex) {
            log(ex);
        } catch (IOException ex) {
            log(ex);
        }
        return result.toArray(new String[result.size()]);
    }

    private void log(String message)
    {
        System.out.println(message);
    }

    private void log(Throwable ex)
    {
        log(ex.getMessage());

    }

     public static void main(String[] args) {
         new test();
     }
     }
