package com.taobao.arthas.bytekit.utils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.relationship.MemberNameResolver;
import org.benf.cfr.reader.state.ClassFileSourceImpl;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.AnalysisType;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.getopt.GetOptParser;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.DumperFactory;
import org.benf.cfr.reader.util.output.DumperFactoryImpl;
import org.benf.cfr.reader.util.output.IllegalIdentifierDump;
import org.benf.cfr.reader.util.output.StreamDumper;
import org.benf.cfr.reader.util.output.ToStringDumper;
import com.alibaba.arthas.deps.org.objectweb.asm.tree.AbstractInsnNode;
import com.alibaba.arthas.deps.org.objectweb.asm.tree.ClassNode;
import com.alibaba.arthas.deps.org.objectweb.asm.tree.InsnList;
import com.alibaba.arthas.deps.org.objectweb.asm.tree.MethodNode;
import com.alibaba.arthas.deps.org.objectweb.asm.util.Printer;
import com.alibaba.arthas.deps.org.objectweb.asm.util.Textifier;
import com.alibaba.arthas.deps.org.objectweb.asm.util.TraceClassVisitor;
import com.alibaba.arthas.deps.org.objectweb.asm.util.TraceMethodVisitor;

public class Decompiler {

	public static String decompile(byte[] bytecode) throws IOException {
		String result = "";

		File tempDirectory = FileUtils.getTempDirectory();
		File file = new File(tempDirectory, RandomStringUtils.randomAlphabetic(8));
		FileUtils.writeByteArrayToFile(file, bytecode);

		result = decompile(file.getAbsolutePath(), null);
		return result;
	}

	public static String decompile(String path) throws IOException {
		byte[] byteArray = FileUtils.readFileToByteArray(new File(path));
		return decompile(byteArray);
	}

	public static String toString(MethodNode methodNode) {
		Printer printer = new Textifier();
		TraceMethodVisitor methodPrinter = new TraceMethodVisitor(printer);

		methodNode.accept(methodPrinter);

		StringWriter sw = new StringWriter();
		printer.print(new PrintWriter(sw));
		printer.getText().clear();

		return sw.toString();
	}

	public static String toString(ClassNode classNode) {
		Printer printer = new Textifier();
		StringWriter sw = new StringWriter();
		PrintWriter printWriter = new PrintWriter(sw);

		TraceClassVisitor traceClassVisitor = new TraceClassVisitor(printWriter);

		classNode.accept(traceClassVisitor);

		printer.print(printWriter);
		printer.getText().clear();

		return sw.toString();
	}



	public static String toString(InsnList insnList) {
        Printer printer = new Textifier();
        TraceMethodVisitor mp = new TraceMethodVisitor(printer);
        insnList.accept(mp);

        StringWriter sw = new StringWriter();
        printer.print(new PrintWriter(sw));
        printer.getText().clear();
        return sw.toString();
    }

	public static String toString(AbstractInsnNode insn) {
		Printer printer = new Textifier();
		TraceMethodVisitor mp = new TraceMethodVisitor(printer);
		insn.accept(mp);

		StringWriter sw = new StringWriter();
		printer.print(new PrintWriter(sw));
		printer.getText().clear();
		return sw.toString();
	}


    /**
     * @see org.benf.cfr.reader.Main#main(String[])
     * @param classFilePath
     * @param methodName
     * @return
     */
    public static String decompile(String classFilePath, String methodName) {
        StringBuilder result = new StringBuilder(8192);

        List<String> argList = new ArrayList<String>();
        argList.add(classFilePath);
        if (methodName != null) {
            argList.add("--methodname");
            argList.add(methodName);
        }
        String args[] = argList.toArray(new String[0]);

        GetOptParser getOptParser = new GetOptParser();

        Options options = null;
        List<String> files = null;
        try {
            Pair processedArgs = getOptParser.parse(args, OptionsImpl.getFactory());
            files = (List) processedArgs.getFirst();
            options = (Options) processedArgs.getSecond();
        } catch (Exception e) {
            getOptParser.showHelp(OptionsImpl.getFactory(), e);
            System.exit(1);
        }

        if ((options.optionIsSet(OptionsImpl.HELP)) || (files.isEmpty())) {
            getOptParser.showOptionHelp(OptionsImpl.getFactory(), options, OptionsImpl.HELP);
            return "";
        }

        ClassFileSourceImpl classFileSource = new ClassFileSourceImpl(options);

        boolean skipInnerClass = (files.size() > 1)
                        && (((Boolean) options.getOption(OptionsImpl.SKIP_BATCH_INNER_CLASSES)).booleanValue());

        Collections.sort(files);

        for (String path : files) {
            classFileSource.clearConfiguration();
            DCCommonState dcCommonState = new DCCommonState(options, classFileSource);
            DumperFactory dumperFactory = new DumperFactoryImpl(options);

            path = classFileSource.adjustInputPath(path);

            AnalysisType type = (AnalysisType) options.getOption(OptionsImpl.ANALYSE_AS);
            if (type == null)
                type = dcCommonState.detectClsJar(path);

            if (type == AnalysisType.JAR) {
                // doJar(dcCommonState, path, dumperFactory);
            }
            if (type == AnalysisType.CLASS)
                result.append(doClass(dcCommonState, path, skipInnerClass, dumperFactory));
        }
        return result.toString();
    }

    public static String doClass(DCCommonState dcCommonState, String path, boolean skipInnerClass,
                    DumperFactory dumperFactory) {
        StringBuilder result = new StringBuilder(8192);
        Options options = dcCommonState.getOptions();
        IllegalIdentifierDump illegalIdentifierDump = IllegalIdentifierDump.Factory.get(options);
        Dumper d = new ToStringDumper();
        try {
            ClassFile c = dcCommonState.getClassFileMaybePath(path);
            if ((skipInnerClass) && (c.isInnerClass()))
                return "";
            dcCommonState.configureWith(c);
            dumperFactory.getProgressDumper().analysingType(c.getClassType());
            try {
                c = dcCommonState.getClassFile(c.getClassType());
            } catch (CannotLoadClassException e) {
            }
            if (((Boolean) options.getOption(OptionsImpl.DECOMPILE_INNER_CLASSES)).booleanValue()) {
                c.loadInnerClasses(dcCommonState);
            }
            if (((Boolean) options.getOption(OptionsImpl.RENAME_DUP_MEMBERS)).booleanValue()) {
                MemberNameResolver.resolveNames(dcCommonState,
                                ListFactory.newList(dcCommonState.getClassCache().getLoadedTypes()));
            }

            c.analyseTop(dcCommonState);

            TypeUsageCollector collectingDumper = new TypeUsageCollector(c);
            c.collectTypeUsages(collectingDumper);

            d = new StringDumper(collectingDumper.getTypeUsageInformation(), options, illegalIdentifierDump);

            // d = dumperFactory.getNewTopLevelDumper(c.getClassType(), summaryDumper,
            // collectingDumper.getTypeUsageInformation(), illegalIdentifierDump);

            String methname = (String) options.getOption(OptionsImpl.METHODNAME);
            if (methname == null)
                c.dump(d);
            else {
                try {
                    for (Method method : c.getMethodByName(methname))
                        method.dump(d, true);
                } catch (NoSuchMethodException e) {
                    throw new IllegalArgumentException("No such method '" + methname + "'.");
                }
            }
            d.print("");
            result.append(d.toString());
        } catch (ConfusedCFRException e) {
            result.append(e.toString()).append("\n");
            for (Object x : e.getStackTrace())
                result.append(x).append("\n");
        } catch (CannotLoadClassException e) {
            result.append("Can't load the class specified:").append("\n");
            result.append(e.toString()).append("\n");
        } catch (RuntimeException e) {
            result.append(e.toString()).append("\n");
            for (Object x : e.getStackTrace())
                result.append(x).append("\n");
        } finally {
            if (d != null)
                d.close();
        }
        return result.toString();
    }

    public static class StringDumper extends StreamDumper {
        private StringWriter sw = new StringWriter();

        public StringDumper(TypeUsageInformation typeUsageInformation, Options options,
                        IllegalIdentifierDump illegalIdentifierDump) {
            super(typeUsageInformation, options, illegalIdentifierDump);
        }

        public void addSummaryError(Method paramMethod, String paramString) {

        }

        public void close() {
            try {
                sw.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected void write(String source) {
            sw.write(source);
        }

        public String toString() {
            return sw.toString();
        }
    }
}