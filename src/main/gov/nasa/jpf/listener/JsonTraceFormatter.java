package gov.nasa.jpf.listener;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.Error;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.ListenerAdapter;
import gov.nasa.jpf.jvm.bytecode.JVMFieldInstruction;
import gov.nasa.jpf.jvm.bytecode.JVMInvokeInstruction;
import gov.nasa.jpf.jvm.bytecode.JVMReturnInstruction;
import gov.nasa.jpf.jvm.bytecode.JVMStaticFieldInstruction;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.util.Left;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.Path;
import gov.nasa.jpf.vm.Step;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.Transition;


public class JsonTraceFormatter extends ListenerAdapter {

    private static int sStepCounter;
    private static int sThreadStartCounter;

    public JsonTraceFormatter (Config config, JPF jpf) {        
    }

    private static void resetCounter() {
        sStepCounter = 0;
        sThreadStartCounter = 0;
    }

    @Override 
    public void searchFinished(Search search) {

        resetCounter();
        JsonPrintWriter out;

        String outputFilePath = "./jsonOutput.json";
        try (FileOutputStream fos = new FileOutputStream(outputFilePath)) {
            out = new JsonPrintWriter(fos, true);

            List<Error> errors = search.getErrors();
            if (errors.size() >= 0) {
                for (Error e : errors) {
                    Path p = e.getPath();
                    writePath(out, p);
                }
            }
            out.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void propertyViolated(Search search) {
        System.out.println("Property Violated");
        JsonPrintWriter out;

        String outputFilePath = "./propertyViolatedJsonOutput.json";
        try (FileOutputStream fos = new FileOutputStream(outputFilePath)) {
            out = new JsonPrintWriter(fos, true);

            List<Error> errors = search.getErrors();
            if (errors.size() >= 0) {
                for (Error e : errors) {
                    Path p = e.getPath();
                    writePath(out, p);
                }
            }
            out.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writePath(JsonPrintWriter writer, Path path) throws IOException {
        writer.startBrace();
		{
			writer.assign("transitions");

            writer.startBracket();

            for (int i = 0; i < path.size(); i++) {
                writeTransition(writer, path, i);
                if (i < path.size() - 1) {
                    writer.delim();
                }
            }

            writer.endBracket();
		}
		writer.delim();
				
		writer.assign("appName", path.getApplication());
		writer.delim();

		writer.assign("type", "Java Path Finder Trace");
		writer.delim();

        writer.assign("time", String.valueOf(new Date().getTime() * 1000));
        
		writer.endBrace();
    }

    private void writeTransition(JsonPrintWriter writer, Path path, int tranId) throws IOException {

        Transition tran = path.get(tranId);

		writer.startBrace();

		writer.assign("transitionId", tranId);
		writer.delim();
		
		// writer thread info (with brace)
        writeThreadInfo(writer, path, tranId);
        writer.delim();

        // write choice generator (without brace)
        ChoiceGenerator<?> cg = tran.getChoiceGenerator();
        writeChoiceGenerator(writer, cg);
        writer.delim();
    
        // write steps and instructions
        // List<Pair<Integer, Step> > stepList = new ArrayList<>();
        writer.assign("steps");
        writer.startBracket();

        writeSteps(writer, tran);

        writer.endBracket();

        writer.endBrace();

        // writeInstructions(writer, stepList);

    }

	private void writeThreadInfo(JsonPrintWriter writer, Path path, int tranId) throws IOException {


        Transition tran = path.get(tranId);
        ThreadInfo ti = tran.getThreadInfo();
        ChoiceGenerator<?> testCg = tran.getChoiceGenerator();

		writer.assign("threadInfo");

        writer.startBrace();
        
        writer.assign("id", sStepCounter++);
        writer.delim();

		writer.assign("threadId", ti.getId());
		writer.delim();

        writer.assign("threadName", ti.getName());
        writer.delim();
        
        writer.assign("threadEntryMethod", ti.getEntryMethod().getFullName());
        writer.delim();

        writer.assign("threadState", ti.getStateName());


        // awake
		if (testCg.getId().equals("START") || testCg.getId().equals("JOIN")) {
			ThreadInfo next = path.get(testCg.getTotalNumberOfChoices() - 1).getThreadInfo();

            writer.delim();

            writer.assign("threadAwake", true);
            writer.delim();

			writer.assign("currentThreadName", next.getName()); // last choice
			writer.delim();

            writer.assign("tid", ++sThreadStartCounter);
		}

        // switch
        Transition prevTran = tranId > 0 ? path.get(tranId - 1) : path.get(tranId);

        int thisTid = tran.getThreadInfo().getId();
        int prevTid = prevTran.getThreadInfo().getId();

        if (tranId == 0 || thisTid != prevTid) {

            writer.delim();
            
            writer.assign("threadSwitch", true);
            writer.delim();

            writer.assign("prevTid", prevTid);
            writer.delim();

            writer.assign("prevThreadName", prevTran.getThreadInfo().getName());
            writer.delim();

            writer.assign("nextTid", thisTid);
            writer.delim();

            writer.assign("nextThreadName", tran.getThreadInfo().getName());
        }

		writer.endBrace();
    }
    
    private void writeChoiceGenerator(JsonPrintWriter writer, ChoiceGenerator<?> cg) throws IOException {

        writer.assign("choiceInfo");
        writer.startBrace();

        writer.assign("id", sStepCounter++);
        writer.delim();

        writer.assign("choiceId", cg.getId());
        writer.delim();

        Integer numChoices = cg.getTotalNumberOfChoices();
        writer.assign("numChoices", numChoices);
        writer.delim();

        writer.assign("currentChoice", String.valueOf(cg.getNextChoice()));
        writer.delim();

        writer.assign("choices");
        writer.startBracket();
        for (int ci = 0; ci < numChoices; ci++) {
            writer.writeString(String.valueOf(cg.getChoice(ci)));
            if (ci != numChoices - 1) {
                writer.delim();
            }
        }

        writer.endBracket();

        writer.endBrace();
    }

    private void writeSteps(JsonPrintWriter writer, Transition tran) throws IOException {
		// write steps

		String lastLine = "";
		int nNoSrc = 0;
        int textHeight = 0;	
        boolean firstElementWritten = false;

        Iterator<Step> iter = tran.iterator();
		// for each step of transition
		while (iter.hasNext()){
			Step s = iter.next();
			// writeStep(writer, s);

			String line = s.getLineString();
			if (line != null) {
				String src = safeString(line);

				if (!line.equals(lastLine) && src.length() > 0) {					
					if (firstElementWritten) {
                        writer.delim();
                    } else {
                        firstElementWritten = true;
                    }

                    writer.startBrace();

                    writer.assign("id", sStepCounter++);
                    writer.delim();

					if (nNoSrc > 0) {
						String noSrc = " [" + nNoSrc + " insn w/o sources]";
						// tempStr.append(noSrc + "\n");
                        writer.assign("noSrc", noSrc);
                        writer.delim();

						textHeight++;
                    }
                    
                    Instruction insn = s.getInstruction();
                    writeInstruction(writer, insn, textHeight);

                    writer.assign("src", " " + Left.format(s.getLocationString(), 20) + ": " + src);                        

                    writer.endBrace();

					textHeight++;

					nNoSrc = 0;
				}
			} else { 
                // increase the counter for non-source instructions
				// what could be retrieved from insn without source?
				nNoSrc++;
			}
			lastLine = line;
		}
    }

    private void writeInstruction(JsonPrintWriter writer, Instruction insn, int height) throws IOException  
	{
        String src = insn.getSourceLine();

        checkLockUnlock(writer, insn, src);   

        checkMethodCall(writer, insn, src);

        checkFieldAccess(writer, insn, src);
    }
    
    private void checkMethodCall(JsonPrintWriter writer, Instruction insn, String src) throws IOException {
        if (insn instanceof JVMInvokeInstruction) {
            writer.assign("isMethodCall", true);
            writer.delim();

            String cls = ((JVMInvokeInstruction)insn).getInvokedMethodClassName();
            String mthd = ((JVMInvokeInstruction)insn).getInvokedMethodClassName();

            writer.assign("calledMethodName", cls + "." + mthd);
            writer.delim();
        }

        if (insn instanceof JVMReturnInstruction) {
            writer.assign("isMethodReturn", true);
            writer.delim();

            writer.assign("returndMethodName", insn.getMethodInfo().getFullName());
            writer.delim();
        }

        
        if (src.contains("wait()")) {
            writer.assign("isThreadRelatedMethod", true);
            writer.delim();

            writer.assign("threadRelatedMethod", "wait");
            writer.delim();
        } else if (src.contains("notify()")) {
            writer.assign("isThreadRelatedMethod", true);
            writer.delim();

            writer.assign("threadRelatedMethod", "notify");
            writer.delim();
        } else if (src.contains("notifyAll()")) {
            writer.assign("isThreadRelatedMethod", true);
            writer.delim();

            writer.assign("threadRelatedMethod", "notifyAll");
            writer.delim();
        } 
    }

    private void checkLockUnlock(JsonPrintWriter writer, Instruction insn, String src) throws IOException {
        if (insn.getMethodInfo().isSynchronized() || insn.getMethodInfo().isSyncRelevant()) {
            writer.assign("isSynchronized", true);
            writer.delim();

            writer.assign("syncMethodName", insn.getMethodInfo().getFullName());
            writer.delim();
            
            // writer.assign("Mnemonic", insn.getMnemonic());
            // writer.delim();
        }
    }

    private void checkFieldAccess(JsonPrintWriter writer, Instruction insn, String src) throws IOException {
        if (insn instanceof JVMStaticFieldInstruction) {
            writer.assign("isFieldAccess", true);
            writer.delim();

            String cls = ((JVMStaticFieldInstruction)insn).getFieldInfo().getClassInfo().getName();
            String field = ((JVMStaticFieldInstruction)insn).getFieldName();

            writer.assign("accessedField", cls + "." + field);
            writer.delim();
        } else if (insn instanceof JVMFieldInstruction) {
            writer.assign("isFieldAccess", true);
            writer.delim();

            String cls = ((JVMFieldInstruction)insn).getFieldInfo().getClassInfo().getName();
            String field = ((JVMFieldInstruction)insn).getFieldName();

            writer.assign("accessedField", cls + "." + field);
            writer.delim();
        }
    }
    
    // replace special characters of the source line
	private String safeString(String line) {
		return line.replaceAll("/\\*.*?\\*/", "").replaceAll("//.*$", "").replaceAll("/\\*.*$", "")
				.replaceAll("^.*?\\*/", "").replaceAll("\\*.*$", "").replaceAll("\"", "'").trim();
	}
}