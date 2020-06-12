package gov.nasa.jpf.test.mc.basic;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import gov.nasa.jpf.util.FileUtils;
import gov.nasa.jpf.util.test.TestJPF;

public class JsonOutputTest extends TestJPF {
    
    @Test
    public void testJsonOutput() {
        if (verifyNoPropertyViolation( // if verifyAssertionError(
            "+listener=.listener.JsonTraceFormatter",
            "+report.console.property_violation=error,trace"
        )) {
            Racer racer = new Racer();
            Thread t = new Thread(racer);
            t.start();
  
            Racer.doSomething(1000);                   // (3)
            int c = 420 / racer.d;               // (4)
            System.out.println(c);

            String testOutputPath = "./jsonOutput.json";
            String sampleOutputPath = "./src/main/gov/nasa/jpf/resources/Racer.json.checked";
        
            File testOutput = new File(testOutputPath);
            File sampleOutput = new File(sampleOutputPath);

            try {
                String testStr = FileUtils.getContentsAsString(testOutput);
                String sampleStr = FileUtils.getContentsAsString(sampleOutput);

                assert(testStr.contentEquals(sampleStr));

                testOutput.delete();
                sampleOutput.delete();
                
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    static class Racer implements Runnable {

        int d = 42;
   
        @Override
       public void run () {
             doSomething(1001);                   // (1)
             d = 0;                               // (2)
        }
   
        // public static void main (String[] args){
        //      Racer racer = new Racer();
        //      Thread t = new Thread(racer);
        //      t.start();
   
        //      doSomething(1000);                   // (3)
        //      int c = 420 / racer.d;               // (4)
        //      System.out.println(c);
        // }
        
        static void doSomething (int n) {
             // not very interesting..
             try { Thread.sleep(n); } catch (InterruptedException ix) {}
        }
    }

    public static void main(String[] testMethods) {
        runTestsOfThisClass(testMethods);
    }
}