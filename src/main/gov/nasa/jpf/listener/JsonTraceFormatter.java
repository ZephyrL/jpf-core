package gov.nasa.jpf.listener;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.Error;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.ListenerAdapter;
import gov.nasa.jpf.annotation.JPFOption;
import gov.nasa.jpf.jvm.bytecode.JVMFieldInstruction;
import gov.nasa.jpf.jvm.bytecode.JVMInvokeInstruction;
import gov.nasa.jpf.jvm.bytecode.JVMReturnInstruction;
import gov.nasa.jpf.jvm.bytecode.JVMStaticFieldInstruction;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.util.Left;
import gov.nasa.jpf.util.Pair;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.ClassInfo;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.VM;
import gov.nasa.jpf.vm.MethodInfo;
import gov.nasa.jpf.vm.Path;
import gov.nasa.jpf.vm.Step;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.Transition;
import gov.nasa.jpf.report.ConsolePublisher;
import gov.nasa.jpf.report.Publisher;

import java.io.FileOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;


public class JsonTraceFormatter extends ListenerAdapter {

    public JsonTraceFormatter (Config config, JPF jpf) {        
    }

    @Override 
    public void searchFinished(Search search) {

        JsonPrintWriter out;

        String outputFilePath = "./jsonOutput.json";
        try (FileOutputStream fos = new FileOutputStream(outputFilePath)) {
            out = new JsonPrintWriter(fos, true);

            // out.println("SearchFinish");
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

        writer.assign("time", String.valueOf(new Date().getTime()));
        
		writer.endBrace();
    }

    private void writeTransition(JsonPrintWriter writer, Path path, int tranId) throws IOException {

        Transition tran = path.get(tranId);
		ChoiceGenerator<?> testCg = tran.getChoiceGenerator();

		writer.startBrace();

		writer.assign("transitionId", tranId);
		writer.delim();
		
		// writer thread info (with brace)
        ThreadInfo tInfo = tran.getThreadInfo();
        writeThreadInfo(writer, tInfo);
        writer.delim();

        // write choice generator (without brace)
        ChoiceGenerator<?> cg = tran.getChoiceGenerator();
        writeChoiceGenerator(writer, cg);
    
        // write steps
        List<Pair<Integer, Step> > stepList = new ArrayList<>();
        writeSteps(writer, tran, stepList);

        writeInstructions(writer, stepList);

        // --- write lttng-alike context ---
        
        // awake
		if (testCg.getId().equals("START") || testCg.getId().equals("JOIN")) {
			writer.delim();
			ThreadInfo next = path.get(testCg.getTotalNumberOfChoices() - 1).getThreadInfo();

			writer.assign("currentThreadName", next.getName()); // last choice
			writer.delim();

			writer.assign("tid", next.getId());
		}

        // switch
		if (tranId > 0) {
			Transition prevTran = path.get(tranId - 1);

			int thisTid = tran.getThreadInfo().getId();
			int prevTid = prevTran.getThreadInfo().getId();

			if (thisTid == prevTid) {
                writer.endBrace();
                return;
			}
        }
        
        writer.delim();
        Transition prevTran = tranId > 0 ? path.get(tranId - 1) : path.get(tranId);

        int thisTid = tran.getThreadInfo().getId();
        int prevTid = prevTran.getThreadInfo().getId();

        writer.assign("switchThread", true);
        writer.delim();

        writer.assign("prevTid", prevTid);
        writer.delim();

        writer.assign("prevThreadName", prevTran.getThreadInfo().getName());
        writer.delim();

        writer.assign("nextTid", thisTid);
        writer.delim();

        writer.assign("nextThreadName", tran.getThreadInfo().getName());

		writer.endBrace();

    }

	private void writeThreadInfo(JsonPrintWriter writer, ThreadInfo ti) throws IOException {

		writer.assign("threadInfo");

		writer.startBrace();

		writer.assign("threadId", ti.getId());
		writer.delim();

        writer.assign("threadName", ti.getName());
        writer.delim();
        
        writer.assign("threadEntryMethod", ti.getEntryMethod().getFullName());
        writer.delim();

        writer.assign("threadState", ti.getStateName());

		writer.endBrace();
    }
    
    private void writeChoiceGenerator(JsonPrintWriter writer, ChoiceGenerator<?> cg) throws IOException {

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
        writer.delim();
    }

    private void writeSteps(JsonPrintWriter writer, Transition tran, List<Pair<Integer, Step>> stepList) throws IOException {
        
		Integer numStep = tran.getStepCount();

		// write steps
		writer.assign("steps");
		writer.startBracket();

		String lastLine = "";
		int nNoSrc = 0;
		boolean firstLine = true;
		int textHeight = 0;		
        

		// for each step of transition
		for (int si = 0; si < numStep; si++) {
			Step s = tran.getStep(si);
			// writeStep(writer, s);

			String line = s.getLineString();
			if (line != null) {
				String src = safeString(line);

				if (!line.equals(lastLine) && src.length() > 0) {					
					if (firstLine){
						firstLine = false;
					} else {
						writer.delim();
					}

					if (nNoSrc > 0) {
						String noSrc = " [" + nNoSrc + " insn w/o sources]";
						// tempStr.append(noSrc + "\n");
						writer.writeString(noSrc);
						writer.delim();

						textHeight++;
					}

					writer.writeString(" " + Left.format(s.getLocationString(), 20) + ": " + src);

					stepList.add(new Pair<>(textHeight, s));

					textHeight++;

					nNoSrc = 0;
				}
			} else { // no source
				// what could be retrieved from insn without source?
				nNoSrc++;
			}
			lastLine = line;
		}
		writer.endBracket();
		writer.delim();

		writer.assign("numSteps", textHeight);
		writer.delim();
    }

    private void writeInstructions(JsonPrintWriter writer, List<Pair<Integer, Step>> stepList) throws IOException {
		writer.assign("instructions");
		writer.startBracket();
		for (int si = 0; si < stepList.size(); si++ ) {

			Pair<Integer,Step> pair = stepList.get(si);
			Integer height = pair._1;
			Step s = pair._2;

			Instruction insn = s.getInstruction();
			writeInstruction(writer, insn, height);

			if (si != stepList.size() - 1) {
				 writer.delim();
			}
		}
		writer.endBracket();
    }

    private void writeInstruction(JsonPrintWriter writer, Instruction insn, int height) throws IOException  
	{
        writer.startBrace();
        
        String src = insn.getSourceLine();

        checkLockUnlock(writer, insn, src);   

        checkMethodCall(writer, insn, src);

        checkFieldAccess(writer, insn, src);


		writer.assign("stepLocation", height);
		writer.delim();

		writer.assign("fileLocation", insn.getFileLocation());

		writer.endBrace();
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