package gov.nasa.jpf.listener;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

class JsonPrintWriter extends PrintWriter {

    public JsonPrintWriter(String path) throws IOException{
        super(path);
    }

    public JsonPrintWriter(FileOutputStream fos, boolean bool) throws IOException {
        super(fos, bool);
    }

    public void startBrace() throws IOException {
        write("{\n");
    }

    public void endBrace() throws IOException {
        write("}\n");
    }

    public void delim() throws IOException {
        write(",\n");
    }

    public void quote() throws IOException {
        write("\"");
    }

    public void startBracket() throws IOException {
        write("[\n");
    }

    public void endBracket() throws IOException {
        write("]\n");
    }

    public void colon() throws IOException {
        write(" : ");
    }

    public void assign(String left) throws IOException {
        quote();
        write(left);
        quote();
        colon();
    }

    public void assign(String left, String right) throws IOException {
        quote();
        write(left);
        quote();
        colon();

        if (right != null) {
            quote();
            write(right);
            quote();
        } else
            write("null");
    }

    public void assign(String left, Integer right) throws IOException {
        quote();
        write(left);
        quote();
        colon();

        write(String.valueOf(right));
    }

    public void assign(String left, Boolean right) throws IOException {
        quote();
        write(left);
        quote();
        colon();

        write(String.valueOf(right));
    }

    public void writeString(String s) throws IOException {
        quote();
        if (s != null){
            write(s);
        }
        quote();
    }
}